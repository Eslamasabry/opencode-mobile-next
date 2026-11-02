package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor

/** Port of Dart `PaseoFailureKind` (lib/paseo/transport.dart). */
public enum class PaseoFailureKind {
    InvalidEndpoint,
    Authentication,
    HostRefused,
    Disconnected,
    InvalidResponse,
    Unavailable,
    NewChatRequired,
    Overloaded,
    DeliveryUnknown,
    StaleRequest,
    ScopeMismatch,

    /** The daemon answered, but has no agent by the id the app asked for. */
    NotFound,

    /** `hello.rejected` / close code 4003: the daemon does not speak this app's protocol. */
    IncompatibleProtocol,
}

/**
 * The module's internal throwable (Dart throws `PaseoFailure`). It never leaves the module: every public call converts
 * it to a [Failure] with [paseoAttempt]. [reason] is the daemon's own sentence, tidied and capped; it only ever reaches
 * `Failure.details` (Details fold), never copy. The message is fixed so logs see no daemon text.
 */
public class PaseoException(
    public val kind: PaseoFailureKind,
    reason: String? = null,
    /** Sign-in detail for [PaseoFailureKind.Authentication]: no password was sent / a password was refused. */
    public val passwordMissing: Boolean = false,
) : Exception("Paseo ${kind.name}") {
    public val reason: String? = tidyPaseoReason(reason)

    /** The neutral failure (plan 04 §8 "Failure kinds"). The tag is `Paseo <kind>`, for logic only. */
    public fun toFailure(): Failure {
        val details = reason?.let { DETAILS.details(it) }
        val tag = WireTag("Paseo ${kind.name}")
        return when (kind) {
            PaseoFailureKind.InvalidEndpoint -> Failure.Refused(RefusalReason.InvalidAddress, details)
            PaseoFailureKind.Authentication ->
                Failure.SignIn(
                    if (passwordMissing) SignInWhy.PasswordMissing else SignInWhy.PasswordRejected,
                    details,
                )
            PaseoFailureKind.HostRefused -> Failure.SignIn(SignInWhy.HostNotAllowed, details)
            // An unanswered daemon is about the computer (B-AGENT-160): both a dropped link and a refusal.
            PaseoFailureKind.Disconnected, PaseoFailureKind.Unavailable -> Failure.ComputerDidNotAnswer(details)
            PaseoFailureKind.InvalidResponse -> Failure.UnreadableReply(details)
            // TODO(contract): a RefusalReason for "start a new chat" (contract request in the S3-PASEO report).
            PaseoFailureKind.NewChatRequired -> Failure.Conflict(tag, details = details)
            PaseoFailureKind.Overloaded -> Failure.Busy(BusyKind.Overloaded, tag, details)
            PaseoFailureKind.DeliveryUnknown -> Failure.Refused(RefusalReason.DispatchUncertain, details)
            PaseoFailureKind.StaleRequest, PaseoFailureKind.ScopeMismatch -> Failure.Refused(RefusalReason.StaleScope, details)
            PaseoFailureKind.NotFound -> Failure.NotFound(tag, details = details)
            PaseoFailureKind.IncompatibleProtocol ->
                Failure.Incompatible(IncompatibleKind.ProtocolMismatch, details)
        }
    }

    private companion object {
        val DETAILS = Redactor()
    }
}

/** Throws the module failure. */
internal fun paseoFail(kind: PaseoFailureKind, reason: String? = null): Nothing = throw PaseoException(kind, reason)

/**
 * The module's boundary (native/AGENTS.md rule 5): runs [block], turns a [PaseoException] into its [Failure] and any
 * other throwable (a bug path) into [Failure.Unexpected] by shape only. Cancellation is rethrown by [attempt].
 */
public suspend inline fun <T> paseoAttempt(block: () -> T): Outcome<T> =
    attempt(
        classify = { error ->
            (error as? PaseoException)?.toFailure() ?: Failure.Unexpected(io.github.eslamasabry.oc.core.common.Redacted.shape(error))
        },
        block = block,
    )

/** A daemon's error sentence made safe for a Details fold: whitespace collapsed, trimmed, capped at 240; null if empty. */
public fun tidyPaseoReason(raw: String?): String? {
    if (raw == null) return null
    val text = raw.replace(WHITESPACE, " ").trim()
    if (text.isEmpty()) return null
    return if (text.length <= 240) text else text.substring(0, 239) + "…"
}

private val WHITESPACE = Regex("\\s+")
