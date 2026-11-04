package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/**
 * The runtime pins the card tools are qualified with (Flutter `AgentCatalog` claude recipe, `PaseoPhoneScripts`).
 * Transport qualification is version-specific: refresh only with new runtime evidence.
 */
public data class CardsPins(
    val claudeVersion: String = "2.1.316",
    val nodeVersion: String = "v24.21.0",
    /** Claude Code and the Paseo daemon are tested releases and Node is the qualified pin. */
    val qualified: Boolean = true,
)

/**
 * The fixed install, removal and verification scripts for the card tools inside the built-in Linux (Flutter
 * `genUiInstallScript` / `genUiVerificationScript`; B-CHAT-041, B-CHAT-042). App-authored: no agent-supplied string
 * takes part. The bash wrapper hands a base64 JSON description to the system Python, whose installer (copied verbatim
 * from Flutter, [CardToolJs.INSTALLER_PY]) owns the file logic: ownership markers, user JSONC and unowned entries left
 * alone, symlink and foreign-owner refusal, atomic writes, rollback after a failed self-check, the Claude CLI user-scope
 * registration and its exact show-card permission rule. Exit codes are [CardsInstallOutcome.ofExitCode].
 */
public object CardsInstallScripts {
    private val PROFILE = Regex("^[a-zA-Z0-9_-]{1,96}$")

    public fun install(profileId: String, agent: CardAgent, enabled: Boolean, pins: CardsPins = CardsPins()): String =
        managed(profileId, agent, enabled, verify = false, pins = pins)

    public fun verification(profileId: String, agent: CardAgent, pins: CardsPins = CardsPins()): String =
        managed(profileId, agent, enabled = true, verify = true, pins = pins)

    /** The Linux user the script runs as: Claude Code under the per-profile agent user, OpenCode 2 as root. */
    public fun runsAsAgentUser(agent: CardAgent): Boolean = agent == CardAgent.Claude

    private fun managed(profileId: String, agent: CardAgent, enabled: Boolean, verify: Boolean, pins: CardsPins): String {
        require(PROFILE.matches(profileId)) { "Invalid managed profile identity" }
        require(agent == CardAgent.Claude || agent == CardAgent.OpenCode2) { "${agent.id} has no MCP registration yet" }
        val claude = agent == CardAgent.Claude
        val home = "/home/oc/.oc-profiles/$profileId"
        val directory = if (claude) "$home/.oc-genui" else "/root/.oc-genui/${agent.id}"
        val config = if (claude) "$home/claude/.claude.json" else "/root/.oc-opencode2/config/opencode/opencode.json"
        val data =
            JsonObject(
                linkedMapOf(
                    "profile" to JsonPrimitive(profileId),
                    "kind" to JsonPrimitive(if (claude) "claude" else "openCode2"),
                    "enable" to JsonPrimitive(enabled),
                    "verify" to JsonPrimitive(verify),
                    "claudeVersion" to JsonPrimitive(pins.claudeVersion),
                    "nodeVersion" to JsonPrimitive(pins.nodeVersion),
                    "qualifiedPins" to JsonPrimitive(pins.qualified),
                    "home" to JsonPrimitive(home),
                    "directory" to JsonPrimitive(directory),
                    "config" to JsonPrimitive(config),
                    "cli" to JsonPrimitive("/home/oc/.local/share/oc-agents/claude/${pins.claudeVersion}/payload/claude"),
                    "node" to JsonPrimitive(if (claude) "/home/oc/.local/node/bin/node" else "/usr/bin/node"),
                    "helper" to JsonPrimitive(CardToolServer.script("$directory/enabled")),
                ),
            )
        val encoded = Base64.Default.encode(data.toString().encodeToByteArray())
        return "set -eu\n" +
            "umask 077\n" +
            "# Python is a system executable, never the agent-owned Node runtime.\n" +
            "[ -x /usr/bin/python3 ] || exit 20\n" +
            "/usr/bin/python3 - '$encoded' <<'OC_GENUI_SETUP' 2>/dev/null\n" +
            CardToolJs.INSTALLER_PY + "\n" +
            "OC_GENUI_SETUP\n"
    }
}
