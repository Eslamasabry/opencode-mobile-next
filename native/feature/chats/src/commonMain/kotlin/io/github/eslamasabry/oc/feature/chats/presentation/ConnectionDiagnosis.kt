package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.SignInWhy

/**
 * Why a saved server did not answer on open, and the one thing most likely to fix it (Flutter
 * `ConnectionFailure.diagnose`, lib/ui/widgets/connection_failure.dart). The typed failure plays the part of the
 * transport text there; the address says where the server was meant to be.
 */
public enum class DiagnosisCause {
    OpenCode1,
    TokenRequired,
    TokenRejected,
    CodexLocal,
    CodexRemote,
    PasswordRejected,
    CertificateChanged,
    CertificateUntrusted,
    Loopback,
    TimedOut,
    NothingAnswered,
    ServerError,
    Unknown,
}

public enum class DiagnosisAction {
    Retry,
    UpdatePassword,
    UpdateToken,
    ReviewCertificate,
    ChangeServer,
}

public data class ConnectionDiagnosis(
    val cause: DiagnosisCause,
    val primary: DiagnosisAction,
    /** The address is on a Tailscale network and nothing answered there: Tailscale being off is the likely cause. */
    val tailnet: Boolean,
    val port: Int,
    val attempts: Int,
    val usesToken: Boolean,
) {
    /** Three failed tries: retrying a server that is not there is no longer the lead; the way out is. */
    val escalated: Boolean
        get() = attempts >= ESCALATE_AFTER && primary == DiagnosisAction.Retry

    /** The action that leads the card. */
    val lead: DiagnosisAction
        get() = if (escalated) DiagnosisAction.ChangeServer else primary

    public companion object {
        public const val ESCALATE_AFTER: Int = 3
        private const val DEFAULT_PORT = 4096

        /** The host of [baseUrl] in lower case, without scheme, credentials, port or path; empty when unreadable. */
        public fun hostOf(baseUrl: String): String {
            val rest = baseUrl.substringAfter("://", baseUrl).substringBefore('/').substringAfterLast('@')
            val host =
                if (rest.startsWith("[")) rest.substringBefore(']') + "]" else rest.substringBefore(':')
            return host.lowercase()
        }

        public fun portOf(baseUrl: String): Int {
            val rest = baseUrl.substringAfter("://", baseUrl).substringBefore('/').substringAfterLast('@')
            val tail = if (rest.startsWith("[")) rest.substringAfter("]", "") else rest.substringAfter(':', "")
            return tail.removePrefix(":").toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
        }

        public fun isLoopback(host: String): Boolean =
            host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"

        /** A Tailscale address: 100.64.0.0/10 or a MagicDNS `*.ts.net` name. */
        public fun isTailnetHost(host: String): Boolean {
            if (host.endsWith(".ts.net")) return true
            val octets = host.split('.').map { it.toIntOrNull() }
            if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
            return octets[0] == 100 && octets[1]!! in 64..127
        }

        /** The diagnosis for a failure that is not the person's own phone server, or null when there is no failure. */
        public fun of(
            failure: Failure?,
            baseUrl: String,
            usesToken: Boolean = false,
            attempts: Int = 1,
        ): ConnectionDiagnosis? {
            failure ?: return null
            val host = hostOf(baseUrl)
            val loopback = isLoopback(host)
            val unreachable =
                failure is Failure.Network || failure is Failure.TimedOut || failure is Failure.ComputerDidNotAnswer
            val (cause, action) =
                when {
                    failure is Failure.Incompatible && failure.kind == IncompatibleKind.LegacyOpenCode1 ->
                        DiagnosisCause.OpenCode1 to DiagnosisAction.ChangeServer
                    failure is Failure.SignIn && failure.why == SignInWhy.TokenMissing ->
                        DiagnosisCause.TokenRequired to DiagnosisAction.UpdateToken
                    failure is Failure.SignIn && (failure.why == SignInWhy.TokenRejected || usesToken) ->
                        DiagnosisCause.TokenRejected to DiagnosisAction.UpdateToken
                    failure is Failure.SignIn -> DiagnosisCause.PasswordRejected to DiagnosisAction.UpdatePassword
                    failure is Failure.Certificate && failure.why == io.github.eslamasabry.oc.core.common.CertificateWhy.Changed ->
                        DiagnosisCause.CertificateChanged to DiagnosisAction.ReviewCertificate
                    failure is Failure.Certificate ->
                        DiagnosisCause.CertificateUntrusted to DiagnosisAction.ReviewCertificate
                    unreachable && usesToken ->
                        (if (loopback) DiagnosisCause.CodexLocal else DiagnosisCause.CodexRemote) to DiagnosisAction.Retry
                    unreachable && loopback -> DiagnosisCause.Loopback to DiagnosisAction.Retry
                    failure is Failure.TimedOut -> DiagnosisCause.TimedOut to DiagnosisAction.Retry
                    unreachable -> DiagnosisCause.NothingAnswered to DiagnosisAction.Retry
                    failure is Failure.Server -> DiagnosisCause.ServerError to DiagnosisAction.Retry
                    else -> DiagnosisCause.Unknown to DiagnosisAction.Retry
                }
            val tailnet =
                !loopback &&
                    (cause == DiagnosisCause.TimedOut || cause == DiagnosisCause.NothingAnswered) &&
                    isTailnetHost(host)
            return ConnectionDiagnosis(cause, action, tailnet, portOf(baseUrl), attempts, usesToken)
        }
    }
}
