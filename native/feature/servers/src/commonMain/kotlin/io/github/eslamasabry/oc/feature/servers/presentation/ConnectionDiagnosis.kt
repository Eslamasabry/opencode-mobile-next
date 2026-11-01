package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Which headline and sentence the failure shows. Words live in `:ui:words` (`ServersWords`), never here. */
public enum class FailureKind {
    OpenCode1, InAppStopped, InAppNotResponding, InAppStartFailed, TokenRequired, TokenRejected,
    CodexListenerUnavailable, CodexEndpointUnreachable, PasswordRejected, CertificateChanged, CertificateNotTrusted,
    CertificateExpired, HttpUnconfirmed, PhoneServerStopped, NothingAnsweredOnPhone, TimedOut, NotReachable,
    ServerError, Unknown,
}

/** Plain things to check, most likely first. */
public sealed interface FailureCheck {
    public data object UpgradeCommand : FailureCheck
    public data object EnterCodexToken : FailureCheck
    public data object EnterCurrentCodexToken : FailureCheck
    public data object StartCodexListener : FailureCheck
    public data object CodexTunnel : FailureCheck
    public data object CodexWss : FailureCheck
    public data object CodexReachable : FailureCheck
    public data object PairAgainForPassword : FailureCheck
    public data object PasswordSetByHand : FailureCheck
    public data object RecheckCertificate : FailureCheck
    public data object TrustedCertificate : FailureCheck
    public data object SelfSignedFingerprint : FailureCheck
    public data object TermuxRunning : FailureCheck
    public data object TunnelConnected : FailureCheck
    public data object OtherComputer : FailureCheck
    public data object TailscaleOn : FailureCheck
    public data object SameNetworkOrVpnTimeout : FailureCheck
    public data class Firewall(val port: Int) : FailureCheck
    public data object ServeRunning : FailureCheck
    public data object SameNetworkOrVpn : FailureCheck
    public data object AddressChanged : FailureCheck
    public data object RestartServe : FailureCheck
    public data object VersionSupported : FailureCheck
    public data object AgentServerRunning : FailureCheck
    public data object ServeRunningRightAddress : FailureCheck

    /** "Tried N times. Retrying will not start a server that is not running." (B-NAV-009) */
    public data class TriedTimes(val attempts: Int) : FailureCheck
}

/** The one action most likely to fix it. Retrying a server that is not running is never that action. */
public enum class FailureAction { Retry, StartPhoneServer, UpdatePassword, UpdateToken, ChangeServer, ReviewCertificate, EditServer }

/**
 * A failed connection a person can act on (Dart `ConnectionFailure`). [details] is the only technical text, already
 * redacted, and shows only under Details (B-NAV-009, B-SEC-104).
 */
public data class ConnectionFailureUi(
    val kind: FailureKind,
    val checks: ImmutableList<FailureCheck>,
    val primary: FailureAction,
    val tailnet: Boolean = false,
    val details: Redacted? = null,
)

public data class DiagnosisContext(
    val backend: BackendKind,
    val supportsTermux: Boolean,
    val managedPhoneServer: Boolean = false,
    val inAppServer: Boolean = false,
    val inAppStartFailed: Boolean = false,
    val attempts: Int = 1,
)

/** Ports `lib/ui/widgets/connection_failure.dart` from error strings to the typed [Failure] (B-NAV-007…009). */
public object ConnectionDiagnosis {
    /** The supported server install (Dart `openCode2UpgradeCommand`): a command, shown verbatim, not copy. */
    public const val UPGRADE_COMMAND: String = "npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.2.5"

