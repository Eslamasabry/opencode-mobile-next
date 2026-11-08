package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The redaction gate every crash report passes (consent, keys and vendor routing are in
 * `health/TelemetryServiceTest`, which drives the one [Reporting] implementation).
 */
class ReportingTest {
    @Test
    fun `B-TELE-002 a stack keeps class method line frames and drops messages and paths`() {
        assertEquals(
            listOf("a.B.run:12", "c.D.\$lambda:7"),
            ReportRedactor.stack(
                listOf("a.B.run:12", "boom: password is hunter2", "/data/user/0/app/x.kt:3", "c.D.\$lambda:7")
            ),
        )
    }

    @Test
    fun `B-TELE-003 an odd exception category is replaced`() {
        assertEquals("java.lang.Throwable", ReportRedactor.category("Oops: token=abc"))
        assertEquals("java.lang.IllegalStateException", ReportRedactor.category("java.lang.IllegalStateException"))
        assertEquals("unnamed_failure", ReportRedactor.name("Has Spaces And Text"))
    }

    @Test
    fun `B-TELE-001 a token, url, path or sentence is redacted, a version kept`() {
        assertEquals("2.1.1", ReportRedactor.value("2.1.1"))
        for (bad in listOf("sk-abcdef1234567890", "https://example.com/a?token=1", "/home/me/secret.txt", "please fix my code")) {
            assertEquals(ReportRedactor.REDACTED, ReportRedactor.value(bad), bad)
        }
    }

    @Test
    fun `B-TELE-007 the no-op default reports nothing`() {
        NoReporting.reportFailure("x")
        NoReporting.reportCrash("y", emptyList())
        NoReporting.setReportingConsent(true)
        assertFalse(NoReporting.reportingConsent.value)
    }
}
