package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Where a capability can run: the `docs/ux-system/capabilities.json` matrix host columns, in that order. */
public enum class KitHost {
    /** builtin: the server in the app's own Linux. */
    ThisPhone,

    /** A Termux-managed server on this phone. */
    Termux,

    /** oc2Computer. */
    OpenCode2,
    Codex,
    Paseo,
    Demo,
}

/** One registry entry, written by hand from capabilities.json and kept equal to it by `KitCapabilityExplainerTest`. */
public data class KitCapability(
    /** The matrix id, verbatim: "voice.model", "flag:sessionDiff". */
    val id: String,
    /** Hosts with status "supported". */
    val supported: Set<KitHost>,
    /** Hosts with status "partly". */
    val partly: Set<KitHost> = emptySet(),
    /** A [KitEnableFlows] id; null explains only. */
    val enableFlow: String? = null,
)

/** The 21 enable-flow ids, one per capabilities.json `enableFlows` entry (Flutter `KitEnableFlows`). */
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

    public val all: List<String> = listOf(
        ADD_SERVER, PHONE_SETUP, PHONE_SETUP_TERMUX, SERVER_GENERATION, ADD_SERVER_CODEX, ADD_SERVER_PASEO,
        ADD_TOOL_CLAUDE, MODEL_SIGN_IN, TEAM_TURN_ON, TEAM_HOST_GUIDE, VOICE_MODEL_SETUP, MCP_ADD, PROJECT_CHOOSE,
        PROJECT_GIT_INIT, ALLOW_NOTIFICATIONS, ALLOW_BACKGROUND, ALLOW_CAMERA, ALLOW_MICROPHONE, TAILSCALE_SETUP,
        QUOTA_COLLECTOR_GUIDE, ADD_EXTERNAL_AGENT,
    )
}

/** What a handler gets: the capability, its flow, and where it was asked. */
public data class KitEnableRequest(
    val capability: String,
    val flow: String,
    /** The current server's host kind, when known. */
    val host: KitHost? = null,
    val serverName: String? = null,
    /** The page the offer was on. */
    val source: String? = null,
)

/** Runs one enable flow (opens the setup page, asks the permission…). The app registers one per flow. */
public fun interface KitEnableFlowHandler {
    public suspend fun run(request: KitEnableRequest)
}

/** Every matrix capability (35), 21 of them with an enable flow (Flutter `KitCapabilities.all`). */
public object KitCapabilities {
    private val H = KitHost.entries.toSet()
    private val phones = setOf(KitHost.ThisPhone, KitHost.Termux)
    private val oc = phones + KitHost.OpenCode2
    private val real = H - KitHost.Demo

    public val all: List<KitCapability> = listOf(
        KitCapability("server.any", real, setOf(KitHost.Demo), KitEnableFlows.ADD_SERVER),
        KitCapability("server.oc2", oc, emptySet(), KitEnableFlows.SERVER_GENERATION),
        KitCapability("server.codex", setOf(KitHost.Codex), setOf(KitHost.OpenCode2, KitHost.Paseo), KitEnableFlows.ADD_SERVER_CODEX),
        KitCapability("server.paseo", setOf(KitHost.Termux, KitHost.Paseo), setOf(KitHost.OpenCode2), KitEnableFlows.ADD_SERVER_PASEO),
        KitCapability("phone.builtin", setOf(KitHost.ThisPhone), setOf(KitHost.Termux), KitEnableFlows.PHONE_SETUP),
        KitCapability("phone.termux", setOf(KitHost.Termux), setOf(KitHost.ThisPhone), KitEnableFlows.PHONE_SETUP_TERMUX),
        KitCapability("phone.any", phones),
        KitCapability("model.auth", oc, setOf(KitHost.Codex, KitHost.Paseo), KitEnableFlows.MODEL_SIGN_IN),
        KitCapability(
            "team.on", setOf(KitHost.ThisPhone, KitHost.OpenCode2), setOf(KitHost.Termux, KitHost.Codex, KitHost.Paseo),
            KitEnableFlows.TEAM_TURN_ON,
        ),
        KitCapability("team.phone", setOf(KitHost.ThisPhone), setOf(KitHost.Termux)),
        KitCapability("team.control", phones, setOf(KitHost.OpenCode2, KitHost.Codex, KitHost.Paseo), KitEnableFlows.TEAM_HOST_GUIDE),
        KitCapability("claude.local", setOf(KitHost.Termux), emptySet(), KitEnableFlows.ADD_TOOL_CLAUDE),
        KitCapability("voice.model", H, emptySet(), KitEnableFlows.VOICE_MODEL_SETUP),
        KitCapability("mcp.any", emptySet(), oc, KitEnableFlows.MCP_ADD),
        KitCapability("project.open", oc, setOf(KitHost.Codex, KitHost.Paseo), KitEnableFlows.PROJECT_CHOOSE),
        KitCapability("project.git", oc, emptySet(), KitEnableFlows.PROJECT_GIT_INIT),
        KitCapability("perm.notifications", real, emptySet(), KitEnableFlows.ALLOW_NOTIFICATIONS),
        KitCapability("perm.battery", real, emptySet(), KitEnableFlows.ALLOW_BACKGROUND),
        KitCapability("perm.camera", setOf(KitHost.OpenCode2), phones, KitEnableFlows.ALLOW_CAMERA),
        KitCapability("perm.mic", real, setOf(KitHost.Demo), KitEnableFlows.ALLOW_MICROPHONE),
        KitCapability("network.tailscale", setOf(KitHost.OpenCode2, KitHost.Paseo), setOf(KitHost.Codex), KitEnableFlows.TAILSCALE_SETUP),
        KitCapability("quota.collector", emptySet(), setOf(KitHost.OpenCode2), KitEnableFlows.QUOTA_COLLECTOR_GUIDE),
        KitCapability("agent.a2a", real, setOf(KitHost.Demo), KitEnableFlows.ADD_EXTERNAL_AGENT),
        KitCapability("flag:fileBrowsing+terminal", oc, setOf(KitHost.Demo)),
        KitCapability("flag:terminal", oc, setOf(KitHost.Demo)),
        KitCapability("flag:sessionDiff", oc, setOf(KitHost.Demo)),
        KitCapability("flag:serverCatalog", oc, setOf(KitHost.Demo)),
        KitCapability("flag:toolInventory", emptySet()),
        KitCapability("flag:usageStatistics", oc, setOf(KitHost.Codex)),
        KitCapability("flag:stagedRevert+sessionNotes", oc),
        KitCapability("flag:worktreeCreate+sessionShare+managedWorkspaces", emptySet(), oc),
        KitCapability("flag:developmentServices", oc),
        KitCapability("flag:remoteUpgrade", emptySet()),
        KitCapability("flag:promptAttachments", oc + KitHost.Demo),
    )

