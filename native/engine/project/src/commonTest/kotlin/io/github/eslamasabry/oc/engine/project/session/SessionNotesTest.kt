package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireExtras
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionNotesTest {
    private val ses = SessionId("ses_1")
    private val here = ServerLocation(directory = "/home/dev/projects/oc_app")

    /** A server holding one note; reads and writes can be held, and a write can fail. */
    private class NoteServer(var value: String? = "Keep changes focused") : SessionNoteWire {
        val requests = mutableListOf<SessionNoteWireRequest>()
        val writes = mutableListOf<String?>()
        var readGate: CompletableDeferred<Unit>? = null
        var readStarted: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var failure: Failure? = null
        var answer: ((SessionNoteWireRequest) -> Outcome<SessionNoteWireReply>)? = null

        override suspend fun send(request: SessionNoteWireRequest): Outcome<SessionNoteWireReply> {
            requests += request
            answer?.let { return it(request) }
            if (request.method == NoteMethod.Get) {
                readStarted?.complete(Unit)
                readGate?.await()
                val entries = listOfNotNull(
                    mapOf("key" to "server.private", "value" to mapOf("secret" to "never exposed")),
                    value?.let { mapOf("key" to SessionNoteRules.KEY, "value" to it) },
                )
                return Outcome.Ok(SessionNoteWireReply.Read(mapOf("data" to entries)))
            }
            writes += request.value
            writeGate?.await()
            failure?.let { return Outcome.Failed(it) }
            value = request.value
            return Outcome.Ok(SessionNoteWireReply.Done)
        }
    }

    private class Harness(val server: NoteServer, location: ServerLocation) {
        var gateway: SessionNoteGateway? = SessionNoteGateway(server, location)
        var wake: CompletableDeferred<Unit>? = null
        var locationRevision = 0
        val notes = SessionNotes(
            current = { gateway },
            prepare = { wake?.await(); gateway },
            locationRevision = { locationRevision },
        )
    }

    private fun harness(server: NoteServer = NoteServer()) = Harness(server, here)

    private fun refused(problem: SessionNoteProblem, result: NoteResult<*>) {
        assertIs<NoteResult.Refused>(result)
        assertEquals(problem, result.problem)
    }

    @Test
    fun `B-PROTO-100 wire operations isolate the mobile-owned key and location`() = runTest {
        val server = NoteServer("Use concise explanations")
        val gateway = SessionNoteGateway(server, here)
        assertEquals(NoteResult.Ok("Use concise explanations"), gateway.load(ses))
        assertEquals(NoteResult.Ok(Unit), gateway.save(ses, "Keep spacing\n🙂"))
        assertEquals(NoteResult.Ok(Unit), gateway.remove(ses))
        assertEquals(listOf(NoteMethod.Get, NoteMethod.Put, NoteMethod.Delete), server.requests.map { it.method })
        assertEquals("{\"value\":\"Keep spacing\\n🙂\"}", server.requests[1].body)
        assertNull(server.requests[2].body)
        for (request in server.requests) {
            val suffix = if (request.method == NoteMethod.Get) "" else "/mobile.note"
            assertEquals("/session/ses_1/instructions/entries$suffix", request.path)
            assertEquals(mapOf("location[directory]" to "/home/dev/projects/oc_app"), request.query)
        }
        // Auth is the bound transport's own: the request carries nothing of it, nor another entry's value.
        assertFalse(server.requests.any { "never exposed" in (it.body ?: "") })
    }

    @Test
    fun `B-PROTO-100 8192 encoded bytes include quotes, escapes and Unicode`() = runTest {
        assertEquals(10, SessionNoteRules.encodedBytes("🙂\n\""))
        val server = NoteServer()
        val gateway = SessionNoteGateway(server, here)
        assertEquals(NoteResult.Ok(Unit), gateway.save(ses, "a".repeat(8190)))
        assertEquals(1, server.requests.size)
        refused(SessionNoteProblem.TooLarge, gateway.save(ses, "a".repeat(8191)))
        refused(SessionNoteProblem.TooLarge, gateway.save(ses, "🙂".repeat(2048)))
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `B-PROTO-101 authorization errors are kept without disabling the capability`() = runTest {
        val auth = Failure.SignIn(SignInWhy.PasswordRejected)
        val server = NoteServer().apply { answer = { Outcome.Failed(auth) } }
        val gateway = SessionNoteGateway(server, here)
        assertEquals(NoteResult.Failed(auth), gateway.load(ses))
        assertEquals(NoteResult.Failed(auth), gateway.save(ses, "draft"))
        assertEquals(NoteResult.Failed(auth), gateway.remove(ses))
        assertTrue(gateway.supported)
    }

    @Test
    fun `B-PROTO-101 an unsupported endpoint hides the feature while a missing session does not`() = runTest {
        var missingSession = true
        val server = NoteServer().apply {
            answer = {
                Outcome.Failed(Failure.NotFound(tag = WireTag(if (missingSession) "SessionNotFoundError" else "NotFoundError")))
            }
        }
        val h = Harness(server, here)
        assertIs<NoteResult.Failed>(h.gateway!!.load(ses))
        assertTrue(h.notes.supported)
        missingSession = false
        refused(SessionNoteProblem.Unsupported, h.gateway!!.load(ses))
        assertFalse(h.notes.supported)
        h.gateway = null // a server without the note contract
        assertFalse(h.notes.supported)
    }

    @Test
    fun `B-PROTO-101 a 413 keeps its byte limit and an unsupported value stays untouched`() = runTest {
        var invalid = false
        val server = NoteServer().apply {
            answer = {
                if (invalid) {
                    Outcome.Ok(SessionNoteWireReply.Read(mapOf("data" to listOf(mapOf("key" to "mobile.note", "value" to mapOf("unknown" to true))))))
                } else {
                    Outcome.Failed(
                        Failure.Rejected(
                            413,
                            WireTag("InstructionEntryValueTooLargeError"),
                            WireExtras(persistentMapOf("maxBytes" to "100", "actualBytes" to "102")),
                        ),
                    )
                }
            }
        }
        val gateway = SessionNoteGateway(server, here)
        assertEquals(NoteResult.Refused(SessionNoteProblem.TooLarge, maxBytes = 100), gateway.save(ses, "a".repeat(100)))
        invalid = true
        refused(SessionNoteProblem.InvalidValue, gateway.load(ses))
        assertEquals(listOf(NoteMethod.Put, NoteMethod.Get), server.requests.map { it.method })
        assertTrue(gateway.supported)
    }

    @Test
    fun `B-STATE-113 save re-reads before mutation and rejects another client change`() = runTest {
        val h = harness()
        val review = (h.notes.load(ses) as NoteResult.Ok).value
        h.server.value = "Changed remotely"
        refused(SessionNoteProblem.Changed, h.notes.save(review, "my draft"))
        assertTrue(h.server.writes.isEmpty())
        assertNull(h.notes.receipt(ses))
    }

    @Test
    fun `B-STATE-113 wake and fresh-read scope changes never write into a new location`() = runTest {
        val h = harness()
        val review = (h.notes.load(ses) as NoteResult.Ok).value
        h.wake = CompletableDeferred()
        val pending = async { h.notes.save(review, "my draft") }
        runCurrent()
        h.locationRevision++
        h.wake!!.complete(Unit)
        refused(SessionNoteProblem.Changed, pending.await())
        h.wake = null

        val next = (h.notes.load(ses) as NoteResult.Ok).value
        h.server.readGate = CompletableDeferred()
        h.server.readStarted = CompletableDeferred()
        val nextPending = async { h.notes.save(next, "my draft") }
        h.server.readStarted!!.await()
        h.locationRevision++
        h.server.readGate!!.complete(Unit)
        refused(SessionNoteProblem.Changed, nextPending.await())
        assertTrue(h.server.writes.isEmpty())
    }

    @Test
    fun `B-STATE-113 duplicate saves serialize and only successful writes create receipts`() = runTest {
        val h = harness()
        val review = (h.notes.load(ses) as NoteResult.Ok).value
        h.server.writeGate = CompletableDeferred()
        val first = async { h.notes.save(review, "my draft") }
        runCurrent()
        refused(SessionNoteProblem.Busy, h.notes.save(review, null))
        h.server.writeGate!!.complete(Unit)
        assertEquals(NoteResult.Ok(Unit), first.await())
        assertEquals(listOf<String?>("my draft"), h.server.writes)
        assertEquals(true, h.notes.receipt(ses))
        // The accepted write moved the note on: the old review is stale.
        refused(SessionNoteProblem.Changed, h.notes.save(review, null))

        val next = (h.notes.load(ses) as NoteResult.Ok).value
        val auth = Failure.SignIn(SignInWhy.PasswordRejected)
        h.server.failure = auth
        assertEquals(NoteResult.Failed(auth), h.notes.save(next, null))
        assertEquals(true, h.notes.receipt(ses))
        h.server.failure = null
        assertEquals(NoteResult.Ok(Unit), h.notes.save(next, null))
        assertEquals(false, h.notes.receipt(ses))
    }
}
