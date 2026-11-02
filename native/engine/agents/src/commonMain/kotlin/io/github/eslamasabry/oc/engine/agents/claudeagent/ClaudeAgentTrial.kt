package io.github.eslamasabry.oc.engine.agents.claudeagent

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.engine.agents.OwnerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// The Claude Agent trial (2026-11-01; AGENTS-BACKENDS-40): OpenCode 2's own "Claude Agent" provider on this phone's
// server, beside Claude Code through Paseo. OpenCode (2.2.3 and later) runs the phone's installed `claude` through the
// Agent SDK with Claude Code's own sign-in, and Claude then works with OpenCode's tools and permissions. The app never
// reads, copies or passes on that sign-in: it points OpenCode at the program and the sign-in folder Claude Code already
// uses on this phone, and tells OpenCode the person chose this login ("connect external"). Port of
// lib/domain/claude_agent_trial.dart and lib/state/claude_agent_trial.dart. Today's Claude Code through Paseo is
// untouched. Branding is "Claude Agent", never "Claude Code" (owner 2026-10-31).

/** OpenCode's provider and integration id. */
public const val CLAUDE_AGENT_PROVIDER_ID: String = "claude-agent"

/** OpenCode's login method for it: "Claude Code login (claude auth login)". */
public const val CLAUDE_AGENT_LOGIN_METHOD_ID: String = "claude-login"

/** The first OpenCode release with the provider (changelog v2.2.3). */
public const val CLAUDE_AGENT_MINIMUM_SERVER: String = "2.2.3"

/** Whether [version] ("2.2.5", "v2.2.5-beta") is [minimum] or later. Unknown or malformed versions are not. */
public fun serverVersionAtLeast(version: String?, minimum: String): Boolean {
    val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")
    fun parse(raw: String?): List<Long>? =
        pattern.find(raw?.trim().orEmpty())?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }
    val current = parse(version) ?: return false
    val required = parse(minimum) ?: return false
    for (i in 0 until 3) if (current[i] != required[i]) return current[i] > required[i]
    return true
}

/** Where the trial stands for the connected phone server. Each value is one words key. */
public enum class ClaudeAgentTrialState {
    /** Not this phone's OpenCode, too old, or the provider is not offered. */
    Unavailable,

    /** Offered, and the person has not tried it yet. */
    Available,

    /** Pointing OpenCode at Claude Code and waiting for its check. */
    Preparing,

    /** OpenCode found Claude Code but no sign-in. */
    NeedsSignIn,

    /** Claude Code is not installed in this phone's Ubuntu. */
    NotInstalled,

    /** The sign-in folder's place is taken by a folder the app did not make. */
    Blocked,

    /** Claude Agent's models are in the list. */
    Ready,

    /** Something else went wrong; Check again retries. */
    Failed,
}

/** The connected server's integrations (OpenCode `/integration`), as the trial needs them. */
public interface ClaudeAgentIntegrations {
    public suspend fun listIntegrations(): Outcome<List<IntegrationInfo>>

    /** Whether the server takes [connectExternal] (OpenCode `POST /integration/{id}/connect/external`). */
    public val connectsExternal: Boolean

    /** Records that the person chose an external login; nothing secret is sent. */
    public suspend fun connectExternal(integrationId: String, methodId: String): Outcome<Unit>

    public val removesCredentials: Boolean

    public suspend fun removeCredential(id: String): Outcome<Unit>
}

/** What the trial needs from the connection (the connection engine binds it; tests pass their own). */
public interface ClaudeAgentTrialHost {
    /** This phone's own OpenCode server (the in-app one), not a computer, and not an isolated view. */
    public val onPhoneServer: Boolean

    /** The connected server's release ("2.2.5"). */
    public val serverVersion: String?

    /** The agent home Claude Code signs in to on this phone; null with no connected profile. */
    public val agentOwner: OwnerId?

    /** Changes whenever the connection moves to another server or profile (`<profileId>@<version>`). */
    public val scope: String?

    public val integrations: ClaudeAgentIntegrations?

    /** The loaded model list has Claude Agent's models; null before the list has loaded. */
    public val hasClaudeAgentModels: Boolean?

    public suspend fun refreshCatalog()
}

/**
 * The trial's state for the connected server. Call it from one lane (the connection engine's); [start] and
 * [checkAgain] run one at a time (a call while one runs returns at once, as in Dart).
 */
