package io.github.eslamasabry.oc.engine.connectors.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpAuthLaunch
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.engine.connectors.capabilities.chatConnectSupported
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogRuntime
import io.github.eslamasabry.oc.engine.connectors.text.WebAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Chat connector progress (Dart `McpChatPhase`). Ready means discovery completed for the next model step. */
public enum class McpChatPhase {
    Suggested,
    Connecting,
    NeedsAuthentication,
    Authorizing,
    CheckingTools,
    ToolsReady,
    ConnectedReadinessUnknown,
    Failed,
    Unavailable,
}

/** Fixed keys for localized copy; never server errors or credentials (Dart `McpChatFailure`). */
public enum class McpChatFailure {
    Unavailable,
    InvalidSuggestion,
    SetupRequired,
    NameConflict,
    SourceChanged,
    ConnectFailed,
    AuthenticationFailed,
    OAuthUnavailable,
    NotConnected,
}

public data class McpChatSnapshot(
    val phase: McpChatPhase,
    val failure: McpChatFailure? = null,
    /** Inert and ephemeral: open only through ExternalLinkPolicy; never persist, log or put in diagnostics. */
    val authorizationUrl: String? = null,
    /** The callback port could not be owned: the person pastes the browser's return URL or code. */
    val manualCodeRequired: Boolean = false,
) {
    override fun toString(): String = "McpChatSnapshot($phase, $failure)"
}

/**
 * One explicitly accepted catalogue suggestion, pinned to its conversation (Dart `McpChatController`; B-AGENT-135).
 * The host supplies a fresh gateway for each operation ([gateway]) and invalidates identity changes via [current].
 * Taps coalesce into one pending operation; an add is never repeated once dispatched (an uncertain answer is
 * reconciled by reading the inventory); an existing server with the same name is never reused or overwritten; success
 * ([McpChatPhase.ToolsReady]) is claimed only after an inventory read says connected and the server has the refresh
 * contract; nothing is published after [current] stops holding.
 */
