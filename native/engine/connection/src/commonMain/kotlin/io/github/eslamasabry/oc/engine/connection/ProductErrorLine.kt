package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted

/**
 * The plain line a failure is said with (port of `lib/domain/product_failure.dart` +
 * `productErrorText`, rows B-SEC-104, B-SEC-131, B-SEC-132). The line is chosen from the failure's
 * closed category only — never from server, protocol, exception or native text — and each value
 * names one sentence in `:ui:words` [wordsKey]. Technical text only reaches the person as the
 * failure's already-redacted [Failure.details], folded under Details.
 */
public enum class ProductErrorLine(public val wordsKey: String) {
    /** Any socket/transport failure collapses to the one connectivity line. */
    Unreachable("e7SharedOpenCodeUnreachableTryAgain"),
    TimedOut("productErrorTimedOut"),
    Certificate("productErrorCertificate"),
    SignIn("productErrorSignIn"),
    NotFound("productErrorNotFound"),
    Conflict("productErrorConflict"),
    Busy("productErrorBusy"),

    /** "The server had a problem (error %d)": the only number shown is the HTTP status. */
    Server("productErrorServer"),
    Rejected("productErrorRejected"),
    UnreadableReply("productErrorUnreadableReply"),

    /** The app's own staged-revert sentence. */
    StagedRevert("productErrorStagedRevert"),
    Storage("productErrorStorage"),
    Secret("serverFailureSecret"),
    Device("productErrorDevice"),
    Termux("productErrorTermux"),
    Computer("productErrorComputer"),
    ProjectGit("productErrorProjectGit"),

    /** An invalid state or an unknown error: said plainly, without blaming the network. */
    Unknown("productErrorUnknown"),
}

/**
 * A line plus its only parameter (the HTTP status for [ProductErrorLine.Server]) and the redacted
 * Details.
 */
public data class ProductErrorText(
    val line: ProductErrorLine,
    val status: Int? = null,
    val details: Redacted? = null,
)

/**
 * The Flutter `SessionRevertPending` tag: a 409 carrying it is the staged-revert sentence, whatever
 * prose came with it.
 */
public const val SESSION_REVERT_PENDING_TAG: String = "SessionRevertPending"

public fun productErrorText(failure: Failure): ProductErrorText {
    val line =
        when (failure) {
            is Failure.Network -> ProductErrorLine.Unreachable
            is Failure.TimedOut -> ProductErrorLine.TimedOut
            is Failure.Certificate -> ProductErrorLine.Certificate
            is Failure.SignIn -> ProductErrorLine.SignIn
            is Failure.NotFound -> ProductErrorLine.NotFound
            is Failure.Conflict ->
                if (failure.tag?.raw == SESSION_REVERT_PENDING_TAG) ProductErrorLine.StagedRevert
                else ProductErrorLine.Conflict
            is Failure.StagedRevert -> ProductErrorLine.StagedRevert
            is Failure.Busy -> ProductErrorLine.Busy
            is Failure.Server ->
                if (failure.status != null) ProductErrorLine.Server else ProductErrorLine.Unknown
            is Failure.Rejected ->
                if (failure.tag?.raw == SESSION_REVERT_PENDING_TAG) ProductErrorLine.StagedRevert
                else ProductErrorLine.Rejected
            is Failure.UnreadableReply -> ProductErrorLine.UnreadableReply
            is Failure.StorageRefused,
            is Failure.Folder -> ProductErrorLine.Storage
            is Failure.SecretUnavailable -> ProductErrorLine.Secret
            is Failure.Device,
            is Failure.Runtime,
            is Failure.Media -> ProductErrorLine.Device
            is Failure.Termux -> ProductErrorLine.Termux
            is Failure.ComputerDidNotAnswer -> ProductErrorLine.Computer
            is Failure.ProjectGit -> ProductErrorLine.ProjectGit
            is Failure.Refused,
            is Failure.Unavailable,
            is Failure.Incompatible,
            is Failure.Unexpected,
            is Failure.Unknown -> ProductErrorLine.Unknown
        }
    return ProductErrorText(line, (failure as? Failure.Server)?.status, failure.details)
}
