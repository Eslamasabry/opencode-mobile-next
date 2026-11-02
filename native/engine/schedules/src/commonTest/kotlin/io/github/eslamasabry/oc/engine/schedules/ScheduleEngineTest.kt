package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleEngineTest {
    private val h = Harness()
    private val far = Long.MAX_VALUE / 2

    private suspend fun fire(entry: ArmedEntry = h.armed().first()): RunReport? {
        h.clock.now = entry.dueAtMs
        return h.engine.runOccurrence(entry, far)
    }

    @Test
    fun `B-STATE-089 create, edit, pause, resume and delete each update the OS schedule`() = runTest {
        val saved = h.saved()
        assertEquals(listOf(ArmedEntry(P, saved.id, 1, T0 + HOUR)), h.armed())
        val edited = (h.engine.save(P, h.draft(Recurrence.daily(10, 30)), editing = saved) as Outcome.Ok).value
        assertEquals(2, edited.revision)
        assertEquals(T0 + 2 * HOUR + 30 * MIN, h.armed().single().dueAtMs)
        h.engine.setEnabled(P, saved.id, false)
        assertTrue(h.armed().isEmpty())
        h.engine.setEnabled(P, saved.id, true)
        assertEquals(4, h.armed().single().revision)
        h.engine.delete(P, saved.id)
        assertTrue(h.armed().isEmpty())
        assertTrue(h.engine.state(P).value.schedules.isEmpty())
    }

    @Test
    fun `B-STATE-089 an unsupported platform and a cross-profile save are refused with a code`() = runTest {
        val saved = h.saved()
        val foreign = saved.copy(profileId = "p2")
        assertIs<Outcome.Failed>(h.engine.save(P, h.draft(), editing = foreign))
        assertEquals(ScheduleCode.Unsupported, h.engine.state(P).value.error)
        h.alarms.supported = false
        assertIs<Outcome.Failed>(h.engine.save(P, h.draft()))
        assertEquals(ScheduleCode.Unsupported, h.engine.state(P).value.error)
        assertEquals(1, h.engine.store.load(P).size)
    }

    @Test
    fun `B-STATE-089 a once time already past is refused, not armed`() = runTest {
        assertIs<Outcome.Failed>(h.engine.save(P, h.draft(Recurrence.once(T0 - MIN))))
        assertEquals(ScheduleCode.ScheduleExpired, h.engine.state(P).value.error)
        assertTrue(h.armed().isEmpty())
    }

    @Test
    fun `B-AGENT-215 an accepted run records a normal conversation and advances once`() = runTest {
        h.saved()
        val report = fire()!!
        assertEquals("ran", report.status)
        assertEquals("Done: three files changed", report.summary)
        assertEquals("Nightly check", report.title)
        assertEquals(1, h.gateway.prompts.size)
        val run = h.engine.store.history(P).single()
        assertEquals(RunStatus.Ran, run.status)
        assertEquals("s1", run.sessionId)
        assertNull(run.summary) // the reply line is never persisted
        assertEquals(T0 + 25 * HOUR, h.armed().single().dueAtMs)
        assertEquals(listOf("started ${ArmedEntry(P, "s1", 1, T0 + HOUR).workName}", "finished ${ArmedEntry(P, "s1", 1, T0 + HOUR).workName}"), h.notices.events)
    }

    @Test
    fun `B-AGENT-215 a duplicate trigger for a finished occurrence is never dispatched twice`() = runTest {
        h.saved()
        val entry = h.armed().first()
        fire(entry)
        // The OS delivers the same occurrence again (retry, double alarm, restored work).
        h.clock.now = entry.dueAtMs + MIN
        assertNull(h.engine.runOccurrence(entry, far))
        assertEquals(1, h.gateway.prompts.size)
        assertEquals(1, h.engine.store.history(P).size)
    }

    @Test
    fun `B-AGENT-215 future or stale revision triggers never send`() = runTest {
        val saved = h.saved()
        val entry = h.armed().first()
        h.clock.now = entry.dueAtMs - MIN // the clock moved back
        assertNull(h.engine.runOccurrence(entry, far))
        h.engine.save(P, h.draft(Recurrence.daily(9, 30)), editing = saved)
        h.clock.now = entry.dueAtMs
        assertNull(h.engine.runOccurrence(entry, far)) // revision 1 is no longer armed
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `B-AGENT-215 a missed occurrence with skip-missed advances without running`() = runTest {
        h.engine.save(P, h.draft().copy(missedPolicy = MissedRunPolicy.Skip))
        val entry = h.armed().first()
        h.clock.now = entry.dueAtMs + 2 * HOUR
        val report = h.engine.runOccurrence(entry, far)!!
        assertEquals("skipped", report.status)
        assertEquals("missed", report.errorCode)
        assertTrue(h.gateway.prompts.isEmpty())
        assertEquals(T0 + 25 * HOUR, h.armed().single().dueAtMs)
    }

    @Test
    fun `B-STATE-090 an interrupted attempt is recovered as interrupted once, never resent, and advances once`() = runTest {
        h.saved()
        val entry = h.armed().first()
        h.clock.now = entry.dueAtMs
        // The first attempt claimed and wrote its in-flight record, then the process died.
        assertEquals(Claim.New, h.engine.claims.claim(entry))
        h.engine.store.recordRun(P, ScheduledRun(entry.occurrence, entry.scheduleId, entry.dueAtMs, entry.dueAtMs, RunStatus.Running, sessionId = "ses_9"))
        val report = h.engine.runOccurrence(entry, far)!!
        assertEquals("failed", report.status)
        assertEquals("interrupted", report.errorCode)
        assertEquals("ses_9", report.sessionId) // the conversation it reached is kept
        assertTrue(h.gateway.prompts.isEmpty())
        assertEquals(RunStatus.Failed, h.engine.store.history(P).single().status)
        assertEquals(T0 + 25 * HOUR, h.armed().single().dueAtMs)
        assertNull(h.engine.runOccurrence(entry, far))
    }

    @Test
    fun `B-STATE-090 an old occurrence cannot advance an edited schedule or recreate a deleted one`() = runTest {
        val saved = h.saved()
        val entry = h.armed().first()
        h.engine.delete(P, saved.id)
        h.clock.now = entry.dueAtMs
        assertNull(h.engine.runOccurrence(entry, far))
        assertTrue(h.engine.store.load(P).isEmpty())
        assertTrue(h.engine.store.history(P).isEmpty())
    }

    @Test
    fun `B-AGENT-216 a new conversation is created once and an ambiguous delivery is never retried`() = runTest {
        h.saved()
        h.gateway.base.admission = PromptAdmission.Uncertain
        val report = fire()!!
        assertEquals("failed", report.status)
        assertEquals("uncertain_delivery", report.errorCode)
        assertNull(report.summary) // no response text
        assertEquals(1, h.gateway.calls.count { it == "createSession" })
        assertEquals(1, h.gateway.prompts.size)
        assertNull(h.engine.runOccurrence(h.armed().first().copy(dueAtMs = T0 + HOUR), far))
        assertEquals(1, h.gateway.prompts.size)
    }

    @Test
    fun `B-AGENT-216 a conversation in another project is refused`() = runTest {
        h.gateway.base.sessionList = persistentListOf(Session(SessionId("ses_x"), directory = "/elsewhere"))
        h.saved(sessionId = "ses_x")
        val report = fire()!!
        assertEquals("skipped", report.status)
        assertEquals("wrong_project", report.errorCode)
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `B-AGENT-216 a busy conversation is skipped, never steered`() = runTest {
        h.gateway.base.sessionList = persistentListOf(Session(SessionId("ses_x"), directory = PROJECT))
        h.gateway.base.statuses = persistentMapOf(SessionId("ses_x") to SessionStatus.Busy)
        h.saved(sessionId = "ses_x")
        val report = fire()!!
        assertEquals("busy", report.errorCode)
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `B-AGENT-217 the captured model and agent ride with the prompt`() = runTest {
        h.engine.save(P, h.draft().copy(agent = "build", modelProviderId = "anthropic", modelId = "claude"))
        fire()
        val sent = h.gateway.prompts.single().second
        assertEquals("build", sent.agent)
        assertEquals("anthropic", sent.model!!.model.providerId)
        assertEquals("claude", sent.model!!.model.modelId)
    }

    @Test
    fun `B-AGENT-218 a changed server refuses before any transport`() = runTest {
        h.saved()
        h.profiles.fingerprints[P] = "n1:server-b"
        val report = fire()!!
        assertEquals("skipped", report.status)
        assertEquals("stale_target", report.errorCode)
        assertEquals(0, h.profiles.opens)
    }

    @Test
    fun `B-SEC-152 a sign-in failure is its own code and opens nothing more`() = runTest {
        h.saved()
        h.profiles.openFailure = Failure.SignIn(io.github.eslamasabry.oc.core.common.SignInWhy.entries.first())
        val report = fire()!!
        assertEquals("failed", report.status)
        assertEquals("auth_required", report.errorCode)
    }

    @Test
    fun `run now uses the same runner and keeps the next scheduled time`() = runTest {
        val saved = h.saved()
        h.engine.runNow(P, saved.id)
        assertEquals(listOf(Triple(P, saved.id, 1)), h.alarms.runNows)
        h.clock.now = T0 + 5 * MIN
        val report = h.engine.runManual(P, saved.id, 1, far)!!
        assertEquals("ran", report.status)
        assertEquals(T0 + HOUR, h.engine.store.load(P).single().nextRunAtMs)
        assertEquals(1, h.engine.store.history(P).size)
    }

    @Test
    fun `B-STATE-091 the morning digest counts only completed runs in its window and claims once`() = runTest {
        h.saved()
        fire()
        h.engine.setDigest(P, ScheduleDigest(enabled = true, hour = 8))
        val digestEntry = h.armed().first { it.digest }
        assertEquals(T0 + 24 * HOUR, digestEntry.dueAtMs)
        h.clock.now = digestEntry.dueAtMs
        val report = h.engine.runOccurrence(digestEntry, far)!!
        assertEquals("digest", report.status)
        assertEquals(1, report.ran)
        assertNull(h.engine.runOccurrence(digestEntry, far))
    }

    @Test
    fun `B-MIGR-013 Flutter-era schedules keep firing after the update`() = runTest {
        val legacy = prompt(next = T0 + HOUR).copy(profileFingerprint = "ab".repeat(32))
        h.backend.values["oc.scheduledPrompts.p1"] = ScheduleCodec.encodeEnvelope(ScheduleCodec.Envelope(listOf(legacy)))
        h.engine.restoreAll()
        assertEquals("n1:server-a", h.engine.store.load(P).single().profileFingerprint)
        assertEquals(1, h.engine.store.load(P).single().revision) // claims and identity unchanged
        assertEquals(listOf(ArmedEntry(P, "a1", 1, T0 + HOUR)), h.armed())
        assertEquals("ran", fire()!!.status)
    }

    @Test
    fun `B-MIGR-007 a removed profile cancels what is armed`() = runTest {
        h.saved()
        h.engine.forgetProfile(P)
        assertEquals(listOf(P), h.alarms.cancelled)
    }
}

class ScheduledOpenTest {
    private val h = Harness()

    @Test
    fun `B-NAV-054 a run notification opens its conversation if reached, else the history`() = runTest {
        val saved = h.saved()
        h.clock.now = T0 + HOUR
        h.engine.runOccurrence(h.armed().first(), Long.MAX_VALUE / 2)
        assertEquals(ScheduledOpen.Conversation(P, saved.id, "s1"), h.engine.resolveOpen(P, saved.id, "s1"))
        assertEquals(ScheduledOpen.History(P, saved.id), h.engine.resolveOpen(P, saved.id, ""))
        assertEquals(ScheduledOpen.History(P, saved.id), h.engine.resolveOpen(P, saved.id, "ses_unknown"))
    }

    @Test
    fun `B-NAV-054 the morning summary opens history, a deleted schedule or changed server opens nothing`() = runTest {
        val saved = h.saved()
        assertEquals(ScheduledOpen.History(P, null), h.engine.resolveOpen(P, ScheduleIds.DIGEST, null))
        h.profiles.fingerprints[P] = "n1:server-b"
        assertNull(h.engine.resolveOpen(P, saved.id, null))
        h.profiles.fingerprints[P] = "n1:server-a"
        h.engine.delete(P, saved.id)
        assertNull(h.engine.resolveOpen(P, saved.id, null))
    }
}
