package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WriterLaneTest {

    @Test
    fun `B-STATE-013 drain waits for a write paused between admission and the lock`() = runTest {
        // The audit's interleaving (codex-01+03 BLOCKER 1): the writer passed admission but has not reached the lock.
        val lane = WriterLane("p")
        val paused = CompletableDeferred<Unit>()
        val reachedPause = CompletableDeferred<Unit>()
        lane.afterAdmission = {
            reachedPause.complete(Unit)
            paused.await()
        }
        val log = mutableListOf<String>()
        launch { lane.write { log += "write"; Outcome.Ok(Unit) } }
        reachedPause.await()
        lane.afterAdmission = null

        lane.stopAdmission()
        val drained = async { lane.drain(); log += "drained" }
        runCurrent()
        assertFalse(drained.isCompleted, "drain returned while an admitted write had not run")

        paused.complete(Unit)
        drained.await()
        assertEquals(listOf("write", "drained"), log)
    }

    @Test
    fun `B-STATE-013 a write asked for after stopAdmission is refused and never runs`() = runTest {
        val lane = WriterLane("p")
        lane.stopAdmission()
        var ran = false
        val result = lane.write { ran = true; Outcome.Ok(Unit) }
        assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing)), result)
        assertFalse(ran)
        lane.drain() // nothing admitted: returns at once
    }

    @Test
    fun `B-STATE-002 drain waits for every queued write, in order`() = runTest {
        val lane = WriterLane("p")
        val gate = CompletableDeferred<Unit>()
        val log = mutableListOf<Int>()
        launch { lane.write { gate.await(); log += 1; Outcome.Ok(Unit) } }
        launch { lane.write { log += 2; Outcome.Ok(Unit) } }
        launch { lane.write { log += 3; Outcome.Ok(Unit) } }
        runCurrent()
        lane.stopAdmission()
        val drained = async { lane.drain() }
        runCurrent()
        assertFalse(drained.isCompleted)
        gate.complete(Unit)
        drained.await()
        assertEquals(listOf(1, 2, 3), log)
    }

    @Test
    fun `B-STATE-002 a writer cancelled while waiting for the lock does not hold up drain`() = runTest {
        val lane = WriterLane("p")
        val gate = CompletableDeferred<Unit>()
        launch { lane.write { gate.await(); Outcome.Ok(Unit) } }
        var cancelledRan = false
        val waiting = launch { lane.write { cancelledRan = true; Outcome.Ok(Unit) } }
        runCurrent()
        waiting.cancel()
        lane.stopAdmission()
        val drained = async { lane.drain() }
        runCurrent()
        assertFalse(drained.isCompleted)
        gate.complete(Unit)
        drained.await()
        assertFalse(cancelledRan)
    }

    @Test
    fun `B-STATE-002 a failed deletion reopens the lane and writes work again`() = runTest {
        val lane = WriterLane("p")
        lane.stopAdmission()
        assertFalse(lane.isAdmitting)
        lane.resumeAdmission()
        assertEquals(Outcome.Ok(7), lane.write { Outcome.Ok(7) })
    }

    @Test
    fun `B-CHAT-051 a write that was admitted before the close lands before the sweep, and a late one is fenced`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = storeOver(InMemoryPrefsBackend(), dispatcher)
            val lanes = DefaultWriterLanes()
            val doomed = ProfileId("111")
            val scope = DefaultProfileScope(store, MapSecretStore(), lanes.app)
            val lane = lanes.forProfile(doomed)

            // Admitted, then paused before the lock (the race window).
            val paused = CompletableDeferred<Unit>()
            lane.afterAdmission = { paused.await() }
            val inFlight = async { store.commit(lane) { putString("oc.model.111", "a|b") } }
            runCurrent()
            lane.afterAdmission = null

            lanes.closeProfile(doomed)
            val deletion = async {
                lanes.drainProfile(doomed)
                scope.sweep(doomed)
            }
            runCurrent()
            assertFalse(deletion.isCompleted)
            paused.complete(Unit)
            assertTrue(inFlight.await() is Outcome.Ok)
            assertTrue(deletion.await() is Outcome.Ok)
            assertNull(store.string("oc.model.111"), "the admitted write was swept, not resurrected afterwards")

            val late = store.commit(lanes.forProfile(doomed)) { putString("oc.model.111", "late") }
            assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing)), late)
            assertNull(store.string("oc.model.111"))
        }

    @Test
    fun `lanes are per profile and stay closed once closed`() {
        val lanes = DefaultWriterLanes()
        val a = lanes.forProfile(ProfileId("1"))
        assertTrue(a === lanes.forProfile(ProfileId("1")))
        lanes.closeProfile(ProfileId("1"))
        assertFalse(lanes.forProfile(ProfileId("1")).isAdmitting)
        assertTrue(lanes.forProfile(ProfileId("2")).isAdmitting)
        assertTrue(lanes.app.isAdmitting)
    }
}

