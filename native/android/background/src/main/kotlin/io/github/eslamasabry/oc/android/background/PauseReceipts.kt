package io.github.eslamasabry.oc.android.background

/**
 * The durable pause receipt (port of the Flutter app's `BackgroundPauseReceipt.kt` and
 * `BackgroundPauseStore.kt`). Only fixed categories and timestamps are persisted; never an OS
 * description.
 */
public data class PauseReceipt(
    val phase: String = PHASE_NONE,
    val reason: String = PauseReason.None.wire,
    val at: Long = 0L,
) {
    public companion object {
        public const val PHASE_NONE: String = "none"
        public const val PHASE_RUNNING: String = "running"
        public const val PHASE_PAUSED: String = "paused"
    }
}

/** One process exit Android recorded: `ApplicationExitInfo.reason` and its wall-clock timestamp. */
public data class PauseExit(val reason: Int, val at: Long)

/**
 * What the app sees now. [bootAt] is the wall-clock boot time (now − elapsedRealtime); 0 = unknown.
 */
public data class PauseObservation(
    val active: Boolean,
    val restricted: Boolean,
    val exit: PauseExit?,
    val now: Long,
    val bootAt: Long = 0L,
)

/** How a running receipt becomes a pause (verbatim from `BackgroundPausePolicy`). */
public object PausePolicy {
    /** Wall-clock slack when comparing a receipt with the boot time. */
    private const val BOOT_SLACK_MS = 60_000L
    private const val USER_REQUESTED_EXIT = 10
    private const val USER_STOPPED_EXIT = 11
    private const val PACKAGE_UPDATED_EXIT = 16

    public fun reconcile(receipt: PauseReceipt, seen: PauseObservation): PauseReceipt =
        when {
            seen.active ->
                if (receipt.phase == PauseReceipt.PHASE_RUNNING) receipt
                else PauseReceipt(PauseReceipt.PHASE_RUNNING, at = seen.now)
            receipt.phase != PauseReceipt.PHASE_RUNNING ->
                // A current-policy statement must not become a historical cause.
                if (receipt.reason == PauseReason.BatteryRestricted.wire && !seen.restricted) {
                    receipt.copy(reason = PauseReason.Interrupted.wire)
                } else {
                    receipt
                }
            else -> interrupted(receipt, seen)
        }

    private fun interrupted(receipt: PauseReceipt, seen: PauseObservation): PauseReceipt {
        val reason = interruptionReason(receipt, seen)
        // Battery policy is observed now. The exit timestamp is evidence only when it followed this
        // running period.
        val at =
            if (reason == PauseReason.UserStopped || reason == PauseReason.AppUpdated)
                checkNotNull(seen.exit).at
            else seen.now
        return PauseReceipt(PauseReceipt.PHASE_PAUSED, reason.wire, at)
    }

    private fun interruptionReason(receipt: PauseReceipt, seen: PauseObservation): PauseReason =
        when {
            seen.restricted -> PauseReason.BatteryRestricted
            isExitAfter(seen.exit, receipt.at, USER_REQUESTED_EXIT, USER_STOPPED_EXIT) ->
                PauseReason.UserStopped
            // An app update ends the process: a known cause, not "unknown".
            isExitAfter(seen.exit, receipt.at, PACKAGE_UPDATED_EXIT) -> PauseReason.AppUpdated
            // The receipt was written before the phone booted: it restarted.
            seen.bootAt > 0L && receipt.at < seen.bootAt - BOOT_SLACK_MS ->
                PauseReason.DeviceRestarted
            else -> PauseReason.Interrupted
        }

    private fun isExitAfter(exit: PauseExit?, startedAt: Long, vararg reasons: Int): Boolean =
        exit != null && exit.at > startedAt && exit.reason in reasons
}

/**
 * The receipt's own private file (`shared_prefs/oc_background_pause.xml` on Android, the
 * Flutter-era file and keys). Synchronous: a receipt must survive the service stopping with no
 * engine around. [commit] is true only when the platform acknowledged the write.
 */
public interface PauseReceiptFile {
    public fun phase(): String?

    public fun reason(): String?

    public fun at(): Long

    public fun commit(receipt: PauseReceipt): Boolean

    /** A hint only (`taskRemovalHintAt`); never replaces a running receipt. */
    public fun recordTaskRemoval(at: Long)
}

/**
 * Reads of the system the receipt is judged against. Read only: nothing here writes another file.
 */
