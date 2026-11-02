package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted

/**
 * Stages of a saved-server connection attempt (frozen contract,
 * docs/native/server-errors-2026-11-02/contract.md). The stage names the boundary that failed; they
 * are the only location information a diagnostic may carry.
 */
public enum class ConnectionStage {
    StorageReady,
    ProfileRead,
    DraftRecovery,
    OpenGateway,
    SaveActiveProfile,
    LoadConversations,
    LoadActivity,
}

/**
 * Annotates [failure] with the [stage] it failed at. Returns the SAME failure subtype with every
 * field preserved except `details`, which is replaced by generated diagnostics naming only the
 * stage, the failure kind, its known enum reason and a numeric HTTP status — never the original raw
 * details, tags, URLs, identifiers, exception text or server prose. The generated text is built
 * through [redact] (callers pass `Redactor::details`).
 */
public fun connectionFailure(
    failure: Failure,
    stage: ConnectionStage,
    redact: (String) -> Redacted,
): Failure {
    val details = redact(stageDiagnostics(stage, failure))
    return failure.withDetails(details)
}

/** Stage · kind · enum reason · HTTP status. Every token is hardcoded or from a closed enum. */
private fun stageDiagnostics(stage: ConnectionStage, failure: Failure): String {
    val parts = mutableListOf(stage.name)
    when (failure) {
        is Failure.Refused -> {
            parts += "Refused"
            parts += failure.reason.name
        }
        is Failure.Unavailable -> parts += "Unavailable"
        is Failure.Network -> {
            parts += "Network"
            parts += failure.why.name
        }
        is Failure.TimedOut -> parts += "TimedOut"
        is Failure.Certificate -> {
            parts += "Certificate"
            parts += failure.why.name
        }
        is Failure.SignIn -> {
            parts += "SignIn"
            parts += failure.why.name
        }
        is Failure.NotFound -> parts += "NotFound"
        is Failure.Conflict -> parts += "Conflict"
        is Failure.Busy -> {
            parts += "Busy"
            parts += failure.kind.name
        }
        is Failure.Server -> {
            parts += "Server"
            failure.status?.let { parts += "HTTP $it" }
        }
        is Failure.Rejected -> {
            parts += "Rejected"
            failure.status?.let { parts += "HTTP $it" }
        }
        is Failure.Incompatible -> {
            parts += "Incompatible"
            parts += failure.kind.name
        }
        is Failure.ComputerDidNotAnswer -> parts += "ComputerDidNotAnswer"
        is Failure.UnreadableReply -> parts += "UnreadableReply"
        is Failure.StorageRefused -> parts += "StorageRefused"
        is Failure.SecretUnavailable -> parts += "SecretUnavailable"
        is Failure.Media -> {
            parts += "Media"
            parts += failure.problem.name
        }
        is Failure.Runtime -> {
            parts += "Runtime"
            parts += failure.problem.name
        }
        is Failure.Device -> parts += "Device"
        is Failure.Folder -> {
            parts += "Folder"
            parts += failure.problem.name
        }
        is Failure.ProjectGit -> parts += "ProjectGit"
        is Failure.StagedRevert -> parts += "StagedRevert"
        is Failure.Termux -> parts += "Termux"
        is Failure.Unexpected -> parts += "Unexpected"
        is Failure.Unknown -> parts += "Unknown"
    }
    return parts.joinToString(" · ")
}

/**
 * Same subtype, every field unchanged except [details]; exhaustive, so a new subtype fails to
 * compile here.
 */
private fun Failure.withDetails(details: Redacted): Failure =
    when (this) {
        is Failure.Refused -> Failure.Refused(reason, details)
        is Failure.Unavailable -> Failure.Unavailable(feature, details)
        is Failure.Network -> Failure.Network(why, details)
        is Failure.TimedOut -> Failure.TimedOut(details)
        is Failure.Certificate -> Failure.Certificate(why, details)
        is Failure.SignIn -> Failure.SignIn(why, details)
        is Failure.NotFound -> Failure.NotFound(tag, requestId, details)
        is Failure.Conflict -> Failure.Conflict(tag, requestId, details)
        is Failure.Busy -> Failure.Busy(kind, tag, details)
        is Failure.Server -> Failure.Server(status, tag, details)
        is Failure.Rejected -> Failure.Rejected(status, tag, extras, details)
        is Failure.Incompatible -> Failure.Incompatible(kind, details)
        is Failure.ComputerDidNotAnswer -> Failure.ComputerDidNotAnswer(details)
        is Failure.UnreadableReply -> Failure.UnreadableReply(details)
        is Failure.StorageRefused -> Failure.StorageRefused(details)
        is Failure.SecretUnavailable -> Failure.SecretUnavailable(details)
        is Failure.Media -> Failure.Media(problem, details)
        is Failure.Runtime -> Failure.Runtime(problem, details)
        is Failure.Device -> Failure.Device(details)
        is Failure.Folder -> Failure.Folder(problem, details)
        is Failure.ProjectGit -> Failure.ProjectGit(details)
        is Failure.StagedRevert -> Failure.StagedRevert(details)
        is Failure.Termux -> Failure.Termux(details)
        is Failure.Unexpected -> Failure.Unexpected(details)
        is Failure.Unknown -> Failure.Unknown(details)
    }
