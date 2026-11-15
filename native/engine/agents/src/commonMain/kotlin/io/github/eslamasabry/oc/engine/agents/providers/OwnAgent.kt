package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Add your own agent" (issue 147): any program that speaks ACP over stdio and runs in the phone's Linux, added by a
 * person with a name, the command that starts it in ACP mode, an optional install command and environment values.
 *
 * The helper (Paseo 0.15.4) takes such an agent as a provider entry in its `config.json`:
 * `agents.providers.<id> = {extends: "acp", label, command: [...], env: {...}}` (its `ProviderOverrideSchema`; ids
 * `^[a-z][a-z0-9-]*$`; a custom entry must have a label). [OwnAgentConfig] renders exactly that.
 *
 * Every environment value is kept in the secure store (they are often keys); only the names are in preferences.
 */
public data class OwnAgent(
    val id: AgentId,
    val name: String,
    /** The start command as words (argv), never a shell line. */
    val start: List<String>,
    /** A command run once in the phone's Linux to install it; null when it is already there. */
    val install: String?,
    /** Environment names, in the order given; their values live in the secure store only. */
    val envNames: List<String>,
) {
    override fun toString(): String = "OwnAgent(${id.raw})"
}

public enum class OwnAgentProblem { Name, Start, Install, EnvName, EnvValue, TooMany }

public object OwnAgentRules {
    public const val PREFIX: String = "own-"
    public const val MAX_AGENTS: Int = 8
    private val ENV_NAME = Regex("^[A-Z_][A-Z0-9_]{0,63}$")

    /** Names the app never lets a person override: they would change how every program in the Linux runs. */
    private val RESERVED = setOf("PATH", "HOME", "LD_PRELOAD", "LD_LIBRARY_PATH", "SHELL", "USER", "PWD")

    /** A stable id from [name], unique among [taken]: `own-` plus lowercase letters, digits and dashes. */
    public fun idFor(name: String, taken: Set<AgentId>): AgentId {
        val slug = name.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
            .split('-').filter { it.isNotEmpty() }.joinToString("-").take(24).ifEmpty { "agent" }
        var candidate = AgentId(PREFIX + slug)
        var n = 2
        while (candidate in taken) candidate = AgentId("$PREFIX$slug-${n++}")
        return candidate
    }

    /**
     * Shell-like words: spaces separate, single and double quotes group, a backslash escapes the next character.
     * Null when quotes are left open or nothing is given.
     */
    public fun words(line: String): List<String>? {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var any = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null && c == quote -> quote = null
                quote == '\'' -> current.append(c)
                c == '\\' && i + 1 < line.length -> { current.append(line[i + 1]); i++; any = true }
                quote == null && (c == '\'' || c == '"') -> { quote = c; any = true }
                quote == null && c.isWhitespace() -> {
                    if (any) out += current.toString()
                    current.clear()
                    any = false
                }
                else -> { current.append(c); any = true }
            }
            i++
        }
        if (quote != null) return null
        if (any) out += current.toString()
        return out.takeIf { it.isNotEmpty() }
    }

    public fun problem(name: String, start: String, install: String, env: List<Pair<String, String>>): OwnAgentProblem? {
        val n = name.trim()
        if (n.isEmpty() || n.length > 40 || n.any { it.isISOControl() }) return OwnAgentProblem.Name
        val argv = words(start) ?: return OwnAgentProblem.Start
        if (argv.any { it.isEmpty() || '\u0000' in it } || argv.size > 32 || start.length > 1000) return OwnAgentProblem.Start
        if (install.length > 2000 || '\u0000' in install) return OwnAgentProblem.Install
        val names = env.map { it.first.trim() }
        if (names.any { !ENV_NAME.matches(it) || it in RESERVED } || names.toSet().size != names.size) return OwnAgentProblem.EnvName
        if (env.any { (_, v) -> v.length > 4096 || v.any { it == '\u0000' } }) return OwnAgentProblem.EnvValue
        return null
    }
}

/**
 * Own agents per phone-agent owner: `oc.ownAgents.<owner>` (a JSON list without values) and the values in the secure
 * store `oc.ownAgentEnv.<owner>` (agent → name → value). Both are swept with the owner's profile. Every value read or
 * written is handed to [registerSecret] (the redactor) first.
 */
