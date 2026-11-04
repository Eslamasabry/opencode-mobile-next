package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** The hosts a capability is judged against (Dart `KitHost`). */
public enum class KitHost { ThisPhone, Termux, OpenCode2, Codex, Paseo, Demo }

/** Every flow that turns a missing capability on (Dart `KitEnableFlows`, `docs/ux-system/capabilities.json`): 21, each distinct. */
public object KitEnableFlows {
    public const val ADD_SERVER: String = "add-server"
    public const val PHONE_SETUP: String = "phone-setup"
    public const val PHONE_SETUP_TERMUX: String = "phone-setup-termux"
    public const val SERVER_GENERATION: String = "server-generation"
    public const val ADD_SERVER_CODEX: String = "add-server-codex"
    public const val ADD_SERVER_PASEO: String = "add-server-paseo"
    public const val ADD_TOOL_CLAUDE: String = "add-tool-claude"
    public const val MODEL_SIGN_IN: String = "model-sign-in"
    public const val TEAM_TURN_ON: String = "team-turn-on"
    public const val TEAM_HOST_GUIDE: String = "team-host-guide"
    public const val VOICE_MODEL_SETUP: String = "voice-model-setup"
    public const val MCP_ADD: String = "mcp-add"
    public const val PROJECT_CHOOSE: String = "project-choose"
    public const val PROJECT_GIT_INIT: String = "project-git-init"
    public const val ALLOW_NOTIFICATIONS: String = "allow-notifications"
    public const val ALLOW_BACKGROUND: String = "allow-background"
    public const val ALLOW_CAMERA: String = "allow-camera"
    public const val ALLOW_MICROPHONE: String = "allow-microphone"
    public const val TAILSCALE_SETUP: String = "tailscale-setup"
    public const val QUOTA_COLLECTOR_GUIDE: String = "quota-collector-guide"
    public const val ADD_EXTERNAL_AGENT: String = "add-external-agent"

    public val all: List<String> =
        listOf(
            ADD_SERVER, PHONE_SETUP, PHONE_SETUP_TERMUX, SERVER_GENERATION, ADD_SERVER_CODEX, ADD_SERVER_PASEO, ADD_TOOL_CLAUDE,
            MODEL_SIGN_IN, TEAM_TURN_ON, TEAM_HOST_GUIDE, VOICE_MODEL_SETUP, MCP_ADD, PROJECT_CHOOSE, PROJECT_GIT_INIT,
            ALLOW_NOTIFICATIONS, ALLOW_BACKGROUND, ALLOW_CAMERA, ALLOW_MICROPHONE, TAILSCALE_SETUP, QUOTA_COLLECTOR_GUIDE,
            ADD_EXTERNAL_AGENT,
        )
}

/** One ask to turn a capability on: which capability, by which flow, on which host (Dart `KitEnableRequest`). */
@Immutable
public data class KitEnableRequest(
    val capability: String,
    val flow: String,
    val host: KitHost? = null,
    val serverName: String? = null,
    val source: String? = null,
)

/**
 * The app's registry of flow handlers (the shell registers the flows it can run; a computer registers only those it
 * can). [enable] returns false and does nothing when no handler is registered for the flow: enabling needs a handler
 * or is a no-op (B-KIT-013), never a dead button.
 */
public class KitEnableRegistry(private val handlers: Map<String, (KitEnableRequest) -> Unit>) {
    public fun canEnable(flow: String): Boolean = flow in handlers

    public fun enable(request: KitEnableRequest): Boolean {
        val handler = handlers[request.flow] ?: return false
        handler(request)
        return true
    }

    public companion object {
        public val None: KitEnableRegistry = KitEnableRegistry(emptyMap())
    }
}

public val LocalKitEnableRegistry: ProvidableCompositionLocal<KitEnableRegistry> = staticCompositionLocalOf { KitEnableRegistry.None }
