package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.attempt

/** Port of Dart `CodexFailureKind` (lib/codex/transport.dart). */
public enum class CodexFailureKind {
    InvalidEndpoint,
    Authentication,
    Disconnected,
    InvalidResponse,
    Unavailable,
    Overloaded,
    DeliveryUnknown,
    StaleRequest,
    ScopeMismatch,
}

/**
 * The module's internal throwable (Dart throws `CodexFailure`). Its message is fixed: remote error bodies, frames and
 * transport credentials never reach it (B-AGENT-062). Public calls convert it to a [Failure] with [codexAttempt].
 */
public class CodexException(public val kind: CodexFailureKind) : Exception("Codex ${kind.name}") {
    /**
     * The neutral failure. Every kind maps to exactly one flat subtype (so [codexKind] can read it back) and never
     * carries details: the app-server's words are never kept.
     */
    public fun toFailure(): Failure =
        when (kind) {
            CodexFailureKind.InvalidEndpoint -> Failure.Refused(RefusalReason.InvalidAddress)
            CodexFailureKind.Authentication -> Failure.SignIn(SignInWhy.TokenRejected)
            CodexFailureKind.Disconnected -> Failure.Network(NetworkWhy.Reset)
            CodexFailureKind.InvalidResponse -> Failure.UnreadableReply()
            CodexFailureKind.Unavailable -> Failure.Rejected(status = null, tag = UNAVAILABLE_TAG)
            CodexFailureKind.Overloaded -> Failure.Busy(BusyKind.Overloaded, OVERLOADED_TAG)
            CodexFailureKind.DeliveryUnknown -> Failure.Refused(RefusalReason.DispatchUncertain)
            CodexFailureKind.StaleRequest -> Failure.Conflict(STALE_TAG)
            CodexFailureKind.ScopeMismatch -> Failure.Refused(RefusalReason.StaleScope)
        }

    public companion object {
        public val UNAVAILABLE_TAG: WireTag = WireTag("Codex Unavailable")
        public val OVERLOADED_TAG: WireTag = WireTag("Codex Overloaded")
        public val STALE_TAG: WireTag = WireTag("Codex StaleRequest")
    }
}

/** Reads a Codex failure kind back from the neutral failure [CodexException.toFailure] made; null for any other. */
public fun Failure.codexKind(): CodexFailureKind? =
    when (this) {
        is Failure.Refused ->
            when (reason) {
                RefusalReason.InvalidAddress -> CodexFailureKind.InvalidEndpoint
                RefusalReason.DispatchUncertain -> CodexFailureKind.DeliveryUnknown
                RefusalReason.StaleScope -> CodexFailureKind.ScopeMismatch
                else -> null
            }
        is Failure.SignIn -> CodexFailureKind.Authentication.takeIf { why == SignInWhy.TokenRejected }
        is Failure.Network -> CodexFailureKind.Disconnected
        is Failure.UnreadableReply -> CodexFailureKind.InvalidResponse
        is Failure.Rejected -> CodexFailureKind.Unavailable.takeIf { tag == CodexException.UNAVAILABLE_TAG }
        is Failure.Busy -> CodexFailureKind.Overloaded.takeIf { tag == CodexException.OVERLOADED_TAG }
        is Failure.Conflict -> CodexFailureKind.StaleRequest.takeIf { tag == CodexException.STALE_TAG }
        else -> null
    }

/** Throws the module failure. */
public fun codexFail(kind: CodexFailureKind): Nothing = throw CodexException(kind)

/**
 * The module boundary (native/AGENTS.md rule 5): a [CodexException] becomes its [Failure], any other throwable (a bug
 * path) [Failure.Unexpected] by shape only. Cancellation is rethrown by [attempt].
 */
public suspend inline fun <T> codexAttempt(block: () -> T): Outcome<T> =
    attempt(
        classify = { error -> (error as? CodexException)?.toFailure() ?: Failure.Unexpected(Redacted.shape(error)) },
        block = block,
    )