public class OwnAgentStore(
    private val prefs: AgentPrefs,
    private val secrets: AgentSecrets,
    private val registerSecret: (String) -> Unit,
) {
    private val lane = Mutex()

    public fun read(owner: OwnerId): List<OwnAgent> {
        val list = AgentJson.parseOrNull(prefs.string(OwnAgentKeys.agents(owner))) as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun strings(key: String) = (o[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            val id = str("id")?.takeIf { it.startsWith(OwnAgentRules.PREFIX) } ?: return@mapNotNull null
            OwnAgent(
                AgentId(id),
                str("name") ?: return@mapNotNull null,
                strings("start")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                str("install")?.ifEmpty { null },
                strings("env").orEmpty(),
            )
        }
    }

    /** The environment values of every own agent of [owner], from the secure store only. */
    public suspend fun env(owner: OwnerId): Outcome<Map<AgentId, Map<String, String>>> =
        when (val read = secrets.read(OwnAgentKeys.env(owner))) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val root = read.value?.let { AgentJson.parseOrNull(it) as? JsonObject }
                (root?.entries?.associate { (id, values) ->
                    AgentId(id) to ((values as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.also(registerSecret)?.let { k to it }
                    }?.toMap() ?: emptyMap())
                } ?: emptyMap()).ok()
            }
        }

    /**
     * Adds a new agent ([id] null) or replaces one. An env value left empty for a name the agent already has keeps the
     * saved value (the field is never prefilled).
     */
    public suspend fun save(
        owner: OwnerId,
        id: AgentId?,
        name: String,
        start: String,
        install: String,
        env: List<Pair<String, String>>,
    ): Outcome<OwnAgent> =
        lane.withLock {
            env.forEach { registerSecret(it.second) }
            if (OwnAgentRules.problem(name, start, install, env) != null) return@withLock Failure.Unexpected().failed()
            val agents = read(owner)
            if (id == null && agents.size >= OwnAgentRules.MAX_AGENTS) return@withLock Failure.Unexpected().failed()
            val values = when (val v = env(owner)) {
                is Outcome.Failed -> return@withLock v
                is Outcome.Ok -> v.value.toMutableMap()
            }
            val agentId = id ?: OwnAgentRules.idFor(name, agents.map { it.id }.toSet())
            val kept = values[agentId].orEmpty()
            values[agentId] = env.associate { (k, v) -> k.trim() to v.ifEmpty { kept[k.trim()].orEmpty() } }
            val agent = OwnAgent(
                agentId,
                name.trim(),
                OwnAgentRules.words(start)!!,
                install.trim().ifEmpty { null },
                env.map { it.first.trim() },
            )
            when (val w = writeEnv(owner, values)) {
                is Outcome.Failed -> return@withLock w
                is Outcome.Ok -> Unit
            }
            val all = agents.filter { it.id != agentId } + agent
            when (val w = writeAgents(owner, all)) {
                is Outcome.Failed -> w
                is Outcome.Ok -> agent.ok()
            }
        }

    public suspend fun remove(owner: OwnerId, id: AgentId): Outcome<Unit> =
        lane.withLock {
            val values = when (val v = env(owner)) {
                is Outcome.Failed -> return@withLock v
                is Outcome.Ok -> v.value - id
            }
            when (val w = writeEnv(owner, values)) {
                is Outcome.Failed -> return@withLock w
                is Outcome.Ok -> Unit
            }
            writeAgents(owner, read(owner).filter { it.id != id })
        }

    public suspend fun forget(owner: OwnerId): Outcome<Unit> =
        lane.withLock {
            when (val d = secrets.delete(OwnAgentKeys.env(owner))) {
                is Outcome.Failed -> return@withLock d
                is Outcome.Ok -> Unit
            }
            prefs.commit { remove(OwnAgentKeys.agents(owner)) }
        }

    private suspend fun writeEnv(owner: OwnerId, values: Map<AgentId, Map<String, String>>): Outcome<Unit> {
        val nonEmpty = values.filterValues { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return secrets.delete(OwnAgentKeys.env(owner))
        val root = JsonObject(nonEmpty.entries.associate { (id, env) -> id.raw to JsonObject(env.mapValues { JsonPrimitive(it.value) }) })
        return secrets.write(OwnAgentKeys.env(owner), AgentJson.encode(root))
    }

    private suspend fun writeAgents(owner: OwnerId, agents: List<OwnAgent>): Outcome<Unit> {
        val key = OwnAgentKeys.agents(owner)
        if (agents.isEmpty()) return prefs.commit { remove(key) }
        val list = JsonArray(
            agents.map { a ->
                JsonObject(
                    linkedMapOf<String, JsonElement>(
                        "id" to JsonPrimitive(a.id.raw),
                        "name" to JsonPrimitive(a.name),
                        "start" to JsonArray(a.start.map(::JsonPrimitive)),
                        "install" to JsonPrimitive(a.install.orEmpty()),
                        "env" to JsonArray(a.envNames.map(::JsonPrimitive)),
                    ),
                )
            },
        )
        return prefs.commit { putString(key, AgentJson.encode(list)) }
    }
}

