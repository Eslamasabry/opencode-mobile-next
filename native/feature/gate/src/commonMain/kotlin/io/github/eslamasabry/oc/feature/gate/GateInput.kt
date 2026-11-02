package io.github.eslamasabry.oc.feature.gate

/**
 * What the update gate screens are fed (plan 05 §7.3, 01 §9.7). It mirrors the `:core:model` contract
 * (`GateState`, `ReentryNotice`, `MigrationActions`) that S2-MIGRRUN and the core owner publish; until that lands in
 * `:core:model`, the shell keeps this copy and the app graph maps one onto the other in one function (S2-GATEUI report,
 * "contract requests"). Nothing here carries a secret: a re-entry names its target and what is needed, never a value.
 */
public sealed interface GateInput {
    /** The app is not ready (bootstrap or the StartupFence). [localeKnown] is false until the locale mirror exists. */
    public data class Closed(val localeKnown: Boolean) : GateInput

    /** The migrator is running [step] (S0, M1 … M6). */
    public data class Running(val step: String) : GateInput

    /**
     * The gate is open. [reenter] lists what needs the person again (asked once, §8.2); [deferredDataFound] is the
     * census flag for AI Team, Memory or Gemma data (02 §3.7 item 4).
     */
    public data class Open(
        val firstNativeLaunch: Boolean,
        val reenter: List<GateReentry> = emptyList(),
        val deferredDataFound: Boolean = false,
    ) : GateInput

    /**
     * The migration stopped. [detail] is the step id and failure class only (for example `M2 · UnwrapFailed ×1`,
     * 01 §9.6): never exception text.
     */
    public data class Blocked(
        val reason: GateBlockReason,
        val neededBytes: Long? = null,
        val restoreAvailable: Boolean = false,
        val detail: String? = null,
    ) : GateInput
}

/** Plan 05 §7.3 `BlockReason`. */
public enum class GateBlockReason {
    LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed, BackupInvalid,
}

/** Plan 05 §7.3 `ReentryKind`: only things the person must do; app-made secrets are renewed silently. */
public enum class GateReentryKind { Password, Token, GitHubSignIn, ByoSetup }

/**
 * One row of the one-time re-enter sheet. [targetName] is the server's plain name (`plainServerName`) or the BYO
 * host's name, so every row names its target; [profileId] is null for app-wide items (GitHub).
 */
public data class GateReentry(val profileId: String?, val targetName: String, val kind: GateReentryKind)

/**
 * The gate's ways out (plan 05 §7.3 `MigrationActions`, plus the two the shell needs that are not migration logic).
 * Implemented by the app graph; the shell only calls them.
 */
public interface GateActions {
    /** Single-flight in the runner: while Running it waits on the same run; from Blocked it resets and runs again. */
    public suspend fun retry()

    public suspend fun restorePreUpdateData()

    public suspend fun startFresh()

    /** Gate-exempt: hands an in-memory report to the share sheet; writes no preference (§8.3). */
    public suspend fun reportProblem()

    public suspend fun openStorageSettings()

    /** Records that the re-enter sheet was seen, so it is never shown again (§8.2 "ask once"). */
    public suspend fun acknowledgeNotices()

    /** Opens what one re-enter row names (the server's password field, the Git page, …). */
    public suspend fun openReentry(item: GateReentry)

    /** Records that the deferred-features notice was shown (02 §3.7 item 4: once, never again). */
    public suspend fun acknowledgeDeferredNotice()
}
