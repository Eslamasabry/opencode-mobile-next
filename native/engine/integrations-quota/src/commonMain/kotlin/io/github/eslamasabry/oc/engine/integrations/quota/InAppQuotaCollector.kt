package io.github.eslamasabry.oc.engine.integrations.quota

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Why setting up usage on In-app Ubuntu stopped. [QuotaSetupError.detail] is Details text; never a
 * token or a key.
 */
public enum class QuotaSetupFailure {
    LinuxNotReady,
    NoNode,
    NodeTooOld,
    Copy,
    Prepare,
    Start,
    Remove,
}

public data class QuotaSetupError(val failure: QuotaSetupFailure, val detail: String? = null) {
    override fun toString(): String = "QuotaSetupError($failure)"
}

/** Where set-up is, for the progress the page shows. */
public enum class QuotaSetupStep {
    Checking,
    Copying,
    SignIns,
    Starting,
    Reading,
}

/** The profile facts the collector needs (a copy, never the live profile). */
public data class QuotaServerProfile(
    val id: ProfileId,
    val baseUrl: String,
    /** True for an OpenCode server (not Paseo, Codex or a phone agent). */
    val isOpenCode: Boolean,
)

/**
 * In-app Ubuntu as the collector host sees it (the Android binding is :android:linux's runtime).
 */
public interface QuotaLinuxHost {
    public data class Status(val installed: Boolean, val ready: Boolean, val services: List<String>)

    public data class RunResult(val exitCode: Int, val output: String)

    public suspend fun status(): Status

    public suspend fun run(script: String, timeoutMs: Long): RunResult

    public suspend fun startService(name: String, script: String, port: Int?)

    public suspend fun stopService(name: String)

    public suspend fun serviceLog(name: String): String
}

/** In-app Ubuntu's root file system, by absolute in-Ubuntu path. */
public interface QuotaRootfs {
    public suspend fun read(path: String): ByteArray?

    public suspend fun write(path: String, bytes: ByteArray)
}

/**
 * Runs the optional quota collector (`tool/quota/collector.mjs`) inside the app's own In-app Ubuntu
 * (Flutter `InAppQuotaCollector`). Security:
 * - The read token is made here from [randomBytes] (a secure source in the app graph), kept in
 *   [secrets] under `oc.quotaToken.<profileId>` and staged as a file locked to 0600. It is never in
 *   a command line or a log.
 * - Provider keys never reach the app: a helper inside Ubuntu writes 0600 key files; the app only
 *   sees counts.
 * - It listens on 127.0.0.1:4195 only.
 */
