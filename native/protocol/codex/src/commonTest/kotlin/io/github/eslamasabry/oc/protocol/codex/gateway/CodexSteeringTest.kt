package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.codexKind
import io.github.eslamasabry.oc.protocol.codex.gateway
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import io.github.eslamasabry.oc.protocol.codex.thread
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/codex_steering_test.dart. */
class CodexSteeringTest {
    private val sid = SessionId("thread-1")

    private fun active(id: String = "turn-active", status: String = "inProgress") = jsonOf("id" to id, "status" to status, "items" to emptyList<Any>())

    private inner class Fixture(scope: TestScope) {
        var current: JsonObject = thread(busy = true, turns = listOf(active()))
        var steerResult: JsonObject? = jsonOf("turnId" to "turn-active")
        val server = FakeAppServer().apply {
            answers["thread/read"] = { jsonOf("thread" to current) }
            answers["thread/resume"] = { jsonOf("thread" to current) }
            answers["turn/start"] = { jsonOf("turn" to active("turn-new")) }
            answers["turn/steer"] = { steerResult ?: JsonObject(emptyMap()) }
        }
        val sockets = FakeSockets(server)
        val g = scope.gateway(sockets)

        fun requests(method: String) = server.calls(method)

        fun expectNoFallback() {
            assertTrue(requests("turn/start").isEmpty())
            assertTrue(requests("turn/interrupt").isEmpty())
        }
    }

    private fun steer(text: String = "Change direction") = OutgoingPrompt(text, delivery = PromptDelivery.Steer)

    private fun <T> Outcome<T>.kind() = failureOrNull()?.codexKind()

