package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Codex on this phone runs with its own OS sandbox off (issue 147 launch blocker, Galaxy S22 build 10045: "the execution
 * tool failed to start"). Codex's Linux sandbox needs Landlock/seccomp/bubblewrap, which the phone's Linux (proot, no
 * real root) does not have, so with the helper's default per-turn policy (`workspace-write`) every shell command fails to
 * start. The phone's Linux is itself confined to the app's private storage.
 *
 * Where: the helper's `config.json`, `agents.providers.codex.options.sandbox_mode = "danger-full-access"`. Paseo 0.15.4
 * (`CodexProviderOptionsSchema`, `configuredProviderOptions`) passes a built-in provider's `options` to the Codex
 * client as provider options; its `applyTurnWorkflowPolicy` then sends `sandboxPolicy: {type: "dangerFullAccess"}` on
 * every turn whatever the mode, while the approval policy still comes from the mode the app chose (`approval_policy`
 * is left unset), so "ask" keeps asking. Codex 0.169.0's app-server schema lists `dangerFullAccess` as a
 * `SandboxPolicy` and `danger-full-access` as a `SandboxMode` (`codex --help`: `-s, --sandbox` possible values
 * read-only, workspace-write, danger-full-access).
 *
 * Only the phone's own helper config gets it: a computer running Paseo has its own config, which the app never writes.
 */
public object PhoneCodexSandbox {
    public const val PROVIDER: String = "codex"
    public const val SANDBOX_MODE: String = "danger-full-access"

    /** [config] with the Codex override added; anything already under `agents.providers.codex` is kept. */
    public fun merge(config: String): String {
        val root = AgentJson.parseOrNull(config) as? JsonObject ?: return config
        val agents = root["agents"] as? JsonObject ?: JsonObject(emptyMap())
        val providers = LinkedHashMap((agents["providers"] as? JsonObject).orEmpty())
        val codex = LinkedHashMap((providers[PROVIDER] as? JsonObject).orEmpty())
        val options = LinkedHashMap((codex["options"] as? JsonObject).orEmpty())
        options["sandbox_mode"] = JsonPrimitive(SANDBOX_MODE)
        codex["options"] = JsonObject(options)
        providers[PROVIDER] = JsonObject(codex)
        val newAgents = LinkedHashMap<String, JsonElement>(agents).apply { put("providers", JsonObject(providers)) }
        val newRoot = LinkedHashMap<String, JsonElement>(root).apply { put("agents", JsonObject(newAgents)) }
        return AgentJson.encode(JsonObject(newRoot))
    }
}

/** The phone helper's `config.json` text: the catalog's ([base], `PaseoConfig.render`), own agents, Codex's sandbox. */
public object PhoneHelperConfig {
    public fun compose(base: String, agents: List<OwnAgent>, env: Map<AgentId, Map<String, String>>): String =
        PhoneCodexSandbox.merge(OwnAgentConfig.merge(base, agents, env))
}
