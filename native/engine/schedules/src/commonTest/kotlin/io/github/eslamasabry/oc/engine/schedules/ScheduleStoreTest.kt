package io.github.eslamasabry.oc.engine.schedules

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleStoreTest {
    private val clock = Clock()
    private val backend = MemoryBackend()
    private val store = ScheduleStore(backend, clock::ms) { TimeZone.UTC }

    private suspend fun code(block: suspend () -> Unit): ScheduleCode =
        assertFailsWith<ScheduleStoreException> { block() }.code

    @Test
    fun `B-STATE-095 schedules, paused state, history and digest opt-in survive restart`() = runTest {
        store.save(prompt())
        store.setEnabled(P, "a1", false)
        store.recordRun(P, ScheduledRun("a1:1:1", "a1", T0, T0, RunStatus.Ran, finishedAtMs = T0 + MIN, sessionId = "ses_1"))
        store.setDigest(P, ScheduleDigest(enabled = true, hour = 7))
        val reopened = ScheduleStore(MemoryBackend(backend.values), clock::ms) { TimeZone.UTC }
        val schedule = reopened.load(P).single()
        assertFalse(schedule.enabled)
        assertEquals(2, schedule.revision)
        assertEquals("ses_1", reopened.history(P).single().sessionId)
        assertTrue(reopened.digest(P).enabled)
        assertEquals("oc.scheduledPrompts.p1", backend.values.keys.single())
    }

    @Test
    fun `B-STATE-095 a schedule is advanced by instant after reload`() = runTest {
        store.save(prompt(next = T0))
        val reloaded = ScheduleStore(MemoryBackend(backend.values), clock::ms) { TimeZone.UTC }
        val loaded = reloaded.load(P).single()
        assertTrue(reloaded.advanceOccurrence(loaded, T0 + 24 * HOUR))
        assertEquals(T0 + 24 * HOUR, reloaded.load(P).single().nextRunAtMs)
    }

    @Test
    fun `B-STATE-096 same-revision and stale edits cannot overwrite`() = runTest {
        store.save(prompt())
        assertEquals(ScheduleCode.StaleSchedule, code { store.save(prompt().copy(prompt = "other")) })
        store.save(prompt(revision = 2).copy(prompt = "edited"))
        assertEquals(ScheduleCode.StaleSchedule, code { store.save(prompt(revision = 2).copy(prompt = "late")) })
        assertEquals("edited", store.load(P).single().prompt)
    }

    @Test
    fun `B-STATE-096 concurrent writers keep unrelated edits`() = runTest {
        store.save(prompt("a1"))
        val other = ScheduleStore(backend, clock::ms) { TimeZone.UTC }
        other.save(prompt("b2"))
        store.setEnabled(P, "a1", false)
        assertEquals(setOf("a1", "b2"), store.load(P).map { it.id }.toSet())
    }

    @Test
    fun `B-STATE-096 advance cannot resurrect a deleted schedule or overwrite an edit or pause`() = runTest {
        store.save(prompt(next = T0))
        val claimed = store.load(P).single()
        store.save(claimed.copy(prompt = "edited", revision = 2))
        assertFalse(store.advanceOccurrence(claimed, T0 + 24 * HOUR))
        assertEquals(T0, store.load(P).single().nextRunAtMs)
        store.delete(P, "a1")
        assertFalse(store.advanceOccurrence(claimed, T0 + 24 * HOUR))
        assertTrue(store.load(P).isEmpty())
    }

    @Test
    fun `B-STATE-096 corrupt or foreign state is not overwritten and fails closed`() = runTest {
        backend.values["oc.scheduledPrompts.p1"] = "{not json"
        assertEquals(ScheduleCode.StorageCorrupt, code { store.save(prompt()) })
        assertEquals("{not json", backend.values["oc.scheduledPrompts.p1"])
        backend.values["oc.scheduledPrompts.p1"] =
            ScheduleCodec.encodeEnvelope(ScheduleCodec.Envelope(listOf(prompt().copy(profileId = "other"))))
        assertEquals(ScheduleCode.StorageCorrupt, code { store.load(P) })
        val drift = ScheduleCodec.encodeEnvelope(ScheduleCodec.Envelope()).replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        backend.values["oc.scheduledPrompts.p1"] = drift
        assertEquals(ScheduleCode.StorageCorrupt, code { store.load(P) })
        backend.values["oc.scheduledPrompts.p1"] =
            ScheduleCodec.encodeEnvelope(ScheduleCodec.Envelope(listOf(prompt(), prompt())))
        assertEquals(ScheduleCode.StorageCorrupt, code { store.load(P) }) // duplicate ids
    }

    @Test
    fun `B-STATE-096 refused persistence never reports success`() = runTest {
        backend.refuseWrites = true
        assertEquals(ScheduleCode.StorageBusy, code { store.save(prompt()) })
        assertTrue(backend.values.isEmpty())
    }

    @Test
    fun `B-STATE-097 deleting a schedule removes its visible history`() = runTest {
        store.save(prompt("a1"))
        store.save(prompt("b2"))
        store.recordRun(P, ScheduledRun("a1:1:1", "a1", T0, T0, RunStatus.Ran))
        store.recordRun(P, ScheduledRun("b2:1:1", "b2", T0, T0, RunStatus.Ran))
        store.delete(P, "a1")
        assertEquals(listOf("b2"), store.history(P).map { it.scheduleId })
    }

    @Test
    fun `B-STATE-097 resuming skips paused occurrences and a past once schedule needs editing`() = runTest {
        store.save(prompt(next = T0))
        store.setEnabled(P, "a1", false)
        clock.now = T0 + 3 * 24 * HOUR + 30 * MIN // three days later, 08:30
        store.setEnabled(P, "a1", true)
        assertEquals(T0 + 3 * 24 * HOUR + HOUR, store.load(P).single().nextRunAtMs) // today 09:00, nothing replayed
        store.save(prompt("once", Recurrence.once(T0 + HOUR), next = T0 + HOUR))
        store.setEnabled(P, "once", false)
        assertEquals(ScheduleCode.ScheduleExpired, code { store.setEnabled(P, "once", true) })
    }

    @Test
    fun `B-STATE-097 the digest claims once per cutoff and respects concurrent disabling`() = runTest {
        val on = ScheduleDigest(enabled = true)
        store.save(prompt())
        store.setDigest(P, on)
        assertTrue(store.claimDigest(P, on, T0))
        assertFalse(store.claimDigest(P, on, T0)) // the expected digest changed (lastDelivered)
        val claimed = store.digest(P)
        assertFalse(store.claimDigest(P, claimed, T0)) // same cutoff
        store.setDigest(P, claimed.copy(enabled = false))
        assertFalse(store.claimDigest(P, claimed, T0 + 24 * HOUR))
    }

    @Test
    fun `B-STATE-090 a terminal record never changes and a running one finishes once`() = runTest {
        store.save(prompt())
        val running = ScheduledRun("a1:1:1", "a1", T0, T0, RunStatus.Running)
        store.recordRun(P, running)
        store.recordRun(P, running.copy(status = RunStatus.Failed, errorCode = "interrupted"))
        assertEquals(
            ScheduleCode.InvalidRunTransition,
            code { store.recordRun(P, running.copy(status = RunStatus.Ran)) },
        )
        assertEquals(1, store.history(P).size)
    }
}
