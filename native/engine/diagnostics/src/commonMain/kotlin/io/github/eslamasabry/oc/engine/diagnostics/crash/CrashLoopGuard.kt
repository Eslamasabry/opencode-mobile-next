package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import io.github.eslamasabry.oc.engine.diagnostics.DiagnosticsSignals
import io.github.eslamasabry.oc.engine.diagnostics.safely
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How this launch opens (ADR §9.7 step 3). */
public enum class LaunchMode { Normal, SafeMode }

/** The ≤ 4 KiB crash-loop record (`files/native/crashloop.json`); the platform reads and writes it. */
public data class CrashLoopRecord(
    /** When the latest launch started (epoch ms), 0 before the first. */
    val launchedAtMs: Long = 0L,
    /** Consecutive launches that crashed within [CrashLoopGuard.EARLY] of their start. */
    val earlyCrashes: Int = 0,
)

/**
 * The pure crash-loop policy (ADR §9.7, plan 07 A02): three consecutive launches that crash within 10 s of start open
 * safe mode. The platform guard persists [CrashLoopRecord] synchronously around these calls.
 */
public class CrashLoopGuard(
    private val clock: Clock,
    private val signals: DiagnosticsSignals = DiagnosticsSignals.None,
    private val early: Duration = EARLY,
    private val threshold: Int = THRESHOLD,
) {
    /** At process start: the mode, and the record to write before anything else runs. */
    public fun onLaunch(previous: CrashLoopRecord): Pair<LaunchMode, CrashLoopRecord> {
        val now = clock.now().toEpochMilliseconds()
        if (previous.earlyCrashes >= threshold) {
            signals.safely { crash(CrashCategory.StartupLoop, fatal = true) }
            // Safe mode starts a fresh count, so leaving it is not a trap.
            return LaunchMode.SafeMode to CrashLoopRecord(launchedAtMs = now, earlyCrashes = 0)
        }
        return LaunchMode.Normal to previous.copy(launchedAtMs = now)
    }

    /** From the uncaught-exception handler. A crash after [early] breaks the streak. */
    public fun onCrash(current: CrashLoopRecord): CrashLoopRecord {
        val sinceLaunch = clock.now().toEpochMilliseconds() - current.launchedAtMs
        return if (current.launchedAtMs > 0 && sinceLaunch in 0..early.inWholeMilliseconds) {
            current.copy(earlyCrashes = current.earlyCrashes + 1)
        } else {
            current.copy(earlyCrashes = 0)
        }
    }

    /** Once the launch has run for [early] without a crash. */
    public fun onStable(current: CrashLoopRecord): CrashLoopRecord = current.copy(earlyCrashes = 0)

    public companion object {
        public val EARLY: Duration = 10.seconds
        public const val THRESHOLD: Int = 3
    }
}
