package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-488: real-progress silence tracking and one-shot compatible Linux mode fallback. */
class SetupStepSilenceTest {
    private class FakeHooks(
        private val cancelled: () -> Boolean = { false },
        private val runAttempt: (Boolean) -> SetupCompatFallback.StepAttempt,
    ) : SetupCompatFallback.Hooks {
        val modes = mutableListOf<Boolean>()
        val retryReasons = mutableListOf<String>()
        var persisted = false
        var cleared = false

        override fun isCancelled(): Boolean = cancelled()

        override fun persistCompat() {
            persisted = true
        }

        override fun clearCompat() {
            cleared = true
        }

        override fun onCompatRetry(reasonLine: String) {
            retryReasons += reasonLine
        }

        override fun runOnce(compatible: Boolean): SetupCompatFallback.StepAttempt {
            modes += compatible
            return runAttempt(compatible)
        }
    }

    @Test
    fun `B-SETUP-022 repeated identical bytes do not reset step silence timer or component change flag`() {
        var clock = 1_000L
        val silence = SetupStepSilence(timeoutMs = 120_000L, nowMs = { clock })
        val component = SetupComponentStatus("node", state = "running")

        val initialBytes = SetupProtocol.Event.Bytes(done = 0L, total = 58_000_000L)
        assertTrue(silence.recordEvent(initialBytes))
        assertTrue(component.applyIfChanged(initialBytes))

        clock += 60_000L
        assertFalse(silence.recordEvent(initialBytes))
        assertFalse(component.applyIfChanged(initialBytes))
        assertEquals(60_000L, silence.silentForMs())
        assertFalse(silence.isTimedOut())

        clock += 60_000L
        assertFalse(silence.recordEvent(initialBytes))
        assertTrue(silence.isTimedOut())

        val advancedBytes = SetupProtocol.Event.Bytes(done = 4_096L, total = 58_000_000L)
        assertTrue(silence.recordEvent(advancedBytes))
        assertTrue(component.applyIfChanged(advancedBytes))
        assertFalse(silence.isTimedOut())
        assertEquals(0L, silence.silentForMs())
    }

    @Test
    fun `B-SETUP-023 silent step in normal mode automatically retries once in compatible mode and persists marker`() {
        val hooks =
            FakeHooks { compatible ->
                if (!compatible) SetupCompatFallback.StepAttempt("stalled", exitCode = null, stalled = true)
                else SetupCompatFallback.StepAttempt(null, exitCode = 0, stalled = false)
            }

        val outcome = SetupCompatFallback.run("essentials", initialCompat = false, hooks = hooks)

        assertNull(outcome.failure)
        assertTrue(outcome.usedCompat)
        assertTrue(outcome.retriedInCompat)
        assertEquals(listOf(false, true), hooks.modes)
        assertTrue(hooks.persisted)
        assertFalse(hooks.cleared)
        assertEquals(1, hooks.retryReasons.size)
        assertTrue(hooks.retryReasons.single().contains("compatibility mode"))
    }

    @Test
    fun `B-SETUP-024 silent step that stalls again in compatible mode fails with Linux mode in Details`() {
        val hooks =
            FakeHooks {
                SetupCompatFallback.StepAttempt("proot info: vpid 1: terminated with signal 15", 143, stalled = true)
            }

        val outcome = SetupCompatFallback.run("essentials", initialCompat = false, hooks = hooks)

        assertEquals(SetupCompatFallback.stalledFailure(compatible = true), outcome.failure)
        assertTrue(SetupCompatFallback.isStalled(outcome.failure))
        assertFalse(SetupStepRetry.interrupted(outcome.failure))
        assertTrue(outcome.usedCompat)
        assertTrue(outcome.retriedInCompat)
        assertEquals(listOf(false, true), hooks.modes)
        assertTrue(hooks.persisted)
        assertTrue(hooks.cleared)
        assertTrue(outcome.detailLines.any { it.contains("Linux mode: compatible") })
        assertTrue(outcome.terminalDetail?.contains("Linux mode: compatible") == true)
    }

    @Test
    fun `B-SETUP-025 segfault crash in normal mode still retries once in compatible mode`() {
        val hooks =
            FakeHooks { compatible ->
                if (!compatible) {
                    SetupCompatFallback.StepAttempt("Segmentation fault", SetupStepRetry.SEGFAULT_EXIT, false)
                } else {
                    SetupCompatFallback.StepAttempt(null, exitCode = 0, stalled = false)
                }
            }

        val outcome = SetupCompatFallback.run("node", initialCompat = false, hooks = hooks)

        assertNull(outcome.failure)
        assertTrue(outcome.usedCompat)
        assertTrue(outcome.retriedInCompat)
        assertEquals(listOf(false, true), hooks.modes)
        assertTrue(hooks.persisted)
        assertFalse(hooks.cleared)
    }

    @Test
    fun `B-SETUP-026 cancelled step never triggers compatible retry`() {
        val hooks =
            FakeHooks(cancelled = { true }) {
                SetupCompatFallback.StepAttempt("cancelled", exitCode = null, stalled = true)
            }

        val outcome = SetupCompatFallback.run("essentials", initialCompat = false, hooks = hooks)

        assertEquals("cancelled", outcome.failure)
        assertFalse(outcome.retriedInCompat)
        assertEquals(listOf(false), hooks.modes)
        assertFalse(hooks.persisted)
    }

