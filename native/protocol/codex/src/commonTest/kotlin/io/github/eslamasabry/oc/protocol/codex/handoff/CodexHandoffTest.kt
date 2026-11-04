package io.github.eslamasabry.oc.protocol.codex.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.protocol.codex.CodexCapabilities
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.gateway
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/codex_agent_handoff_test.dart. */
class CodexHandoffTest {
    private val sid = SessionId("thread-source")

    private fun turn(index: Int, userText: String? = null) =
        jsonOf(
            "id" to "turn-$index",
            "status" to "completed",
            "itemsView" to "full",
            "startedAt" to 100 + index * 2,
            "completedAt" to 101 + index * 2,
            "items" to listOf(
                mapOf("id" to "user-$index", "type" to "userMessage", "content" to listOf(mapOf("type" to "text", "text" to (userText ?: "Prompt $index")))),
                mapOf(
                    "id" to "tool-$index", "type" to "commandExecution", "command" to "dart analyze lib/parser.dart", "cwd" to "/project",
                    "status" to "completed", "exitCode" to 0, "aggregatedOutput" to "Analysis passed $index",
                ),
                mapOf("id" to "agent-$index", "type" to "agentMessage", "phase" to "final_answer", "text" to "Answer $index"),
            ),
        )

    private fun thread(turns: List<Any>) =
        jsonOf(
            "id" to "thread-source", "cwd" to "/project", "name" to "Repair parser", "createdAt" to 100, "updatedAt" to 999,
            "historyMode" to "legacy", "model" to "gpt-6.1-sol", "status" to mapOf("type" to "idle"), "turns" to turns,
        )

    private inner class Fixture(scope: TestScope) {
        var current: JsonObject = thread(listOf(turn(0)))
        val server = FakeAppServer().apply { answers["thread/read"] = { jsonOf("thread" to current) } }
        val g = scope.gateway(FakeSockets(server))

        suspend fun read(isCurrent: () -> Boolean = { true }, maxPages: Int = 100) = readCodexHandoffHistory(g, sid, isCurrent, maxPages)

        fun reads() = server.calls("thread/read")

        fun expectReadOnly() = assertTrue(server.methods().all { it in setOf("initialize", "initialized", "thread/read") }, server.methods().toString())
    }

    private fun messages(read: HandoffHistoryRead) = assertIs<HandoffHistoryRead.Read>(read).messages

