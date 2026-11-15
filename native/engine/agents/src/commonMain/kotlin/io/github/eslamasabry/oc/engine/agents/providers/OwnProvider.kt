package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Use your own provider" for a built-in phone agent (issue 147): an endpoint, a key and an optional model, used
 * instead of signing in to the agent's plan.
 *
 * Which agents: only those whose own program reads an endpoint and a key from its settings and reports itself signed
 * in with them, so the app's sign-in check stays truthful. Today that is Claude Code (`ANTHROPIC_BASE_URL` +
 * `ANTHROPIC_AUTH_TOKEN` in `settings.json`'s `env`, read by the helper's Claude and by `claude auth status`). Codex,
 * Antigravity and the others keep their own sign-in or their own provider setup in their terminal.
 */
public object OwnProviderAgents {
    public val supported: Set<AgentId> = setOf(AgentId.CLAUDE)

    public fun supports(agent: AgentId): Boolean = agent in supported
}

/** What a person gave, without the key (the key lives only in the secure store). */
public data class OwnProvider(val endpoint: String, val model: String?)

/** Why a typed value was refused before anything was saved; each has one plain sentence in the screen. */
public enum class OwnProviderProblem { Unsupported, Endpoint, Key, Model }

/**
 * The storage names. Preferences hold endpoint and model per agent (`oc.agentOwnProviders.<owner>`); the keys are one
 * secure-store entry per owner (`oc.agentProviderKeys.<owner>`, a JSON object agent → key). Both are `oc.<what>.<id>`
 * so the profile deletion sweep finds them (AGENTS.md rule 9).
 */
public object OwnProviderKeys {
    public const val PROVIDERS_PREFIX: String = "oc.agentOwnProviders."
    public const val SECRETS_PREFIX: String = "oc.agentProviderKeys."

    public fun providers(owner: OwnerId): String = PROVIDERS_PREFIX + owner.raw

    public fun secrets(owner: OwnerId): String = SECRETS_PREFIX + owner.raw
}

public object OwnProviderRules {
    private val MODEL = Regex("^[A-Za-z0-9._:/@\\[\\]-]{1,200}$")
    private val LOCAL = Regex("^http://(127\\.0\\.0\\.1|localhost)(:[0-9]{1,5})?(/[^\\s\"'\\\\]*)?$")

    /** The first problem with the typed values, or null when they can be saved. */
    public fun problem(agent: AgentId, endpoint: String, key: String, model: String?): OwnProviderProblem? {
        if (!OwnProviderAgents.supports(agent)) return OwnProviderProblem.Unsupported
        if (!endpointOk(endpoint.trim())) return OwnProviderProblem.Endpoint
        if (!keyOk(key)) return OwnProviderProblem.Key
        val m = model?.trim().orEmpty()
        if (m.isNotEmpty() && !MODEL.matches(m)) return OwnProviderProblem.Model
        return null
    }

    /** HTTPS without user info, or plain HTTP only to a program on this phone (a local proxy). */
    public fun endpointOk(endpoint: String): Boolean =
        endpoint.length <= 2048 && endpoint.none { it.isWhitespace() || it == '"' || it == '\'' || it == '\\' } &&
            (AgentArtifact.isHttpsWithoutCredentials(endpoint) || LOCAL.matches(endpoint))

    public fun keyOk(key: String): Boolean =
        key.isNotEmpty() && key.length <= 4096 && key.none { it.isWhitespace() || it.isISOControl() }
}

/**
 * The saved own providers of one phone-agent owner. The key is written to the secure store first and only then the
 * endpoint and model, so a refused secret write saves nothing. Every key this store is given or reads back is handed to
 * [registerSecret] (the app's redactor) first, so no log line, diagnostics report or Details text can carry it; no
 * `toString` here has one.
 */
