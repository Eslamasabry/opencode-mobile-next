package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpAuthLaunch
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpServerDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Flutter lib/domain/mcp_chat.dart + lib/state/mcp_chat_controller.dart + parseMcpAuthorizationCode (mcp_oauth.dart).

/** Chat connector progress. Ready means discovery completed for the next model step, never that a tool ran. */
public enum class McpChatPhase {
    Suggested, Connecting, NeedsAuthentication, Authorizing, CheckingTools, ToolsReady, ConnectedReadinessUnknown,
    Failed, Unavailable,
}

/** Fixed keys for plain copy; never server errors or credentials. */
public enum class McpChatFailure {
    Unavailable, InvalidSuggestion, SetupRequired, NameConflict, SourceChanged, ConnectFailed, AuthenticationFailed,
    OauthUnavailable, NotConnected,
}

/**
 * [authorizationUrl] is inert and ephemeral: open it only through ExternalLinkPolicy; never persist or log it.
 * [manualCodeRequired]: paste the browser's return URL or code (the native app owns no loopback callback listener).
 */
public data class McpChatSnapshot(
    val phase: McpChatPhase,
    val failure: McpChatFailure? = null,
    val authorizationUrl: String? = null,
    val manualCodeRequired: Boolean = false,
) {
    override fun toString(): String = "McpChatSnapshot($phase, $failure)"
}

/** One accepted catalog suggestion: [serverName] and the draft it would add (null when it cannot be added as-is). */
public data class McpChatSuggestion(
    val serverName: String,
    val draft: McpServerDraft?,
    val hosted: Boolean,
    val needsKey: Boolean = false,
    val needsSettings: Boolean = false,
)

/**
 * One explicitly accepted connector suggestion, pinned to its conversation. [gateway] supplies a fresh transport for
 * each operation; [current] answers whether the profile/conversation identity is unchanged. An uncertain add is
 * reconciled by refetching, never re-added; invalidation and disposal never revoke server credentials.
 */
