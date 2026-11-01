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
        val out = attempt({ Failure.Unexpected(Redacted.shape(it)) }) { error("boom secret") }
        val failure = assertIs<Failure.Unexpected>(assertIs<Outcome.Failed>(out).failure)
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

    @Serializable private data class Nested(val items: List<Probe>, val name: String)

    @Test
    fun `B-PROTO-137 decoding errors never contain the input JSON`() {
        val fakeKey = "sk-ant-" + "api03-FAKE0137" + "notarealkey00"
        val bodies =
            listOf(
                """{"count":"$fakeKey"}""", // wrong type
                """{"items":[{"count":"$fakeKey"}],"name":"x"}""", // nested wrong type
                """{"items":[],"name":"$fakeKey""", // truncated
                """{"items":[], $fakeKey}""", // malformed
                """["$fakeKey"]""", // wrong shape
            )
        for (body in bodies) {
            val error = assertFailsWith<SerializationException> { WireJson.decodeFromString(Nested.serializer(), body) }
            val text = error.toString() + error.message.orEmpty() + error.cause?.message.orEmpty()
            assertFalse(fakeKey in text || "FAKE0137" in text, "decode error echoed the body: $body")
        }
        assertTrue(WireJson.decodeFromString(Probe.serializer(), """{"count":3,"extra":1}""").count == 3)
    }

    @Test
    fun `network failures are one closed family under Failure`() {
        val failures: List<Failure> =
            listOf(
                Failure.Net.Offline(),
                Failure.Net.Unreachable(Failure.Net.Unreachable.Why.Dns, null),
                Failure.Net.NotSupported(FeatureId("sessionNotes")),
                Failure.Net.Rejected(422, WireTag("ValidationError"), WireExtras.None, null),
            )
        assertTrue(failures.all { it is Failure.Net })
        assertEquals(null, Failure.Net.Offline().details)
    }
}