public class OwnProviderStore(
    private val prefs: AgentPrefs,
    private val secrets: AgentSecrets,
    private val registerSecret: (String) -> Unit,
) {
    private val lane = Mutex()

    /** Endpoint and model per agent id; agents this app no longer offers it for are left out. */
    public fun read(owner: OwnerId): Map<AgentId, OwnProvider> {
        val root = AgentJson.parseOrNull(prefs.string(OwnProviderKeys.providers(owner))) as? JsonObject
            ?: return emptyMap()
        return buildMap {
            for ((id, value) in root) {
                val agent = AgentId(id)
                if (!OwnProviderAgents.supports(agent)) continue
                val obj = value as? JsonObject ?: continue
                val endpoint = (obj["endpoint"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                val model = (obj["model"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifEmpty { null }
                put(agent, OwnProvider(endpoint, model))
            }
        }
    }

    /** The saved key for [agent], read from the secure store only. */
    public suspend fun key(owner: OwnerId, agent: AgentId): Outcome<String?> =
        when (val keys = keys(owner)) {
            is Outcome.Failed -> keys
            is Outcome.Ok -> keys.value[agent.raw].ok()
        }

    public suspend fun save(owner: OwnerId, agent: AgentId, endpoint: String, key: String, model: String?): Outcome<Unit> =
        lane.withLock {
            registerSecret(key)
            if (OwnProviderRules.problem(agent, endpoint, key, model) != null) return@withLock Failure.Unexpected().failed()
            val keys = when (val read = keys(owner)) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value.toMutableMap()
            }
            keys[agent.raw] = key
            when (val w = secrets.write(OwnProviderKeys.secrets(owner), encode(keys))) {
                is Outcome.Failed -> return@withLock w
                is Outcome.Ok -> Unit
            }
            val all = read(owner).toMutableMap()
            all[agent] = OwnProvider(endpoint.trim(), model?.trim()?.ifEmpty { null })
            writeProviders(owner, all)
        }

    /** Forgets [agent]'s own provider: its key leaves the secure store, its endpoint the preferences. */
    public suspend fun remove(owner: OwnerId, agent: AgentId): Outcome<Unit> =
        lane.withLock {
            val keys = when (val read = keys(owner)) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value.toMutableMap()
            }
            if (keys.remove(agent.raw) != null) {
                val w = if (keys.isEmpty()) secrets.delete(OwnProviderKeys.secrets(owner))
                else secrets.write(OwnProviderKeys.secrets(owner), encode(keys))
                if (w is Outcome.Failed) return@withLock w
            }
            writeProviders(owner, read(owner) - agent)
        }

    /** Everything of [owner] (its connection is deleted): the secure-store entry and the preference. */
    public suspend fun forget(owner: OwnerId): Outcome<Unit> =
        lane.withLock {
            when (val d = secrets.delete(OwnProviderKeys.secrets(owner))) {
                is Outcome.Failed -> return@withLock d
                is Outcome.Ok -> Unit
            }
            prefs.commit { remove(OwnProviderKeys.providers(owner)) }
        }

    private suspend fun keys(owner: OwnerId): Outcome<Map<String, String>> =
        when (val read = secrets.read(OwnProviderKeys.secrets(owner))) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val root = read.value?.let { AgentJson.parseOrNull(it) as? JsonObject }
                (root?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }
                    ?.toMap() ?: emptyMap()).onEach { registerSecret(it.value) }.ok()
            }
        }

    private suspend fun writeProviders(owner: OwnerId, all: Map<AgentId, OwnProvider>): Outcome<Unit> {
        val key = OwnProviderKeys.providers(owner)
        if (all.isEmpty()) return prefs.commit { remove(key) }
        val root = JsonObject(
            all.entries.associate { (agent, p) ->
                agent.raw to JsonObject(
                    linkedMapOf<String, JsonElement>(
                        "endpoint" to JsonPrimitive(p.endpoint),
                        "model" to JsonPrimitive(p.model.orEmpty()),
                    ),
                )
            },
        )
        return prefs.commit { putString(key, AgentJson.encode(root)) }
    }

    private fun encode(keys: Map<String, String>): String =
        AgentJson.encode(JsonObject(keys.mapValues { JsonPrimitive(it.value) }))
}