public class McpChatConnect(
    public val item: McpChatSuggestion,
    private val gateway: suspend () -> McpGateway,
    private val capabilities: () -> ServerCapabilities,
    private val current: () -> Boolean,
) {
    private val state = MutableStateFlow(McpChatSnapshot(McpChatPhase.Suggested))
    public val snapshot: StateFlow<McpChatSnapshot> = state.asStateFlow()

    public var isDisposed: Boolean = false
        private set
    private var invalidated = false
    private var addDispatched = false
    private var authDispatched = false
    private var oauthRevision = 0
    private var launch: McpAuthLaunch? = null
    private var work: CompletableDeferred<Unit>? = null

    private fun check(): Boolean {
        if (isDisposed || invalidated) return false
        if (!current()) {
            invalidate()
            return false
        }
        return true
    }

    private fun set(phase: McpChatPhase, failure: McpChatFailure? = null, url: String? = null, manual: Boolean = false) {
        if (isDisposed) return
        state.value = McpChatSnapshot(phase, failure, url, manual)
    }

    private fun fail(failure: McpChatFailure) =
        set(if (failure == McpChatFailure.Unavailable) McpChatPhase.Unavailable else McpChatPhase.Failed, failure)

    /** No remote cancellation: invalidation cannot revoke unrelated or established credentials. */
    public fun invalidate() {
        if (isDisposed) return
        invalidated = true
        clearLaunch()
        if (state.value.failure != McpChatFailure.SourceChanged) fail(McpChatFailure.SourceChanged)
    }

    private fun clearLaunch() {
        oauthRevision++
        launch = null
    }

    /** One operation at a time: a call while another runs waits for that one instead of starting its own. */
    private suspend fun run(onError: McpChatFailure, action: suspend () -> Unit) {
        if (isDisposed) return
        work?.let {
            it.await()
            return
        }
        val mine = CompletableDeferred<Unit>()
        work = mine
        try {
            val outcome = attempt({ Failure.Device() }) { if (check()) action() }
            if (outcome is Outcome.Failed && check()) fail(onError)
        } finally {
            if (work === mine) work = null
            mine.complete(Unit)
        }
    }

    /** Gateway answers become exceptions inside [run], so any failure maps to the operation's one fixed key. */
    private fun <T> Outcome<T>.orThrow(): T =
        when (this) {
            is Outcome.Ok -> value
            is Outcome.Failed -> throw McpChatStepFailed()
        }

    private val supported: Boolean
        get() = capabilities().let { it.mcpChatConnect && it.serverCatalog && it.mcpRuntimeAdds }

    public suspend fun connect(serverId: String) {
        if (serverId != item.serverName) {
            if (!isDisposed) fail(McpChatFailure.InvalidSuggestion)
            return
        }
        run(McpChatFailure.ConnectFailed) {
            if (!supported) {
                fail(McpChatFailure.Unavailable)
                return@run
            }
            if (addDispatched) {
                refreshNow()
                return@run
            }
            val draft = item.draft
            if (draft == null || !item.hosted || item.needsKey || item.needsSettings || draft.headers.isNotEmpty() ||
                draft.environment.isNotEmpty() || draft.command.isNotEmpty() ||
                CatalogUrl.parse(draft.url.orEmpty())?.scheme != "https"
            ) {
                fail(McpChatFailure.SetupRequired)
                return@run
            }
            set(McpChatPhase.Connecting)
            val g = gateway()
            if (!check()) return@run
            val servers = g.mcpServers().orThrow()
            if (!check()) return@run
            if (servers.any { it.name == serverId }) {
                fail(McpChatFailure.NameConflict)
                return@run
            }
            addDispatched = true
            g.addMcpServer(draft, McpConfigScope.RuntimeLocation).orThrow()
            if (!check()) return@run
            refreshNow()
        }
    }

    public suspend fun refresh(): Unit = run(McpChatFailure.ConnectFailed) { refreshNow() }

    private suspend fun refreshNow() {
        if (!supported) {
            fail(McpChatFailure.Unavailable)
            return
        }
        if (!addDispatched) return
        set(McpChatPhase.CheckingTools)
        val g = gateway()
        if (!check()) return
        val rows = g.mcpServers().orThrow()
        if (!check()) return
        val matches = rows.filter { it.name == item.serverName }
        if (matches.size != 1) {
            fail(McpChatFailure.NotConnected)
            return
        }
        when (matches.single().status) {
            "connected" -> {
                clearLaunch()
                authDispatched = false
                set(if (capabilities().mcpChatToolRefresh) McpChatPhase.ToolsReady else McpChatPhase.ConnectedReadinessUnknown)
            }
            "needs_auth" -> {
                val l = launch
                set(
                    if (l == null) McpChatPhase.NeedsAuthentication else McpChatPhase.Authorizing,
                    url = l?.authorizationUrl,
                    manual = l != null,
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
            if (!supported || !caps.mcpChatOAuth || !caps.mcpOAuth) {
                fail(McpChatFailure.OauthUnavailable)
                return@run
            }
            if (!addDispatched || authDispatched || state.value.phase != McpChatPhase.NeedsAuthentication) return@run
            val g = gateway()
            if (!check() || requestRevision != oauthRevision) return@run
            authDispatched = true
            val started = g.startMcpAuthentication(item.serverName).orThrow()
            if (!check() || requestRevision != oauthRevision) return@run
            val url = CatalogUrl.parse(started.authorizationUrl)
            if (url == null || url.scheme != "https" || url.host.isEmpty() || url.userInfo.isNotEmpty() ||
                Regex("[\\s\\x00-\\x1f\\x7f]").containsMatchIn(started.authorizationUrl) ||
                started.oauthState.isEmpty() || url.query["state"] != started.oauthState
            ) {
                fail(McpChatFailure.AuthenticationFailed)
                return@run
            }
            launch = started
            ++oauthRevision
            if (check()) set(McpChatPhase.Authorizing, url = started.authorizationUrl, manual = true)
        }
    }

    public suspend fun completeOAuth(callbackOrCode: String): Unit =
        run(McpChatFailure.AuthenticationFailed) {
            val l = launch
            if (l == null || !authDispatched || !capabilities().mcpChatOAuth) {
                fail(McpChatFailure.AuthenticationFailed)
                return@run
            }
            val code = parseMcpAuthorizationCode(callbackOrCode, l.oauthState)
            if (code == null || code.isBlank()) {
                fail(McpChatFailure.AuthenticationFailed)
                return@run
            }
            val g = gateway()
            if (!check()) return@run
            // Ignore the mutation's answer; the following inventory read is the authority.
            g.completeMcpAuthentication(item.serverName, code).orThrow()
            if (!check()) return@run
            clearLaunch()
            authDispatched = false
            refreshNow()
        }

    /** Hides the local launch at once, then cancels only an authentication this controller started. */
    public suspend fun cancelOAuth() {
        if (!check()) return
        val pending = work
        if (!authDispatched && !(state.value.phase == McpChatPhase.NeedsAuthentication && pending != null)) return
        clearLaunch()
        set(McpChatPhase.CheckingTools)
        pending?.await()
        clearLaunch()
        run(McpChatFailure.AuthenticationFailed) {
            if (!authDispatched) {
                set(McpChatPhase.NeedsAuthentication)
                return@run
            }
            val g = gateway()
            if (!check()) return@run
            g.cancelMcpAuthentication(item.serverName).orThrow()
            if (!check()) return@run
            authDispatched = false
            set(McpChatPhase.NeedsAuthentication)
        }
    }

    /** Local only: never cancels or revokes anything on the server. */
    public fun dispose() {
        if (isDisposed) return
        isDisposed = true
        clearLaunch()
    }
}

private class McpChatStepFailed : Exception()

/**
 * The authorization code from a pasted callback URL (its `state` must equal [expectedState]) or a bare code; null for
 * anything else (empty, over 8 KiB, line breaks, a callback with the wrong state or no code, an error callback).
 */
public fun parseMcpAuthorizationCode(value: String, expectedState: String): String? {
    val input = value.trim()
    if (input.isEmpty() || input.length > 8192 || input.contains('\r') || input.contains('\n')) return null
    val url = CatalogUrl.parse(input)
    val query = url?.query.orEmpty()
    val code = query["code"]
    if (url != null && code != null) {
        if (query["state"] != expectedState) return null
        return code.takeIf { it.isNotBlank() }
    }
    if (url != null && url.rawQuery != null) return null
    return input
}
