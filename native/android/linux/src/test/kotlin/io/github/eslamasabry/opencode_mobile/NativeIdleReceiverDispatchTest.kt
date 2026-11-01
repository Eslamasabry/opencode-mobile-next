package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeIdleReceiverDispatchTest {
    private class Fixture {
        var work: (() -> Unit)? = null
        var watch: (() -> Unit)? = null
        var delay = 0L
        var workCancelled = 0
        var watchCancelled = 0
        var checks = 0
        var finishes = 0
        var rejectWorker = false
        var rejectWatch = false
        var badCheck = false
        var badFinish = false
        val dispatch = NativeIdleReceiverDispatch({ task ->
            if (rejectWorker) error("unavailable")
            work = task
            val cancel: () -> Unit = { workCancelled++ }
            cancel
        }, { millis, task ->
            if (rejectWatch) error("unavailable")
            delay = millis; watch = task
            val cancel: () -> Unit = { watchCancelled++ }
            cancel
        })
        fun start() = dispatch.dispatch({ checks++; if (badCheck) error("unavailable") },
            { finishes++; if (badFinish) error("unavailable") })
    }

    @Test fun successfulWorkerFinishesOnceAndCancelsItsEightSecondWatchdog() {
        val f = Fixture(); f.start()
        assertEquals(8000L, f.delay)
        assertEquals(0, f.checks); assertEquals(0, f.finishes)
        f.work!!()
        assertEquals(1, f.checks); assertEquals(1, f.finishes)
        assertEquals(1, f.watchCancelled)
        f.watch!!()
        assertEquals(1, f.finishes)
    }

    @Test fun stopCheckFailureStillFinishesItsPendingBroadcast() {
        val f = Fixture(); f.badCheck = true; f.start(); f.work!!()
        assertEquals(1, f.checks); assertEquals(1, f.finishes)
    }

    @Test fun boundedWorkerRejectionFinishesImmediatelyWithoutRunningCheck() {
        val f = Fixture(); f.rejectWorker = true; f.start()
        assertEquals(0, f.checks); assertEquals(1, f.finishes)
        assertEquals(1, f.watchCancelled)
    }

    @Test fun watchdogAdmissionFailureDoesNotLaunchUnboundedReceiverWork() {
        val f = Fixture(); f.rejectWatch = true; f.start()
        assertNull(f.work)
        assertEquals(0, f.checks); assertEquals(1, f.finishes)
    }

    @Test fun timeoutFinishesAndPreventsAnUnstartedLateWorkerFromChecking() {
        val f = Fixture(); f.start(); f.watch!!()
        assertEquals(1, f.workCancelled); assertEquals(1, f.finishes)
        f.work!!()
        assertEquals(0, f.checks); assertEquals(1, f.finishes)
    }

    @Test fun timeoutDuringCheckInterruptsOwnWorkerAndNeverFinishesTwice() {
        val f = Fixture()
        f.dispatch.dispatch({ f.checks++; f.watch!!() }, { f.finishes++ })
        f.work!!()
        assertEquals(1, f.checks); assertEquals(1, f.workCancelled)
        assertEquals(1, f.finishes)
    }

    @Test fun pendingFinishFailureIsContainedAndNeverRetried() {
        val f = Fixture(); f.badFinish = true; f.start(); f.work!!(); f.watch!!()
        assertEquals(1, f.finishes)
    }

    @Test fun watchdogFiringDuringSubmissionCannotLeaveAWorkerOrPendingBroadcast() {
        var checks = 0; var finishes = 0; var queued = 0; var cancelled = 0
        val dispatch = NativeIdleReceiverDispatch({ _ -> queued++; {} }, { _, work ->
            work()
            val cancel: () -> Unit = { cancelled++ }
            cancel
        })
        dispatch.dispatch({ checks++ }, { finishes++ })
        assertEquals(0, checks); assertEquals(0, queued)
        assertEquals(1, finishes); assertEquals(1, cancelled)
    }
}
