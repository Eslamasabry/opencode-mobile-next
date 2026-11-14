package io.github.eslamasabry.oc.core.common

/**
 * The failure's stable technical label for a Details fold, a problem report or a log: its kind and, where it has one,
 * its reason, why, problem or HTTP status, e.g. `Refused · StaleScope`, `Network · Refused`, `Server · HTTP 503`.
 *
 * Every word is written here or is a closed enum's name, so it survives R8 (which renames classes: a class name read
 * at run time printed `un5` on a release build's Details) and never carries server text, addresses or details. The
 * plain-words message a person reads stays in `:ui:words`; this is for Details and "Recent problems" only.
 */
public fun Failure.label(): String {
    val (name, reasonName) =
        when (this) {
            is Failure.Refused -> "Refused" to reason.name
            is Failure.Unavailable -> "Unavailable" to feature.name.takeIf { it.isNotBlank() && it.length <= MAX_FEATURE }
            is Failure.Network -> "Network" to why.name
            is Failure.TimedOut -> "TimedOut" to null
            is Failure.Certificate -> "Certificate" to why.name
            is Failure.SignIn -> "SignIn" to why.name
            is Failure.NotFound -> "NotFound" to null
            is Failure.Conflict -> "Conflict" to null
            is Failure.Busy -> "Busy" to kind.name
            is Failure.Server -> "Server" to status?.let { "HTTP $it" }
            is Failure.Rejected -> "Rejected" to status?.let { "HTTP $it" }
            is Failure.Incompatible -> "Incompatible" to kind.name
            is Failure.ComputerDidNotAnswer -> "ComputerDidNotAnswer" to null
            is Failure.UnreadableReply -> "UnreadableReply" to null
            is Failure.StorageRefused -> "StorageRefused" to null
            is Failure.SecretUnavailable -> "SecretUnavailable" to null
            is Failure.Media -> "Media" to problem.name
            is Failure.Runtime -> "Runtime" to problem.name
            is Failure.Device -> "Device" to null
            is Failure.Folder -> "Folder" to problem.name
            is Failure.ProjectGit -> "ProjectGit" to null
            is Failure.StagedRevert -> "StagedRevert" to null
            is Failure.Unexpected -> "Unexpected" to null
            is Failure.Unknown -> "Unknown" to null
        }
    return if (reasonName == null) name else "$name · $reasonName"
}

/** A feature id is app-authored, but a long one is cut from the label rather than printed whole. */
private const val MAX_FEATURE = 64
