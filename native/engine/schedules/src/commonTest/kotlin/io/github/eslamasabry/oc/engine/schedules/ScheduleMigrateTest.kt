package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A schedule made in the old app (2.1.5) carries SHA-256 of that app's saved-profile JSON, which includes the server
 * version; the phone's own server is refreshed on update, so the version has moved by the time native looks.
 */
class ScheduleMigrateTest {
    private val far = Long.MAX_VALUE / 2

    /** The row exactly as 2.1.5's `toJson()` wrote it (without `name`) when the schedule was saved. */
    private fun oldRowText(address: String = "http://127.0.0.1:4097", version: String? = "2.2.3") =
        """{"id":"p1","baseUrl":"$address","backend":"openCode","username":"opencode","flavor":"v2",""" +
            """"requiresOpenCode2Migration":false,"openCode2HistoryPending":false""" +
            (if (version != null) ""","serverVersion":"$version"""" else "") + "}"

    @Suppress("UNCHECKED_CAST")
    private fun row(text: String) = DartJson.decode(text) as Map<String, Any?>

    private suspend fun Harness.migrated(
        stampText: String,
        nowRowText: String,
        readable: Boolean,
    ) {
        flutterReadable = readable
        val stamp = DartJson.sha256Hex(stampText) // what 2.1.5 stored
        val now = row(nowRowText)
        profiles.legacyRows[P] = now
        profiles.legacyFingerprints[P] = DartJson.sha256Hex(DartJson.encode(now))
        val schedule = prompt(next = T0 + HOUR).copy(profileFingerprint = stamp)
        backend.values["oc.scheduledPrompts.p1"] = ScheduleCodec.encodeEnvelope(ScheduleCodec.Envelope(listOf(schedule)))
    }

    private suspend fun Harness.fire(): RunReport? {
        clock.now = T0 + HOUR
        return engine.runOccurrence(ArmedEntry(P, "a1", 1, T0 + HOUR), far)
    }

    @Test
    fun `B-MIGR-013 a 2_1_5 schedule runs after the update moved the server version, as restored`() = runTest {
        for (readable in listOf(true, false)) {
            val h = Harness()
            h.migrated(oldRowText(), oldRowText(version = "2.2.6"), readable)
            h.engine.restoreAll()
            assertEquals("ran", h.fire()!!.status, "readable=$readable")
            assertEquals(1, h.gateway.prompts.size)
        }
    }

    @Test
    fun `B-MIGR-013 a 2_1_5 schedule runs from the worker even when nothing migrated it first`() = runTest {
        val h = Harness()
        h.migrated(oldRowText(), oldRowText(version = "2.2.6"), readable = true)
        // Armed while the profile's row could not be read (nothing could be migrated), then the worker fires.
        val row = h.profiles.legacyRows.remove(P)!!
        h.engine.restoreAll()
        h.profiles.legacyRows[P] = row
        assertEquals("ran", h.fire()!!.status)
    }

    @Test
    fun `B-MIGR-013 a 2_1_5 schedule saved before the server reported a version still runs`() = runTest {
        val h = Harness()
        h.migrated(oldRowText(version = null), oldRowText(version = "2.2.6"), readable = false)
        h.engine.restoreAll()
        assertEquals("ran", h.fire()!!.status)
    }

    @Test
    fun `B-AGENT-218 a 2_1_5 schedule whose server address changed is still skipped`() = runTest {
        for (readable in listOf(true, false)) {
            val h = Harness()
            h.migrated(oldRowText(), oldRowText(address = "http://10.0.2.2:4157", version = "2.2.6"), readable)
            h.engine.restoreAll()
            val report = h.fire()
            assertTrue(report == null || report.status == "skipped", "readable=$readable")
            assertEquals("stale_target", report?.errorCode)
            assertTrue(h.gateway.prompts.isEmpty())
        }
    }

    @Test
    fun `B-AGENT-218 a 2_1_5 schedule whose server was deleted never runs`() = runTest {
        val h = Harness()
        h.migrated(oldRowText(), oldRowText(version = "2.2.6"), readable = true)
        h.profiles.fingerprints.remove(P)
        h.profiles.legacyRows.remove(P)
        assertNull(h.fire())
        assertTrue(h.gateway.prompts.isEmpty())
    }

    @Test
    fun `a stamp that matches no version of the row is not recognised`() {
        val stamp = DartJson.sha256Hex(oldRowText(address = "http://other.example"))
        assertFalse(LegacyFingerprints.matches(row(oldRowText(version = "2.2.6")), stamp))
        assertTrue(LegacyFingerprints.matches(row(oldRowText(version = "2.2.6")), DartJson.sha256Hex(oldRowText(version = "2.2.3"))))
    }
}
