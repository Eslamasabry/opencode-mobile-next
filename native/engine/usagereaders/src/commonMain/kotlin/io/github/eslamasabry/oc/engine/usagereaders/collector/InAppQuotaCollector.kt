package io.github.eslamasabry.oc.engine.usagereaders.collector

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.usagereaders.UsageServer
import io.github.eslamasabry.oc.engine.usagereaders.UsageSwitches
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64

/** Why setting up usage on In-app Ubuntu stopped. The page words it; the detail is for Details only. */
public enum class QuotaSetupFailure {
    /** In-app Ubuntu is not installed or not ready yet. */
    LinuxNotReady,

    /** There is no Node in In-app Ubuntu. */
    NoNode,

    /** Node is there but older than 20. */
    NodeTooOld,

    /** Copying the collector or its helper into In-app Ubuntu failed. */
    Copy,

    /** The token or the key files could not be prepared. */
    Prepare,

    /** The collector did not start, or did not answer in time. */
    Start,

    /** Turning it off did not finish. */
    Remove,
}

/** Where set-up is, for the progress the page shows. */
public enum class QuotaSetupStep { Checking, Copying, SignIns, Starting, Reading }

/** The end of a set-up or a turn-off. [detail] is technical text for Details only; never a token or a key. */
public sealed interface QuotaSetupResult {
    public data object Done : QuotaSetupResult

    public data class Stopped(val failure: QuotaSetupFailure, val detail: String? = null) : QuotaSetupResult
}

/**
 * Runs the optional quota collector (`tool/quota/collector.mjs`) inside the app's own In-app Ubuntu (Flutter
 * `InAppQuotaCollector`), so Usage can show Codex, GLM and MiniMax for that server.
 *
 * Security: the read token is made here from [randomBytes] (a secure source), kept in [secrets] under
 * `oc.quotaToken.<profileId>` and staged as a 0600 file; it is never in a command line or a log. Provider keys never
 * reach the app: [KEYS_HELPER_SOURCE] runs inside Ubuntu and prints counts only. It listens on 127.0.0.1:[PORT].
 *
 * B-LINUX-177 (amended): the collector runs only while something reads usage ([QuotaCollectorDemand]); starting the
 * phone's OpenCode starts none. [tokenFor] is the read path: reading the token wakes the collector first.
 */
