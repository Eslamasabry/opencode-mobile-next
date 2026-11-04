package io.github.eslamasabry.oc.feature.servers.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ProbeResult

/** One explicit saved-server check; secrets and server error text never enter display facts. */
public fun interface ServerConnectionCheck {
    public suspend fun inspect(id: ProfileId): Outcome<ConnectionCheckFacts>
}

@ContributesTo(AppScope::class)
public interface ServerConnectionCheckBindings {
    @Multibinds(allowEmpty = true) public val serverConnectionChecks: Set<ServerConnectionCheck>
}

public enum class ConnectionCheckStep {
    Address,
    SignIn,
    Version,
    LiveUpdates,
    Speed,
}

public enum class ConnectionCheckState {
    Waiting,
    Running,
    Pass,
    Warn,
    Fail,
    Skipped,
}

public enum class ConnectionCheckStream {
    NotConnected,
    Connecting,
    Live,
    Down,
}

public enum class ConnectionCheckReason {
    Ok,
    Unreachable,
    NotServer,
    NeedsPassword,
    PasswordRejected,
    VersionUnsupported,
    VersionNotReady,
    StreamNotChecked,
    StreamConnecting,
    StreamDown,
    Slow,
    EarlierFailed,
    Trust,
    Changed,
}

public data class ConnectionCheckFacts(
    val probe: Outcome<ProbeResult>,
    val stream: ConnectionCheckStream,
    val elapsedMs: Long,
)

public data class ConnectionCheckResult(
    val state: ConnectionCheckState = ConnectionCheckState.Waiting,
    val reason: ConnectionCheckReason = ConnectionCheckReason.Ok,
    val value: String? = null,
)

public data class ConnectionCheckUi(
    val id: ProfileId,
    val serverName: String,
    val running: Boolean = true,
    val results: Map<ConnectionCheckStep, ConnectionCheckResult> =
        ConnectionCheckStep.entries.associateWith {
            ConnectionCheckResult(
                if (it == ConnectionCheckStep.Address) ConnectionCheckState.Running
                else ConnectionCheckState.Waiting
            )
        },
) {
    public fun finish(facts: ConnectionCheckFacts): ConnectionCheckUi {
        val rows =
            ConnectionCheckStep.entries
                .associateWith {
                    ConnectionCheckResult(
                        ConnectionCheckState.Skipped,
                        ConnectionCheckReason.EarlierFailed,
                    )
                }
                .toMutableMap()
        fun set(
            step: ConnectionCheckStep,
            state: ConnectionCheckState,
            reason: ConnectionCheckReason = ConnectionCheckReason.Ok,
            value: String? = null,
        ) {
            rows[step] = ConnectionCheckResult(state, reason, value)
        }
        when (val probe = facts.probe) {
            is Outcome.Failed ->
                when (val failure = probe.failure) {
                    is Failure.SecretUnavailable -> {
                        set(
                            ConnectionCheckStep.Address,
                            ConnectionCheckState.Skipped,
                            ConnectionCheckReason.NeedsPassword,
                        )
                        set(
                            ConnectionCheckStep.SignIn,
                            ConnectionCheckState.Fail,
                            ConnectionCheckReason.NeedsPassword,
                        )
                    }
                    is Failure.SignIn -> {
                        set(ConnectionCheckStep.Address, ConnectionCheckState.Pass)
                        val missing =
                            failure.why in setOf(SignInWhy.PasswordMissing, SignInWhy.TokenMissing)
                        set(
                            ConnectionCheckStep.SignIn,
                            ConnectionCheckState.Fail,
                            if (missing) ConnectionCheckReason.NeedsPassword
                            else ConnectionCheckReason.PasswordRejected,
                        )
                    }
                    is Failure.Incompatible -> {
                        set(ConnectionCheckStep.Address, ConnectionCheckState.Pass)
                        set(ConnectionCheckStep.SignIn, ConnectionCheckState.Pass)
                        set(
                            ConnectionCheckStep.Version,
                            ConnectionCheckState.Fail,
                            ConnectionCheckReason.VersionUnsupported,
                        )
                    }
                    is Failure.Busy,
                    is Failure.Server -> {
                        set(ConnectionCheckStep.Address, ConnectionCheckState.Pass)
                        set(ConnectionCheckStep.SignIn, ConnectionCheckState.Pass)
                        set(
                            ConnectionCheckStep.Version,
                            ConnectionCheckState.Warn,
                            ConnectionCheckReason.VersionNotReady,
                        )
                    }
                    is Failure.Certificate ->
                        set(
                            ConnectionCheckStep.Address,
                            ConnectionCheckState.Fail,
                            ConnectionCheckReason.Trust,
                        )
                    is Failure.Refused ->
                        set(
                            ConnectionCheckStep.Address,
                            ConnectionCheckState.Fail,
                            if (
                                failure.reason in
                                    setOf(
                                        RefusalReason.CleartextNotConfirmed,
                                        RefusalReason.PinNeedsHttps,
                                    )
                            )
                                ConnectionCheckReason.Trust
                            else ConnectionCheckReason.Changed,
                        )
                    is Failure.Network,
                    is Failure.TimedOut,
                    is Failure.ComputerDidNotAnswer ->
                        set(
                            ConnectionCheckStep.Address,
                            ConnectionCheckState.Fail,
                            ConnectionCheckReason.Unreachable,
                        )
                    else ->
                        set(
                            ConnectionCheckStep.Address,
                            ConnectionCheckState.Fail,
                            ConnectionCheckReason.NotServer,
                        )
                }
            is Outcome.Ok -> {
                set(ConnectionCheckStep.Address, ConnectionCheckState.Pass)
                set(ConnectionCheckStep.SignIn, ConnectionCheckState.Pass)
                set(
                    ConnectionCheckStep.Version,
                    ConnectionCheckState.Pass,
                    value = probe.value.serverVersion,
                )
                val (state, reason) =
                    when (facts.stream) {
                        ConnectionCheckStream.Live ->
                            ConnectionCheckState.Pass to ConnectionCheckReason.Ok
                        ConnectionCheckStream.Connecting ->
                            ConnectionCheckState.Warn to ConnectionCheckReason.StreamConnecting
                        ConnectionCheckStream.Down ->
                            ConnectionCheckState.Fail to ConnectionCheckReason.StreamDown
                        ConnectionCheckStream.NotConnected ->
                            ConnectionCheckState.Skipped to ConnectionCheckReason.StreamNotChecked
                    }
                set(ConnectionCheckStep.LiveUpdates, state, reason)
                set(
                    ConnectionCheckStep.Speed,
                    if (facts.elapsedMs > 1500) ConnectionCheckState.Warn
                    else ConnectionCheckState.Pass,
                    if (facts.elapsedMs > 1500) ConnectionCheckReason.Slow
                    else ConnectionCheckReason.Ok,
                    facts.elapsedMs.toString(),
                )
            }
        }
        return copy(running = false, results = rows)
    }
}
