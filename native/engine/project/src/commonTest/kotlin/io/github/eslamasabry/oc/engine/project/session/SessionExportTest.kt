package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionExportTest {
    private class FakePort(var answer: (SessionExportRequest) -> Outcome<ByteArray>) : SessionExportPort {
        val requests = mutableListOf<SessionExportRequest>()

        override suspend fun download(request: SessionExportRequest): Outcome<ByteArray> {
            requests += request
            return answer(request)
        }
    }

    @Test
    fun `B-PROTO-096 exports complete response bytes with default redaction and no location`() = runTest {
        val text = "{ \"data\": {\"info\": {\"id\":\"ses_full\"}, \"messages\": [" +
            List(5001) { "{\"text\":\"message $it\"}" }.joinToString(",") + "]}}"
        val body = DartJson.utf8(text)
        val port = FakePort { Outcome.Ok(body) }
        val exporter = SessionExporter(port)

        val result = exporter.export(SessionId("ses_full"))
        assertIs<Outcome.Ok<ByteArray>>(result)
        assertEquals(text, result.value.decodeToString())
        val request = port.requests.single()
        assertEquals("/session/ses_full/export", request.path)
        assertEquals(mapOf("sanitize" to "true"), request.query)
        // The caller gets its own buffer: changing it never changes the transport's bytes.
        result.value[0] = 0x58
        assertEquals('{'.code.toByte(), body[0])

        exporter.export(SessionId("ses_full"), sanitize = false)
        assertEquals(mapOf("sanitize" to "false"), port.requests.last().query)
    }

    @Test
    fun `B-PROTO-096 byte errors keep their tag and do not disable export`() = runTest {
        val failures = listOf(
            Failure.SignIn(SignInWhy.PasswordRejected),
            Failure.NotFound(tag = WireTag("SessionNotFoundError")),
            Failure.Server(500, WireTag("UnknownError")),
        )
        for (failure in failures) {
            val exporter = SessionExporter(FakePort { Outcome.Failed(failure) })
            assertEquals(Outcome.Failed(failure), exporter.export(SessionId("ses_missing")))
            assertTrue(exporter.supported.value, "still supported after $failure")
        }
    }

    @Test
    fun `B-PROTO-096 an absent endpoint is remembered without repeated requests`() = runTest {
        val port = FakePort { Outcome.Failed(Failure.NotFound(tag = WireTag("NotFound"))) }
        val exporter = SessionExporter(port)
        repeat(2) {
            val result = exporter.export(SessionId("ses_x"))
            assertIs<Outcome.Failed>(result)
            assertEquals(Failure.Unavailable(SessionExporter.FEATURE), result.failure)
        }
        assertFalse(exporter.supported.value)
        assertEquals(1, port.requests.size)
    }

    @Test
    fun `B-PROTO-096 405 and 501 also latch export off`() = runTest {
        for (failure in listOf(Failure.Rejected(405), Failure.Server(501))) {
            val exporter = SessionExporter(FakePort { Outcome.Failed(failure) })
            exporter.export(SessionId("ses_x"))
            assertFalse(exporter.supported.value)
        }
    }
}