    @Test
    fun `B-SETUP-027 compat mode uses longer silence bound and watchdog stops silent process once`() {
        assertEquals(SetupCompatFallback.SILENCE_TIMEOUT_MS, SetupCompatFallback.timeoutForMode(compatible = false))
        assertEquals(
            SetupCompatFallback.COMPAT_SILENCE_TIMEOUT_MS,
            SetupCompatFallback.timeoutForMode(compatible = true),
        )
        assertTrue(SetupCompatFallback.COMPAT_SILENCE_TIMEOUT_MS > SetupCompatFallback.SILENCE_TIMEOUT_MS)

        val clock = java.util.concurrent.atomic.AtomicLong(0L)
        val silence = SetupStepSilence(timeoutMs = 50L, nowMs = { clock.get() })
        val stops = java.util.concurrent.atomic.AtomicInteger(0)
        val stopped = java.util.concurrent.CountDownLatch(1)
        val watchdog =
            SetupSilenceWatchdog(
                silence = silence,
                isCancelled = { false },
                stopProcess = {
                    stops.incrementAndGet()
                    stopped.countDown()
                },
                pollMs = 5L,
            )
        watchdog.start()
        try {
            clock.set(60L)
            assertTrue(stopped.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(watchdog.isStalled)
            assertTrue(watchdog.isStopped())
            assertEquals(1, stops.get())
        } finally {
            watchdog.stop()
        }
    }

    @Test
    fun `B-SETUP-029 silent step whose process is busy is not stopped past the silence bound`() {
        val clock = java.util.concurrent.atomic.AtomicLong(0L)
        val busy = java.util.concurrent.atomic.AtomicBoolean(true)
        val busyChecks = java.util.concurrent.atomic.AtomicInteger(0)
        val silence = SetupStepSilence(timeoutMs = 50L, nowMs = { clock.get() })
        val stops = java.util.concurrent.atomic.AtomicInteger(0)
        val stopped = java.util.concurrent.CountDownLatch(1)
        val watchdog =
            SetupSilenceWatchdog(
                silence = silence,
                isCancelled = { false },
                stopProcess = {
                    stops.incrementAndGet()
                    stopped.countDown()
                },
                pollMs = 5L,
                isBusy = {
                    busyChecks.incrementAndGet()
                    busy.get()
                },
            )
        watchdog.start()
        try {
            clock.set(60L)
            Thread.sleep(60L)
            assertEquals("busy silent process must not be stopped", 0, stops.get())
            assertFalse(watchdog.isStalled)

            clock.set(120L)
            Thread.sleep(60L)
            assertEquals("busy silent process must stay alive past multiple bounds", 0, stops.get())
            assertFalse(watchdog.isStalled)
            assertTrue("expected isBusy hook to be consulted", busyChecks.get() >= 2)

            busy.set(false)
            clock.set(180L)
            assertTrue(stopped.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(watchdog.isStalled)
            assertEquals(1, stops.get())
        } finally {
            watchdog.stop()
        }
    }

    @Test
    fun `B-SETUP-030 proc cpu sampler detects busy process tree and idle stalled tree`() {
        fun fakeStat(pid: Int, comm: String, utime: Long, stime: Long, cutime: Long = 0, cstime: Long = 0): String =
            "$pid ($comm) S 1 1 1 0 -1 0 0 0 0 0 $utime $stime $cutime $cstime 20 0 1 0 100 0 0"

        assertEquals(35L, ProcessTreeCpu.cpuTicks(fakeStat(42, "node (main)", 10, 5, 15, 5)))
        assertNull(ProcessTreeCpu.cpuTicks("not a stat line"))

        var call = 0
        val busy =
            ProcessTreeCpu.sampleBusy(
                pids = { listOf(10, 11) },
                sampleMs = 1L,
                readStat = { pid ->
                    if (pid == 10) fakeStat(10, "proot", 5, 5)
                    else fakeStat(11, "tar", if (call++ == 0) 20 else 25, 10)
                },
                sleep = {},
            )
        assertTrue(busy)

        val idle =
            ProcessTreeCpu.sampleBusy(
                pids = { listOf(10, 11) },
                sampleMs = 1L,
                readStat = { pid -> fakeStat(pid, "proot", 5, 5) },
                sleep = {},
            )
        assertFalse(idle)

        val exited = ProcessTreeCpu.sampleBusy(pids = { emptyList() }, sampleMs = 1L, sleep = {})
        assertFalse(exited)
    }

    @Test
    fun `B-SETUP-031 completed script with exit zero succeeds even if silence watchdog raced teardown`() {
        val result =
            SetupCompatFallback.resolveScriptFailure(
                cancelled = false,
                completionExitCode = 0,
                stalled = true,
                compatible = false,
            ) { "exit 143" }
        assertNull(result)

        val stalledResult =
            SetupCompatFallback.resolveScriptFailure(
                cancelled = false,
                completionExitCode = null,
                stalled = true,
                compatible = true,
            ) { "exit 143" }
        assertEquals(SetupCompatFallback.stalledFailure(compatible = true), stalledResult)

        val cancelledResult =
            SetupCompatFallback.resolveScriptFailure(
                cancelled = true,
                completionExitCode = 0,
                stalled = false,
                compatible = false,
            ) { null }
        assertEquals("cancelled", cancelledResult)
    }
}