@Suppress("LongParameterList")
public class InAppQuotaCollector(
    private val switches: UsageSwitches,
    private val linux: InAppLinux,
    private val files: RootfsFiles,
    private val secrets: SecretStore,
    private val loadCollector: suspend () -> String,
    private val randomBytes: (Int) -> ByteArray,
    private val probe: suspend (token: String) -> Boolean,
    /** This runner has In-app Ubuntu. */
    private val supported: Boolean,
    private val demand: QuotaCollectorDemand? = null,
    private val probeAttempts: Int = 20,
    private val probeDelayMs: Long = 1_000L,
) {
    /** Whether usage on In-app Ubuntu applies to [server]: the app's own server on a runner that has it. */
    public fun appliesTo(server: UsageServer?): Boolean = supported && server != null && server.inApp

    /** Set up for [profileId] and not turned off. */
    public fun isEnabled(profileId: String): Boolean = switches.bool(enabledKey(profileId)) ?: false

    /** The token for [profileId], or null when usage was never set up. */
    public suspend fun readToken(profileId: String): String? =
        secrets.read(tokenKey(profileId)).getOrNull()?.takeIf { it.isNotEmpty() }

    /** The read path for the quota gateway: the token, after making sure the collector runs. */
    public suspend fun tokenFor(server: UsageServer): String? {
        if (!appliesTo(server)) return null
        val token = readToken(server.id) ?: return null
        demand?.use(server, token)
        return token
    }

    /** The paths of OpenCode's sign-in file, OpenCode 2's own data folder first. */
    public fun authCandidates(): List<String> = listOf(OPENCODE2_AUTH_PATH, OPENCODE1_AUTH_PATH)

    /** The script the service runs. Only paths appear here, never a value. */
    public fun serviceScript(): String {
        val candidates = authCandidates().joinToString(" ")
        return "set -eu\n" +
            "umask 077\n" +
            "cd $DIR\n" +
            "node $KEYS_PATH $DIR $AGENT_PROFILES_ROOT $candidates >/dev/null 2>&1 || true\n" +
            "if [ -f $AUTH_ENV_PATH ]; then . $AUTH_ENV_PATH; fi\n" +
            "if [ -s $GLM_KEY_PATH ]; then export OCMN_GLM_KEY_FILE=$GLM_KEY_PATH; fi\n" +
            "if [ -s $MINIMAX_KEY_PATH ]; then export OCMN_MINIMAX_KEY_FILE=$MINIMAX_KEY_PATH; fi\n" +
            "export OCMN_QUOTA_READ_TOKEN_FILE=$TOKEN_PATH\n" +
            "export OCMN_QUOTA_PORT=$PORT\n" +
            "exec node $COLLECTOR_PATH\n"
    }

    private fun newToken(): String = Base64.UrlSafe.encode(randomBytes(TOKEN_BYTES)).replace("=", "")

    /** Copies [content] when it is missing or its hash changed. True when written; throws when the write failed. */
    private suspend fun place(path: String, content: String): Boolean {
        val bytes = content.encodeToByteArray()
        val current = files.read(path)
        if (current != null && DartJson.sha256Hex(current) == DartJson.sha256Hex(bytes)) return false
        if (!files.write(path, bytes)) throw FileNotWritten()
        return true
    }

    private class FileNotWritten : Exception()

    /** Puts the collector and its helper in place; true when either changed. */
    private suspend fun installFiles(): Boolean {
        val a = place(COLLECTOR_PATH, loadCollector())
        val b = place(KEYS_PATH, KEYS_HELPER_SOURCE)
        return a || b
    }

    private suspend fun stageToken(token: String) {
        if (!files.write(TOKEN_PATH, "$token\n".encodeToByteArray())) throw FileNotWritten()
    }

    private suspend fun linuxReady(): Boolean =
        linux.status().getOrNull()?.let { it.installed && it.ready } ?: false

    private suspend fun output(script: String, timeoutMs: Long): String =
        linux.run(script, timeoutMs).getOrNull()?.output.orEmpty()

    /**
     * Sets usage up: checks Node, copies the collector, makes and stages the token, prepares the key files, starts the
     * service and waits for it to answer. Safe to run again: it only changes what differs.
     */
    @Suppress("ReturnCount", "LongMethod")
    public suspend fun setUp(server: UsageServer, onStep: (QuotaSetupStep) -> Unit = {}): QuotaSetupResult {
        val id = server.id
        onStep(QuotaSetupStep.Checking)
        if (!linuxReady()) return QuotaSetupResult.Stopped(QuotaSetupFailure.LinuxNotReady)
        val major = parseNodeMajor(output(NODE_CHECK_SCRIPT, 30_000L))
            ?: return QuotaSetupResult.Stopped(QuotaSetupFailure.NoNode)
        if (major < 20) {
            return QuotaSetupResult.Stopped(
                QuotaSetupFailure.NodeTooOld,
                "Node $major is installed; the collector needs 20 or later.",
            )
        }

        onStep(QuotaSetupStep.Copying)
        val staged = attempt({ Failure.Unexpected() }) {
            installFiles()
            var stored = secrets.read(tokenKey(id)).getOrNull()
            if (stored == null || stored.length < TOKEN_MIN) {
                stored = newToken()
                if (secrets.write(tokenKey(id), stored) is Outcome.Failed) throw FileNotWritten()
            }
            stageToken(stored)
            stored
        }
        val token = when (staged) {
            is Outcome.Ok -> staged.value
            is Outcome.Failed -> return QuotaSetupResult.Stopped(
                QuotaSetupFailure.Copy,
                "Copying into In-app Ubuntu failed.",
            )
        }
        if (FILES_OK !in output(LOCK_SCRIPT, 30_000L)) {
            return QuotaSetupResult.Stopped(
                QuotaSetupFailure.Prepare,
                "The files in In-app Ubuntu could not be locked down.",
            )
        }

        onStep(QuotaSetupStep.SignIns)
        if (KEYS_OK !in output(keysScript(), 30_000L)) {
            return QuotaSetupResult.Stopped(QuotaSetupFailure.Prepare, "The sign-in helper did not finish.")
        }

        onStep(QuotaSetupStep.Starting)
        if (linux.startService(SERVICE_NAME, serviceScript(), PORT) is Outcome.Failed) {
            return QuotaSetupResult.Stopped(QuotaSetupFailure.Start, "The collector did not start.")
        }
        var answered = false
        var tries = 0
        while (tries < probeAttempts && !answered) {
            answered = probe(token)
            if (!answered) delay(probeDelayMs)
            tries++
        }
        if (!answered) {
            val log = linux.serviceLog(SERVICE_NAME).getOrNull().orEmpty()
            return QuotaSetupResult.Stopped(
                QuotaSetupFailure.Start,
                log.trim().ifEmpty { "The collector did not answer in time." },
            )
        }
        switches.putBool(enabledKey(id), true)
        // Set up and answering; it stops again once nothing reads usage.
        demand?.touch()
        return QuotaSetupResult.Done
    }

    /**
     * Brings a collector that was set up and not turned off back (called by [QuotaCollectorDemand] when a read needs
     * it). Quiet; false when it did not. One that runs with unchanged files is left alone.
     */
    @Suppress("ReturnCount")
    public suspend fun ensureRunning(server: UsageServer): Boolean {
        if (!appliesTo(server) || !isEnabled(server.id)) return false
        if (!linuxReady()) return false
        val token = readToken(server.id) ?: return false
        val started = attempt({ Failure.Unexpected() }) {
            val changed = installFiles()
            val status = linux.status().getOrNull()
            if (!changed && status != null && SERVICE_NAME in status.services) {
                true
            } else {
                stageToken(token)
                linux.run(
                    "chmod 700 $DIR && chmod 600 $TOKEN_PATH $COLLECTOR_PATH $KEYS_PATH",
                    30_000L,
                )
                linux.startService(SERVICE_NAME, serviceScript(), PORT) is Outcome.Ok
            }
        }
        val ok = started.getOrNull() == true
        if (ok) demand?.touch()
        return ok
    }

    /** Turns usage off: stops the service and removes its files, key copies, the token and the switch. */
    public suspend fun turnOff(profileId: String): QuotaSetupResult {
        demand?.cancel()
        linux.stopService(SERVICE_NAME)
        if (REMOVED !in output("rm -rf $DIR && echo \"$REMOVED\"", 60_000L)) {
            return QuotaSetupResult.Stopped(QuotaSetupFailure.Remove)
        }
        secrets.delete(tokenKey(profileId))
        switches.remove(enabledKey(profileId))
        return QuotaSetupResult.Done
    }

    private fun keysScript(): String =
        "umask 077; node $KEYS_PATH $DIR $AGENT_PROFILES_ROOT ${authCandidates().joinToString(" ")}"

    public companion object {
        public const val COLLECTOR_ASSET: String = "tool/quota/collector.mjs"
        public const val PORT: Int = 4195
        public const val SERVICE_NAME: String = "quota"
        public const val DIR: String = "/root/.oc-builtin/quota"
        public const val COLLECTOR_PATH: String = "$DIR/collector.mjs"
        public const val KEYS_PATH: String = "$DIR/keys.mjs"
        public const val TOKEN_PATH: String = "$DIR/read.token"
        public const val AUTH_ENV_PATH: String = "$DIR/auth.env"
        public const val GLM_KEY_PATH: String = "$DIR/glm.key"
        public const val MINIMAX_KEY_PATH: String = "$DIR/minimax.key"

        /** The agents' Codex sign-ins, used only when OpenCode has none. */
        public const val AGENT_PROFILES_ROOT: String = "/home/oc/.oc-profiles"
        public const val OPENCODE1_AUTH_PATH: String = "/root/.local/share/opencode/auth.json"
        public const val OPENCODE2_AUTH_PATH: String = "/root/.oc-opencode2/data/opencode/auth.json"

        private const val TOKEN_BYTES = 48
        private const val TOKEN_MIN = 48
        private const val FILES_OK = "qc files=ok"
        private const val KEYS_OK = "qc keys=ok"
        private const val REMOVED = "qc removed"
        private const val NODE_CHECK_SCRIPT =
            "v=\$(node -p \"process.versions.node\" 2>/dev/null) || v=; echo \"qc node=\${v%%.*}\""
        private const val LOCK_SCRIPT =
            "umask 077; chmod 700 $DIR && chmod 600 $TOKEN_PATH $COLLECTOR_PATH $KEYS_PATH && echo \"$FILES_OK\""

        /** Secure-storage slot for a profile's read token. */
        public fun tokenKey(profileId: String): String = "oc.quotaToken.$profileId"

        /** Preference switch: set up here and not turned off (`oc.<what>.<profileId>`, swept with the profile). */
        public fun enabledKey(profileId: String): String = "oc.quotaCollector.$profileId"

        /** The Node major version in In-app Ubuntu; null when there is none. */
        public fun parseNodeMajor(output: String): Int? =
            Regex("qc node=(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()

        /**
         * Runs inside In-app Ubuntu (Node >= 20). Reads OpenCode's sign-in file and writes 0600 key files for GLM and
         * MiniMax plus `auth.env` (file paths only) for Codex. Prefers OpenCode's own Codex sign-in; an agent's only
         * when OpenCode has none. A key removed in OpenCode is removed here. It prints counts, never a value.
         */
        public val KEYS_HELPER_SOURCE: String = """
import { chmodSync, existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';

const [dir, agentRoot, ...candidates] = process.argv.slice(2);
const read = (path) => {
  try {
    const value = JSON.parse(readFileSync(path, 'utf8'));
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
};
const docs = candidates.map((path) => [path, read(path)]).filter(([, doc]) => doc);
const apiKey = (ids) => {
  for (const [, doc] of docs) {
    for (const id of ids) {
      const entry = doc[id];
      if (entry && typeof entry.key === 'string' && entry.key.trim()) return entry.key.trim();
    }
  }
  return null;
};
const put = (name, value) => {
  const file = `${'$'}{dir}/${'$'}{name}`;
  rmSync(file, { force: true });
  if (!value) return 0;
  writeFileSync(file, `${'$'}{value}\n`, { mode: 0o600 });
  chmodSync(file, 0o600);
  return 1;
};
const quote = (text) => `'${'$'}{String(text).replaceAll("'", "'\\''")}'`;

const glm = put('glm.key', apiKey(['zai-coding-plan']));
const minimax = put('minimax.key', apiKey(['minimax-coding-plan', 'minimax']));

let env = '';
let codex = 'none';
const oauth = (entry) => entry && entry.type === 'oauth' && typeof entry.access === 'string';
const own = docs.find(([, doc]) => oauth(doc.openai) || oauth(doc.codex));
if (own) {
  env = `export OCMN_QUOTA_AUTH_FILE=${'$'}{quote(own[0])}\nexport OCMN_QUOTA_AUTH_FORMAT=opencode\n`;
  codex = 'opencode';
} else if (existsSync(agentRoot)) {
  for (const name of readdirSync(agentRoot).sort()) {
    const file = `${'$'}{agentRoot}/${'$'}{name}/codex/auth.json`;
    const doc = read(file);
    if (doc && doc.tokens && doc.tokens.access_token && doc.tokens.account_id) {
      env = `export OCMN_QUOTA_AUTH_FILE=${'$'}{quote(file)}\nexport OCMN_QUOTA_AUTH_FORMAT=codex\n`;
      codex = 'agent';
      break;
    }
  }
}
rmSync(`${'$'}{dir}/auth.env`, { force: true });
writeFileSync(`${'$'}{dir}/auth.env`, env, { mode: 0o600 });
console.log(`qc keys=ok codex=${'$'}{codex} glm=${'$'}{glm} minimax=${'$'}{minimax}`);
""".trimStart()
    }
}
