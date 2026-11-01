package io.github.eslamasabry.oc.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Infra tests of slice-0 seams (plan 03 §22.1 :core:common row). Catalog ids arrive with slice-1 behaviour.
class CommonTest {
    @Test
    fun `attempt rethrows cancellation`() = runTest {
        assertFailsWith<CancellationException> {
            attempt({ Failure.Unexpected(null) }) { throw CancellationException("stop") }
        }
    }

    @Test
    fun `attempt turns other throwables into the classified failure`() = runTest {
        val out = attempt({ Failure.Network(Redacted.shape(it)) }) { error("boom secret") }
        val failure = assertIs<Failure.Network>(assertIs<Outcome.Failed>(out).failure)
        assertEquals("IllegalStateException", failure.details?.text)
    }

    @Test
    fun `AppLog buffers before install and flushes through the redacting root`() {
        AppLog.tagged("early").i { "before install" }
        val seen = mutableListOf<LogEntry>()
        AppLog.install(
            object : RedactingLogSink {
                override fun write(entry: LogEntry) {
                    seen += entry.copy(message = entry.message.replace("install", "•••"))
                }
            },
            Severity.Info,
        )
        AppLog.tagged("late").d { "below min severity" }
        AppLog.tagged("late").i { "after install" }
        assertEquals(listOf("before •••", "after •••"), seen.map { it.message })
    }

    @Serializable private data class Probe(val count: Int)

    @Test
    fun `WireJson decode errors never contain the input`() {
        val secret = "sk-live-SECRET"
        val error = assertFailsWith<SerializationException> {
            WireJson.decodeFromString(Probe.serializer(), """{"count":"$secret"}""")
        }
        assertFalse(error.message.orEmpty().contains(secret))
        assertTrue(WireJson.decodeFromString(Probe.serializer(), """{"count":3,"extra":1}""").count == 3)
    }
}
