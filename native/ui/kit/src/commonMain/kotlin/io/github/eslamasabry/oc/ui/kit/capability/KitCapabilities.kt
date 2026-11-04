package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt

/**
 * Distinct doors from the capabilities matrix. No flow runs until its owner registers a handler.
 */
public enum class KitEnableFlow(public val wire: String) {
    AddServer("add-server"),
    PhoneSetup("phone-setup"),
    PhoneSetupTermux("phone-setup-termux"),
    ServerGeneration("server-generation"),
    AddServerCodex("add-server-codex"),
    AddServerPaseo("add-server-paseo"),
    AddToolClaude("add-tool-claude"),
    ModelSignIn("model-sign-in"),
    TeamTurnOn("team-turn-on"),
    TeamHostGuide("team-host-guide"),
    VoiceModelSetup("voice-model-setup"),
    McpAdd("mcp-add"),
    ProjectChoose("project-choose"),
    ProjectGitInit("project-git-init"),
    AllowNotifications("allow-notifications"),
    AllowBackground("allow-background"),
    AllowCamera("allow-camera"),
    AllowMicrophone("allow-microphone"),
    TailscaleSetup("tailscale-setup"),
    QuotaCollectorGuide("quota-collector-guide"),
    AddExternalAgent("add-external-agent"),
}

/** Documentation hosts only. Runtime admission always uses live server/platform capabilities. */
public enum class KitHost {
    ThisPhone,
    Termux,
    OpenCode2,
    Codex,
    Paseo,
    Demo,
}

public data class KitCapability(
    val id: String,
    val supported: Set<KitHost>,
    val partly: Set<KitHost> = emptySet(),
    val enableFlow: KitEnableFlow? = null,
)

public data class KitEnableRequest(
    val capability: String,
    val flow: KitEnableFlow,
    val host: KitHost? = null,
    val serverName: String? = null,
    val source: String? = null,
)

public fun interface KitEnableFlowHandler {
    public suspend fun enable(request: KitEnableRequest): Outcome<Unit>
}

/**
 * Screen/app-owned registry: changing owners drops their handlers instead of retaining stale
 * closures.
 */
public class KitCapabilities {
    private val handlers = mutableStateMapOf<KitEnableFlow, KitEnableFlowHandler>()

    public fun register(flow: KitEnableFlow, handler: KitEnableFlowHandler) {
        handlers[flow] = handler
    }

    public fun unregister(flow: KitEnableFlow) {
        handlers.remove(flow)
    }

    public fun canEnable(capability: String): Boolean =
        byId(capability)?.enableFlow?.let(handlers::containsKey) == true

    public val unregisteredFlows: List<KitEnableFlow>
        get() = KitEnableFlow.entries.filterNot(handlers::containsKey)

    /**
     * Unknown capability or missing handler is a no-op. Cancellation propagates; exceptions never
     * become UI text.
     */
    public suspend fun enable(
        capability: String,
        host: KitHost? = null,
        serverName: String? = null,
        source: String? = null,
    ): Boolean {
        val flow = byId(capability)?.enableFlow ?: return false
        val handler = handlers[flow] ?: return false
        val result =
            attempt({ Failure.Unknown() }) {
                handler.enable(KitEnableRequest(capability, flow, host, serverName, source))
            }
        return result is Outcome.Ok && result.value is Outcome.Ok
    }

