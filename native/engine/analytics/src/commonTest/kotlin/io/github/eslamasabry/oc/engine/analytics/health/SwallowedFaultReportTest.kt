package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.reportPhoneServerFollowRefused
import io.github.eslamasabry.oc.engine.analytics.reportPhraseChipsUnreadable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Two handled failures with fixed names and no fields: sent once with crash reports on, nothing while off. */
class SwallowedFaultReportTest {
    private object NoConsentStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private fun TestScope.service(vendor: RecordingVendor, crashes: Boolean): TelemetryService =
        TelemetryService(vendor, true, scope = this).also {
            it.start(
                TelemetryConsent(usage = false, crashes = crashes),
                store = NoConsentStore,
                reports = MemoryTelemetryReportStore(),
            )
        }

    @Test
    fun `B-SEC-213 unreadable phrase suggestions are one non-fatal with a fixed name and no fields`() = runTest {
        val vendor = RecordingVendor()
        service(vendor, crashes = true).reportPhraseChipsUnreadable()
        assertEquals(listOf("phrase_chips_unreadable" to emptyMap<String, String>()), vendor.failures.toList())
    }

    @Test
    fun `B-SEC-213 unreadable phrase suggestions send nothing while crash reports are off`() = runTest {
        val vendor = RecordingVendor()
        service(vendor, crashes = false).reportPhraseChipsUnreadable()
        assertEquals(emptyList<Pair<String, Map<String, String>>>(), vendor.failures.toList())
    }

    @Test
    fun `B-SEC-214 a refused phone-server follow is one non-fatal with a fixed name and no fields`() = runTest {
        val vendor = RecordingVendor()
        service(vendor, crashes = true).reportPhoneServerFollowRefused()
        assertEquals(listOf("phone_server_follow_refused" to emptyMap<String, String>()), vendor.failures.toList())
    }

    @Test
    fun `B-SEC-214 a refused phone-server follow sends nothing while crash reports are off`() = runTest {
        val vendor = RecordingVendor()
        service(vendor, crashes = false).reportPhoneServerFollowRefused()
        assertEquals(emptyList<Pair<String, Map<String, String>>>(), vendor.failures.toList())
    }
}
