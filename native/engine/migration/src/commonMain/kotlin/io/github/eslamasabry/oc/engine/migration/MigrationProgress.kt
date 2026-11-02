package io.github.eslamasabry.oc.engine.migration

/**
 * What the day-1 runner reports (plan 05 §7.3), platform-neutral. `:androidApp` maps `:android:migration`'s
 * `RunnerState` onto it in one function (graph/MigrationWiring.kt). Nothing here carries a secret, a path or a server
 * address: ids, enums and counts only (B-MIGR-011).
 */
public sealed interface MigrationProgress {
    /** Not started yet (the StartupFence has not reached the runner). */
    public data object Waiting : MigrationProgress

    /** The runner is on [step] (`S0`, `M1` … `M6`). */
    public data class Running(val step: String) : MigrationProgress

    /**
     * Done (or nothing to move). [firstNativeLaunch] is true on the first open after a Flutter install was adopted.
     * [reenter] and [deferred] are non-empty only until the person has seen them once.
     */
    public data class Open(
        val firstNativeLaunch: Boolean,
        val reenter: List<ReentryItem> = emptyList(),
        val deferred: Set<DeferredFeature> = emptySet(),
    ) : MigrationProgress

    /**
     * Stopped safely: nothing was deleted, the source files were only read and the byte backup is kept. [step] is the
     * step id only; [restoreAvailable] and [startFreshAvailable] say which extra ways out exist right now.
     */
    public data class Blocked(
        val reason: MigrationBlock,
        val step: String? = null,
        val neededBytes: Long? = null,
        val restoreAvailable: Boolean = false,
        val startFreshAvailable: Boolean = false,
    ) : MigrationProgress
}

/** Plan 05 §7.3 `BlockReason`, with `BackupInvalid` (S2-MIGRRUN). */
public enum class MigrationBlock { LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed, BackupInvalid }

/** Plan 05 §7.3 `ReentryKind`: only things the person must do; app-made secrets are renewed silently. */
public enum class ReentryNeed { Password, Token, GitHubSignIn, ByoSetup }

/** One secret the person must type again. [profileId] is the stored id; null for app-wide items. */
public data class ReentryItem(val profileId: String?, val need: ReentryNeed)

/** Data the census found for features this release does not open yet (plan 02 §3.7 item 4). */
public enum class DeferredFeature { AiTeam, Memory, Gemma }
