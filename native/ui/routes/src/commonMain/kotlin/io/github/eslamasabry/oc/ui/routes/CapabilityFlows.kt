package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * Stable doors from docs/ux-system/capabilities.json. The app supplies handlers; the kit imports no
 * features.
 */
public enum class CapabilityEnableFlow(
    public val id: String,
    public val capability: String,
    public val phoneOnly: Boolean = false,
) {
    AddServer("add-server", "server.any"),
    PhoneSetup("phone-setup", "phone.builtin", true),
    PhoneSetupTermux("phone-setup-termux", "phone.termux", true),
    ServerGeneration("server-generation", "server.oc2"),
    AddServerCodex("add-server-codex", "server.codex"),
    AddServerPaseo("add-server-paseo", "server.paseo"),
    AddToolClaude("add-tool-claude", "claude.local", true),
    ModelSignIn("model-sign-in", "model.auth"),
    TeamTurnOn("team-turn-on", "team.on"),
    TeamHostGuide("team-host-guide", "team.control"),
    VoiceModelSetup("voice-model-setup", "voice.model", true),
    McpAdd("mcp-add", "mcp.any"),
    ProjectChoose("project-choose", "project.open"),
    ProjectGitInit("project-git-init", "project.git"),
    AllowNotifications("allow-notifications", "perm.notifications", true),
    AllowBackground("allow-background", "perm.battery", true),
    AllowCamera("allow-camera", "perm.camera", true),
    AllowMicrophone("allow-microphone", "perm.mic", true),
    TailscaleSetup("tailscale-setup", "network.tailscale", true),
    QuotaCollectorGuide("quota-collector-guide", "quota.collector"),
    AddExternalAgent("add-external-agent", "agent.a2a"),
}

public data class CapabilityEnableRequest(
    val flow: CapabilityEnableFlow,
    val ref: ConversationRef? = null,
)

public fun interface CapabilityEnableHandler {
    public suspend fun enable(request: CapabilityEnableRequest): Outcome<Unit>
}

/**
 * Missing platform doors explain only: no dead button and no fallback that could mutate a different
 * target.
 */
public enum class CapabilityFlowLimit {
    TeamAfterSwitch,
    ExternalAgentsDropped,
    TermuxMigrationOnly,
}

public class CapabilityFlows(
    handlers: Map<CapabilityEnableFlow, CapabilityEnableHandler>,
    unavailable: Map<CapabilityEnableFlow, CapabilityFlowLimit> = emptyMap(),
) {
    private val handlers = handlers.toMap()
    public val unavailable: Map<CapabilityEnableFlow, CapabilityFlowLimit> = unavailable.toMap()
    public val resolved: Set<CapabilityEnableFlow>
        get() = handlers.keys + unavailable.keys

    public val registered: Set<CapabilityEnableFlow>
        get() = handlers.keys

    public fun canEnable(capability: String): Boolean =
        handlers.keys.any { it.capability == capability }

    public suspend fun enable(request: CapabilityEnableRequest): Outcome<Unit> =
        handlers[request.flow]?.enable(request)
            ?: Outcome.Failed(Failure.Unavailable(FeatureId(request.flow.capability)))

    public companion object {
        public val desktopFlows: Set<CapabilityEnableFlow> =
            CapabilityEnableFlow.entries.filterNot { it.phoneOnly }.toSet()
        public val androidFlows: Set<CapabilityEnableFlow> = CapabilityEnableFlow.entries.toSet()
    }
}

/** Help › What works here: the platform's real setup doors. */
@kotlinx.serialization.Serializable public data object CapabilitiesRoute : AppRoute

/**
 * The pairing kind is selected before the page opens; no credentials or automatic mutations in
 * routes.
 */
@kotlinx.serialization.Serializable
public enum class CapabilityComputerKind {
    Codex,
    Paseo,
}

@kotlinx.serialization.Serializable
public data class CapabilityAddComputerRoute(val kind: CapabilityComputerKind) : AppRoute

@kotlinx.serialization.Serializable
public data class CapabilityPhoneAgentRoute(val agent: String) : AppRoute

@kotlinx.serialization.Serializable
public data class CapabilityAgentAccountRoute(
    val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId
) : AppRoute

@kotlinx.serialization.Serializable
public enum class CapabilityServerDoor {
    AddServer,
    OpenCode2,
    Tailscale,
}

@kotlinx.serialization.Serializable
public data class CapabilityServerSetupRoute(val door: CapabilityServerDoor) : AppRoute
