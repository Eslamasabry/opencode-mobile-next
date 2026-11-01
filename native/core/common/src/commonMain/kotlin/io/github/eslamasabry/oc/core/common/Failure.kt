package io.github.eslamasabry.oc.core.common

import kotlin.jvm.JvmInline

/** A closed hierarchy, one subtype per Flutter ProductFailureCategory (plan 03 §10.1). Frozen at the end of slice 0. */
public sealed interface Failure {
    /** Technical text for the Details fold ONLY (ADR §9.6). Null only for app-authored refusals. */
    public val details: Redacted?

    public data class Refused(val reason: RefusalReason, override val details: Redacted? = null) : Failure
    public data class Unavailable(val feature: FeatureId, override val details: Redacted? = null) : Failure
    public data class StagedRevert(override val details: Redacted?) : Failure
    public data class Folder(val problem: FolderProblem, override val details: Redacted?) : Failure
    public data class Network(override val details: Redacted?) : Failure
    public data class TimedOut(override val details: Redacted?) : Failure
    public data class Certificate(override val details: Redacted?) : Failure
    public data class SignIn(override val details: Redacted?) : Failure
    public data class NotFound(override val details: Redacted?) : Failure
    public data class Conflict(override val details: Redacted?) : Failure
    public data class Busy(override val details: Redacted?) : Failure
    public data class Server(val status: Int?, override val details: Redacted?) : Failure
    public data class Rejected(val status: Int?, override val details: Redacted?) : Failure
    public data class ComputerDidNotAnswer(override val details: Redacted?) : Failure
    public data class UnreadableReply(override val details: Redacted?) : Failure
    public data class Device(override val details: Redacted?) : Failure
    public data class Storage(override val details: Redacted?) : Failure
    public data class ProjectGit(override val details: Redacted?) : Failure
    public data class Termux(override val details: Redacted?) : Failure
    public data class Unexpected(override val details: Redacted?) : Failure
    public data class Unknown(override val details: Redacted?) : Failure
}

public enum class FolderProblem { NotInstalled, Missing, Denied, Linked }

/** App-authored refusal reasons. Append-only; each has exactly one sentence in :ui:words (exhaustive when). */
public enum class RefusalReason {
    ProfileClosing, ProfileDeleted, StaleScope, ConsentMissing, CleartextNotConfirmed, PasswordMissing,
    AlreadyAnswered, DispatchUncertain, OfflineQueueFull, MigrationPending, NotOnThisPhone,
}

/** Names a ServerCapabilities/PlatformCapabilities flag exactly as spelled in the Kotlin data class. */
@JvmInline public value class FeatureId(public val name: String)
