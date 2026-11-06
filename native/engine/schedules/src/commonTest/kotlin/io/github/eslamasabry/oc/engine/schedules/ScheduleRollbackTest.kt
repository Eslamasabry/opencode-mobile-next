package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val LEGACY = "ab".repeat(32)
private const val ALIAS_KEY = "oc.nativeScheduleFingerprint.p1"

/** Flutter 2.1.4 wrote this schedule (lib/scheduling/models.dart toJson, stamped with the Flutter fingerprint). */
private val FLUTTER_ENVELOPE =
    """{"schemaVersion":1,"schedules":[{"id":"a1","profileId":"p1","profileFingerprint":"$LEGACY",""" +
        """"prompt":"Check the nightly build","projectPath":"/work/app","workspace":null,"sessionId":null,"agent":null,""" +
        """"modelProviderId":null,"modelId":null,"recurrence":{"kind":"daily","at":null,"hour":9,"minute":0,"hours":1},""" +
        """"missedPolicy":"catchUp","enabled":true,"nextRunAt":"2026-11-02T09:00:00.000Z","createdAt":"2026-11-02T08:00:00.000Z",""" +
        """"revision":1}],"runs":[],"digest":{"enabled":false,"hour":8,"minute":0,"lastDeliveredAt":null}}"""

/** Flutter's armed index for it (ScheduledPromptStore.replace, org.json key order). */
private const val FLUTTER_INDEX = """[{"id":"a1","revision":1,"dueAtMs":1793610000000}]"""

private fun Harness.flutterInstall() {
    backend.values["oc.scheduledPrompts.p1"] = FLUTTER_ENVELOPE
    backend.values["oc.scheduleIndex.p1"] = FLUTTER_INDEX
}

/** E00-S04: while the Flutter build can still be restored, native keeps every shared key readable by it. */
class ScheduleRollbackTest {
    private val h = Harness().apply { flutterReadable = true }
    private val far = Long.MAX_VALUE / 2

    private suspend fun fire(entry: ArmedEntry): RunReport? {
        h.clock.now = entry.dueAtMs
        return h.engine.runOccurrence(entry, far)
    }

    @Test
    fun `B-MIGR-013 while Flutter can be restored the Flutter stamp is kept and native records its fingerprint apart`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        assertEquals(FLUTTER_ENVELOPE, h.backend.values["oc.scheduledPrompts.p1"]) // not rewritten
        assertEquals(LEGACY, h.engine.store.load(P).single().profileFingerprint)
        val alias = assertNotNull(h.backend.values[ALIAS_KEY])
        assertTrue("n1:server-a" in alias && LEGACY in alias)
        assertEquals(listOf(ArmedEntry(P, "a1", 1, T0 + HOUR)), h.armed())
        assertEquals("ran", fire(h.armed().single())!!.status)
        assertEquals(LEGACY, h.engine.store.load(P).single().profileFingerprint)
        assertEquals(1, h.gateway.prompts.size)
    }

    @Test
    fun `B-AGENT-218 during the trial a Flutter schedule made for another server stays refused`() = runTest {
        h.flutterInstall()
        h.profiles.legacyFingerprints[P] = "cd".repeat(32) // the profile now names server B
        h.engine.restoreAll()
        assertNull(h.backend.values[ALIAS_KEY])
        val report = fire(ArmedEntry(P, "a1", 1, T0 + HOUR))
        assertTrue(report == null || report.status != "ran")
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `B-AGENT-218 an accepted Flutter stamp stops running when native edits the server identity`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        h.profiles.fingerprints[P] = "n1:server-b" // the address or account changed on native
        val report = fire(ArmedEntry(P, "a1", 1, T0 + HOUR))
        assertTrue(report == null || report.status != "ran")
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `B-MIGR-013 once Flutter can no longer be restored the schedules are re-stamped and the alias removed`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        h.profiles.legacyFingerprints[P] = "ef".repeat(32) // a later non-identity edit changed the Dart JSON
        h.flutterReadable = false
        h.engine.restoreAll()
        val schedule = h.engine.store.load(P).single()
        assertEquals("n1:server-a", schedule.profileFingerprint)
        assertEquals(1, schedule.revision)
        assertNull(h.backend.values[ALIAS_KEY])
        assertEquals("ran", fire(h.armed().single())!!.status)
    }

    @Test
    fun `B-MIGR-004 a schedule made on native during the trial carries the Flutter stamp and still runs on native`() = runTest {
        val saved = h.saved()
        assertEquals(LEGACY, saved.profileFingerprint)
        assertNotNull(h.backend.values[ALIAS_KEY])
        assertEquals("ran", fire(h.armed().single())!!.status)
    }

    @Test
    fun `B-NAV-054 a run notification of a Flutter-stamped schedule still opens during the trial`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        assertIs<ScheduledOpen.History>(h.engine.resolveOpen(P, "a1", null))
        h.profiles.fingerprints[P] = "n1:server-b"
        assertNull(h.engine.resolveOpen(P, "a1", null))
    }
}