    public companion object {
        /** Port of the frozen matrix in docs/ux-system/capabilities.json. */
        public val all: List<KitCapability> =
            listOf(
                KitCapability(
                    "server.any",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                    ),
                    setOf(KitHost.Demo),
                    KitEnableFlow.AddServer,
                ),
                KitCapability(
                    "server.oc2",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    emptySet(),
                    KitEnableFlow.ServerGeneration,
                ),
                KitCapability(
                    "server.codex",
                    setOf(KitHost.Codex),
                    setOf(KitHost.OpenCode2, KitHost.Paseo),
                    KitEnableFlow.AddServerCodex,
                ),
                KitCapability(
                    "server.paseo",
                    setOf(KitHost.Termux, KitHost.Paseo),
                    setOf(KitHost.OpenCode2),
                    KitEnableFlow.AddServerPaseo,
                ),
                KitCapability(
                    "phone.builtin",
                    setOf(KitHost.ThisPhone),
                    setOf(KitHost.Termux),
                    KitEnableFlow.PhoneSetup,
                ),
                KitCapability(
                    "phone.termux",
                    setOf(KitHost.Termux),
                    setOf(KitHost.ThisPhone),
                    KitEnableFlow.PhoneSetupTermux,
                ),
                KitCapability("phone.any", setOf(KitHost.ThisPhone, KitHost.Termux), emptySet()),
                KitCapability(
                    "model.auth",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Codex, KitHost.Paseo),
                    KitEnableFlow.ModelSignIn,
                ),
                KitCapability(
                    "team.on",
                    setOf(KitHost.ThisPhone, KitHost.OpenCode2),
                    setOf(KitHost.Termux, KitHost.Codex, KitHost.Paseo),
                    KitEnableFlow.TeamTurnOn,
                ),
                KitCapability("team.phone", setOf(KitHost.ThisPhone), setOf(KitHost.Termux)),
                KitCapability(
                    "team.control",
                    setOf(KitHost.ThisPhone, KitHost.Termux),
                    setOf(KitHost.OpenCode2, KitHost.Codex, KitHost.Paseo),
                    KitEnableFlow.TeamHostGuide,
                ),
                KitCapability(
                    "claude.local",
                    setOf(KitHost.Termux),
                    emptySet(),
                    KitEnableFlow.AddToolClaude,
                ),
                KitCapability(
                    "voice.model",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                        KitHost.Demo,
                    ),
                    emptySet(),
                    KitEnableFlow.VoiceModelSetup,
                ),
                KitCapability(
                    "mcp.any",
                    emptySet(),
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    KitEnableFlow.McpAdd,
                ),
                KitCapability(
                    "project.open",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Codex, KitHost.Paseo),
                    KitEnableFlow.ProjectChoose,
                ),
                KitCapability(
                    "project.git",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    emptySet(),
                    KitEnableFlow.ProjectGitInit,
                ),
                KitCapability(
                    "perm.notifications",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                    ),
                    emptySet(),
                    KitEnableFlow.AllowNotifications,
                ),
                KitCapability(
                    "perm.battery",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                    ),
                    emptySet(),
                    KitEnableFlow.AllowBackground,
                ),
                KitCapability(
                    "perm.camera",
                    setOf(KitHost.OpenCode2),
                    setOf(KitHost.ThisPhone, KitHost.Termux),
                    KitEnableFlow.AllowCamera,
                ),
                KitCapability(
                    "perm.mic",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                    ),
                    setOf(KitHost.Demo),
                    KitEnableFlow.AllowMicrophone,
                ),
                KitCapability(
                    "network.tailscale",
                    setOf(KitHost.OpenCode2, KitHost.Paseo),
                    setOf(KitHost.Codex),
                    KitEnableFlow.TailscaleSetup,
                ),
                KitCapability(
                    "quota.collector",
                    emptySet(),
                    setOf(KitHost.OpenCode2),
                    KitEnableFlow.QuotaCollectorGuide,
                ),
                KitCapability(
                    "agent.a2a",
                    setOf(
                        KitHost.ThisPhone,
                        KitHost.Termux,
                        KitHost.OpenCode2,
                        KitHost.Codex,
                        KitHost.Paseo,
                    ),
                    setOf(KitHost.Demo),
                    KitEnableFlow.AddExternalAgent,
                ),
                KitCapability(
                    "flag:fileBrowsing+terminal",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Demo),
                ),
                KitCapability(
                    "flag:terminal",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Demo),
                ),
                KitCapability(
                    "flag:sessionDiff",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Demo),
                ),
                KitCapability(
                    "flag:serverCatalog",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Demo),
                ),
                KitCapability("flag:toolInventory", emptySet(), emptySet()),
                KitCapability(
                    "flag:usageStatistics",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    setOf(KitHost.Codex),
                ),
                KitCapability(
                    "flag:stagedRevert+sessionNotes",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    emptySet(),
                ),
                KitCapability(
                    "flag:worktreeCreate+sessionShare+managedWorkspaces",
                    emptySet(),
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                ),
                KitCapability(
                    "flag:developmentServices",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2),
                    emptySet(),
                ),
                KitCapability("flag:remoteUpgrade", emptySet(), emptySet()),
                KitCapability(
                    "flag:promptAttachments",
                    setOf(KitHost.ThisPhone, KitHost.Termux, KitHost.OpenCode2, KitHost.Demo),
                    emptySet(),
                ),
            )
        private val index = all.associateBy(KitCapability::id)

        public fun byId(id: String): KitCapability? = index[id]
    }
}

public val LocalKitCapabilities:
    androidx.compose.runtime.ProvidableCompositionLocal<KitCapabilities> =
    staticCompositionLocalOf {
        KitCapabilities()
    }
