package io.github.eslamasabry.oc.engine.diagnostics.exit

import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.DiagnosticsSignals
import io.github.eslamasabry.oc.engine.diagnostics.FreezeKind
import io.github.eslamasabry.oc.engine.diagnostics.crash.NativeCrashRecord
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import io.github.eslamasabry.oc.engine.diagnostics.safely
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue

/**
 * Remembers the newest crash or freeze already reported, so each is signalled once across restarts (the JVM crash
 * record is read on every start). The platform keeps it in a preference; [InMemory] is for tests and desktop.
 */
public interface CrashSignalLedger {
    /** Epoch ms of the newest event already signalled; 0 when none. */
    public suspend fun lastSignaledMs(): Long

    public suspend fun markSignaled(ms: Long)

    public class InMemory(private var last: Long = 0L) : CrashSignalLedger {
        override suspend fun lastSignaledMs(): Long = last

        override suspend fun markSignaled(ms: Long) {
            last = ms
        }
    }
}

/**
 * The diagnostics half of the launch (Dart `AppExitRecovery.runOnce`'s first lines, B-ANDR-036): reads the launch
 * report once per process, puts the last JVM crash (bounded stack) into the ring and the trace, marks the last exit,
 * and reports crash and freeze CATEGORIES (plan 18: `crash_seen`, `freeze_seen`). Recovery itself is
 * `:engine:runtime`'s `AppExitRecovery`, which reads [runOnce]'s report.
 */
public class LaunchIntake(
    private val history: ExitHistory,
    private val diagnostics: Diagnostics,
    private val trace: PerfTrace,
    private val signals: DiagnosticsSignals,
    private val ledger: CrashSignalLedger,
) {
    private val once = Mutex()
    private var report: LaunchReport? = null

    /** Runs once per process; later calls answer the same report without reading again. */
    public suspend fun runOnce(): LaunchReport =
        once.withLock {
            report?.let { return@withLock it }
            val read = history.launchReport()
            report = read
            intake(read)
            read
        }

    private suspend fun intake(report: LaunchReport) {
        val crash = report.lastCrash
        if (crash != null) {
            trace.mark("crash.last", crash.traceAttrs)
            diagnostics.record("crash.last", crash.diagnosticMessage, crash.diagnosticStack, at = crash.timestamp)
        }
        val exit = report.exit
        trace.mark("app.lastExit", exit?.traceAttrs ?: mapOf("kind" to "none"))
        signal(exit, crash)
    }

    private suspend fun signal(exit: ExitRecord?, jvmCrash: NativeCrashRecord?) {
        val watermark = ledger.lastSignaledMs()
        var newest = watermark
        var jvmCounted = false
        if (exit != null && exit.timestampMs > watermark) {
            val paired = jvmCrash != null && (jvmCrash.timestampMs - exit.timestampMs).absoluteValue <= PAIR_WINDOW_MS
            when (exit.reason) {
                ExitReason.CRASH -> {
                    val oom = paired && jvmCrash.isOutOfMemory
                    report(if (oom) CrashCategory.OutOfMemory else CrashCategory.Kotlin)
                    jvmCounted = paired
                }
                ExitReason.CRASH_NATIVE -> report(CrashCategory.Native)
                ExitReason.INITIALIZATION_FAILURE -> report(CrashCategory.Other)
                ExitReason.ANR -> {
                    val foreground = exit.importance in 1..ExitReason.IMPORTANCE_FOREGROUND
                    signals.safely { freeze(FreezeKind.NotResponding, ANR_MS, foreground) }
                }
                else -> Unit
            }
            newest = maxOf(newest, exit.timestampMs)
        }
        if (jvmCrash != null && jvmCrash.timestampMs > watermark) {
            if (!jvmCounted) report(if (jvmCrash.isOutOfMemory) CrashCategory.OutOfMemory else CrashCategory.Kotlin)
            newest = maxOf(newest, jvmCrash.timestampMs)
        }
        if (newest > watermark) ledger.markSignaled(newest)
    }

    private fun report(category: CrashCategory) {
        signals.safely { crash(category, fatal = true) }
    }

    public companion object {
        /** An exit and a JVM crash record this close together are the same crash. */
        public const val PAIR_WINDOW_MS: Long = 60_000L

        /** Android's input-dispatch ANR threshold: an ANR exit carries no duration, it lasted at least this. */
        public const val ANR_MS: Long = 5_000L
    }
}