    private val byId: Map<String, KitCapability> = all.associateBy { it.id }

    public fun byId(id: String): KitCapability? = byId[id]

    /** Titles that name several things ("Files and terminal aren't available…"). */
    internal val pluralTitles: Set<String> = setOf(
        "team.control", "mcp.any", "perm.notifications", "agent.a2a", "flag:fileBrowsing+terminal", "flag:serverCatalog",
        "flag:stagedRevert+sessionNotes", "flag:worktreeCreate+sessionShare+managedWorkspaces",
        "flag:developmentServices", "flag:promptAttachments",
    )
}

/**
 * The enable-flow handlers the app registers at start-up, so the kit imports no screens. Provided to the kit through
 * [LocalKitEnableFlows]; without a handler a capability only explains, never offers a dead button.
 */
@OptIn(ExperimentalAtomicApi::class)
public class KitEnableFlowRegistry {
    private val handlers = AtomicReference<Map<String, KitEnableFlowHandler>>(emptyMap())
    private val log = AppLog.tagged("kit")

    public fun register(flow: String, handler: KitEnableFlowHandler) {
        require(flow in KitEnableFlows.all) { "\"$flow\" is not a KitEnableFlows id" }
        while (true) {
            val current = handlers.load()
            if (handlers.compareAndSet(current, current + (flow to handler))) return
        }
    }

    /** True when [capability] has a flow and that flow a handler. */
    public fun canEnable(capability: String): Boolean {
        val flow = KitCapabilities.byId(capability)?.enableFlow ?: return false
        return flow in handlers.load()
    }

    public val unregisteredFlows: List<String> get() = KitEnableFlows.all.filter { it !in handlers.load() }

    /**
     * Runs [capability]'s flow; false when it has none, no handler is registered (a no-op), or the handler failed. A
     * failing handler never crashes the screen: the failure is logged (its kind only) and the explainer stays.
     */
    public suspend fun enable(capability: String, host: KitHost? = null, serverName: String? = null, source: String? = null): Boolean {
        val flow = KitCapabilities.byId(capability)?.enableFlow ?: return false
        val handler = handlers.load()[flow] ?: return false
        val request = KitEnableRequest(capability, flow, host, serverName, source)
        return when (attempt({ Failure.Unexpected() }) { handler.run(request) }) {
            is Outcome.Ok -> true
            is Outcome.Failed -> {
                log.w { "the enable flow $flow failed" }
                false
            }
        }
    }
}

/** The app's enable flows; the default has none, so every capability only explains. */
public val LocalKitEnableFlows: androidx.compose.runtime.ProvidableCompositionLocal<KitEnableFlowRegistry> =
    staticCompositionLocalOf { KitEnableFlowRegistry() }