public interface PauseSystemFacts {
    public fun now(): Long

    public fun bootTime(): Long

    public fun backgroundRestricted(): Boolean

    /** This process's newest recorded exit (helper processes excluded), or null. */
    public fun latestExit(): PauseExit?
}

/**
 * The saved live-mode opt-in, `oc.keepLiveInBackground` (the Flutter key, verbatim, so a migrated
 * install keeps it). [read] is null when nothing was saved. [write] is true only on an acknowledged
 * commit.
 */
public interface LiveOptInStore {
    public fun read(): Boolean?

    public suspend fun write(value: Boolean): Boolean

    public companion object {
        public const val KEY: String = "oc.keepLiveInBackground"
    }
}

/**
 * Port of `BackgroundPauseStore`: the receipt plus the opt-in it turns off. Every method is
 * serialised on one lock in the receipt file's own order; [status] answers [PauseState.Unsupported]
 * when the receipt cannot be read or saved (the Flutter store threw "Background state could not be
 * saved or read.", and the channel answered unsupported).
 */
public class PauseReceiptStore(
    private val file: PauseReceiptFile,
    private val facts: PauseSystemFacts,
    private val optIn: LiveOptInStore,
) {
    private val lock = Any()

    /**
     * The service ran its foreground start. A temporary sign-in claim records running without
     * changing the saved preference; the live-mode controller persists its explicit choice only
     * after this acknowledgement.
     */
    public suspend fun confirmedStarted(persistOptIn: Boolean = true): Boolean {
        val saved =
            synchronized(lock) {
                file.commit(PauseReceipt(PauseReceipt.PHASE_RUNNING, at = facts.now()))
            }
        return saved && (!persistOptIn || optIn.write(true))
    }

    /**
     * Android's time limit stopped the service. Restore is always turned off, even if the receipt
     * failed.
     */
    public suspend fun timeout(): Boolean {
        val saved =
            synchronized(lock) {
                file.commit(
                    PauseReceipt(PauseReceipt.PHASE_PAUSED, PauseReason.TimeLimit.wire, facts.now())
                )
            }
        val disabled = optIn.write(false)
        return saved && disabled
    }

    /** The person turned live mode off: no pause, no restore. */
    public suspend fun clear(): Boolean {
        val cleared = synchronized(lock) { file.commit(PauseReceipt()) }
        val disabled = optIn.write(false)
        return cleared && disabled
    }

    public fun recordTaskRemoved() {
        file.recordTaskRemoval(facts.now())
    }

    /** The receipt reconciled with what Android shows now; a pause turns the opt-in off. */
    public suspend fun status(active: Boolean): PauseState {
        val wire =
            synchronized(lock) {
                val previous = read() ?: return PauseState.Unsupported
                val restricted = facts.backgroundRestricted()
                val exit =
                    if (!active && previous.phase == PauseReceipt.PHASE_RUNNING) facts.latestExit()
                    else null
                val receipt =
                    PausePolicy.reconcile(
                        previous,
                        PauseObservation(active, restricted, exit, facts.now(), facts.bootTime()),
                    )
                if (receipt != previous && !file.commit(receipt)) return PauseState.Unsupported
                val paused = !active && receipt.phase == PauseReceipt.PHASE_PAUSED
                mapOf(
                    "supported" to true,
                    "active" to active,
                    "paused" to paused,
                    "reason" to if (paused) receipt.reason else PauseReason.None.wire,
                    "at" to if (paused) receipt.at else null,
                    "canResume" to paused,
                )
            }
        if (wire["paused"] == true && !optIn.write(false)) return PauseState.Unsupported
        return PauseState.fromWire(wire)
    }

    private fun read(): PauseReceipt? {
        val phase = file.phase() ?: PauseReceipt.PHASE_NONE
        val reason = file.reason() ?: PauseReason.None.wire
        val at = file.at()
        if (phase !in PHASES || PauseReason.fromWire(reason) == null || at < 0L) return null
        if (phase != PauseReceipt.PHASE_NONE && at <= 0L) return null
        if (phase == PauseReceipt.PHASE_PAUSED && reason == PauseReason.None.wire) return null
        return PauseReceipt(phase, reason, at)
    }

    private companion object {
        val PHASES =
            setOf(PauseReceipt.PHASE_NONE, PauseReceipt.PHASE_RUNNING, PauseReceipt.PHASE_PAUSED)
    }
}
