package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** B-332: the copied error log says which build it came from, and a fault repeated close together is one entry. */
class DiagnosticsFoldTest {
    private val clock = TestClock()
    private val start = Instant.parse("2026-11-14T09:00:00Z")

    private fun entries(report: JsonObject) = (report["entries"] as JsonArray).map { it as JsonObject }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.content

    @Test
    fun `B-332 one fault repeated 18 times in 7 s between two others is one entry with its count`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        // The field report: 18 of 20 entries were one fault in 7 s, with two other lines in between.
        for (i in 0 until 18) {
            diagnostics.record("chat.stream", "Network · Reset", at = start + (i * 400).milliseconds)
            if (i == 5) diagnostics.record("connection", "connection lost after 3 tries", at = start + 2_100.milliseconds)
            if (i == 11) diagnostics.record("composer", "Refused · ConsentMissing", at = start + 4_500.milliseconds)
        }
        val kept = diagnostics.errors.value
        assertEquals(3, kept.size)
        val fault = kept.single { it.source == "chat.stream" }
        assertEquals(18, fault.occurrences)
        assertEquals(start, fault.firstAt)
        assertEquals(start + (17 * 400).milliseconds, fault.at)
        assertEquals(1, kept.single { it.source == "connection" }.occurrences)
        assertEquals(1, kept.single { it.source == "composer" }.occurrences)
    }

    @Test
    fun `B-332 different faults never fold, whatever their timing`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        diagnostics.record("chat", "failed", "at a (A.kt:1)", at = start)
        diagnostics.record("files", "failed", "at a (A.kt:1)", at = start)
        diagnostics.record("chat", "failed again", "at a (A.kt:1)", at = start)
        diagnostics.record("chat", "failed", "at b (B.kt:2)", at = start)
        diagnostics.record("chat", "failed", at = start)
        assertEquals(5, diagnostics.errors.value.size)
        assertTrue(diagnostics.errors.value.all { it.occurrences == 1 })
    }

    @Test
    fun `B-332 a repeat folds while it keeps coming within the window of its last time, then starts a new entry`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        diagnostics.record("chat", "failed", at = start)
        diagnostics.record("other", "between", at = start + 5.seconds)
        diagnostics.record("chat", "failed", at = start + 8.seconds)
        diagnostics.record("chat", "failed", at = start + 16.seconds) // 8 s after its last time
        diagnostics.record("chat", "failed", at = start + 27.seconds) // 11 s after: a new burst
        val kept = diagnostics.errors.value
        assertEquals(listOf("between", "failed", "failed"), kept.map { it.message })
        assertEquals(3, kept[1].occurrences)
        assertEquals(start, kept[1].firstAt)
        assertEquals(start + 16.seconds, kept[1].at)
        assertEquals(1, kept[2].occurrences)
        assertEquals(start + 27.seconds, kept[2].firstAt)
    }

    @Test
    fun `B-332 the newest entries are kept at the limit and a fault that repeated counts as new`() {
        val diagnostics = Diagnostics(Redactor(), clock, maxEntries = 3)
        diagnostics.record("x", "A", at = start)
        diagnostics.record("x", "B", at = start + 1.seconds)
        diagnostics.record("x", "C", at = start + 2.seconds)
        diagnostics.record("x", "A", at = start + 3.seconds) // folds; A is now the newest but one
        diagnostics.record("x", "D", at = start + 4.seconds) // the limit cuts B, the oldest
        val kept = diagnostics.errors.value
        assertEquals(listOf("C", "A", "D"), kept.map { it.message })
        assertEquals(2, kept[1].occurrences)
        assertEquals(start, kept[1].firstAt)
        assertEquals(start + 3.seconds, kept[1].at)
    }

    @Test
    fun `B-332 the export gives a folded entry its count and its first and last time`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        diagnostics.record("chat", "failed", at = start)
        diagnostics.record("other", "once", at = start + 1.seconds)
        diagnostics.record("chat", "failed", at = start + 2.seconds)
        val list = entries(diagnostics.reportJson())
        assertEquals(2, list.size)
        val once = list.single { it.text("message") == "once" }
        assertNull(once["occurrences"])
        assertNull(once["firstTime"])
        val folded = list.single { it.text("message") == "failed" }
        assertEquals(2, folded.getValue("occurrences").jsonPrimitive.int)
        assertEquals(start.toString(), folded.text("firstTime"))
        assertEquals((start + 2.seconds).toString(), folded.text("lastTime"))
        assertEquals((start + 2.seconds).toString(), folded.text("time"))
    }

    @Test
    fun `B-332 the copied report says which build and Android it came from`() {
        val build =
            ReportBuild(
                versionName = "2.2.0",
                versionCode = 10042,
                installedAt = Instant.parse("2026-10-01T08:00:00Z"),
                updatedAt = Instant.parse("2026-11-14T07:30:00Z"),
                androidVersion = "16",
                apiLevel = 36,
            )
        val diagnostics = Diagnostics(Redactor(), clock, reportBuild = { build })
        diagnostics.record("chat", "failed", at = start)
        val report = diagnostics.reportJson()
        val facts = assertNotNull(report["build"] as? JsonObject)
        assertEquals("2.2.0", facts.text("versionName"))
        assertEquals(10042L, facts.getValue("versionCode").jsonPrimitive.long)
        assertEquals("2026-10-01T08:00:00Z", facts.text("installed"))
        assertEquals("2026-11-14T07:30:00Z", facts.text("updated"))
        assertEquals("16", facts.text("android"))
        assertEquals(36, facts.getValue("apiLevel").jsonPrimitive.int)
        // Only these facts: no device name, serial, account, address or path can ride along.
        assertEquals(setOf("versionName", "versionCode", "installed", "updated", "android", "apiLevel"), facts.keys)
        assertEquals(setOf("app", "privacy", "build", "entryCount", "entries"), report.keys)
        assertTrue("\"versionCode\": 10042" in diagnostics.reportText())
    }

    @Test
    fun `B-332 build text goes through the report's redaction and unknown facts are left out`() {
        // A long opaque value (32+ characters with a digit), built here so no token-like literal is committed.
        val secret = "fake" + "7".repeat(8) + "wxyz".repeat(6)
        val diagnostics =
            Diagnostics(
                Redactor(),
                clock,
                reportBuild = { ReportBuild(versionName = "2.2.0 token=$secret", versionCode = 7, androidVersion = "16 $secret") },
            )
        val text = diagnostics.reportText()
        assertFalse(secret in text)
        val facts = assertNotNull(diagnostics.reportJson()["build"] as? JsonObject)
        assertEquals(setOf("versionName", "versionCode", "android"), facts.keys)
    }

    @Test
    fun `B-332 a build reader that fails still leaves every entry in the report`() {
        val diagnostics = Diagnostics(Redactor(), clock, reportBuild = { throw IllegalStateException("package gone") })
        diagnostics.record("chat", "failed", at = start)
        val report = diagnostics.reportJson()
        assertNull(report["build"])
        assertEquals(1, entries(report).size)
        assertFalse("package gone" in diagnostics.reportText())
    }
}
