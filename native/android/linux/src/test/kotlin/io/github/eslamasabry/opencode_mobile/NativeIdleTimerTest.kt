package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIdleTimerTest {
    private class Fixture {
        data class Task(val delay: Long, val work: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        var badClock = false
        var badQueue = false
        var badAlarm = false
        var badCancel = false
        var badCheck = false
        var checks = 0
        val tasks = mutableListOf<Task>()
        val deadlines = mutableListOf<Long>()
        var alarmCancels = 0
        val alarms = object : NativeIdleTimer.Alarms {
            override fun schedule(deadlineElapsedMs: Long) {
                if (badAlarm) error("unavailable")
                deadlines.add(deadlineElapsedMs)
            }
            override fun cancel() { alarmCancels++; if (badCancel) error("unavailable") }
        }
        val timer = NativeIdleTimer({ if (badClock) error("unavailable") else now }, { delay, work ->
            if (badQueue) error("unavailable")
            val task = Task(delay, work)
            tasks.add(task)
            val cancel: () -> Unit = { task.cancelled = true }
            cancel
        }, alarms, { checks++; if (badCheck) error("unavailable") })
    }

    @Test fun deadlineUsesElapsedClockAndRunsExactlyOneStopCheck() {
        val f = Fixture(); f.now = 100
        f.timer.schedule(500)
        assertEquals(listOf(500L), f.deadlines)
        assertEquals(400L, f.tasks.single().delay)
        f.now = 500; f.tasks.single().work()
        assertEquals(1, f.checks)
        f.tasks.single().work()
        assertEquals(1, f.checks)
        assertEquals(2, f.alarmCancels)
    }

    @Test fun repeatedUnchangedDeadlineDoesNotReplaceTheAlarmOrLocalTask() {
        val f = Fixture(); f.timer.schedule(500)
        val task = f.tasks.single()
        f.now = 100; f.timer.schedule(500)
        assertEquals(listOf(500L), f.deadlines)
        assertEquals(1, f.tasks.size)
        assertFalse(task.cancelled)
    }

    @Test fun changedDeadlineCancelsAndRejectsAnAlreadyQueuedOldCallback() {
        val f = Fixture(); f.timer.schedule(500)
        val old = f.tasks.single()
        f.now = 100; f.timer.schedule(1000)
        assertTrue(old.cancelled)
        f.now = 500; old.work()
        assertEquals(0, f.checks)
        f.now = 1000; f.tasks.last().work()
        assertEquals(1, f.checks)
    }

    @Test fun earlierDeadlinePreemptsRatherThanWaitingForThePreviousOne() {
        val f = Fixture(); f.timer.schedule(1000)
        val old = f.tasks.single()
        f.now = 100; f.timer.schedule(200)
        assertTrue(old.cancelled)
        assertEquals(100L, f.tasks.last().delay)
        f.now = 200; f.tasks.last().work()
        assertEquals(1, f.checks)
    }
    @Test fun cancelThenRescheduleSameDeadlineKeepsOldGenerationCallbackInert() {
        val f = Fixture(); f.timer.schedule(100)
        val old = f.tasks.single()
        f.timer.cancel(); f.timer.schedule(100)
        f.now = 100; old.work()
        assertEquals(0, f.checks)
        f.tasks.last().work(); assertEquals(1, f.checks)
    }

    @Test fun explicitCancelAndNullScheduleInvalidateEveryOldTask() {
        val f = Fixture(); f.timer.schedule(100)
        val first = f.tasks.last()
        f.timer.cancel(); assertTrue(first.cancelled)
        f.timer.schedule(200)
        val second = f.tasks.last()
        f.timer.schedule(null); assertTrue(second.cancelled)
        f.now = 300; first.work(); second.work()
        assertEquals(0, f.checks)
        f.timer.schedule(400); f.now = 400; f.tasks.last().work()
        assertEquals(1, f.checks)
    }

    @Test fun negativeDeadlineCancelsWithoutSchedulingAnotherCheck() {
        val f = Fixture(); f.timer.schedule(100)
        val old = f.tasks.single()
        f.timer.schedule(-1); f.now = 100; old.work()
        assertEquals(0, f.checks)
        assertEquals(1, f.tasks.size)
        assertEquals(listOf(100L), f.deadlines)
    }

    @Test fun elapsedDeadlineQueuesImmediateAuthorityCheckWithoutNegativeDelay() {
        val f = Fixture(); f.now = 500; f.timer.schedule(100)
        assertEquals(0L, f.tasks.single().delay)
        f.tasks.single().work(); assertEquals(1, f.checks)
    }

    @Test fun earlyCallbackRearmsRemainingTimeWithoutCheckingStopPrematurely() {
        val f = Fixture(); f.timer.schedule(100)
        f.now = 60; f.tasks.single().work()
        assertEquals(0, f.checks)
        assertEquals(40L, f.tasks.last().delay)
        f.now = 100; f.tasks.last().work()
        assertEquals(1, f.checks)
    }

    @Test fun reversedClockCancelsOldDeadlineAndRetainsItsHighWater() {
        val f = Fixture(); f.now = 100; f.timer.schedule(200)
        val old = f.tasks.single()
        f.now = 99; f.timer.schedule(300)
        assertTrue(old.cancelled)
        assertEquals(1, f.tasks.size)
        f.now = 100; old.work(); assertEquals(0, f.checks)
        f.timer.schedule(300); assertEquals(2, f.tasks.size)
    }

    @Test fun reversedClockAtDispatchDoesNotStopAndCannotReuseOldTask() {
        val f = Fixture(); f.now = 100; f.timer.schedule(200)
        val old = f.tasks.single()
        f.now = 99; old.work()
        f.now = 200; old.work()
        assertEquals(0, f.checks)
    }

    @Test fun invalidOrUnavailableClockCancelsAndCannotDispatchAStop() {
        val f = Fixture(); f.timer.schedule(100)
        val old = f.tasks.single()
        f.badClock = true; f.timer.schedule(200)
        f.badClock = false; f.now = 100; old.work()
        assertEquals(0, f.checks)
        f.now = -1; f.timer.schedule(200)
        assertEquals(1, f.tasks.size)
    }

    @Test fun unavailableAlarmStillLeavesProcessFallbackOperational() {
        val f = Fixture(); f.badAlarm = true; f.badCancel = true
        f.timer.schedule(100)
        f.now = 100; f.tasks.single().work()
        assertEquals(1, f.checks)
    }

    @Test fun unavailableProcessQueueDoesNotEraseAnIndependentAlarm() {
        val f = Fixture(); f.badQueue = true
        f.timer.schedule(100)
        assertEquals(listOf(100L), f.deadlines)
        assertTrue(f.tasks.isEmpty())
        assertEquals(0, f.checks)
        f.timer.cancel(); assertEquals(2, f.alarmCancels)
    }

    @Test fun checkExceptionIsContainedAndLaterAuthorizedScheduleStillWorks() {
        val f = Fixture(); f.badCheck = true; f.timer.schedule(100)
        f.now = 100; f.tasks.single().work()
        assertEquals(1, f.checks)
        f.badCheck = false; f.timer.schedule(200)
        f.now = 200; f.tasks.last().work()
        assertEquals(2, f.checks)
    }

    @Test fun maximumDeadlineDelayCannotWrapAndCancelledTaskCannotFire() {
        val f = Fixture(); f.timer.schedule(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, f.tasks.single().delay)
        val old = f.tasks.single()
        f.timer.cancel(); f.now = Long.MAX_VALUE; old.work()
        assertEquals(0, f.checks)
    }

    @Test fun stopCheckRunsOutsideTimerGuardSoLinuxCanUpdateItsDeadline() {
        var now = 0L
        val tasks = mutableListOf<() -> Unit>()
        lateinit var timer: NativeIdleTimer
        val updated = java.util.concurrent.CountDownLatch(1)
        var blocked = false
        timer = NativeIdleTimer({ now }, { _, work -> tasks.add(work); {} },
            object : NativeIdleTimer.Alarms {
                override fun schedule(deadlineElapsedMs: Long) { /* the fixture never arms an alarm */ }
                override fun cancel() { /* nothing armed to cancel */ }
            }, {
                Thread({ timer.schedule(200); updated.countDown() }, "fixture-idle-update")
                    .apply { isDaemon = true; start() }
                blocked = !updated.await(1, java.util.concurrent.TimeUnit.SECONDS)
            })
        timer.schedule(100); now = 100; tasks.first().invoke()
        assertFalse(blocked)
        assertEquals(0L, updated.count)
        assertEquals(2, tasks.size)
    }
}