@OptIn(ExperimentalAtomicApi::class)
public class McpChatController(
    public val item: McpCatalogItem,
    private val gateway: suspend () -> Outcome<McpGateway>,
    private val capabilities: () -> ServerCapabilities,
    private val current: () -> Boolean,
    /** The engine scope the operations run in (one pending operation at a time). */
    private val scope: CoroutineScope,
    private val callbacks: OAuthCallbackPort =
        OAuthCallbackPort { _, _, _ -> Outcome.Failed(Failure.Unavailable(OAUTH)) },
) {
    private val state = MutableStateFlow(McpChatSnapshot(McpChatPhase.Suggested))
    public val snapshot: StateFlow<McpChatSnapshot> = state.asStateFlow()
    private val work = AtomicReference<Deferred<Unit>?>(null)

    public var isDisposed: Boolean = false
        private set
    private var invalidated = false
    private var addDispatched = false
    private var authDispatched = false
    private var oauthRevision = 0
    private var launch: McpAuthLaunch? = null
    private var listener: OAuthCallbackListener? = null

    private fun check(): Boolean {
        if (isDisposed || invalidated) return false
        if (!current()) {
            invalidate()
            return false
        }
        return true
    }

    private fun set(
        phase: McpChatPhase,
        failure: McpChatFailure? = null,
        url: String? = null,
        manual: Boolean = false,
    ) {
        if (isDisposed) return
        state.value = McpChatSnapshot(phase, failure, url, manual)
    }

    private fun fail(failure: McpChatFailure) =
        set(if (failure == McpChatFailure.Unavailable) McpChatPhase.Unavailable else McpChatPhase.Failed, failure)

    /** No remote cancellation: invalidation cannot revoke credentials established elsewhere. */
    public fun invalidate() {
        if (isDisposed) return
        invalidated = true
        clearLaunch()
        if (state.value.failure != McpChatFailure.SourceChanged) fail(McpChatFailure.SourceChanged)
    }

    private fun clearLaunch() {
        oauthRevision++
        launch = null
        val open = listener
        listener = null
        if (open != null) scope.launch { open.close() }
    }

    /** Shares one reservation: a second tap while work is pending waits for the same work. */
    private suspend fun run(failure: McpChatFailure, action: suspend () -> Unit) {
        if (isDisposed) return
        while (true) {
            val pending = work.load()
            if (pending != null) return pending.await()
            val job =
                scope.async(start = CoroutineStart.LAZY) {
                    val outcome = attempt({ Failure.Unavailable(CONNECT) }) { if (check()) action() }
                    if (outcome is Outcome.Failed && check()) fail(failure)
                }
            if (!work.compareAndSet(null, job)) {
                job.cancel()
                continue
            }
            job.invokeOnCompletion { work.compareAndSet(job, null) }
            job.start()
            return job.await()
        }
    }

    /** The value of [outcome], or null after recording [failure] (when still current). */
    private fun <T> take(outcome: Outcome<T>, failure: McpChatFailure): T? =
        when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> {
                if (check()) fail(failure)
                null
            }
        }

    private val supported: Boolean get() = capabilities().chatConnectSupported()

    public suspend fun connect(serverId: String) {
        // Validate before coalescing: a different name cannot borrow another call.
        if (serverId != item.serverName) {
            if (!isDisposed) fail(McpChatFailure.InvalidSuggestion)
            return
        }
        run(McpChatFailure.ConnectFailed) {
            if (!supported) return@run fail(McpChatFailure.Unavailable)
            if (addDispatched) return@run refreshNow()
            val draft = item.draft
            if (draft == null || item.runtime != McpCatalogRuntime.Hosted || item.needsKey || item.needsSettings ||
                draft.headers.isNotEmpty() || draft.environment.isNotEmpty() || draft.command.isNotEmpty() ||
                WebAddress.parse(draft.url.orEmpty())?.scheme != "https"
            ) {
                return@run fail(McpChatFailure.SetupRequired)
            }
            set(McpChatPhase.Connecting)
            val api = take(gateway(), McpChatFailure.ConnectFailed) ?: return@run
            if (!check()) return@run
            val servers = take(api.mcpServers(), McpChatFailure.ConnectFailed) ?: return@run
            if (!check()) return@run
            if (servers.any { it.name == serverId }) return@run fail(McpChatFailure.NameConflict)
            // An uncertain answer is reconciled, never automatically re-added.
            addDispatched = true
            take(api.addMcpServer(draft, McpConfigScope.RuntimeLocation), McpChatFailure.ConnectFailed) ?: return@run
            if (!check()) return@run
            refreshNow()
        }
    }

    public suspend fun refresh() {
        run(McpChatFailure.ConnectFailed) { refreshNow() }
    }

    private suspend fun refreshNow() {
        if (!supported) return fail(McpChatFailure.Unavailable)
        if (!addDispatched) return
        set(McpChatPhase.CheckingTools)
        val api = take(gateway(), McpChatFailure.ConnectFailed) ?: return
        if (!check()) return
        val rows = take(api.mcpServers(), McpChatFailure.ConnectFailed) ?: return
        if (!check()) return
        val matches = rows.filter { it.name == item.serverName }
        if (matches.size != 1) return fail(McpChatFailure.NotConnected)
        when (matches.single().status) {
            "connected" -> {
                clearLaunch()
                authDispatched = false
                set(
                    if (capabilities().mcpChatToolRefresh) {
                        McpChatPhase.ToolsReady
                    } else {
                        McpChatPhase.ConnectedReadinessUnknown
                    },
                )
            }
            "needs_auth" -> {
                val open = launch
                set(
                    if (open == null) McpChatPhase.NeedsAuthentication else McpChatPhase.Authorizing,
                    url = open?.authorizationUrl,
                    manual = open != null && listener == null,
                )
            }
            "needs_client_registration" -> fail(McpChatFailure.SetupRequired)
            else -> fail(McpChatFailure.NotConnected)
        }
    }

    public suspend fun startOAuth() {
        val requestRevision = oauthRevision
        run(McpChatFailure.AuthenticationFailed) {
            val caps = capabilities()
            if (!supported || !caps.mcpChatOAuth || !caps.mcpOAuth) return@run fail(McpChatFailure.OAuthUnavailable)
            if (!addDispatched || authDispatched || state.value.phase != McpChatPhase.NeedsAuthentication) return@run
            val api = take(gateway(), McpChatFailure.AuthenticationFailed) ?: return@run
            if (!check() || requestRevision != oauthRevision) return@run
            authDispatched = true
            val started =
                take(api.startMcpAuthentication(item.serverName), McpChatFailure.AuthenticationFailed)
                    ?: return@run
            if (!check() || requestRevision != oauthRevision) return@run
            val url = WebAddress.parse(started.authorizationUrl)
            if (url == null || url.scheme != "https" || !url.hasAuthority || url.host.isEmpty() ||
                url.userInfo.isNotEmpty() ||
                UNSAFE.containsMatchIn(started.authorizationUrl) || started.oauthState.isEmpty() ||
                url.queryParameters["state"] != started.oauthState
            ) {
                return@run fail(McpChatFailure.AuthenticationFailed)
            }
            launch = started
            val revision = ++oauthRevision
            val redirect = mcpLoopbackRedirect(started.authorizationUrl)
            if (redirect != null) {
                // The server on this phone may own the port: then the person pastes the code instead.
                val bound = callbacks.bind(redirect, started.oauthState, CALLBACK_TIMEOUT_MS)
                if (bound is Outcome.Ok) {
                    if (!check() || revision != oauthRevision) {
                        bound.value.close()
                        return@run
                    }
                    listener = bound.value
                    scope.launch { receiveCode(bound.value, revision) }
                }
            }
            if (check()) set(McpChatPhase.Authorizing, url = started.authorizationUrl, manual = listener == null)
        }
    }

    private suspend fun receiveCode(from: OAuthCallbackListener, revision: Int) {
        val code = from.code()
        work.load()?.join() // let startOAuth release its reservation first
        when (code) {
            is Outcome.Ok -> if (check() && revision == oauthRevision) completeOAuth(code.value)
            is Outcome.Failed ->
                if (!isDisposed && revision == oauthRevision && check()) {
                    clearLaunch()
                    fail(McpChatFailure.AuthenticationFailed)
                }
        }
    }

    public suspend fun completeOAuth(callbackOrCode: String) {
        run(McpChatFailure.AuthenticationFailed) {
            val open = launch
            if (open == null || !authDispatched || !capabilities().mcpChatOAuth || callbackOrCode.length > 8192) {
                return@run fail(McpChatFailure.AuthenticationFailed)
            }
            val code =
                (parseMcpAuthorizationCode(callbackOrCode, open.oauthState) as? ParsedCode.Code)?.value
                    ?.takeIf { it.isNotBlank() }
                    ?: return@run fail(McpChatFailure.AuthenticationFailed)
            val api = take(gateway(), McpChatFailure.AuthenticationFailed) ?: return@run
            if (!check()) return@run
            // The mutation's answer is ignored; the inventory read that follows is the authority.
            take(api.completeMcpAuthentication(item.serverName, code), McpChatFailure.AuthenticationFailed)
                ?: return@run
            if (!check()) return@run
            clearLaunch()
            authDispatched = false
            refreshNow()
        }
    }

    /** Hides the local launch at once, then cancels a dispatched sign-in on the server. */
    public suspend fun cancelOAuth() {
        if (!check()) return
        val pending = work.load()
        if (!authDispatched && !(state.value.phase == McpChatPhase.NeedsAuthentication && pending != null)) return
        clearLaunch()
        set(McpChatPhase.CheckingTools)
        pending?.join()
        clearLaunch()
        run(McpChatFailure.AuthenticationFailed) {
            if (!authDispatched) return@run set(McpChatPhase.NeedsAuthentication)
            val api = take(gateway(), McpChatFailure.AuthenticationFailed) ?: return@run
            if (!check()) return@run
            take(api.cancelMcpAuthentication(item.serverName), McpChatFailure.AuthenticationFailed) ?: return@run
            if (!check()) return@run
            authDispatched = false
            set(McpChatPhase.NeedsAuthentication)
        }
    }

    public fun dispose() {
        if (isDisposed) return
        clearLaunch()
        isDisposed = true
    }

    private companion object {
        val CONNECT = FeatureId("mcpChatConnect")
        val OAUTH = FeatureId("mcpChatOAuth")
        val UNSAFE = Regex("""[\s\x00-\x1f\x7f]""")
        const val CALLBACK_TIMEOUT_MS = 3 * 60_000L
    }
}
