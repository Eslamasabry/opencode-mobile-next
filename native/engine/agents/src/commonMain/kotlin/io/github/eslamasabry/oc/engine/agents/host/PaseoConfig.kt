package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The helper's `config.json`, rendered here and written by `:android:agenthost` at every start (plan 17 §1.10). Byte for
 * byte the Dart `PhoneAgentsHost.configuration()` output for the same catalog and port: catalog-authored commands only
 * (ACP installation is discovery, not admission); relay, service proxy, MCP injection, web UI, dictation and voice off
 * (they would download speech models at start); a small rotating `daemon.log`, never `/dev/null`.
 */
public object PaseoConfig {
    /** 11 S11-6: stable build 4099, preview build 4199. */
    public const val STABLE_PORT: Int = 4099
    public const val PREVIEW_PORT: Int = 4199

    /** Env for the helper (16 L12). Never the password. */
    public val environment: Map<String, String> = linkedMapOf(
        "PASEO_RELAY_ENABLED" to "false",
        "PASEO_WEB_UI_ENABLED" to "false",
        "PASEO_DICTATION_ENABLED" to "false",
        "PASEO_VOICE_MODE_ENABLED" to "false",
        "PASEO_SERVICE_PROXY_ENABLED" to "false",
    )

    public fun render(catalog: AgentCatalog = AgentCatalog.builtIn, port: Int = STABLE_PORT): PaseoConfigFile {
        fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*pairs))
        fun off() = obj("enabled" to JsonPrimitive(false))
        val providers = linkedMapOf<String, JsonElement>()
        for (agent in catalog.agents) {
            val recipe = agent.recipe ?: continue
            if (agent.route != AgentRoute.AcpPaseo) continue
            providers[agent.providerId] = obj(
                "extends" to JsonPrimitive("acp"),
                "label" to JsonPrimitive(agent.name),
                "command" to JsonArray(
                    (listOf("/home/oc/.local/bin/${recipe.executable}") + recipe.launchArgs).map(::JsonPrimitive),
                ),
            )
        }
        val root = obj(
            "version" to JsonPrimitive(1),
            "daemon" to obj(
                "listen" to JsonPrimitive("127.0.0.1:$port"),
                "relay" to off(),
                "serviceProxy" to off(),
                "mcp" to obj("injectIntoAgents" to JsonPrimitive(false)),
            ),
            "features" to obj("webUi" to off(), "dictation" to off(), "voiceMode" to off()),
            "log" to obj(
                "level" to JsonPrimitive("fatal"),
                "file" to obj(
                    "level" to JsonPrimitive("fatal"),
                    "path" to JsonPrimitive("daemon.log"),
                    "rotate" to obj("maxSize" to JsonPrimitive("1M"), "maxFiles" to JsonPrimitive(1)),
                ),
            ),
            "agents" to obj("providers" to JsonObject(providers)),
        )
        return PaseoConfigFile(AgentJson.encode(root))
    }
}
