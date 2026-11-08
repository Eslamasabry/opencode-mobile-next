package io.github.eslamasabry.oc.engine.analytics.product

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class TelemetryAnswersTest {
    /** Flutter 2.1.5's `FlutterSharedPreferences.xml` as the app finds it after the update. */
    private fun flutter215(vararg rows: Pair<String, RawPref>) =
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
    fun `B-180-010 the six Flutter 2_1_5 keys are the physical keys native reads`() {
        assertEquals("flutter.oc.telemetry.crashReports", FlutterPrefsCodec.physicalKey(TelemetryKeys.CONSENT))
        assertEquals("flutter.oc.telemetry.lastVersion", FlutterPrefsCodec.physicalKey(TelemetryKeys.LAST_VERSION))
        assertEquals("flutter.oc.telemetryConsentAsked", FlutterPrefsCodec.physicalKey(TelemetryKeys.CONSENT_ASKED))
        assertEquals("flutter.oc.crashReports.offer", FlutterPrefsCodec.physicalKey(TelemetryKeys.CRASH_OFFER))
        assertEquals(
            "flutter.oc.crashReports.lastOffered",
            FlutterPrefsCodec.physicalKey(TelemetryKeys.CRASH_LAST_OFFERED),
        )
    }

    @Test
    fun `B-180-011 a Flutter yes, its asked mark, last version and crash-offer answers carry over`() = runTest {
        val a =
            answers(
                flutter215(
                    TelemetryKeys.CONSENT to RawPref.Bool(true),
                    TelemetryKeys.CONSENT_ASKED to RawPref.Bool(true),
                    TelemetryKeys.LAST_VERSION to RawPref.Str("2.1.5+70"),
                    TelemetryKeys.CRASH_OFFER to RawPref.Bool(false),
                    TelemetryKeys.CRASH_LAST_OFFERED to RawPref.Int64(1_793_000_000_000L),
                )
            )
        assertEquals(true, a.consent())
        assertTrue(a.consentAsked(), "a person who answered once is never asked again")
        assertEquals("2.1.5+70", a.lastVersion())
        assertFalse(a.crashOfferEnabled())
        assertEquals(1_793_000_000_000L, a.crashLastOfferedMs())
        // So the switch build, which starts OFF before the store opens, takes Flutter's yes.
        assertEquals(true, ConsentCarryOver.adopt(a.consent(), early = false))
    }

    @Test
    fun `B-180-012 a Flutter no stays no, and nothing stored means the defaults`() = runTest {
        assertEquals(false, ConsentCarryOver.adopt(answers(flutter215(TelemetryKeys.CONSENT to RawPref.Bool(false))).consent(), early = true))
        val fresh = answers(flutter215())
        assertNull(fresh.consent())
        assertNull(ConsentCarryOver.adopt(fresh.consent(), early = false))
        assertFalse(fresh.consentAsked())
        assertTrue(fresh.crashOfferEnabled(), "Offer crash reports is on unless turned off")
        assertEquals(0L, fresh.crashLastOfferedMs())
        assertNull(ConsentCarryOver.adopt(true, early = true), "same answer: nothing to change")
    }

    @Test
    fun `B-180-013 native writes the same keys Flutter reads, so a rollback sees the answers`() = runTest {
        val disk = flutter215()
        val a = answers(disk)
        a.setConsent(true)
        a.markConsentAsked()
        a.setLastVersion("3.0.0+90")
        a.setCrashOfferEnabled(false)
        a.setCrashLastOfferedMs(42L)
        testScheduler.advanceUntilIdle()
        val written = disk.persisted()
        assertEquals(RawPref.Bool(true), written["flutter.oc.telemetry.crashReports"])
        assertEquals(RawPref.Bool(true), written["flutter.oc.telemetryConsentAsked"])
        assertEquals(RawPref.Str("3.0.0+90"), written["flutter.oc.telemetry.lastVersion"])
        assertEquals(RawPref.Bool(false), written["flutter.oc.crashReports.offer"])
        assertEquals(RawPref.Int64(42L), written["flutter.oc.crashReports.lastOffered"])
    }

    @Test
    fun `B-180-014 an int-sized lastOffered is read too`() = runTest {
        assertEquals(7L, answers(flutter215(TelemetryKeys.CRASH_LAST_OFFERED to RawPref.Int32(7))).crashLastOfferedMs())
    }
}
