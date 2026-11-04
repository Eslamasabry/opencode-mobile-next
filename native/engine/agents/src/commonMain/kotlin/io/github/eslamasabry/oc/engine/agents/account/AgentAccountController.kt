package io.github.eslamasabry.oc.engine.agents.account

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public enum class AccountPanelStatus {
    Loading,
    Ready,
    Unavailable,
    Error,
    Disconnected,
}

public enum class AccountLoginStatus {
    Idle,
    Starting,
    Waiting,
    Cancelling,
    Cancelled,
    Failed,
    Uncertain,
    Completed,
}

/** What one open account panel shows. [deviceCode] is transient: never persisted or logged. */
public data class AccountPanelState(
    val status: AccountPanelStatus = AccountPanelStatus.Loading,
    val loginStatus: AccountLoginStatus = AccountLoginStatus.Idle,
    val account: AgentRuntimeAccount? = null,
    val limits: ImmutableList<AccountRateBucket>? = null,
    val usage: AccountTokenUsage? = null,
    val deviceCode: AccountDeviceCode? = null,
    val metricsLoading: Boolean = false,
    val updatedAt: Instant? = null,
    val canSignIn: Boolean = false,
    val canCancel: Boolean = false,
)

/**
 * Transient account state for one open panel (port of lib/state/agent_account.dart). No preference
 * or credential writes. Every command runs on [scope]'s serial lane; read and login revisions drop
 * any answer that arrives after the account changed, the session disconnected or the panel closed.
 *
 * - [refresh] never starts sign-in. Subscription metrics are read only for `chatgpt` accounts
 *   (API-key and other host auth cannot be labelled subscription usage), and each metric failing
 *   alone never erases the account read.
 * - A sign-in code is never shown late: a completion, cancel, account change, disconnect or [close]
 *   while the start is in flight suppresses it. A cancel the runtime could not confirm stays
 *   [AccountLoginStatus.Uncertain] until an explicit retry of Cancel; it never permits a new
 *   sign-in.
 */
