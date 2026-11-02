package io.github.eslamasabry.oc.android.migration

/**
 * What the gate shows (plan 05 §7.3). This is the runner's own copy of `GateState`, with the fields 05 asks for
 * (`BackupInvalid`, `restoreAvailable`, notices by kind). `:androidApp` maps it onto the `:core:model`/`:core:data`
 * gate type once the core contract settles; the requested additions are listed in the S2-MIGRRUN report.
 */
public sealed interface RunnerState {
    public data object Closed : RunnerState

    public data class Running(val step: String) : RunnerState

    public data class Open(val firstNativeLaunch: Boolean, val notices: MigrationNoticeSet) : RunnerState

    public data class Blocked(
        val reason: BlockReason,
        val step: String?,
        val neededBytes: Long? = null,
        /** R2 is allowed only while the gate has never opened (plan 05 §9.4). R2 itself is S6's (not built here). */
        val restoreAvailable: Boolean,
    ) : RunnerState
}

public enum class BlockReason { LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed, BackupInvalid }

/** One row of the one-time sheet (plan 05 §8.2). App-made secrets never appear. */
public enum class ReentryKind { Password, Token, GitHubSignIn, ByoSetup }

/** [profileId] is a stored profile id (digits); never a secret, never a server address. */
public data class ReentryNotice(val profileId: String, val kind: ReentryKind)

/** Feature data the census found that the switch release does not open (plan 02 §3.7 item 4; S2-GATEUI's notice). */
public enum class DeferredFeature { AiTeam, Memory, Gemma }

public data class MigrationNoticeSet(
    val reenter: List<ReentryNotice> = emptyList(),
    /** Non-empty only until [MigrationRunner.acknowledgeNotices]; the notice shows once (02 §3.7 item 4). */
    val deferredFeatures: Set<DeferredFeature> = emptySet(),
)