public class InAppQuotaCollector(
    private val prefs: KeyValueStore,
    private val lane: WriterLane,
    private val secrets: SecretStore,
    private val linux: QuotaLinuxHost,
    private val rootfs: QuotaRootfs,
    private val randomBytes: (Int) -> ByteArray,
    private val loadCollector: suspend () -> String,
    /**
     * The keys helper (Flutter `keysHelperSource`) ships with the app; the Android binding loads
     * its text.
     */
    private val loadKeysHelper: suspend () -> String,
    private val probe: suspend (token: String) -> Boolean,
    private val supported: Boolean,
    private val probeAttempts: Int = 20,
    private val probeDelayMs: Long = 1_000,
) {
    public fun appliesTo(profile: QuotaServerProfile?): Boolean =
        supported && looksLikeInAppServer(profile)

    public fun isEnabled(profileId: ProfileId): Boolean = prefs.bool(enabledKey(profileId)) ?: false

    /** OpenCode's sign-in file, OpenCode 2's own data folder first. */
    public fun authCandidates(): List<String> = listOf(OPENCODE2_AUTH_PATH, OPENCODE1_AUTH_PATH)

    /** The service script: paths in, values never. */
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

    /**
     * Sets usage up. Safe to run again: it keeps the token and rewrites only files that changed.
     * Null when done.
     */
    public suspend fun setUp(
        profile: QuotaServerProfile,
        onStep: (QuotaSetupStep) -> Unit = {},
    ): QuotaSetupError? {
        if (!appliesTo(profile)) return QuotaSetupError(QuotaSetupFailure.LinuxNotReady)
        val id = profile.id
        onStep(QuotaSetupStep.Checking)
        checkLinux()?.let {
            return it
        }
        val node = linux.run(NODE_CHECK, 30_000)
        val major = parseNodeMajor(node.output) ?: return QuotaSetupError(QuotaSetupFailure.NoNode)
        if (major < 20) {
            return QuotaSetupError(
                QuotaSetupFailure.NodeTooOld,
                "Node $major is installed; the collector needs 20 or later.",
            )
        }

        onStep(QuotaSetupStep.Copying)
        val token: String
        try {
            installFiles()
            var stored = secrets.read(tokenKey(id)).getOrNull()
            if (stored == null || stored.length < TOKEN_MIN_LENGTH) {
                stored = newToken()
                if (secrets.write(tokenKey(id), stored) is Outcome.Failed) {
                    return QuotaSetupError(
                        QuotaSetupFailure.Copy,
                        "Copying into In-app Ubuntu failed (secret).",
                    )
                }
            }
            token = stored
            rootfs.write(TOKEN_PATH, "$token\n".encodeToByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return QuotaSetupError(
                QuotaSetupFailure.Copy,
                "Copying into In-app Ubuntu failed (${e::class.simpleName}).",
            )
        }
        val modes = linux.run(LOCK_FILES, 30_000)
        if ("qc files=ok" !in modes.output) {
            return QuotaSetupError(
                QuotaSetupFailure.Prepare,
                "The files in In-app Ubuntu could not be locked down.",
            )
        }

        onStep(QuotaSetupStep.SignIns)
        val keys =
            linux.run(
                "umask 077; node $KEYS_PATH $DIR $AGENT_PROFILES_ROOT ${authCandidates().joinToString(" ")}",
                30_000,
            )
        if ("qc keys=ok" !in keys.output)
            return QuotaSetupError(QuotaSetupFailure.Prepare, "The sign-in helper did not finish.")

        onStep(QuotaSetupStep.Starting)
        try {
            linux.startService(SERVICE_NAME, serviceScript(), PORT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return QuotaSetupError(
                QuotaSetupFailure.Start,
                "The collector did not start (${e::class.simpleName}).",
            )
        }
        var answered = false
        var attempt = 0
        while (attempt < probeAttempts && !answered) {
            answered = probe(token)
            if (!answered) delay(probeDelayMs)
            attempt++
        }
        if (!answered) {
            val log =
                try {
                    linux.serviceLog(SERVICE_NAME)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
            return QuotaSetupError(
                QuotaSetupFailure.Start,
                log.trim().ifEmpty { "The collector did not answer in time." },
            )
        }
        if (prefs.commit(lane) { putBool(enabledKey(id), true) } is Outcome.Failed) {
            return QuotaSetupError(QuotaSetupFailure.Prepare, "The setting could not be saved.")
        }
        return null
    }

    /** A collector that was set up and not turned off comes back. Quiet: false when it did not. */
    public suspend fun ensureRunning(profile: QuotaServerProfile): Boolean {
        if (!appliesTo(profile) || !isEnabled(profile.id)) return false
        return try {
            if (checkLinux() != null) return false
            val token = readToken(secrets, profile.id) ?: return false
            val changed = installFiles()
            if (!changed && SERVICE_NAME in linux.status().services) return true
            rootfs.write(TOKEN_PATH, "$token\n".encodeToByteArray())
            val modes =
                linux.run(
                    "chmod 700 $DIR && chmod 600 $TOKEN_PATH $COLLECTOR_PATH $KEYS_PATH",
                    30_000,
                )
            if (modes.exitCode != 0) return false
            if (changed && SERVICE_NAME in linux.status().services) linux.stopService(SERVICE_NAME)
            linux.startService(SERVICE_NAME, serviceScript(), PORT)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Turns usage off: stops the service, removes its files, key copies, the token and the switch.
     */
    public suspend fun turnOff(profileId: ProfileId): QuotaSetupError? {
        linux.stopService(SERVICE_NAME)
        val result = linux.run("rm -rf $DIR && echo \"qc removed\"", 60_000)
        if ("qc removed" !in result.output) return QuotaSetupError(QuotaSetupFailure.Remove)
        if (secrets.delete(tokenKey(profileId)) is Outcome.Failed)
            return QuotaSetupError(QuotaSetupFailure.Remove)
        if (prefs.commit(lane) { remove(enabledKey(profileId)) } is Outcome.Failed)
            return QuotaSetupError(QuotaSetupFailure.Remove)
        return null
    }

    private suspend fun checkLinux(): QuotaSetupError? {
        val status = linux.status()
        return if (!status.installed || !status.ready)
            QuotaSetupError(QuotaSetupFailure.LinuxNotReady)
        else null
    }

    private suspend fun installFiles(): Boolean {
        val a = place(COLLECTOR_PATH, loadCollector())
        val b = place(KEYS_PATH, loadKeysHelper())
        return a || b
    }

    /** Writes [content] only when it is missing or different. True when written. */
    private suspend fun place(path: String, content: String): Boolean {
        val bytes = content.encodeToByteArray()
        if (rootfs.read(path)?.contentEquals(bytes) == true) return false
        rootfs.write(path, bytes)
        return true
    }

    /** 48 random bytes: 64 Bearer-safe characters (base64url, no padding). */
    private fun newToken(): String = Base64.UrlSafe.encode(randomBytes(48)).trimEnd('=')

    public companion object {
        public const val PORT: Int = IN_APP_QUOTA_PORT
        public const val SERVICE_NAME: String = "quota"
        public const val DIR: String = "/root/.oc-builtin/quota"
        public const val COLLECTOR_PATH: String = "$DIR/collector.mjs"
        public const val KEYS_PATH: String = "$DIR/keys.mjs"
        public const val TOKEN_PATH: String = "$DIR/read.token"
        public const val AUTH_ENV_PATH: String = "$DIR/auth.env"
        public const val GLM_KEY_PATH: String = "$DIR/glm.key"
        public const val MINIMAX_KEY_PATH: String = "$DIR/minimax.key"
        public const val AGENT_PROFILES_ROOT: String = "/home/oc/.oc-profiles"
        public const val OPENCODE1_AUTH_PATH: String = "/root/.local/share/opencode/auth.json"
        public const val OPENCODE2_AUTH_PATH: String = "/root/.oc-opencode2/data/opencode/auth.json"
        public const val TOKEN_PREFIX: String = "oc.quotaToken."
        public const val ENABLED_PREFIX: String = "oc.quotaCollector."
        public const val OWNER: String = ":engine:integrations"

        /** The in-app OpenCode server address (BuiltinLinux.serverUrl). */
        public const val IN_APP_SERVER_PORT: Int = 4097
        private const val TOKEN_MIN_LENGTH = 48

        private const val NODE_CHECK =
            "v=\$(node -p \"process.versions.node\" 2>/dev/null) || v=; echo \"qc node=\${v%%.*}\""
        private const val LOCK_FILES =
            "umask 077; chmod 700 $DIR && chmod 600 $TOKEN_PATH $COLLECTOR_PATH $KEYS_PATH && echo \"qc files=ok\""

        public fun tokenKey(profileId: ProfileId): String = TOKEN_PREFIX + profileId.raw

        /** A key under `oc.<what>.<profileId>`, so the profile deletion sweep removes it. */
        public fun enabledKey(profileId: ProfileId): String = ENABLED_PREFIX + profileId.raw

        /** Registers the switch and the secure slot with the per-profile deletion registry. */
        public fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    ENABLED_PREFIX,
                    KeyScope.Profile,
                    PrefType.Bool,
                    OWNER,
                    "usage collector set up on In-app Ubuntu",
                )
            )
            scope.registerSecret(TOKEN_PREFIX, OWNER)
        }

        public fun parseNodeMajor(output: String): Int? =
            Regex("qc node=(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()

        /** Shape only: an OpenCode profile at http://127.0.0.1:4097. */
        public fun looksLikeInAppServer(profile: QuotaServerProfile?): Boolean {
            if (profile == null || !profile.isOpenCode) return false
            val url = QuotaUrl.parse(profile.baseUrl) ?: return false
            if (url.scheme != "http" || url.host != "127.0.0.1") return false
            val port = url.authority.substringAfterLast(':', "").toIntOrNull() ?: 80
            return port == IN_APP_SERVER_PORT
        }

        /**
         * The token for [profileId], or null when usage was never set up (or storage cannot
         * answer).
         */
        public suspend fun readToken(secrets: SecretStore, profileId: ProfileId): String? =
            secrets.read(tokenKey(profileId)).getOrNull()?.takeIf { it.isNotEmpty() }

        /**
         * A token reader for the quota gateway when [profile] is the app's own server; null for
         * every other server. [onUse] wakes the collector before a read (Flutter
         * `QuotaCollectorDemand.use`).
         */
        public fun tokenReaderFor(
            profile: QuotaServerProfile,
            secrets: SecretStore,
            supported: Boolean = true,
            onUse: suspend (token: String) -> Unit = {},
        ): (suspend () -> String?)? =
            if (supported && looksLikeInAppServer(profile)) {
                {
                    val token = readToken(secrets, profile.id)
                    if (token != null) onUse(token)
                    token
                }
            } else {
                null
            }
    }
}