/**
 * Writes an own provider into Claude Code's own settings (`<agent home>/claude/settings.json`, the `CLAUDE_CONFIG_DIR`
 * the helper, the sign-in terminal and the sign-in check all use), or takes it out again. Runs as the agent user in the
 * phone's Linux. The values travel on stdin only (never in the script text, an argument or the environment of another
 * process); the script prints nothing and answers with its exit code. Other settings in the file are kept.
 */
public object OwnProviderScript {
    private val OWNER = Regex("^[A-Za-z0-9_-]{1,80}$")

    /** The `env` names this app writes and removes; nothing else in the file is touched. */
    public val MANAGED: List<String> = listOf(
        "ANTHROPIC_BASE_URL",
        "ANTHROPIC_AUTH_TOKEN",
        "ANTHROPIC_MODEL",
        "ANTHROPIC_DEFAULT_OPUS_MODEL",
        "ANTHROPIC_DEFAULT_SONNET_MODEL",
        "ANTHROPIC_DEFAULT_HAIKU_MODEL",
    )

    /** Exit codes. */
    public const val OK: Int = 0
    public const val BAD_INPUT: Int = 3
    public const val UNREADABLE_SETTINGS: Int = 4

    public fun settingsPath(owner: OwnerId): String {
        require(OWNER.matches(owner.raw)) { "Not an agent home id." }
        return "/home/oc/.oc-profiles/${owner.raw}/claude/settings.json"
    }

    /** The script for [owner]'s Claude Code. Its stdin is [apply] or [clear]. */
    public fun script(owner: OwnerId): String = scriptAt(settingsPath(owner))

    internal fun scriptAt(path: String): String {
        require(path.startsWith("/") && path.none { it == '"' || it == '\'' || it == '\\' || it.isISOControl() })
        // Double quotes only: the program is one single-quoted shell argument.
        val managed = MANAGED.joinToString(",") { "\"$it\"" }
        return SCRIPT.replace("⟨PATH⟩", path).replace("⟨MANAGED⟩", managed)
    }

    /** The stdin that sets [provider] with [key] (the model, when given, also stands for every model size). */
    public fun apply(provider: OwnProvider, key: String): String {
        val env = linkedMapOf<String, JsonElement>(
            "ANTHROPIC_BASE_URL" to JsonPrimitive(provider.endpoint),
            "ANTHROPIC_AUTH_TOKEN" to JsonPrimitive(key),
        )
        provider.model?.let { model ->
            for (name in MANAGED.drop(2)) env[name] = JsonPrimitive(model)
        }
        return AgentJson.encode(JsonObject(mapOf("set" to JsonObject(env)))) + "\n"
    }

    /** The stdin that removes every [MANAGED] name. */
    public fun clear(): String = AgentJson.encode(JsonObject(mapOf("set" to JsonObject(emptyMap())))) + "\n"

    private val SCRIPT = """
set -eu
umask 077
exec python3 -c '
import json, os, sys
path = "⟨PATH⟩"
managed = [⟨MANAGED⟩]
try:
    wanted = json.loads(sys.stdin.readline())["set"]
    assert isinstance(wanted, dict)
    assert all(k in managed and isinstance(v, str) for k, v in wanted.items())
except Exception:
    sys.exit(3)
settings = {}
if os.path.exists(path):
    try:
        with open(path) as f:
            settings = json.load(f)
        assert isinstance(settings, dict)
    except Exception:
        sys.exit(4)
env = settings.get("env")
env = dict(env) if isinstance(env, dict) else {}
for k in managed:
    env.pop(k, None)
env.update(wanted)
if env:
    settings["env"] = env
else:
    settings.pop("env", None)
os.makedirs(os.path.dirname(path), mode=0o700, exist_ok=True)
tmp = path + ".oc-tmp"
fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w") as f:
    json.dump(settings, f, indent=2)
os.replace(tmp, path)
'
""".trimStart()
}
