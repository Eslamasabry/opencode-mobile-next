package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryKeys
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentKeys
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

/**
 * s23-continuity hooks and B-180: Flutter 2.1.5's answers carry over from its own keys in the adopted preferences, are
 * applied to the vendor, the question is asked once (Share both / Not now / Choose), and a crash is offered once.
 */
class TelemetryContinuityTest {
    private val now = Instant.parse("2026-11-01T10:15:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }

    private class RecordingVendor : TelemetryVendor {
        val collection = mutableListOf<TelemetryConsent>()
        val events = mutableListOf<String>()

        override fun setCollection(usage: Boolean, crashes: Boolean) {
            collection += TelemetryConsent(usage, crashes)
        }

        override fun crash(category: String, frames: List<String>, fatal: Boolean) = Unit

        override fun failure(name: String, keys: Map<String, String>) = Unit

        override fun breadcrumb(name: String) = Unit

        override fun event(name: String, props: Map<String, String>) {
            events += name
        }

        override fun setKeys(keys: Map<String, String>) = Unit

        override fun setUserProperties(properties: Map<String, String>) = Unit

        override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long) = Unit

        override suspend fun problemReport(text: String, collecting: Boolean) = Unit

        override suspend fun appInstanceId(): String? = null
    }

    private fun disk(vararg rows: Pair<String, RawPref>) =
        InMemoryPrefsBackend(rows.associate { (k, v) -> FlutterPrefsCodec.physicalKey(k) to v })

    @OptIn(MigrationOnly::class)
    private fun TestScope.store(disk: InMemoryPrefsBackend) =
        PrefsKeyValueStore(
            disk,
            FlutterPrefsCodec(JavaListDecoder.Unsupported),
            StateMigrationGate.alwaysOpen(),
            StandardTestDispatcher(testScheduler),
        )

    private inner class Rig(scope: TestScope, val backend: InMemoryPrefsBackend, available: Boolean = true) {
        val vendor = RecordingVendor()
        val kv = scope.store(backend)
        val service = TelemetryService(vendor, available, scope = scope)
        val continuity =
            TelemetryContinuity(
                TelemetryAnswers(kv, WriterLane("app")),
                HealthConsentStore(kv, WriterLane("app")),
                service,
                clock,
            )

        fun persisted(key: String): RawPref? = backend.persisted()[FlutterPrefsCodec.physicalKey(key)]
    }

    @Test
    fun `B-180-017 Flutter's first single yes carries over to both answers and nobody is asked again`() = runTest {
        val rig = Rig(this, disk(TelemetryKeys.CONSENT to RawPref.Bool(true)))
        rig.continuity.adopt(); runCurrent()
        assertEquals(TelemetryConsent(usage = true, crashes = true), rig.service.consent)
        assertEquals(ConsentAsk.Closed, rig.continuity.question.value)
        // Saved once under the split keys, the question marked asked; applied to the vendor without a reset.
        assertEquals(RawPref.Bool(true), rig.persisted(HealthConsentKeys.USAGE))
        assertEquals(RawPref.Bool(true), rig.persisted(HealthConsentKeys.CRASHES))
        assertEquals(RawPref.Bool(true), rig.persisted(HealthConsentKeys.ASKED))
        assertEquals(listOf(TelemetryConsent(true, true)), rig.vendor.collection)
    }

    @Test
    fun `B-180-019 Flutter's split answers win, and an old yes with the crash offer off keeps crashes off`() = runTest {
        val split =
            Rig(
                this,
                disk(
                    HealthConsentKeys.USAGE to RawPref.Bool(false),
                    HealthConsentKeys.CRASHES to RawPref.Bool(true),
                    TelemetryKeys.CONSENT to RawPref.Bool(true),
                    HealthConsentKeys.ASKED to RawPref.Bool(true),
                ),
            )
        split.continuity.adopt(); runCurrent()
        assertEquals(TelemetryConsent(usage = false, crashes = true), split.service.consent)

        val offerOff =
            Rig(this, disk(TelemetryKeys.CONSENT to RawPref.Bool(true), HealthConsentKeys.CRASH_OFFER to RawPref.Bool(false)))
        offerOff.continuity.adopt(); runCurrent()
        assertEquals(TelemetryConsent(usage = true, crashes = false), offerOff.service.consent)

        // A Flutter "no" is an answer too: both off, never asked again.
        val no = Rig(this, disk(TelemetryKeys.CONSENT to RawPref.Bool(false)))
        no.continuity.adopt(); runCurrent()
        assertEquals(TelemetryConsent(), no.service.consent)
        assertEquals(ConsentAsk.Closed, no.continuity.question.value)
    }

    @Test
    fun `B-180-018 a build that can send asks once and saves asked first - Share both, Not now, Choose`() = runTest {
        val share = Rig(this, disk())
        share.continuity.adopt(); runCurrent()
        assertEquals(ConsentAsk.Question, share.continuity.question.value)
        assertEquals(TelemetryConsent(), share.service.consent, "off until the person agrees")
        assertEquals(RawPref.Bool(true), share.persisted(TelemetryKeys.CONSENT_ASKED))
        share.continuity.answer(share = true); runCurrent()
        assertEquals(TelemetryConsent(usage = true, crashes = true), share.service.consent)
        assertEquals(RawPref.Bool(true), share.persisted(HealthConsentKeys.USAGE))
        assertEquals(RawPref.Bool(true), share.persisted(HealthConsentKeys.CRASHES))
        // This start's cold open, dropped before the answer, is sent once agreed.
        assertEquals(listOf("app_open"), share.vendor.events)

        val notNow = Rig(this, disk())
        notNow.continuity.adopt(); runCurrent()
        notNow.continuity.answer(share = false); runCurrent()
        assertEquals(ConsentAsk.Closed, notNow.continuity.question.value)
        assertEquals(TelemetryConsent(), notNow.service.consent)
        assertTrue(notNow.vendor.events.isEmpty())

        val choose = Rig(this, disk())
        choose.continuity.adopt(); runCurrent()
        choose.continuity.choose()
        assertEquals(ConsentAsk.Choose, choose.continuity.question.value)
        choose.continuity.saveChoice(usage = false, crashes = true); runCurrent()
        assertEquals(TelemetryConsent(usage = false, crashes = true), choose.service.consent)
        assertEquals(RawPref.Bool(false), choose.persisted(HealthConsentKeys.USAGE))
        assertEquals(RawPref.Bool(true), choose.persisted(HealthConsentKeys.CRASHES))

        // A build with no vendor never asks.
        val quiet = Rig(this, disk(), available = false)
        quiet.continuity.adopt(); runCurrent()
        assertEquals(ConsentAsk.Closed, quiet.continuity.question.value)
    }

    @Test
    fun `E02-S91-010 a fresh crash is offered once, and with Offer crash reports off it is marked seen without showing`() =
        runTest {
            val crash = LaunchReport(exit = ExitRecord(reason = ExitReason.CRASH, timestampMs = now.toEpochMilliseconds() - 3_600_000))
            val c = Rig(this, disk(), available = false).continuity
            assertTrue(c.enabled(), "on unless turned off")
            c.considerCrash(crash); runCurrent()
            assertNotNull(c.offer.pending.value)
            assertNotNull(c.takeCrash())
            assertNull(c.offer.pending.value)

            val off = Rig(this, disk(), available = false).continuity
            assertTrue(off.setEnabled(false))
            off.considerCrash(crash); runCurrent()
            assertNull(off.offer.pending.value)
            assertEquals(crash.exit!!.timestampMs, off.lastOfferedMs())
            assertFalse(off.enabled())
        }
}
