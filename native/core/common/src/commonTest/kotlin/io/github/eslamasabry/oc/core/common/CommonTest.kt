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
    fun `Failure is one flat closed list with one name per subtype (plan 03 S10_1)`() {
        // An exhaustive `when` with no else: a nested family or a new subtype fails compilation here.
        fun name(f: Failure): String =
            when (f) {
                is Failure.Refused -> "Refused"
                is Failure.Unavailable -> "Unavailable"
                is Failure.Network -> "Network"
                is Failure.TimedOut -> "TimedOut"
                is Failure.Certificate -> "Certificate"
                is Failure.SignIn -> "SignIn"
                is Failure.NotFound -> "NotFound"
                is Failure.Conflict -> "Conflict"
                is Failure.Busy -> "Busy"
                is Failure.Server -> "Server"
                is Failure.Rejected -> "Rejected"
                is Failure.Incompatible -> "Incompatible"
                is Failure.ComputerDidNotAnswer -> "ComputerDidNotAnswer"
                is Failure.UnreadableReply -> "UnreadableReply"
                is Failure.StorageRefused -> "StorageRefused"
                is Failure.SecretUnavailable -> "SecretUnavailable"
                is Failure.Media -> "Media"
                is Failure.Runtime -> "Runtime"
                is Failure.Device -> "Device"
                is Failure.Folder -> "Folder"
                is Failure.ProjectGit -> "ProjectGit"
                is Failure.StagedRevert -> "StagedRevert"
                is Failure.Termux -> "Termux"
                is Failure.Unexpected -> "Unexpected"
                is Failure.Unknown -> "Unknown"
            }
        val samples: List<Failure> =
            listOf(
                Failure.Network(NetworkWhy.Offline),
                Failure.Network(NetworkWhy.Dns),
                Failure.Unavailable(FeatureId("sessionNotes")),
                Failure.Rejected(422, WireTag("ValidationError")),
                Failure.Busy(BusyKind.SessionBusy, WireTag("SessionBusyError")),
                Failure.Incompatible(IncompatibleKind.LegacyOpenCode1),
                Failure.StorageRefused(),
            )
        assertEquals(listOf("Network", "Network", "Unavailable", "Rejected", "Busy", "Incompatible", "StorageRefused"), samples.map(::name))
        assertEquals(NetworkWhy.Unknown, Failure.Network().why)
        assertEquals(BusyKind.Overloaded, Failure.Busy().kind)
        assertTrue(samples.all { it.details == null })
    }
}
