package io.github.eslamasabry.oc.engine.transcript.ledger

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolPhrase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val S = "s1"

internal fun info(id: String, role: Role, at: Long, done: Long? = null): MessageInfo =
    MessageInfo(id, S, role, time = MsgTime(at, done))

internal fun text(id: String, mid: String, words: String): Part = Part(id, mid, S, PartType.Text, "text", text = words)

internal fun tool(
    id: String,
    mid: String,
    name: String,
    status: ToolStatus,
    input: Map<String, String> = emptyMap(),
    start: Long? = null,
    end: Long? = null,
    output: String? = null,
): Part =
    Part(
        id,
        mid,
        S,
        PartType.Tool,
        "tool",
        callId = "call-$id",
        toolName = name,
        tool =
            ToolState(
                status = status,
                input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                output = output,
                startedAtMs = start,
                completedAtMs = end,
            ),
    )

internal fun msg(info: MessageInfo, vararg parts: Part): MessageWithParts = MessageWithParts(info, parts.toList().toImmutableList())

class TurnLedgerTest {
    private val prompt = msg(info("m1", Role.User, 1), text("p1", "m1", "fix it"))

    @Test
    fun `B-CHAT-063 turns group by prompt with the same ids as the transcript rows`() {
        val ledger = TurnLedger(S)
        ledger.load(
            listOf(
                msg(info("m0", Role.Assistant, 0)),
                prompt,
                msg(info("m2", Role.Assistant, 2), tool("t1", "m2", "read", ToolStatus.Completed, start = 2, end = 3)),
                msg(info("m3", Role.User, 4), text("p2", "m3", "again")),
            )
        )
        val turns = ledger.snapshot().turns
        assertEquals(listOf(TurnLedger.HEAD, "m1", "m3"), turns.map { it.turnId })
        assertEquals(listOf("m1", "m2"), turns[1].messages.map { it.info.id })
    }

    @Test
    fun `B-CHAT-117 a late running report never revives a finished call`() {
        val ledger = TurnLedger(S)
        ledger.load(listOf(prompt, msg(info("m2", Role.Assistant, 2))))
        ledger.apply(GatewayEvent.PartUpserted(tool("t1", "m2", "bash", ToolStatus.Completed, start = 2, end = 5)))
        ledger.apply(GatewayEvent.PartUpserted(tool("t1", "m2", "bash", ToolStatus.Running, start = 2)))
        val part = ledger.snapshot().messages.last().parts.single()
        assertEquals(ToolStatus.Completed, part.tool?.status)
    }

    @Test
    fun `B-CHAT-117 a text delta marks words without copying the stream`() {
        val ledger = TurnLedger(S)
        ledger.load(listOf(prompt, msg(info("m2", Role.Assistant, 2), text("x", "m2", ""))))
        assertTrue(ledger.apply(GatewayEvent.PartDelta(SessionId(S), MessageId("m2"), PartId("x"), DeltaField.Text, "Hel")))
        assertFalse(ledger.apply(GatewayEvent.PartDelta(SessionId(S), MessageId("m2"), PartId("x"), DeltaField.Text, "lo")))
        assertEquals("Hel", ledger.snapshot().messages.last().parts.single().text)
    }

    @Test
    fun `B-CHAT-063 another conversation's events change nothing`() {
        val ledger = TurnLedger(S)
        val other = MessageInfo("o1", "s2", Role.User)
        assertFalse(ledger.apply(GatewayEvent.MessageUpserted(other)))
        assertTrue(ledger.snapshot().messages.isEmpty())
    }
}

class TurnWorkTest {
    private val prompt = msg(info("m1", Role.User, 1), text("p1", "m1", "fix it"))

    private fun snapshot(vararg messages: MessageWithParts): LedgerSnapshot =
        TurnLedger(S).also { it.load(messages.toList()) }.snapshot()

    @Test
    fun `B-CHAT-063 the work line counts what the turn did and names every step in plain words`() {
        val s =
            snapshot(
                prompt,
                msg(
                    info("m2", Role.Assistant, 2, done = 9),
                    tool("t1", "m2", "read", ToolStatus.Completed, mapOf("filePath" to "/p/a.kt"), 2, 3),
                    tool("t2", "m2", "bash", ToolStatus.Completed, mapOf("command" to "ls"), 3, 4_200 + 3),
                    tool("t3", "m2", "mcp__github__create_issue", ToolStatus.Completed, start = 5, end = 6),
                    text("x", "m2", "Done."),
                ),
            )
        val work = assertNotNull(turnWork(s, busy = false)["m1"])
        assertEquals(WorkCounts(read = 1, ran = 1, other = 1, steps = 1), work.counts)
        assertEquals(WorkLineState.Done, work.state)
        assertEquals(listOf(ToolPhrase.Read, ToolPhrase.Shell, ToolPhrase.McpTool), work.steps.map { it.facts.phrase })
        // B-CHAT-111: the raw id never is the words.
        assertFalse(work.steps[2].facts.titleArgs.any { it.contains("mcp__") })
        assertEquals(4_200L, work.steps[1].durationMs)
    }