    public fun diagnose(
        failure: Failure,
        origin: ServerOrigin,
        context: DiagnosisContext,
        policy: AddressPolicy,
    ): ConnectionFailureUi {
        val details = failure.details
        fun ui(kind: FailureKind, checks: List<FailureCheck?>, primary: FailureAction, tailnet: Boolean = false) =
            ConnectionFailureUi(kind, checks.filterNotNull().toImmutableList(), primary, tailnet, details)

        if (failure is Net.Incompatible && failure.kind == Net.Incompatible.Kind.LegacyOpenCode1) {
            return ui(FailureKind.OpenCode1, listOf(FailureCheck.UpgradeCommand), FailureAction.ChangeServer)
        }
        val codex = context.backend == BackendKind.Codex
        val signIn = (failure as? Net.SignInNeeded)?.why
        val trust = (failure as? Net.TrustRefused)?.why
        val unauthorized =
            signIn in PASSWORD_PROBLEMS || (failure is Net.ServerFailed && failure.status in setOf(401, 403))
        val timedOut = failure is Net.Unreachable && failure.why == Net.Unreachable.Why.Timeout
        val nothingAnswered = (failure is Net.Unreachable && !timedOut) || failure is Net.Offline
        val serverError = (failure is Net.ServerFailed && failure.status >= 500) || failure is Net.ServerBusy
        val loopback = policy.isLoopback(origin.host)
        val tokenMissing = codex && signIn == Net.SignInNeeded.Why.TokenMissing
        val tokenRejected = codex && (signIn == Net.SignInNeeded.Why.TokenRejected || unauthorized)
        val retried = if (context.attempts >= 3) FailureCheck.TriedTimes(context.attempts) else null
        // Only a remote address that did not answer: a refusal or a sign-in problem means Tailscale did its part.
        val tailnet = !loopback && (nothingAnswered || timedOut) && policy.isTailnet(origin.host)
        val tailnetCheck = if (tailnet) FailureCheck.TailscaleOn else null
        val otherwiseQuiet = nothingAnswered || timedOut || (!unauthorized && !serverError)

        return when {
            context.inAppServer && !codex -> {
                val kind =
                    when {
                        context.inAppStartFailed -> FailureKind.InAppStartFailed
                        otherwiseQuiet -> FailureKind.InAppStopped
                        else -> FailureKind.InAppNotResponding
                    }
                ui(kind, emptyList(), FailureAction.StartPhoneServer)
            }
            tokenMissing -> ui(FailureKind.TokenRequired, listOf(FailureCheck.EnterCodexToken, retried), FailureAction.UpdateToken)
            tokenRejected ->
                ui(FailureKind.TokenRejected, listOf(FailureCheck.EnterCurrentCodexToken, retried), FailureAction.UpdateToken)
            codex && (nothingAnswered || timedOut) ->
                if (loopback) {
                    ui(
                        FailureKind.CodexListenerUnavailable,
                        listOf(FailureCheck.StartCodexListener, FailureCheck.CodexTunnel, retried),
                        FailureAction.Retry,
                    )
                } else {
                    ui(
                        FailureKind.CodexEndpointUnreachable,
                        listOf(FailureCheck.CodexWss, FailureCheck.CodexReachable, retried),
                        FailureAction.Retry,
                    )
                }
            unauthorized ->
                ui(
                    FailureKind.PasswordRejected,
                    listOf(FailureCheck.PairAgainForPassword, FailureCheck.PasswordSetByHand, retried),
                    FailureAction.UpdatePassword,
                )
            trust == Net.TrustRefused.Why.CertificateChanged ->
                ui(FailureKind.CertificateChanged, listOf(FailureCheck.RecheckCertificate), FailureAction.ReviewCertificate)
            trust == Net.TrustRefused.Why.CertificateUntrusted ->
                ui(
                    FailureKind.CertificateNotTrusted,
                    listOf(FailureCheck.TrustedCertificate, FailureCheck.SelfSignedFingerprint, retried),
                    FailureAction.ReviewCertificate,
                )
            trust == Net.TrustRefused.Why.CertificateExpired ->
                ui(FailureKind.CertificateExpired, listOf(retried), FailureAction.Retry)
            trust == Net.TrustRefused.Why.CleartextNotConfirmed ->
                ui(FailureKind.HttpUnconfirmed, emptyList(), FailureAction.EditServer)
            trust != null -> ui(FailureKind.CertificateNotTrusted, listOf(retried), FailureAction.EditServer)
            context.managedPhoneServer && !codex && loopback && otherwiseQuiet ->
                ui(FailureKind.PhoneServerStopped, listOf(retried), FailureAction.StartPhoneServer)
            !codex && loopback && otherwiseQuiet ->
                ui(
                    FailureKind.NothingAnsweredOnPhone,
                    listOf(
                        if (context.supportsTermux) FailureCheck.TermuxRunning else null,
                        FailureCheck.TunnelConnected,
                        FailureCheck.OtherComputer,
                        retried,
                    ),
                    // Loopback names an endpoint, not whether Termux or a tunnel hosts it; Retry guesses nothing.
                    FailureAction.Retry,
                )
            timedOut ->
                ui(
                    FailureKind.TimedOut,
                    listOf(tailnetCheck, FailureCheck.SameNetworkOrVpnTimeout, FailureCheck.Firewall(origin.port), retried),
                    FailureAction.Retry,
                    tailnet,
                )
            nothingAnswered ->
                ui(
                    FailureKind.NotReachable,
                    listOf(
                        tailnetCheck, FailureCheck.ServeRunning, FailureCheck.SameNetworkOrVpn,
                        FailureCheck.AddressChanged, retried,
                    ),
                    FailureAction.Retry,
                    tailnet,
                )
            serverError ->
                ui(
                    FailureKind.ServerError,
                    listOf(FailureCheck.RestartServe, FailureCheck.VersionSupported, retried),
                    FailureAction.Retry,
                )
            else ->
                ui(
                    FailureKind.Unknown,
                    listOf(if (codex) FailureCheck.AgentServerRunning else FailureCheck.ServeRunningRightAddress, retried),
                    FailureAction.Retry,
                )
        }
    }

    private val PASSWORD_PROBLEMS =
        setOf(
            Net.SignInNeeded.Why.PasswordMissing,
            Net.SignInNeeded.Why.PasswordRejected,
            Net.SignInNeeded.Why.HostNotAllowed,
        )
}