public class AgentAccountController(
    private val session: AgentAccountSession,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val mutableState = MutableStateFlow(AccountPanelState())
    public val state: StateFlow<AccountPanelState> = mutableState.asStateFlow()

    private var disposed = false
    private var readRevision = 0
    private var loginRevision = 0
    private var refreshQueued = false

    private var status = AccountPanelStatus.Loading
    private var loginStatus = AccountLoginStatus.Idle
    private var account: AgentRuntimeAccount? = null
    private var limits: ImmutableList<AccountRateBucket>? = null
    private var usage: AccountTokenUsage? = null
    private var deviceCode: AccountDeviceCode? = null
    private var metricsLoading = false
    private var updatedAt: Instant? = null

    private val subscription: Job = scope.launch { session.events.collect(::onEvent) }

    public val loginPending: Boolean
        get() =
            loginStatus == AccountLoginStatus.Starting ||
                loginStatus == AccountLoginStatus.Waiting ||
                loginStatus == AccountLoginStatus.Cancelling

    public val canSignIn: Boolean
        get() =
            session.active &&
                session.supported &&
                status == AccountPanelStatus.Ready &&
                account?.signedIn == false &&
                account?.requiresSignIn == true &&
                !loginPending &&
                loginStatus != AccountLoginStatus.Uncertain

    public val canCancel: Boolean
        get() =
            session.active &&
                (loginPending || loginStatus == AccountLoginStatus.Uncertain) &&
                loginStatus != AccountLoginStatus.Cancelling

    private fun publish() {
        if (disposed) return
        mutableState.value = snapshot()
    }

    private fun snapshot() =
        AccountPanelState(
            status,
            loginStatus,
            account,
            limits,
            usage,
            deviceCode,
            metricsLoading,
            updatedAt,
            canSignIn,
            canCancel,
        )

    private fun clearIdentity() {
        account = null
        limits = null
        usage = null
        updatedAt = null
    }

    private fun onEvent(event: AccountEvent) {
        if (disposed) return
        when (event.kind) {
            AccountEventKind.Disconnected -> invalidate()
            AccountEventKind.LoginCompleted -> {
                ++loginRevision
                deviceCode = null
                loginStatus =
                    if (event.success) AccountLoginStatus.Completed else AccountLoginStatus.Failed
                queueRefresh()
            }
            AccountEventKind.LoginCancelled -> {
                ++loginRevision
                deviceCode = null
                loginStatus =
                    if (event.success) AccountLoginStatus.Cancelled
                    else AccountLoginStatus.Uncertain
                queueRefresh()
            }
            AccountEventKind.Changed -> {
                // Another account: this session's attempt is cancelled (only this one) before the
                // code can be used.
                if (loginPending) {
                    ++loginRevision
                    deviceCode = null
                    scope.launch { cancel() }
                }
                queueRefresh()
            }
            AccountEventKind.LimitsChanged -> queueRefresh()
        }
    }

    /**
     * Removes stale identity and metrics at once on an account notification, then re-reads once.
     */
    private fun queueRefresh() {
        ++readRevision
        clearIdentity()
        status = AccountPanelStatus.Loading
        publish()
        if (refreshQueued) return
        refreshQueued = true
        scope.launch {
            refreshQueued = false
            if (!disposed) refresh()
        }
    }

    public suspend fun refresh() {
        if (disposed) return
        if (!session.active) {
            invalidate()
            return
        }
        val revision = ++readRevision
        clearIdentity()
        metricsLoading = false
        status =
            if (session.supported) AccountPanelStatus.Loading else AccountPanelStatus.Unavailable
        publish()
        if (!session.supported) return
        when (val read = guarded { session.read() }) {
            is Outcome.Failed -> {
                if (!current(revision)) return
                status =
                    when (read.failure) {
                        is Failure.Unavailable -> AccountPanelStatus.Unavailable
                        is Failure.Network,
                        is Failure.ComputerDidNotAnswer -> AccountPanelStatus.Disconnected
                        else -> AccountPanelStatus.Error
                    }
                publish()
            }
            is Outcome.Ok -> {
                if (!current(revision)) return
                val value = read.value
                account = value
                status = AccountPanelStatus.Ready
                updatedAt = clock.now()
                metricsLoading = value.type == SUBSCRIPTION_TYPE
                publish()
                if (!metricsLoading) return
                val fetchedLimits = (guarded { session.rateLimits() } as? Outcome.Ok)?.value
                if (!current(revision)) return
                limits = fetchedLimits
                publish()
                val fetchedUsage = (guarded { session.usage() } as? Outcome.Ok)?.value
                if (!current(revision)) return
                usage = fetchedUsage
                metricsLoading = false
                updatedAt = clock.now()
                publish()
            }
        }
    }

    private fun current(revision: Int): Boolean =
        !disposed && revision == readRevision && session.active

    public suspend fun signIn() {
        if (!canSignIn) return
        val revision = ++loginRevision
        loginStatus = AccountLoginStatus.Starting
        deviceCode = null
        publish()
        when (val started = guarded { session.startDeviceLogin() }) {
            is Outcome.Ok -> {
                if (disposed || revision != loginRevision || !session.active) return
                deviceCode = started.value
                loginStatus = AccountLoginStatus.Waiting
            }
            is Outcome.Failed -> {
                if (disposed || revision != loginRevision) return
                val failure = started.failure
                loginStatus =
                    if (
                        failure is Failure.Refused &&
                            failure.reason == RefusalReason.DispatchUncertain
                    ) {
                        AccountLoginStatus.Uncertain
                    } else {
                        AccountLoginStatus.Failed
                    }
            }
        }
        publish()
    }

    public suspend fun cancel() {
        if (disposed || !canCancel || loginStatus == AccountLoginStatus.Cancelling) return
        val revision = ++loginRevision
        deviceCode = null
        loginStatus = AccountLoginStatus.Cancelling
        publish()
        // A failed cancel keeps the uncertainty visible: never read as cancelled.
        val confirmed = (guarded { session.cancelLogin() } as? Outcome.Ok)?.value == true
        if (disposed || revision != loginRevision) return
        loginStatus = if (confirmed) AccountLoginStatus.Cancelled else AccountLoginStatus.Uncertain
        publish()
        refresh()
    }

    /** The session disconnected: nothing in flight may publish, and no command is replayed. */
    public fun invalidate() {
        if (disposed) return
        ++readRevision
        ++loginRevision
        deviceCode = null
        clearIdentity()
        metricsLoading = false
        if (loginPending) loginStatus = AccountLoginStatus.Uncertain
        status = AccountPanelStatus.Disconnected
        publish()
    }

    /** The panel closed. Late answers are dropped and the session is closed. */
    public fun close() {
        if (disposed) return
        ++readRevision
        ++loginRevision
        deviceCode = null
        account = null
        limits = null
        usage = null
        mutableState.value = snapshot()
        disposed = true
        subscription.cancel()
        // A ViewModel may already have cancelled its scope when onCleared runs. Ownership cleanup
        // must still
        // cancel this session's login handle; it never starts or repeats a login.
        scope.launch(NonCancellable) { session.close() }
    }

    private suspend inline fun <T> guarded(
        crossinline block: suspend () -> Outcome<T>
    ): Outcome<T> =
        when (val outcome = attempt({ Failure.Unexpected() }) { block() }) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> outcome
        }

    private companion object {
        const val SUBSCRIPTION_TYPE = "chatgpt"
    }
}