public class ClaudeAgentTrial(
    private val host: ClaudeAgentTrialHost,
    private val link: ClaudeAgentLinkRunner,
    /** Runs the one delayed late-load re-read; the owner's scope, cancelled with it. */
    private val scope: CoroutineScope,
    private val pollInterval: Duration = 1500.milliseconds,
    private val pollAttempts: Int = 30,
    /**
     * OpenCode loads its built-in providers for a project on the first read there, so the first list after a server
     * start can miss Claude Agent (emulator, 2026-11-01). One more read after this delay, once per scope.
     */
    private val lateLoadDelay: Duration = 3.seconds,
) {
    private var integration: IntegrationInfo? = null
    private var seenScope: String? = null
    private var lateLoadScope: String? = null
    private var outcome: ClaudeAgentTrialState? = null
    private var generation = 0
    private val running = Mutex()
    private val mutableState = MutableStateFlow(ClaudeAgentTrialState.Unavailable)

    /** Published on every change the trial makes; [stateNow] also reflects host changes not yet published. */
    public val state: StateFlow<ClaudeAgentTrialState> = mutableState.asStateFlow()

    /**
     * The flag behind `ServerCapabilities.claudeAgent`: this phone's OpenCode is 2.2.3 or later and offers the
     * provider in the current scope. The capability is DERIVED from this ([capabilities]), so it is not an input.
     */
    public val capable: Boolean
        get() = host.onPhoneServer && serverVersionAtLeast(host.serverVersion, CLAUDE_AGENT_MINIMUM_SERVER) &&
            integration != null && seenScope == host.scope

    public fun capabilities(base: ServerCapabilities): ServerCapabilities = base.withClaudeAgent(capable)

    public fun stateNow(): ClaudeAgentTrialState {
        if (!capable) return ClaudeAgentTrialState.Unavailable
        if (running.isLocked) return ClaudeAgentTrialState.Preparing
        outcome?.let { return it }
        val connections = integration!!.connections
        if (connections.isEmpty()) return ClaudeAgentTrialState.Available
        // OpenCode lists the connection in use first, and reports its check there.
        if (connections.first().status == "needs_auth") return ClaudeAgentTrialState.NeedsSignIn
        // Connected and no problem reported: ready unless the loaded list proves Claude Agent has no models here.
        return if (host.hasClaudeAgentModels == false) ClaudeAgentTrialState.Failed else ClaudeAgentTrialState.Ready
    }

    /** Takes the integrations a screen just read (the model picker reads them anyway). */
    public fun observe(integrations: List<IntegrationInfo>) {
        val found = integrations.lastOrNull { integration ->
            integration.id == CLAUDE_AGENT_PROVIDER_ID &&
                integration.methods.any { it.type == "external" && it.id == CLAUDE_AGENT_LOGIN_METHOD_ID }
        }
        val current = host.scope
        if (current != seenScope) outcome = null
        integration = found
        seenScope = current
        publish()
        if (found == null && current != null && lateLoadScope != current && host.onPhoneServer &&
            serverVersionAtLeast(host.serverVersion, CLAUDE_AGENT_MINIMUM_SERVER)
        ) {
            lateLoadScope = current
            scope.launch {
                delay(lateLoadDelay)
                if (host.scope == current && integration == null) refresh()
            }
        }
    }

    /** Reads the server's integrations itself. Unknown stays unknown; the picker's own read retries. */
    public suspend fun refresh() {
        val integrations = host.integrations ?: return
        if (!host.onPhoneServer) return
        (integrations.listIntegrations() as? Outcome.Ok)?.let { observe(it.value) }
    }

    /** Turns the trial on: points OpenCode at Claude Code, tells OpenCode the person chose Claude Code's own login,
     *  and waits for its check. */
    public suspend fun start(): Unit = run(fresh = false)

    /** After signing in: the same, with a fresh check by OpenCode (a new connection replaces the old ones). */
    public suspend fun checkAgain(): Unit = run(fresh = true)

    private suspend fun run(fresh: Boolean) {
        if (!capable) return
        if (!running.tryLock()) return
        val owner = host.agentOwner
        val integrations = host.integrations
        val myGeneration = ++generation
        outcome = null
        publish()
        fun current() = myGeneration == generation && seenScope == host.scope
        try {
            if (owner == null || integrations == null || !integrations.connectsExternal) {
                outcome = ClaudeAgentTrialState.Failed
                return
            }
            val linked = link.link(owner)
            if (!current()) return
            when (linked) {
                ClaudeAgentLinkResult.NotInstalled -> { outcome = ClaudeAgentTrialState.NotInstalled; return }
                ClaudeAgentLinkResult.Blocked -> { outcome = ClaudeAgentTrialState.Blocked; return }
                ClaudeAgentLinkResult.Failed -> { outcome = ClaudeAgentTrialState.Failed; return }
                ClaudeAgentLinkResult.Linked, ClaudeAgentLinkResult.LinkedWithoutResume -> Unit
            }
            val before = integration?.connections.orEmpty().mapNotNull { it.id }.toSet()
            // One connection: OpenCode checks the login when a connection starts.
            if (before.isEmpty() || fresh) {
                val connected = integrations.connectExternal(CLAUDE_AGENT_PROVIDER_ID, CLAUDE_AGENT_LOGIN_METHOD_ID)
                if (connected is Outcome.Failed) {
                    if (current()) outcome = ClaudeAgentTrialState.Failed
                    return
                }
                if (!current()) return
            }
            if (!waitForCheck(integrations, ::current)) {
                if (current()) outcome = ClaudeAgentTrialState.Failed
                return
            }
            if (!current()) return
            if (fresh && before.isNotEmpty()) removeOld(integrations, before)
        } finally {
            running.unlock()
            if (myGeneration == generation) publish()
        }
    }

    /** False when a read failed (the Dart catch). */
    private suspend fun waitForCheck(integrations: ClaudeAgentIntegrations, current: () -> Boolean): Boolean {
        for (attempt in 0 until pollAttempts) {
            if (attempt > 0) delay(pollInterval)
            if (!current()) return true
            val listed = integrations.listIntegrations()
            if (listed !is Outcome.Ok) return false
            observe(listed.value)
            if (integration?.connections?.firstOrNull()?.status == "needs_auth") return true
            host.refreshCatalog()
            if (!current()) return true
            if (host.hasClaudeAgentModels == true) return true
        }
        if (current() && host.hasClaudeAgentModels != true) outcome = ClaudeAgentTrialState.Failed
        return true
    }

    private suspend fun removeOld(integrations: ClaudeAgentIntegrations, before: Set<String>) {
        if (!integrations.removesCredentials) return
        val connections = integration?.connections.orEmpty()
        // Never remove the last one: the newest stays whatever happens.
        if (connections.none { it.id != null && it.id !in before }) return
        // A leftover connection only shows as a second "Claude Agent".
        for (id in before) integrations.removeCredential(id)
        (integrations.listIntegrations() as? Outcome.Ok)?.let { observe(it.value) }
    }

    private fun publish() {
        mutableState.value = stateNow()
    }
}