public object OwnAgentKeys {
    public const val AGENTS_PREFIX: String = "oc.ownAgents."
    public const val ENV_PREFIX: String = "oc.ownAgentEnv."

    public fun agents(owner: OwnerId): String = AGENTS_PREFIX + owner.raw

    public fun env(owner: OwnerId): String = ENV_PREFIX + owner.raw
}

/** The helper's provider entries for own agents, added to the rendered `config.json` text. */
public object OwnAgentConfig {
    /**
     * [config] (as `PaseoConfig.render` wrote it) with one `extends: "acp"` provider per own agent: [LAUNCHER] and the
     * start words as given; env carries the saved values.
     */
    /**
     * The helper starts every own agent through `sh -c` with the same PATH the install and the check use (the agent
     * home's `~/.local/bin` first), so a program installed with npm or pip there is found by its bare name. The
     * person's words follow as `$0 "$@"`: they are arguments, never parsed by the shell.
     */
    public val LAUNCHER: List<String> =
        listOf("/bin/sh", "-c", "PATH=\"\$HOME/.local/bin:/home/oc/.local/bin:\$PATH\" exec \"\$0\" \"\$@\"")

    public fun merge(config: String, agents: List<OwnAgent>, env: Map<AgentId, Map<String, String>>): String {
        if (agents.isEmpty()) return config
        val root = AgentJson.parseOrNull(config) as? JsonObject ?: return config
        val agentsNode = root["agents"] as? JsonObject ?: JsonObject(emptyMap())
        val providers = LinkedHashMap((agentsNode["providers"] as? JsonObject).orEmpty())
        for (agent in agents) {
            if (agent.id.raw in providers) continue
            val entry = linkedMapOf<String, JsonElement>(
                "extends" to JsonPrimitive("acp"),
                "label" to JsonPrimitive(agent.name),
                "command" to JsonArray((LAUNCHER + agent.start).map(::JsonPrimitive)),
            )
            val values = env[agent.id].orEmpty().filterKeys { it in agent.envNames }
            if (values.isNotEmpty()) entry["env"] = JsonObject(values.mapValues { JsonPrimitive(it.value) })
            providers[agent.id.raw] = JsonObject(entry)
        }
        val newAgents = LinkedHashMap(agentsNode).apply { put("providers", JsonObject(providers)) }
        val newRoot = LinkedHashMap(root).apply { put("agents", JsonObject(newAgents)) }
        return AgentJson.encode(JsonObject(newRoot))
    }
}

/**
 * "Check": starts the agent's command in the phone's Linux the way the helper will, sends ACP `initialize` and reads the
 * answer. The environment values ride on stdin (one JSON line) like the provider key; the script prints nothing and
 * answers with its exit code ([OwnAgentCheckResult.of]).
 */
public object OwnAgentCheckScript {
    public fun script(owner: OwnerId): String {
        require(Regex("^[A-Za-z0-9_-]{1,80}$").matches(owner.raw)) { "Not an agent home id." }
        return SCRIPT.replace("⟨HOME⟩", "/home/oc/.oc-profiles/${owner.raw}")
    }