/**
 * E00-S04: every key native writes into `FlutterSharedPreferences.xml` decodes with the Flutter 2.1.4 readers
 * ([Flutter214Schedules]); the only other key is native-only and never read by Flutter.
 */
class FlutterFormatCompatTest {
    private val h = Harness().apply { flutterReadable = true }
    private val far = Long.MAX_VALUE / 2

    private fun assertFlutterReadsEverything() {
        val nativeOnly = mutableListOf<String>()
        for ((key, value) in h.backend.values) {
            if (Flutter214Schedules.FLUTTER_PREFIXES.none { key.startsWith(it) }) {
                nativeOnly += key
                continue
            }
            try {
                Flutter214Schedules.read(key, value)
            } catch (e: Flutter214Schedules.Refused) {
                fail("Flutter 2.1.4 refuses $key: ${e.message}")
            }
        }
        assertTrue(nativeOnly.all { it.startsWith(ScheduleKeys.NATIVE_FINGERPRINT_PREFIX) }, "unexpected keys $nativeOnly")
    }

    @Test
    fun `B-MIGR-007 the Flutter vectors accept what native writes for saves, runs, failures, run now, digest and drafts`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        val saved = (h.engine.save(P, h.draft(Recurrence.weekdays(10, 15))) as Outcome.Ok).value
        h.engine.setPromptDraft(P, saved.id, "Half typed")
        h.engine.setPromptDraft(P, null, "A new one")
        h.engine.setDigest(P, ScheduleDigest(enabled = true, hour = 8, minute = 30))
        h.clock.now = T0 + HOUR
        assertEquals("ran", h.engine.runOccurrence(ArmedEntry(P, "a1", 1, T0 + HOUR), far)!!.status)
        h.profiles.openFailure = Failure.TimedOut()
        val failing = h.armed().first { it.scheduleId == saved.id }
        h.clock.now = failing.dueAtMs
        assertEquals("failed", h.engine.runOccurrence(failing, far)!!.status)
        h.profiles.openFailure = null
        h.engine.runManual(P, saved.id, saved.revision, far)
        h.engine.setEnabled(P, saved.id, false)
        val digest = h.armed().first { it.digest }
        h.clock.now = digest.dueAtMs
        assertNotNull(h.engine.runOccurrence(digest, far))
        assertFlutterReadsEverything()
        val envelope = Flutter214Schedules.envelope(h.backend.values.getValue("oc.scheduledPrompts.p1"), P)
        // Both schedules carry the stamp Flutter's runner compares with scheduledProfileFingerprint(profile).
        assertTrue(envelope.schedules.all { it.profileFingerprint == LEGACY })
        assertTrue(envelope.runs.any { it.status == "failed" && it.errorCode != null })
    }

    @Test
    fun `B-MIGR-004 a schedule written by Flutter runs on native and the Flutter build still accepts it afterwards`() = runTest {
        h.flutterInstall()
        h.engine.restoreAll()
        h.clock.now = T0 + HOUR
        assertEquals("ran", h.engine.runOccurrence(ArmedEntry(P, "a1", 1, T0 + HOUR), far)!!.status)
        assertFlutterReadsEverything()

        // Back on Flutter: the schedule is valid, still its own, advanced past the occurrence native sent…
        val schedule = Flutter214Schedules.envelope(h.backend.values.getValue("oc.scheduledPrompts.p1"), P).schedules.single()
        assertEquals(LEGACY, schedule.profileFingerprint)
        assertEquals(true, schedule.enabled)
        assertEquals(1L, schedule.revision)
        assertEquals("2026-11-03T09:00:00.000Z", schedule.nextRunAt)
        // …the sent occurrence is a finished claim, so Flutter never sends it again…
        val claims = Flutter214Schedules.claims(h.backend.values.getValue("oc.scheduleClaims.p1"))
        assertEquals(true, claims.getValue("a1:1:1793610000000").finished)
        // …and the next occurrence is armed in the index Flutter reads.
        val next = Flutter214Schedules.index(h.backend.values.getValue("oc.scheduleIndex.p1")).single()
        assertEquals(Flutter214Schedules.IndexEntry("a1", 1, T0 + HOUR + 86_400_000L), next)
    }

    @Test
    fun `B-MIGR-004 the Flutter vectors refuse a re-stamped schedule, which is why native must not write one`() {
        // Reverse safety: the old migration's output (an n1: stamp) decodes, but Flutter's runner compares the stamp
        // with scheduledProfileFingerprint(profile) and refuses it as stale_target. Pin that the vector sees the stamp.
        val restamped = FLUTTER_ENVELOPE.replace(LEGACY, "n1:server-a")
        val schedule = Flutter214Schedules.envelope(restamped, P).schedules.single()
        assertTrue(schedule.profileFingerprint != LEGACY)
    }
}