    @Test
    fun `B-AGENT-055 a full persisted Codex thread is read once without resuming it`() = runTest {
        val f = Fixture(this)
        f.current = thread((0 until 30).map { turn(it) })
        val m = messages(f.read())
        assertEquals(90, m.size)
        assertEquals("user-0", m.first().id)
        assertEquals("agent-29", m.last().id)
        for (i in 0 until 30) {
            assertEquals("Prompt $i", (m[i * 3].parts.single() as HandoffPart.Text).text)
            val tool = m[i * 3 + 1].parts.single() as HandoffPart.Tool
            assertEquals("shell", tool.name)
            assertEquals("Analysis passed $i", tool.output)
            assertEquals("Answer $i", (m[i * 3 + 2].parts.single() as HandoffPart.Text).text)
        }
        assertEquals(jsonOf("threadId" to "thread-source", "includeTurns" to true), f.reads().single().params())
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-055 fully hydrated paginated Codex threads are accepted`() = runTest {
        val f = Fixture(this)
        f.current = JsonObject(thread(listOf(turn(0))) + ("historyMode" to JsonPrimitive("paginated")))
        assertEquals(listOf("user-0", "tool-0", "agent-0"), messages(f.read()).map { it.id })
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-055 a full snapshot still obeys the common history budget`() = runTest {
        val f = Fixture(this)
        f.current = thread((0 until 30).map { turn(it) })
        assertEquals(HandoffHistoryRead.Refused(HandoffHistoryFailureKind.PageLimit), f.read(maxPages = 1))
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-055 the record drops the oldest whole turns and preserves the newest`() = runTest {
        val f = Fixture(this)
        f.current = thread(listOf(turn(0, "old ".repeat(5000)), turn(1, "Newest exact prompt\nالعربية")))
        val record = assertIs<HandoffRecordResult.Built>(
            buildHandoffRecord(HandoffSource("thread-source", directory = "/project"), messages(f.read()), "Waiting for you", maxBytes = 3000),
        ).record
        assertEquals(3, record.omittedMessages)
        assertFalse("Answer 0" in record.markdown)
        assertTrue("Newest exact prompt\nالعربية" in record.markdown)
        assertTrue("Answer 1" in record.markdown)
        assertTrue("Analysis passed 1" in record.markdown)
        assertTrue(record.byteLength <= 3000)
        f.g.close()
    }

    @Test
    fun `B-AGENT-056 the record uses shared redaction and leaves server data intact`() = runTest {
        val prompt = "  Fix this\n```dart\nparse(\"\");\n```\nالعربية"
        val secret = "synthetic-private-access-token"
        val f = Fixture(this)
        val t = turn(0, prompt)
        val items = (t["items"] as kotlinx.serialization.json.JsonArray).toMutableList()
        items[1] = JsonObject(items[1] as JsonObject + ("aggregatedOutput" to JsonPrimitive("api_key=$secret\nAnalysis passed")))
        f.current = thread(listOf(JsonObject(t + ("items" to kotlinx.serialization.json.JsonArray(items)))))
        val before = f.current.toString()
        val m = messages(f.read())
        val record = assertIs<HandoffRecordResult.Built>(buildHandoffRecord(HandoffSource("thread-source", directory = "/project"), m, "Waiting for you")).record
        for (text in listOf(prompt, "Answer 0", "dart analyze lib/parser.dart", "Analysis passed")) assertTrue(text in record.markdown, text)
        assertFalse(secret in record.markdown)
        assertFalse("fixture-token" in record.markdown)
        assertTrue(record.redacted)
        assertEquals(before, f.current.toString())
        assertTrue(secret in (m[1].parts.single() as HandoffPart.Tool).output!!)
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-056 the handoff read fails closed on malformed input`() = runTest {
        val good = thread(listOf(turn(0)))
        val cases = mapOf(
            "another thread" to JsonObject(good + ("id" to JsonPrimitive("other-thread"))),
            "another project" to JsonObject(good + ("cwd" to JsonPrimitive("/elsewhere"))),
            "missing turns" to JsonObject(good - "turns"),
            "malformed turns" to JsonObject(thread(emptyList()) + ("turns" to JsonPrimitive("not-a-list"))),
            "summary-only turns" to thread(listOf(JsonObject(turn(0) + ("itemsView" to JsonPrimitive("summary"))))),
            "unloaded turns" to thread(listOf(JsonObject(turn(0) + ("itemsView" to JsonPrimitive("notLoaded"))))),
            "missing item view" to thread(listOf(JsonObject(turn(0) - "itemsView"))),
            "malformed item" to thread(listOf(JsonObject(turn(0) + ("items" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("not-an-item"))))))),
            "repeated item id" to thread(listOf(turn(0), turn(0))),
        )
        for ((name, t) in cases) {
            val f = Fixture(this)
            f.current = t
            assertIs<HandoffHistoryRead.Refused>(f.read(), name)
            f.expectReadOnly()
            f.g.close()
        }
    }

    @Test
    fun `B-AGENT-056 a project change away and back invalidates an in-flight read`() = runTest {
        val f = Fixture(this)
        f.server.gated += "thread/read"
        val pending = async { f.read() }
        runCurrent()
        assertTrue(f.server.isHeld("thread/read"))
        f.g.setLocation(ServerLocation(directory = "/elsewhere"))
        f.g.setLocation(ServerLocation(directory = "/project"))
        f.server.release("thread/read")
        runCurrent()
        assertIs<HandoffHistoryRead.Refused>(pending.await())
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-056 a caller selection change invalidates an in-flight read`() = runTest {
        val f = Fixture(this)
        f.server.gated += "thread/read"
        var current = true
        val pending = async { f.read(isCurrent = { current }) }
        runCurrent()
        current = false
        f.server.release("thread/read")
        runCurrent()
        assertEquals(HandoffHistoryRead.Refused(HandoffHistoryFailureKind.StaleScope), pending.await())
        f.g.close()
    }

    @Test
    fun `B-AGENT-056 a disconnect invalidates an in-flight snapshot without resuming`() = runTest {
        val f = Fixture(this)
        f.server.gated += "thread/read"
        val pending = async { f.read() }
        runCurrent()
        f.server.drop()
        f.server.release("thread/read")
        runCurrent()
        assertIs<HandoffHistoryRead.Refused>(pending.await())
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-057 a Codex source prepares a reviewable Claude draft without mutations`() = runTest {
        val f = Fixture(this)
        f.current = thread((0 until 30).map { turn(it) })
        val claudeTarget = ServerCapabilities(agentHandoffFiles = true)
        val draft = assertIs<Outcome.Ok<HandoffDraft>>(
            prepareCodexHandoffDraft(f.g, sid, claudeTarget, "Waiting for you", "codex-review", isCurrent = { true }),
        ).value
        assertTrue("Prompt 0" in draft.record.markdown)
        assertTrue("Answer 29" in draft.record.markdown)
        assertEquals(".handoff/codex-review/record.md", draft.relativePath)
        assertTrue(draft.relativePath in draft.prompt)
        f.expectReadOnly()
        f.g.close()
    }

    @Test
    fun `B-AGENT-057 a Codex destination stays unavailable without a qualified file writer`() = runTest {
        val f = Fixture(this)
        assertFalse(CodexCapabilities.Base.agentHandoffFiles)
        assertFalse(f.g.capabilities.value.agentHandoffFiles)
        assertEquals(
            Outcome.Failed(Failure.Unavailable(FeatureId("agentHandoffFiles"))),
            prepareCodexHandoffDraft(f.g, sid, f.g.capabilities.value, "Waiting for you", "codex-review", isCurrent = { true }),
        )
        assertTrue(f.server.sent.isEmpty())
        f.g.close()
    }
}
