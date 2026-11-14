package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnLedger
import io.github.eslamasabry.oc.engine.transcript.ledger.endsOnRefusal
import io.github.eslamasabry.oc.engine.transcript.ledger.turnWork
import io.github.eslamasabry.oc.engine.transcript.turnfacts.editedFileStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Cache-first (CF): a conversation's last page is kept as it read and draws the same rows and facts again. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeptTranscriptTest {
    private val json = Json { encodeDefaults = false }

    private fun through(kept: KeptTranscript): KeptTranscript =
        json.decodeFromString(KeptTranscript.serializer(), json.encodeToString(KeptTranscript.serializer(), kept))

    private fun info(id: String, role: Role, at: Long, done: Long? = at + 1) =
        MessageInfo(id, Ev.S, role, providerId = "prov", modelId = "model", time = MsgTime(at, done))

    private fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    private fun words(id: String, mid: String, text: String) = Part(id, mid, Ev.S, PartType.Text, "text", text = text)

    private fun tool(
        id: String,
        mid: String,
        name: String,
        status: ToolStatus,
        input: Map<String, String>,
        output: String? = null,
        metadata: JsonObject? = null,
    ) =
        Part(
            id,
            mid,
            Ev.S,
            PartType.Tool,
            "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status = status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    metadata = metadata,
                    startedAtMs = 10,
                    completedAtMs = 20,
                ),
        )

    private fun snapshot(messages: List<MessageWithParts>): LedgerSnapshot =
        TurnLedger(Ev.S).also { it.load(messages) }.snapshot()

    @Test
    fun `CF-10 the kept page carries the words the reply streamed, not only the ledger's first words`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = TranscriptHandle(Ev.S, dispatcher, backgroundScope) { testScheduler.currentTime }
        backgroundScope.launch { live.state.collect {} }
        Ev.prompt("u1", "Fix the login bug").forEach(live::submit)
        live.submit(Ev.assistant("a1", completed = 5))
        live.submit(Ev.text("a1", "a1-t"))
        live.submit(Ev.delta("a1", "a1-t", "Done: the token check "))
        live.submit(Ev.delta("a1", "a1-t", "now runs first."))
        live.awaitIdle()

        // The ledger keeps a streamed reply's first delta only.
        val ledger =
            listOf(
                msg(info("u1", Role.User, 1), words("u1-t", "u1", "Fix the login bug")),
                msg(info("a1", Role.Assistant, 2), words("a1-t", "a1", "Done: the token check ")),
            )
        val kept = through(KeptTranscript.fit("Login", KeptTranscript.withWords(ledger, live.keptMessages()), 64 * 1024)!!)

        assertEquals("Login", kept.title)
        assertEquals("Done: the token check now runs first.", kept.messages().last().parts.single().text)
        assertEquals("Fix the login bug", kept.messages().first().parts.single().text)
        live.close()
    }

    @Test
    fun `CF-11 fitting keeps the newest messages under the cap and trims long tool text`() {
        val messages =
            (0 until 40).map { i ->
                msg(
                    info("m$i", if (i % 2 == 0) Role.User else Role.Assistant, i.toLong()),
                    words("p$i", "m$i", "message $i ".repeat(20)),
                    tool("t$i", "m$i", "read", ToolStatus.Completed, mapOf("filePath" to "/a/f$i.kt"), "x".repeat(20_000)),
                )
            }
        val fitted = KeptTranscript.fit("Title", messages, maxBytes = 64 * 1024)!!
        assertTrue(fitted.history.isNotEmpty() && fitted.history.size < messages.size)
        assertEquals("m39", fitted.history.last().info.id, "the newest message is always kept")
        assertTrue(fitted.messages().all { m -> m.parts.all { (it.tool?.output?.length ?: 0) <= KeptTranscript.MAX_TOOL_TEXT } })
        val size = Json.encodeToString(KeptTranscript.serializer(), fitted).encodeToByteArray().size
        assertTrue(size <= 64 * 1024, "fits: $size")

        assertNull(KeptTranscript.fit("Title", messages.takeLast(1), maxBytes = 100), "nothing fits a tiny cap")
    }

    @Test
    fun `REOPEN-1 the kept page keeps what the turn facts read, so a declined step and an edited file read as they did`() {
        val declined = "The user rejected permission to use this specific tool call."
        val messages =
            listOf(
                msg(info("u1", Role.User, 1), words("u1-t", "u1", "create a file s2.txt with one line")),
                msg(
                    info("a1", Role.Assistant, 2).copy(tokens = Tokens(input = 900, output = 40), cost = 0.01),
                    Part("a1-r", "a1", Ev.S, PartType.Reasoning, "reasoning", text = "A new file."),
                    tool(
                        "w1",
                        "a1",
                        "write",
                        ToolStatus.Completed,
                        mapOf("filePath" to "/root/projects/s2.txt", "content" to "hello\n"),
                        "Wrote file successfully.",
                        JsonObject(mapOf("exists" to JsonPrimitive(false))),
                    ),
                    words("a1-t", "a1", "Created s2.txt with a single line: hello"),
                ),
                msg(info("u2", Role.User, 10), words("u2-t", "u2", "create a file s2b.txt with one line")),
                msg(
                    info("a2", Role.Assistant, 11).copy(errorText = "MessageAbortedError", errorKind = MessageErrorKind.Aborted),
                    tool("w2", "a2", "write", ToolStatus.Error, mapOf("filePath" to "/root/projects/s2b.txt", "content" to "hi\n"), declined),
                ),
            )
        val back = through(KeptTranscript.fit("say hello in one line", messages, 64 * 1024)!!).messages()

        assertEquals(messages, back, "nothing short is lost or changed on the way through the store")
        val live = snapshot(messages)
        val kept = snapshot(back)
        assertEquals(turnWork(live, busy = false), turnWork(kept, busy = false), "the same steps, states and counts")
        assertEquals(StepState.Declined, turnWork(kept, busy = false).getValue("u2").steps.single().state)
        assertTrue(endsOnRefusal(kept.turns.last()), "the decline is still the person's")
        assertEquals(editedFileStats(live.turns[0].messages), editedFileStats(kept.turns[0].messages))
        assertEquals(listOf("/root/projects/s2.txt"), editedFileStats(kept.turns[0].messages).map { it.path })
    }

    @Test
    fun `REOPEN-2 a long file's content is trimmed but its line count, its path and its numbers stay`() {
        val content = (1..3_000).joinToString("\n") { "line $it" } + "\n"
        val metadata =
            JsonObject(
                mapOf(
                    "exists" to JsonPrimitive(false),
                    "filediff" to
                        JsonObject(
                            mapOf(
                                "before" to JsonPrimitive(""),
                                "after" to JsonPrimitive(content),
                                "additions" to JsonPrimitive(3_000),
                                "deletions" to JsonPrimitive(0),
                            )
                        ),
                )
            )
        val messages =
            listOf(
                msg(info("u1", Role.User, 1), words("u1-t", "u1", "write the long file")),
                msg(
                    info("a1", Role.Assistant, 2),
                    tool("w1", "a1", "write", ToolStatus.Completed, mapOf("filePath" to "/p/long.txt", "content" to content), "ok", metadata),
                ),
            )
        val back = through(KeptTranscript.fit(null, messages, 64 * 1024)!!).messages()
        val state = back.last().parts.single().tool!!

        val kept = (state.input["content"] as JsonPrimitive).content
        assertTrue(kept.length < content.length, "the content was trimmed")
        assertEquals(content.count { it == '\n' }, kept.count { it == '\n' }, "its lines are still counted right")
        assertEquals("/p/long.txt", (state.input["filePath"] as JsonPrimitive).content)
        assertEquals(editedFileStats(messages), editedFileStats(back), "the same +N for the new file")
        assertEquals(
            (metadata["filediff"] as JsonObject)["additions"],
            (state.metadata!!["filediff"] as JsonObject)["additions"],
        )
    }
}
