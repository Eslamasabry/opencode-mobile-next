package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.engine.analytics.ErrorReporter
import io.github.eslamasabry.oc.engine.analytics.NoOpReportSink
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryKeys
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** s23-continuity hooks (B-180, E02-S91): Flutter 2.1.5's answers carry over, the question is asked once, a crash is offered once. */
class TelemetryContinuityTest {
    private val now = Instant.parse("2026-11-01T10:15:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }

    private fun disk(vararg rows: Pair<String, RawPref>) =
        InMemoryPrefsBackend(rows.associate { (k, v) -> FlutterPrefsCodec.physicalKey(k) to v })

    @OptIn(MigrationOnly::class)
    private fun TestScope.answers(disk: InMemoryPrefsBackend) =
        TelemetryAnswers(
            PrefsKeyValueStore(
                disk,
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                StandardTestDispatcher(testScheduler),
            ),
            WriterLane("app"),
        )

    @Test
    fun `B-180-017 Flutter's yes carries over and nobody is asked again`() = runTest {
        val reporting = ErrorReporter(NoOpReportSink, initialConsent = false)
        val c = TelemetryContinuity(
            answers(disk(TelemetryKeys.CONSENT to RawPref.Bool(true), TelemetryKeys.CONSENT_ASKED to RawPref.Bool(true))),
            reporting, available = true, clock,
        )
        c.adopt(); runCurrent()
        assertTrue(reporting.reportingConsent.value)
        assertFalse(c.question.value)
    }

    @Test
    fun `B-180-018 a build that can send asks once, saves asked first, and the answer is the consent`() = runTest {
        val backend = disk()
        val reporting = ErrorReporter(NoOpReportSink, initialConsent = false)
        val c = TelemetryContinuity(answers(backend), reporting, available = true, clock)
        c.adopt(); runCurrent()
        assertTrue(c.question.value)
        assertEquals(RawPref.Bool(true), backend.persisted()[FlutterPrefsCodec.physicalKey(TelemetryKeys.CONSENT_ASKED)])
        c.answer(share = true)
        c.mirror(true); runCurrent()
        assertFalse(c.question.value)
        assertTrue(reporting.reportingConsent.value)
        assertEquals(RawPref.Bool(true), backend.persisted()[FlutterPrefsCodec.physicalKey(TelemetryKeys.CONSENT)])
        // A build with no vendor never asks.
        val quiet = TelemetryContinuity(answers(disk()), ErrorReporter(NoOpReportSink, false), available = false, clock)
        quiet.adopt(); runCurrent()
        assertFalse(quiet.question.value)
    }

    @Test
    fun `E02-S91-010 a fresh crash is offered once; Offer crash reports off marks it seen without showing`() = runTest {
        val crash = LaunchReport(exit = ExitRecord(reason = ExitReason.CRASH, timestampMs = now.toEpochMilliseconds() - 3_600_000))
        val c = TelemetryContinuity(answers(disk()), ErrorReporter(NoOpReportSink, false), available = false, clock)
        assertTrue(c.enabled(), "on unless turned off")
        c.considerCrash(crash); runCurrent()
        assertNotNull(c.offer.pending.value)
        assertNotNull(c.takeCrash())
        assertNull(c.offer.pending.value)

        val off = TelemetryContinuity(answers(disk()), ErrorReporter(NoOpReportSink, false), available = false, clock)
        assertTrue(off.setEnabled(false))
        off.considerCrash(crash); runCurrent()
        assertNull(off.offer.pending.value)
        assertEquals(crash.exit!!.timestampMs, off.lastOfferedMs())
    }
}
