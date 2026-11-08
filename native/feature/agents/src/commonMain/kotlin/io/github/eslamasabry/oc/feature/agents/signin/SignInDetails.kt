package io.github.eslamasabry.oc.feature.agents.signin

import io.github.eslamasabry.oc.core.common.Failure

/**
 * The technical text under a failed sign-in's Details (no-raw-errors rule): the fixed code, the
 * step it stopped at and the failure's kind with its already-redacted details. The words above it
 * say the same in plain language; this is only for a report. Never CLI output (B-SEC-098).
 */
public object SignInDetails {
    public fun of(problem: SignInProblem): String? {
        val code = problem.code ?: return null
        if (problem !is SignInProblem.Start) return code
        return listOfNotNull("$code · ${problem.step.code}", problem.failure?.let(::describe))
            .joinToString("\n")
    }

    /** Explicit names, so a minified release build still says which failure it was. */
    public fun describe(failure: Failure): String {
        val (kind, what) =
            when (failure) {
                is Failure.Refused -> "Refused" to failure.reason.name
                is Failure.Unavailable -> "Unavailable" to failure.feature.name
                is Failure.Network -> "Network" to failure.why.name
                is Failure.TimedOut -> "TimedOut" to null
                is Failure.Certificate -> "Certificate" to failure.why.name
                is Failure.SignIn -> "SignIn" to failure.why.name
                is Failure.NotFound -> "NotFound" to null
                is Failure.Conflict -> "Conflict" to null
                is Failure.Busy -> "Busy" to failure.kind.name
                is Failure.Server -> "Server" to failure.status?.let { "HTTP $it" }
                is Failure.Rejected -> "Rejected" to failure.status?.let { "HTTP $it" }
                is Failure.Incompatible -> "Incompatible" to failure.kind.name
                is Failure.ComputerDidNotAnswer -> "ComputerDidNotAnswer" to null
                is Failure.UnreadableReply -> "UnreadableReply" to null
                is Failure.StorageRefused -> "StorageRefused" to null
                is Failure.SecretUnavailable -> "SecretUnavailable" to null
                is Failure.Media -> "Media" to failure.problem.name
                is Failure.Runtime -> "Runtime" to failure.problem.name
                is Failure.Device -> "Device" to null
                is Failure.Folder -> "Folder" to failure.problem.name
                is Failure.ProjectGit -> "ProjectGit" to null
                is Failure.StagedRevert -> "StagedRevert" to null
                is Failure.Unexpected -> "Unexpected" to null
                is Failure.Unknown -> "Unknown" to null
            }
        return listOfNotNull(
                if (what == null) kind else "$kind ($what)",
                failure.details?.text?.trim()?.takeIf { it.isNotEmpty() },
            )
            .joinToString(" · ")
    }
}
