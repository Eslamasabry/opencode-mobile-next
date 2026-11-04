package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.gateway
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import io.github.eslamasabry.oc.protocol.codex.str
import io.github.eslamasabry.oc.protocol.codex.thread
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocket
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory
import io.github.eslamasabry.oc.protocol.codex.turn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/codex_journey_test.dart. */
class CodexJourneyTest {
    @Test
    fun `B-AGENT-067 connect, send to existing and new threads, stream, approve, abort and recover authoritatively`() = runTest {
        val threads = mutableMapOf(
            "thread-existing" to thread("thread-existing", turns = listOf(turn("turn-history", prefix = "turn-history")), name = "Existing thread"),
            "thread-new" to thread("thread-new", name = "New thread"),
        )
        var failNextTurn = false
        val first = FakeAppServer()
        val replacement = FakeAppServer()
        for (s in listOf(first, replacement)) {
            val read: (JsonObject) -> JsonObject? = { jsonOf("thread" to threads.getValue(it.params().str("threadId")!!)) }
            s.answers["thread/read"] = read
            s.answers["thread/resume"] = read
            s.answers["thread/start"] = { jsonOf("thread" to threads.getValue("thread-new")) }
            s.answers["turn/start"] = { frame ->
                if (failNextTurn) {
                    failNextTurn = false
                    s.drop()
                    null
                } else {
                    val id = if (frame.params().str("threadId") == "thread-new") "turn-new" else "turn-retry"
                    jsonOf("turn" to turn(id, "inProgress", prefix = id))
                }
            }
        }
        val sockets = CodexSocketFactory { _: CodexEndpoint, _: String -> (if (first.closed) replacement else first) as CodexSocket }
        val g = gateway(sockets)
        val statuses = mutableListOf<StreamStatus>()
        val events = mutableListOf<GatewayEvent>()
        val d = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(d) { g.events.status.collect { statuses += it } }
        backgroundScope.launch(d) { g.events.all.collect { events += it.event } }
        val existing = SessionId("thread-existing")
        fun turnStarts(s: FakeAppServer) = s.calls("turn/start")

        g.startListening()
        runCurrent()
        assertTrue(g.transport.connected)
        assertTrue(StreamStatus.Connected in statuses)

        val history = assertIs<Outcome.Ok<List<MessageWithParts>>>(g.messages(existing)).value
        assertEquals("turn-history-agent", history.last().info.id)

        assertIs<PromptAdmission.Confirmed>((g.prompt(existing, OutgoingPrompt("Inspect the existing thread")) as Outcome.Ok).value)
        val send = turnStarts(first).last().params()
        assertEquals("thread-existing", send.str("threadId"))
        assertEquals("Inspect the existing thread", ((send["input"] as JsonArray)[0] as JsonObject).str("text"))
        assertEquals(SessionStatus.Busy, (g.sessionStatuses() as Outcome.Ok).value[existing])

        first.event("turn/started", jsonOf("threadId" to "thread-existing", "turn" to turn("turn-retry", "inProgress", prefix = "turn-retry")))
        first.event("item/agentMessage/delta", jsonOf("threadId" to "thread-existing", "turnId" to "turn-retry", "itemId" to "turn-retry-agent", "delta" to "streamed answer"))
        first.event("turn/completed", jsonOf("threadId" to "thread-existing", "turn" to turn("turn-retry", prefix = "turn-retry")))
        runCurrent()
        assertEquals(1, events.count { it is GatewayEvent.PartDelta })
        assertEquals(SessionStatus.Idle, (g.sessionStatuses() as Outcome.Ok).value[existing])
        assertEquals(1, events.count { it is GatewayEvent.RunEnded && it.outcome == RunOutcome.Idle })

        first.event("turn/started", jsonOf("threadId" to "thread-existing", "turn" to turn("turn-retry", "inProgress", prefix = "turn-retry")))
        first.event(
            "item/commandExecution/requestApproval",
            jsonOf("threadId" to "thread-existing", "turnId" to "turn-retry", "itemId" to "command-1", "command" to "echo approved"),
            id = 41,
        )
        runCurrent()
        val rejected = (g.pendingPermissions() as Outcome.Ok).value.single()
        assertIs<Outcome.Ok<Unit>>(g.replyPermission(existing, rejected.id, PermissionReply.Reject))
        runCurrent()
        assertEquals(jsonOf("id" to 41, "result" to mapOf("decision" to "decline")), first.sent.last())

        first.event(
            "item/commandExecution/requestApproval",
            jsonOf("threadId" to "thread-existing", "turnId" to "turn-retry", "itemId" to "command-2", "command" to "echo once"),
            id = 42,
        )
        runCurrent()
        val approved = (g.pendingPermissions() as Outcome.Ok).value.single()
        assertIs<Outcome.Ok<Unit>>(g.replyPermission(existing, approved.id, PermissionReply.Once))
        runCurrent()
        assertEquals(jsonOf("id" to 42, "result" to mapOf("decision" to "accept")), first.sent.last())

        threads["thread-existing"] = thread("thread-existing", turns = listOf(turn("turn-abort", "inProgress", prefix = "turn-abort")), name = "Existing thread")
        assertEquals(Outcome.Ok(StopResult.Stopped), g.stop(existing))
        assertEquals(jsonOf("threadId" to "thread-existing", "turnId" to "turn-abort"), first.calls("turn/interrupt").last().params())
        threads["thread-existing"] = thread("thread-existing", turns = listOf(turn("turn-history", prefix = "turn-history")), name = "Existing thread")

        val created = (g.createSession(NewSession()) as Outcome.Ok).value
        assertEquals("thread-new", created.id.raw)
        assertIs<Outcome.Ok<PromptAdmission>>(g.prompt(created.id, OutgoingPrompt("Start new work")))
        val newSend = turnStarts(first).last().params()
        assertEquals("thread-new", newSend.str("threadId"))
        assertEquals("Start new work", ((newSend["input"] as JsonArray)[0] as JsonObject).str("text"))

        failNextTurn = true
        assertEquals(Outcome.Ok(PromptAdmission.Uncertain), g.prompt(existing, OutgoingPrompt("Delivery may be uncertain")))
        assertEquals(3, turnStarts(first).size)

        advanceTimeBy(1100)
        runCurrent()
        assertEquals(1, replacement.calls("initialize").size)
        assertEquals(2, replacement.calls("thread/read").size)
        val resumes = replacement.calls("thread/resume").map { it.params().str("threadId") }
        assertEquals(2, resumes.size)
        assertTrue(resumes.containsAll(listOf("thread-existing", "thread-new")))
        assertTrue(turnStarts(replacement).isEmpty())

        val authoritative = (g.messages(existing) as Outcome.Ok).value
        assertEquals("turn-history-agent", authoritative.last().info.id)
        assertTrue(turnStarts(replacement).isEmpty())
        assertIs<PromptAdmission.Confirmed>((g.prompt(existing, OutgoingPrompt("Retry after history refresh")) as Outcome.Ok).value)
        assertEquals(1, turnStarts(replacement).size)
        g.close()
    }
}