    public fun stdin(agent: OwnAgent, env: Map<String, String>): String =
        AgentJson.encode(
            JsonObject(
                linkedMapOf(
                    "argv" to JsonArray(agent.start.map(::JsonPrimitive)),
                    "env" to JsonObject(env.filterKeys { it in agent.envNames }.mapValues { JsonPrimitive(it.value) }),
                ),
            ),
        ) + "\n"

    private val SCRIPT = """
set -eu
umask 077
cd "⟨HOME⟩" 2>/dev/null || cd /tmp
exec python3 -c '
import json, os, select, subprocess, sys, time
try:
    req = json.loads(sys.stdin.readline())
    argv = [str(a) for a in req["argv"]]
    env = dict(os.environ)
    env["HOME"] = "⟨HOME⟩"
    env["PATH"] = "⟨HOME⟩/.local/bin:/home/oc/.local/bin:" + env.get("PATH", "/usr/bin:/bin")
    env.update({str(k): str(v) for k, v in req.get("env", {}).items()})
except Exception:
    sys.exit(3)
try:
    p = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env)
except FileNotFoundError:
    sys.exit(12)
except Exception:
    sys.exit(13)
msg = {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": 1, "clientCapabilities": {}}}
code = 10
try:
    p.stdin.write((json.dumps(msg) + "\n").encode())
    p.stdin.flush()
    end = time.time() + 30
    buf = b""
    while time.time() < end:
        r, _, _ = select.select([p.stdout], [], [], max(0.0, end - time.time()))
        if not r:
            break
        chunk = os.read(p.stdout.fileno(), 65536)
        if not chunk:
            code = 14 if code == 10 else code
            break
        buf += chunk
        while b"\n" in buf:
            line, buf = buf.split(b"\n", 1)
            try:
                answer = json.loads(line)
            except Exception:
                code = 11
                continue
            if isinstance(answer, dict) and answer.get("id") == 1:
                result = answer.get("result")
                code = 0 if isinstance(result, dict) and "protocolVersion" in result else 11
                raise StopIteration
except StopIteration:
    pass
except Exception:
    code = 14
finally:
    try:
        p.kill()
    except Exception:
        pass
sys.exit(code)
'
""".trimStart()
}

/** What [OwnAgentCheckScript] found; each has one plain sentence in the screen, the exit code goes under Details. */
public enum class OwnAgentCheckResult {
    /** It answered `initialize` as an ACP agent. */
    Answered,

    /** It started but did not answer within 30 seconds. */
    NoAnswer,

    /** It answered something that is not ACP. */
    NotAcp,

    /** The program was not found (not installed, or not on the PATH). */
    NotFound,

    /** It stopped at once. */
    Stopped,

    /** The check itself could not run. */
    CouldNotRun;

    public companion object {
        public fun of(exitCode: Int): OwnAgentCheckResult =
            when (exitCode) {
                0 -> Answered
                10 -> NoAnswer
                11 -> NotAcp
                12 -> NotFound
                13, 14 -> Stopped
                else -> CouldNotRun
            }
    }
}

/**
 * Runs a person's install command once, as the agent user, in the owner's agent home. The command comes on stdin (it
 * is the person's own text; nothing of it is logged). npm and pip install into `~/.local` (no root needed), whose `bin`
 * is put first on the PATH for the install and for [OwnAgentCheckScript]. Answers with the command's exit code.
 */
public object OwnAgentInstallScript {
    public const val TIMEOUT_SECONDS: Int = 900

    public fun script(owner: OwnerId): String {
        require(Regex("^[A-Za-z0-9_-]{1,80}$").matches(owner.raw)) { "Not an agent home id." }
        val home = "/home/oc/.oc-profiles/${owner.raw}"
        return """
set -eu
umask 022
export HOME='$home'
export NPM_CONFIG_PREFIX="${'$'}HOME/.local"
export PATH="${'$'}HOME/.local/bin:/home/oc/.local/bin:${'$'}PATH"
mkdir -p "${'$'}HOME/.local/bin"
cd "${'$'}HOME"
command=${'$'}(cat)
[ -n "${'$'}command" ] || exit 3
exec sh -c "${'$'}command" </dev/null >/dev/null 2>&1
""".trimStart()
    }
}
