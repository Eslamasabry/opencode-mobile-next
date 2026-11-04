package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import kotlinx.collections.immutable.ImmutableList
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReportProblemTest {
    private val at = Instant.parse("2026-09-27T12:00:00Z")
    private val clock = TestClock(at)
    private val redactor = Redactor()
    private val files = FakeReportFiles()

    private fun open(maxEntries: Int = 128, maxBytes: Int = 262_144): ReportProblem =
        assertNotNull(ReportProblem.open(files, redactor, clock, maxEntries, maxBytes).getOrNull())

    private fun snapshot() = files.text(ReportFile.Snapshot)

    private fun pending() = files.text(ReportFile.Pending)

    private fun span(id: Long, name: String, durationMicros: Long = 1_000, wall: Instant = at) =
        PerfSpan(id = id, name = name, startMicros = id, durationMicros = durationMicros, wallStart = wall)

    @Test
    fun `B-STATE-075 all event kinds survive restart without a close or flush`() {
        val service = open()
        service.recordError("failed request", "test frame", source = "gateway", at = at)
        service.recordTiming(span(1, "request", durationMicros = 12_500, wall = at + 1.seconds))
        service.recordAndroidExit(ExitRecord(reason = ExitReason.CRASH, timestampMs = (at + 2.seconds).toEpochMilliseconds()))
        service.recordThermal("severe", 0.9, at = at + 3.seconds)

        assertNotNull(snapshot())
        assertNull(pending())
        val restarted = open()
        assertEquals(
            listOf(ProblemEventKind.Error, ProblemEventKind.Timing, ProblemEventKind.AndroidExit, ProblemEventKind.Thermal),
            restarted.entries.map { it.kind },
        )
        assertEquals(service.entries.map { it.toJson() }, restarted.entries.map { it.toJson() })
        assertEquals(at, restarted.entries.first().at)
        assertEquals("gateway", restarted.entries.first().source)
        assertContains(restarted.entries.first().stack, "test frame")
        assertContains(restarted.entries[1].message, "OCTRACE")
        assertContains(restarted.entries[1].message, "13ms")
        assertContains(restarted.entries[2].message, "Crash")
        assertContains(restarted.entries[2].message, "reason=4")
        assertContains(restarted.entries[3].message, "severe")
        assertFalse(restarted.storageFailed)
    }

    @Test
    fun `B-STATE-075 restart keeps the newest within the count limit and a failure outlives later timings`() {
        val service = open(maxEntries = 3)
        for (i in 0 until 8) service.recordError("failure $i", at = at + i.seconds)
        assertEquals(listOf("failure 5", "failure 6", "failure 7"), service.entries.map { it.message })
        assertEquals(listOf("failure 5", "failure 6", "failure 7"), open(maxEntries = 3).entries.map { it.message })

        files.files.clear()
        val small = open(maxEntries = 4)
        small.recordError("chat.start failed", source = "chat.start", at = at)
        for (i in 0 until 20) small.recordTiming(span(i.toLong(), "linux.status $i", wall = at + i.seconds))
        assertEquals(4, small.entries.size)
        assertEquals("chat.start failed", small.entries.first().message)
        assertContains(small.entries.last().message, "linux.status 19")
        assertEquals("chat.start failed", open(maxEntries = 4).entries.first().message)
    }

    @Test
    fun `B-STATE-075 the snapshot stays within the UTF-8 byte cap and a too-large event is dropped`() {
        val service = open(maxBytes = 1_024)
        for (i in 0 until 12) {
            service.recordError("$i ${"🔥".repeat(70)}", at = at)
            assertTrue(files.files.getValue(ReportFile.Snapshot).size <= 1_024)
        }
        assertTrue(service.entries.isNotEmpty())
        assertTrue(service.entries.size < 12)
        assertTrue(service.entries.last().message.startsWith("11 "))
        assertEquals(service.entries.map { it.toJson() }, open(maxBytes = 1_024).entries.map { it.toJson() })

        files.files.clear()
        val fresh = open(maxBytes = 1_024)
        fresh.recordError("small")
        fresh.recordError("🔥".repeat(1_000), "frame".repeat(3_000))
        assertTrue(files.files.getValue(ReportFile.Snapshot).size <= 1_024)
        assertTrue(fresh.entries.isEmpty())
        assertTrue(open(maxBytes = 1_024).entries.isEmpty())
    }

    @Test
    fun `B-STATE-072 clear erases memory, the committed snapshot and a pending write across restart`() {
        val service = open()
        service.recordError("old failure")
        files.put(ReportFile.Pending, assertNotNull(snapshot()))
        assertEquals(Outcome.Ok(Unit), service.clear())
        assertTrue(service.entries.isEmpty())
        assertFalse(service.storageFailed)
        assertNull(snapshot())
        assertNull(pending())
        assertTrue(open().entries.isEmpty())
    }

    @Test
    fun `B-STATE-080 consumers are notified of committed changes and entries are read-only`() {
        val service = open()
        val start = service.state.value.committed
        service.recordError("first failure")
        assertEquals(start + 1, service.state.value.committed)
        val entries = service.entries
        assertIs<ImmutableList<ProblemEvent>>(entries)
        assertFails { @Suppress("UNCHECKED_CAST") (entries as MutableList<ProblemEvent>).clear() }
        assertEquals("first failure", service.entries.single().message)
        service.clear()
        assertEquals(start + 2, service.state.value.committed)
        assertTrue(service.entries.isEmpty())
    }

    @Test
    fun `B-STATE-079 a corrupt or incompatible snapshot is discarded and new events work`() {
        for (malformed in listOf("{", "[]", """{"version":-1,"entries":[]}""", """{"version":1,"entries":"invalid"}""", """{"version":"1","entries":[]}""")) {
            files.files.clear()
            files.put(ReportFile.Snapshot, malformed)
            val service = open()
            assertTrue(service.entries.isEmpty(), malformed)
            assertNull(snapshot(), malformed)
            service.recordError("recovered")
            assertEquals("recovered", open().entries.single().message, malformed)
        }
    }

    @Test
    fun `B-STATE-079 an invalid persisted event kind is rejected`() {
        open().recordError("failure")
        val raw = assertNotNull(snapshot())
        assertContains(raw, "\"kind\":\"error\"")
        files.put(ReportFile.Snapshot, raw.replaceFirst("\"kind\":\"error\"", "\"kind\":\"not-a-kind\""))
        assertTrue(open().entries.isEmpty())
    }

    @Test
    fun `B-STATE-079 an oversized snapshot is discarded before recovery and new events are accepted`() {
        files.put(ReportFile.Snapshot, " ".repeat(2_048))
        val service = open(maxBytes = 1_024)
        assertTrue(service.entries.isEmpty())
        service.recordError("fresh")
        assertTrue(files.files.getValue(ReportFile.Snapshot).size <= 1_024)
        assertEquals("fresh", open(maxBytes = 1_024).entries.single().message)
    }

    @Test
    fun `B-STATE-079 the committed snapshot wins over a pending write, which is removed`() {
        val service = open()
        service.recordError("committed")
        val committed = assertNotNull(snapshot())
        service.recordError("uncommitted")
        files.put(ReportFile.Pending, assertNotNull(snapshot()))
        files.put(ReportFile.Snapshot, committed)
        val restarted = open()
        assertEquals("committed", restarted.entries.single().message)
        assertNull(pending())
    }

    @Test
    fun `B-STATE-079 an orphan pending snapshot is not resurrected`() {
        open().recordError("uncommitted")
        files.files[ReportFile.Pending] = files.files.remove(ReportFile.Snapshot)!!
        assertTrue(open().entries.isEmpty())
        assertNull(pending())
    }

    @Test
    fun `B-STATE-079 recovered events are redacted again with the secrets known now`() {
        val secret = "fakePreviouslyUnknownCredential"
        open().recordError(secret, secret, source = secret)
        redactor.registerSecret(secret)
        val restarted = open()
        assertEquals(1, restarted.entries.size)
        assertFalse(secret in restarted.reportText())
        assertFalse(secret in assertNotNull(snapshot()))
    }

    @Test
    fun `B-STATE-080 a failed write is reported without publishing the uncommitted event`() {
        val service = open()
        service.recordError("committed")
        val before = snapshot()
        val committed = service.state.value.committed
        files.refuseWrites = true
        val result = service.recordError("fakeFailedWritePayload")
        val failure = assertIs<Outcome.Failed>(result).failure
        assertIs<Failure.StorageRefused>(failure)
        assertNull(failure.details)
        assertFalse("fakeFailedWritePayload" in failure.toString())
        assertFalse("/data/" in failure.toString())
        assertTrue(service.storageFailed)
        assertEquals(committed, service.state.value.committed)
        assertEquals("committed", service.entries.single().message)
        assertEquals(before, snapshot())

        files.refuseWrites = false
        assertEquals(Outcome.Ok(Unit), service.recordError("retry succeeded"))
        assertFalse(service.storageFailed)
        assertEquals("retry succeeded", open().entries.last().message)
    }

    @Test
    fun `B-STATE-080 a failed deletion reports failure rather than claiming a successful clear`() {
        val service = open()
        service.recordError("retained until deleted")
        files.refuseDelete += ReportFile.Snapshot
        assertIs<Failure.StorageRefused>(assertIs<Outcome.Failed>(service.clear()).failure)
        assertTrue(service.storageFailed)
        assertTrue(service.entries.isNotEmpty())
        assertNotNull(snapshot())
        files.refuseDelete.clear()
        assertEquals(Outcome.Ok(Unit), service.clear())
        assertFalse(service.storageFailed)
        assertTrue(service.entries.isEmpty())
    }

    @Test
    fun `B-STATE-080 invalid entry and byte limits are refused`() {
        assertFailsWith<IllegalArgumentException> { ReportProblem.open(files, redactor, clock, maxEntries = 0) }
        assertFailsWith<IllegalArgumentException> { ReportProblem.open(files, redactor, clock, maxBytes = 1_023) }
    }

    @Test
    fun `B-STATE-075 every output and the disk are redacted at write time, also after a restart`() {
        val known = "fakeLoadedCredentialForReportTests"
        val provider = "sk-proj-fakeReportProviderKey987654321"
        val bearer = "fakeBearerValueForReportTests"
        val password = "fakePasswordForReportTests"
        val text = "$known $provider\nBearer $bearer\npassword=$password"
        redactor.registerSecret(known)
        val service = open()
        service.recordError(text, text, source = text, at = at)
        service.recordTiming(span(1, known).copy(parent = provider))
        val outputs =
            listOf(
                service.entries.joinToString { it.toJson().toString() },
                service.reportText(),
                service.reportJson().toString(),
                service.notificationText(text),
                service.logText(text),
                assertNotNull(snapshot()),
            )
        for (output in outputs) {
            for (secret in listOf(known, provider, bearer, password)) assertFalse(secret in output, "leaked into: $output")
            assertContains(output, Redactor.MASK)
        }
        redactor.forget(known)
        val restarted = open()
        assertEquals(2, restarted.entries.size)
        assertFalse(known in restarted.reportText())
        assertFalse(known in assertNotNull(snapshot()))
        assertTrue(service.notificationText("x".repeat(1_000)).length <= 300)
    }
}
