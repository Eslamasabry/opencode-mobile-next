package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Records what diagnostics hands the analytics port. */
internal class RecordingSignals : DiagnosticsSignals {
    val crashes = mutableListOf<Pair<CrashCategory, Boolean>>()
    val freezes = mutableListOf<Triple<FreezeKind, Long, Boolean>>()
    val errors = mutableListOf<Failure>()

    override fun crash(category: CrashCategory, fatal: Boolean) {
        crashes += category to fatal
    }

    override fun freeze(kind: FreezeKind, durationMs: Long, foreground: Boolean) {
        freezes += Triple(kind, durationMs, foreground)
    }

    override fun error(failure: Failure) {
        errors += failure
    }
}

class DiagnosticsTest {
    private val clock = TestClock()

    @Test
    fun `B-SEC-008 diagnostics redact secrets, URLs and long token-like values`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        val bearer = "abcdefghijklmnopqrstuvwxyz0123456789TOKEN"
        val apiKey = "super-secret-api-key-value-123456789"
        diagnostics.record(
            source = "kotlin",
            message =
                "Authorization: Bearer $bearer\npassword=hunter2 api_key=\"$apiKey\" " +
                    "https://user:pass@example.com/private?q=chat&token=hidden#frag",
            stack = "at safe_function (Main.kt:10)",
        )
        val report = diagnostics.reportText()
        for (secret in listOf(bearer, apiKey, "hunter2", "user:pass", "q=chat", "hidden")) {
            assertFalse(secret in report, secret)
        }
        assertTrue("example.com/private" in report)
        assertTrue("safe_function" in report)
    }

    @Test
    fun `B-SEC-008 diagnostics keep long code names but redact long tokens`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        val exceptionName = "ForegroundServiceDidNotStartInTimeException"
        val token = "q8Zx1kP0vR7mN2sL5tY9wE3uI6oA4dFgHjK"
        val safe = diagnostics.sanitize("RemoteServiceException\$$exceptionName $token")
        assertTrue(exceptionName in safe)
        assertFalse(token in safe)
    }

    @Test
    fun `B-SEC-008 diagnostics coalesce bursts and keep a bounded ring`() {
        val diagnostics = Diagnostics(Redactor(), clock, maxEntries = 3)
        val start = Instant.parse("2026-08-28T10:00:00Z")
        diagnostics.record("kotlin", "same", at = start)
        diagnostics.record("kotlin", "same", at = start + 1.seconds)
        assertEquals(2, diagnostics.errors.value.single().occurrences)
        assertEquals(start + 1.seconds, diagnostics.errors.value.single().at)

        for (index in 0 until 4) diagnostics.record("platform", "error-$index", at = start + (index + 1).minutes)
        val entries = diagnostics.errors.value
        assertEquals(3, entries.size)
        assertEquals(listOf("error-1", "error-2", "error-3"), entries.map { it.message })
    }

    @Test
    fun `B-SEC-008 a repeat outside the window is a new entry and blanks get fixed words`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        val start = Instant.parse("2026-08-28T10:00:00Z")
        diagnostics.record("kotlin", "same", at = start)
        diagnostics.record("kotlin", "same", at = start + 11.seconds)
        diagnostics.record("", "  ", at = start + 12.seconds)
        val entries = diagnostics.errors.value
        assertEquals(3, entries.size)
        assertEquals("app", entries.last().source)
        assertEquals("Unknown application error", entries.last().message)
    }

    @Test
    fun `B-SEC-008 messages and stacks are bounded`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        diagnostics.record("x".repeat(500), "m ".repeat(5_000), "s ".repeat(20_000))
        val entry = diagnostics.errors.value.single()
        assertTrue(entry.source.length <= Diagnostics.SOURCE_LIMIT + 20)
        assertTrue(entry.message.length <= Diagnostics.MESSAGE_LIMIT + 20)
        assertTrue(entry.stack.length <= Diagnostics.STACK_LIMIT + 20)
        assertTrue(entry.message.endsWith("[truncated]"))
    }

    @Test
    fun `B-SEC-008 a shown failure reports its category to the port and keeps only its kind in the ring`() {
        val signals = RecordingSignals()
        val diagnostics = Diagnostics(Redactor(), clock, signals)
        val failure = Failure.Network(NetworkWhy.Reset, Redactor().details("synthetic-provider-secret"))
        diagnostics.failureShown(failure)
        diagnostics.failureShown(Failure.Refused(RefusalReason.ConsentMissing))
        assertEquals(listOf<Failure>(failure, Failure.Refused(RefusalReason.ConsentMissing)), signals.errors)
        assertEquals(listOf("Network Reset", "Refused ConsentMissing"), diagnostics.errors.value.map { it.message })
        assertFalse("synthetic-provider-secret" in diagnostics.reportText())
    }

    @Test
    fun `B-SEC-008 a throwing signal adapter never breaks recording`() {
        val broken =
            object : DiagnosticsSignals by DiagnosticsSignals.None {
                override fun error(failure: Failure): Unit = throw IllegalStateException("adapter down")
            }
        val diagnostics = Diagnostics(Redactor(), clock, broken)
        diagnostics.failureShown(Failure.Unknown())
        assertEquals(1, diagnostics.errors.value.size)
    }

    @Test
    fun `B-SEC-008 clearing empties the ring and tells listeners`() {
        val diagnostics = Diagnostics(Redactor(), clock)
        var heard = 0
        val remove = diagnostics.onCleared { heard++ }
        diagnostics.record("kotlin", "one")
        diagnostics.clear()
        assertTrue(diagnostics.isEmpty)
        assertEquals(1, heard)
        remove()
        diagnostics.clear()
        assertEquals(1, heard)
    }
}