    @Test
    fun `B-CHAT-063 running work names the step running now and stops spinning when the run ends`() {
        val running =
            msg(
                info("m2", Role.Assistant, 2),
                tool("t1", "m2", "read", ToolStatus.Completed, start = 2, end = 3),
                tool("t2", "m2", "bash", ToolStatus.Running, mapOf("command" to "npm test"), start = 4),
            )
        val live = assertNotNull(turnWork(snapshot(prompt, running), busy = true)["m1"])
        assertEquals(WorkLineState.Running, live.state)
        assertEquals("t2", live.now?.partId)
        val ended = assertNotNull(turnWork(snapshot(prompt, running), busy = false)["m1"])
        assertNull(ended.now)
        assertTrue(ended.steps.none { it.state == StepState.Running })
    }

    @Test
    fun `B-CHAT-063 work that ended on a failure says so and a stopped run reads stopped`() {
        val failed =
            snapshot(prompt, msg(info("m2", Role.Assistant, 2, done = 5), tool("t1", "m2", "bash", ToolStatus.Error, start = 2, end = 3)))
        assertEquals(WorkLineState.EndedFailed, turnWork(failed, busy = false)["m1"]?.state)
        assertEquals(WorkLineState.Stopped, turnWork(failed, busy = false, stopped = true)["m1"]?.state)
        val gotPast =
            snapshot(
                prompt,
                msg(
                    info("m2", Role.Assistant, 2, done = 5),
                    tool("t1", "m2", "bash", ToolStatus.Error, start = 2, end = 3),
                    text("x", "m2", "That failed, so I used another way."),
                ),
            )
        assertEquals(WorkLineState.Done, turnWork(gotPast, busy = false)["m1"]?.state)
    }

    @Test
    fun `B-CHAT-063 a request waiting for the person is never a spinner`() {
        val s = snapshot(prompt, msg(info("m2", Role.Assistant, 2), tool("t1", "m2", "bash", ToolStatus.Running, start = 2)))
        assertEquals(WorkLineState.WaitingForYou, turnWork(s, busy = true, waitingForYou = true)["m1"]?.state)
    }

    @Test
    fun `B-CHAT-117 a spinner stops once later words of its own turn exist`() {
        val s =
            snapshot(
                prompt,
                msg(info("m2", Role.Assistant, 2), tool("t1", "m2", "read", ToolStatus.Running, start = 2), text("x", "m2", "Read it.")),
            )
        assertEquals(StepState.Done, turnWork(s, busy = true)["m1"]?.steps?.single()?.state)
    }

    @Test
    fun `B-CHAT-063 a turn without steps has no work line`() {
        val s = snapshot(prompt, msg(info("m2", Role.Assistant, 2, done = 3), text("x", "m2", "Hi")))
        assertTrue(turnWork(s, busy = false).isEmpty())
        assertTrue(turnWork(LedgerSnapshot(0, emptyList(), emptyList()), busy = false).isEmpty())
        assertEquals(persistentListOf<Part>(), persistentListOf())
    }

    @Test
    fun `B-CHAT-112 B-CHAT-117 a history page that left before a live result never overwrites it`() {
        val ledger = TurnLedger(S)
        val running = msg(info("m2", Role.Assistant, 2), tool("t1", "m2", "bash", ToolStatus.Running, start = 2))
        ledger.load(listOf(prompt, running))
        val fence = ledger.historyVersion()
        // While the page is on its way, the call finishes live.
        ledger.apply(GatewayEvent.PartUpserted(tool("t1", "m2", "bash", ToolStatus.Completed, start = 2, end = 5, output = "BUILD OK")))
        ledger.apply(GatewayEvent.MessageUpserted(info("m2", Role.Assistant, 2, done = 6)))
        // The page still says running (its snapshot is older).
        ledger.load(listOf(prompt, running), fence)
        val m2 = ledger.snapshot().messages.single { it.info.id == "m2" }
        val call = m2.parts.single().tool!!
        assertEquals(ToolStatus.Completed, call.status)
        assertEquals("BUILD OK", call.output)
        assertEquals(6L, m2.info.time?.completedMs)
        // A page read after the change wins again.
        ledger.load(listOf(prompt, msg(info("m2", Role.Assistant, 2, done = 7), tool("t1", "m2", "bash", ToolStatus.Completed, output = "x"))), ledger.historyVersion())
        assertEquals(7L, ledger.snapshot().messages.single { it.info.id == "m2" }.info.time?.completedMs)
    }

    @Test
    fun `B-CHAT-100 equal timestamps keep the server's order so the work stays in its turn`() {
        val ledger = TurnLedger(S)
        ledger.load(
            listOf(
                msg(info("z-user", Role.User, 100), text("p", "z-user", "go")),
                msg(info("x-step", Role.Assistant, 100), tool("t1", "x-step", "bash", ToolStatus.Completed, mapOf("command" to "ls"), 100, 101)),
                msg(info("a-final", Role.Assistant, 100, done = 102), text("f", "a-final", "done")),
            )
        )
        val snapshot = ledger.snapshot()
        assertEquals(listOf("z-user", "x-step", "a-final"), snapshot.messages.map { it.info.id })
        assertEquals(listOf("z-user"), snapshot.turns.map { it.turnId })
    }
}
