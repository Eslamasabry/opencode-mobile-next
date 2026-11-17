package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.reportNetClientCloseFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * A connection whose close failed while releasing its engine (line run 44079402632; the app no longer closes over it)
 * is one non-fatal for the developer, sent only with crash reports on, and carries the error's kind and nothing else.
 */
class NetCloseFaultReportTest {
    private fun TestScope.service(vendor: RecordingVendor, crashes: Boolean): TelemetryService =
        TelemetryService(vendor, true, scope = this).also {
            it.start(
                TelemetryConsent(usage = false, crashes = crashes),
                store = NoConsentStore,
                reports = MemoryTelemetryReportStore(),
            )
        }

    private object NoConsentStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    @Test
    fun `B-PROTO-148 a failed close is one non-fatal with its fixed name and the error's kind only`() = runTest {
        val vendor = RecordingVendor()
        val fault = NoSuchElementException("https://host.example/x?token=1") // a message that must never leave
        service(vendor, crashes = true).reportNetClientCloseFailed(fault)
        assertEquals(
            listOf("net_client_close_failed" to mapOf("failure" to "no_such_element_exception")),
            vendor.failures.toList(),
        )
    }

    @Test
    fun `B-PROTO-148 a failed close sends nothing while crash reports are off`() = runTest {
        val vendor = RecordingVendor()
        service(vendor, crashes = false).reportNetClientCloseFailed(NoSuchElementException())
        assertEquals(emptyList<Pair<String, Map<String, String>>>(), vendor.failures.toList())
    }
}