    @Test
    fun `B-AGENT-070 an explicit steer targets the authoritative active turn without resuming`() = runTest {
        val f = Fixture(this)
        assertTrue(f.g.capabilities.value.promptSteering)
        assertTrue(f.g.capabilities.value.withGenUi(true).promptSteering)
        assertEquals(false, ServerCapabilities().promptSteering)
        assertNull(f.g.midTurnPrompt(SessionId("unknown")))
        val sent = f.g.prompt(
            sid,
            OutgoingPrompt(
                "Keep the public API unchanged.",
                model = ModelSelection(ModelRef("codex", "gpt-6.1-sol"), "high"),
                delivery = PromptDelivery.Steer,
            ),
        )
        assertEquals(Outcome.Ok(PromptAdmission.Confirmed("turn-active")), sent)
        assertEquals(jsonOf("threadId" to "thread-1", "includeTurns" to true), f.requests("thread/read").single().params())
        assertEquals(
            jsonOf(
                "threadId" to "thread-1",
                "input" to listOf(mapOf("type" to "text", "text" to "Keep the public API unchanged.", "text_elements" to emptyList<Any>())),
                "expectedTurnId" to "turn-active",
            ),
            f.requests("turn/steer").single().params(),
        )
        assertTrue(f.requests("thread/resume").isEmpty())
        assertNull(f.g.midTurnPrompt(sid))
        f.g.session(sid)
        assertEquals(MidTurnPrompt.JoinsTurn, f.g.midTurnPrompt(sid))
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a default send while busy steers and while idle starts a new turn`() = runTest {
        for (busy in listOf(true, false)) {
            val f = Fixture(this)
            f.current = thread(busy = busy, turns = listOf(active(status = if (busy) "inProgress" else "completed")))
            f.g.messages(sid)
            assertIs<Outcome.Ok<*>>(f.g.prompt(sid, OutgoingPrompt("Next instruction")))
            assertEquals(if (busy) 1 else 0, f.requests("turn/steer").size)
            assertEquals(if (busy) 0 else 1, f.requests("turn/start").size)
            assertTrue(f.requests("turn/interrupt").isEmpty())
            f.g.close()
        }
    }

    @Test
    fun `B-AGENT-070 an explicit queue or unsupported attachment never dispatches input`() = runTest {
        val f = Fixture(this)
        assertEquals(CodexFailureKind.Unavailable, f.g.prompt(sid, OutgoingPrompt("Wait for later", delivery = PromptDelivery.Queue)).kind())
        val attachment = PromptAttachment(mime = "text/plain", filename = "fixture.txt", url = "data:text/plain;base64,Zml4dHVyZQ==")
        assertEquals(
            CodexFailureKind.Unavailable,
            f.g.prompt(sid, OutgoingPrompt("Inspect attachment", delivery = PromptDelivery.Steer, attachments = persistentListOf(attachment))).kind(),
        )
        assertTrue(f.requests("turn/steer").isEmpty())
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a missing or ambiguous running turn cannot become a new turn`() = runTest {
        for (turns in listOf(emptyList(), listOf(active(status = "completed")), listOf(active(), active("turn-another")))) {
            val f = Fixture(this)
            f.current = thread(busy = true, turns = turns)
            assertEquals(CodexFailureKind.StaleRequest, f.g.prompt(sid, steer()).kind())
            assertTrue(f.requests("turn/steer").isEmpty())
            f.expectNoFallback()
            f.g.close()
        }
    }

    @Test
    fun `B-AGENT-070 a changed running turn rejects stale input instead of steering its replacement`() = runTest {
        val f = Fixture(this)
        f.g.messages(sid)
        f.current = thread(busy = true, turns = listOf(active("turn-replacement")))
        assertEquals(CodexFailureKind.StaleRequest, f.g.prompt(sid, steer("Instruction for the old turn")).kind())
        assertTrue(f.requests("turn/steer").isEmpty())
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a turn completed during preflight cannot receive stale steering`() = runTest {
        val f = Fixture(this)
        f.g.messages(sid)
        f.server.gated += "thread/read"
        val pending = async { f.g.prompt(sid, steer("Instruction for the completed turn")) }
        runCurrent()
        assertTrue(f.server.isHeld("thread/read"))
        f.server.event("turn/completed", jsonOf("threadId" to "thread-1", "turn" to active(status = "completed")))
        f.server.release("thread/read")
        runCurrent()
        assertEquals(CodexFailureKind.StaleRequest, pending.await().kind())
        assertTrue(f.requests("turn/steer").isEmpty())
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a foreign project or malformed active ids fail before steering`() = runTest {
        for (t in listOf(
            thread(cwd = "/other-project", busy = true, turns = listOf(active())),
            thread(busy = true, turns = listOf(active(id = ""))),
            JsonObject(thread(busy = true, turns = listOf(active())) + ("id" to kotlinx.serialization.json.JsonPrimitive("thread-other"))),
        )) {
            val f = Fixture(this)
            f.current = t
            assertTrue(f.g.prompt(sid, steer()) is Outcome.Failed)
            assertTrue(f.requests("turn/steer").isEmpty())
            f.expectNoFallback()
            f.g.close()
        }
    }

    @Test
    fun `B-AGENT-070 scope changes, including away and back, fence the read and the steer acknowledgement`() = runTest {
        for (method in listOf("thread/read", "turn/steer")) {
            for (returnToSource in listOf(false, true)) {
                val f = Fixture(this)
                f.server.gated += method
                val pending = async { f.g.prompt(sid, steer("Scoped instruction")) }
                runCurrent()
                assertTrue(f.server.isHeld(method))
                f.g.setLocation(ServerLocation(directory = "/other-project"))
                if (returnToSource) f.g.setLocation(ServerLocation(directory = "/project"))
                f.server.release(method)
                runCurrent()
                val label = "$method back=$returnToSource"
                if (method == "thread/read") {
                    assertEquals(CodexFailureKind.ScopeMismatch, pending.await().kind(), label)
                } else {
                    // The steer went out and its acknowledgement crossed the change: delivery is uncertain.
                    assertEquals(Outcome.Ok(PromptAdmission.Uncertain), pending.await(), label)
                }
                assertEquals(if (method == "thread/read") 0 else 1, f.requests("turn/steer").size, label)
                assertTrue((f.g.sessionStatuses() as Outcome.Ok).value.isEmpty(), label)
                f.expectNoFallback()
                f.g.close()
            }
        }
    }

    @Test
    fun `B-AGENT-070 a disconnect during the active-turn read cannot reconnect and mutate`() = runTest {
        val f = Fixture(this)
        f.server.gated += "thread/read"
        val pending = async { f.g.prompt(sid, steer()) }
        runCurrent()
        f.server.drop()
        runCurrent()
        assertTrue(pending.await() is Outcome.Failed)
        assertEquals(1, f.sockets.opened)
        assertTrue(f.requests("turn/steer").isEmpty())
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a remote rejection stays sanitized and never falls back to start`() = runTest {
        val f = Fixture(this)
        f.server.errors["turn/steer"] = -32600
        val failure = f.g.prompt(sid, steer()).failureOrNull()!!
        assertEquals(CodexFailureKind.Unavailable, failure.codexKind())
        assertNull(failure.details)
        assertTrue("sk-fixture-secret" !in failure.toString() && "private remote" !in failure.toString())
        assertEquals(1, f.requests("turn/steer").size)
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a missing or mismatched acknowledgement blocks resend until reconciliation`() = runTest {
        for (result in listOf(JsonObject(emptyMap()), jsonOf("turnId" to "turn-other"))) {
            val f = Fixture(this)
            f.steerResult = result
            assertEquals(Outcome.Ok(PromptAdmission.Uncertain), f.g.prompt(sid, steer()))
            assertEquals(CodexFailureKind.DeliveryUnknown, f.g.prompt(sid, steer()).kind())
            assertEquals(1, f.requests("turn/steer").size)
            f.expectNoFallback()
            f.g.close()
        }
    }

    @Test
    fun `B-AGENT-070 a lost steer acknowledgement blocks resend without reconnecting`() = runTest {
        val f = Fixture(this)
        f.server.gated += "turn/steer"
        val pending = async { f.g.prompt(sid, steer()) }
        runCurrent()
        assertTrue(f.server.isHeld("turn/steer"))
        f.server.drop()
        runCurrent()
        assertEquals(Outcome.Ok(PromptAdmission.Uncertain), pending.await())
        assertEquals(CodexFailureKind.DeliveryUnknown, f.g.prompt(sid, steer()).kind())
        assertEquals(1, f.sockets.opened)
        assertEquals(1, f.requests("turn/steer").size)
        f.expectNoFallback()
        f.g.close()
    }

    @Test
    fun `B-AGENT-070 a completion before the steer acknowledgement remains idle`() = runTest {
        val f = Fixture(this)
        f.g.messages(sid)
        f.server.gated += "turn/steer"
        val pending = async { f.g.prompt(sid, steer()) }
        runCurrent()
        f.server.event("turn/completed", jsonOf("threadId" to "thread-1", "turn" to active(status = "completed")))
        f.server.release("turn/steer")
        runCurrent()
        assertIs<Outcome.Ok<PromptAdmission>>(pending.await())
        assertEquals(SessionStatus.Idle, (f.g.sessionStatuses() as Outcome.Ok).value[sid])
        assertEquals(1, f.requests("turn/steer").size)
        f.expectNoFallback()
        f.g.close()
    }
}
