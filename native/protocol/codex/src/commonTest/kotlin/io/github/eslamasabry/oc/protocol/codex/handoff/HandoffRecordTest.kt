package io.github.eslamasabry.oc.protocol.codex.handoff

import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/codex_handoff_record_test.dart. */
class HandoffRecordTest {
    private fun thread(turns: List<JsonObject>) = jsonOf("id" to "source", "historyMode" to "legacy", "turns" to turns)

    private fun turn(items: List<Any>, id: String = "turn-1", status: String = "completed") =
        jsonOf("id" to id, "status" to status, "itemsView" to "full", "items" to items)

    private fun user(text: String, id: String = "user-1") =
        jsonOf("id" to id, "type" to "userMessage", "content" to listOf(mapOf("type" to "text", "text" to text)))

    private fun answer(text: String, id: String = "answer-1", phase: String? = "final_answer") =
        jsonOf("id" to id, "type" to "agentMessage", "phase" to phase, "text" to text)

    private fun record(thread: JsonObject, maxBytes: Int = 98_304): HandoffRecordResult =
        buildHandoffRecord(
            HandoffSource(id = "source", directory = "/repo", agent = "Codex", model = "gpt-6.1-sol"),
            codexHandoffMessages(thread),
            lastState = "waiting for you",
            maxBytes = maxBytes,
        )

    private fun built(thread: JsonObject, maxBytes: Int = 98_304): HandoffRecord =
        assertIs<HandoffRecordResult.Built>(record(thread, maxBytes)).record

    private fun invalid(thread: JsonObject) {
        val e = assertFailsWith<CodexException> { codexHandoffMessages(thread) }
        assertEquals(CodexFailureKind.InvalidResponse, e.kind)
        assertFalse("secret" in e.toString())
    }

    private val token = "sk-test-super-secret-0123456789abcdefghijklmnop"

    @Test
    fun `B-AGENT-058 keeps full prompts and final text, excluding reasoning and commentary`() {
        val prompt = "  Keep this exactly\nالعربية 🚀\n```dart\nx();\n```"
        val longAnswer = "界".repeat(6000)
        val r = built(
            thread(
                listOf(
                    turn(
                        listOf(
                            user(prompt),
                            jsonOf("id" to "reasoning-1", "type" to "reasoning", "summary" to listOf("private reasoning excluded"), "content" to listOf("private reasoning body excluded")),
                            answer("interim commentary excluded", id = "commentary-1", phase = "commentary"),
                            answer(longAnswer),
                            answer("Legacy visible answer", id = "legacy-1", phase = null),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(prompt in r.markdown)
        assertTrue(longAnswer in r.markdown)
        assertTrue("Legacy visible answer" in r.markdown)
        assertTrue("did not record whether" in r.markdown)
        assertFalse("private reasoning" in r.markdown)
        assertFalse("interim commentary" in r.markdown)
        assertEquals(r.markdown.encodeToByteArray().size, r.byteLength)
    }

    @Test
    fun `B-AGENT-058 retains command cwd and exit outcome, masking before shortening`() {
        val r = built(
            thread(
                listOf(
                    turn(
                        listOf(
                            user("Run the tests"),
                            jsonOf(
                                "id" to "command-1", "type" to "commandExecution", "command" to "dart test", "cwd" to "/repo",
                                "status" to "completed", "exitCode" to 1, "aggregatedOutput" to "Failed\nAPI_KEY=$token",
                                "arbitrary" to "excluded arbitrary metadata",
                            ),
                        ),
                    ),
                ),
            ),
        )
        for (text in listOf("dart test", "/repo", "exitCode: 1", "Failed", "status: \"error\"")) assertTrue(text in r.markdown, text)
        assertFalse(token in r.markdown)
        assertFalse("excluded arbitrary metadata" in r.markdown)
        assertTrue(r.redacted)
    }

    @Test
    fun `B-AGENT-058 lists successful paths and step outcomes without patch bodies`() {
        val r = built(
            thread(
                listOf(
                    turn(
                        listOf(user("Fix the parser")) +
                            listOf("completed", "failed", "declined").map { status ->
                                jsonOf(
                                    "id" to "file-$status", "type" to "fileChange", "status" to status,
                                    "changes" to listOf(
                                        mapOf(
                                            "path" to "lib/$status.dart",
                                            "kind" to mapOf("type" to "update", "move_path" to if (status == "completed") "lib/moved.dart" else null),
                                            "diff" to "raw patch excluded",
                                        ),
                                    ),
                                )
                            },
                    ),
                ),
            ),
        )
        val provenance = r.markdown.split("## Tasks").first()
        assertTrue("lib/completed.dart" in provenance)
        assertTrue("lib/moved.dart" in provenance)
        assertFalse("lib/failed.dart" in provenance)
        assertFalse("lib/declined.dart" in provenance)
        assertTrue("lib/failed.dart" in r.markdown)
        assertTrue("not executed" in r.markdown)
        assertFalse("raw patch excluded" in r.markdown)
    }

    @Test
    fun `B-AGENT-058 summarizes known tools through allowlisted fields and text results`() {
        val r = built(
            thread(
                listOf(
                    turn(
                        listOf(
                            user("Continue the work"),
                            jsonOf(
                                "id" to "mcp-1", "type" to "mcpToolCall", "tool" to "inspect", "status" to "completed",
                                "arguments" to mapOf("path" to "lib/parser.dart", "command" to "inspect parser", "url" to "excluded-tool-url", "secret" to "excluded-tool-argument"),
                                "result" to mapOf(
                                    "content" to listOf(
                                        mapOf("type" to "text", "text" to "Found parser\nAPI_KEY=$token"),
                                        mapOf("type" to "resource", "resource" to mapOf("text" to "excluded-resource-body")),
                                    ),
                                    "structuredContent" to mapOf("secret" to "excluded-structured-content"),
                                    "_meta" to mapOf("secret" to "excluded-tool-meta"),
                                ),
                                "error" to mapOf("message" to "excluded-tool-error"),
                            ),
                            jsonOf(
                                "id" to "dynamic-1", "type" to "dynamicToolCall", "tool" to "test", "status" to "completed", "success" to false,
                                "arguments" to mapOf("cmd" to "dart test", "cwd" to "/repo"),
                                "contentItems" to listOf(
                                    mapOf("type" to "inputText", "text" to "Two tests failed"),
                                    mapOf("type" to "inputImage", "imageUrl" to "excluded-image-url"),
                                    mapOf("type" to "inputAudio", "audioUrl" to "excluded-audio-url"),
                                ),
                            ),
                            jsonOf(
                                "id" to "function-1", "type" to "functionCallOutput", "name" to "check",
                                "output" to listOf(
                                    mapOf("type" to "input_text", "text" to "Check result"),
                                    mapOf("type" to "input_image", "image_url" to "excluded-function-image"),
                                    mapOf("type" to "encrypted_content", "encrypted_content" to "excluded-encrypted-content"),
                                ),
                            ),
                            jsonOf(
                                "id" to "collab-1", "type" to "collabAgentToolCall", "tool" to "wait", "status" to "interrupted",
                                "prompt" to "excluded-agent-prompt", "agentsStates" to mapOf("private-id" to "excluded-agent-state"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        for (included in listOf(
            "mcp:inspect", "lib/parser.dart", "inspect parser", "Found parser", "dynamic:test", "Two tests failed",
            "status: \"error\"", "function:check", "Check result", "success not reported", "agent:wait",
            "status: \"cancelled\"", "Non-text result content excluded.",
        )) {
            assertTrue(included in r.markdown, included)
        }
        assertFalse("excluded-" in r.markdown)
        assertFalse(token in r.markdown)
        assertTrue(r.redacted)
    }

    @Test
    fun `B-AGENT-058 discloses plan, compaction, attachments and unsupported items`() {
        val r = built(
            thread(
                listOf(
                    turn(
                        listOf(
                            jsonOf(
                                "id" to "user-1", "type" to "userMessage",
                                "content" to listOf(mapOf("type" to "text", "text" to "Continue"), mapOf("type" to "image", "url" to "data:secret-image-excluded")),
                            ),
                            jsonOf("id" to "plan-1", "type" to "plan", "text" to "1. Check the parser"),
                            jsonOf("id" to "compact-1", "type" to "contextCompaction", "text" to "private compaction metadata excluded"),
                            jsonOf("id" to "unknown-1", "type" to "futureItem", "metadata" to "private unsupported metadata excluded"),
                        ),
                    ),
                ),
            ),
        )
        for (t in listOf("1. Check the parser", "conversation compaction event", "Attachment contents excluded", "unsupported item", "No tasks reported.")) {
            assertTrue(t in r.markdown, t)
        }
        for (t in listOf("private compaction", "private unsupported", "secret-image")) assertFalse(t in r.markdown, t)
    }

    @Test
    fun `B-AGENT-059 bounds concatenated text tool results without silently clipping`() {
        invalid(
            thread(
                listOf(
                    turn(
                        listOf(
                            jsonOf(
                                "id" to "function-1", "type" to "functionCallOutput", "name" to "check",
                                "output" to List(2) { mapOf("type" to "input_text", "text" to "x".repeat(600_000)) },
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `B-AGENT-059 records empty failed, interrupted and running turns without remote error bodies`() {
        val r = built(
            thread(
                listOf(
                    JsonObject(turn(emptyList(), status = "failed") + ("error" to jsonOf("message" to "remote secret error excluded"))),
                    turn(emptyList(), id = "turn-2", status = "interrupted"),
                    turn(emptyList(), id = "turn-3", status = "inProgress"),
                ),
            ),
        )
        assertTrue("The Codex turn failed." in r.markdown)
        assertTrue("interrupted before completion" in r.markdown)
        assertTrue("still in progress" in r.markdown)
        assertFalse("remote secret error" in r.markdown)
    }

    @Test
    fun `B-AGENT-059 keeps newest turns full and refuses an oversized newest turn`() {
        val newest = "界".repeat(200)
        val r = built(
            thread(
                listOf(
                    turn(listOf(user("x".repeat(3000)), answer("Old answer"))),
                    turn(listOf(user(newest, id = "user-2"), answer("New answer", id = "answer-2")), id = "turn-2"),
                ),
            ),
            maxBytes = 2200,
        )
        assertTrue(newest in r.markdown)
        assertTrue("New answer" in r.markdown)
        assertFalse("Old answer" in r.markdown)
        assertEquals(2, r.omittedMessages)
        assertTrue(r.byteLength <= 2200)
        assertIs<HandoffRecordResult.TooLarge>(record(thread(listOf(turn(listOf(user("界".repeat(100_000))))))))
    }

    @Test
    fun `B-AGENT-059 malformed identities, arrays, states and fields fail without content`() {
        val base = thread(emptyList())
        val malformed = listOf(
            jsonOf("id" to "source", "turns" to null),
            JsonObject(base - "historyMode"),
            JsonObject(base + ("historyMode" to kotlinx.serialization.json.JsonPrimitive("unknown"))),
            thread(listOf(jsonOf("status" to "completed", "items" to emptyList<Any>()))),
            thread(listOf(turn(emptyList()), turn(emptyList()))),
            thread(listOf(turn(listOf(user("one"), user("two"))))),
            thread(listOf(turn(emptyList(), status = "unknown"))),
            thread(listOf(JsonObject(turn(emptyList()) + ("itemsView" to kotlinx.serialization.json.JsonPrimitive("summary"))))),
            thread(listOf(JsonObject(turn(emptyList()) + ("itemsView" to kotlinx.serialization.json.JsonPrimitive("notLoaded"))))),
            thread(listOf(JsonObject(turn(emptyList()) - "itemsView"))),
            thread(listOf(jsonOf("id" to "turn", "status" to "completed", "items" to "not-list"))),
            thread(listOf(turn(listOf(answer("secret malformed text", phase = "unknown"))))),
            thread(listOf(turn(listOf(jsonOf("id" to "command", "type" to "commandExecution", "status" to "unknown"))))),
            thread(listOf(turn(listOf(jsonOf("id" to "user", "type" to "userMessage", "content" to null))))),
        )
        for (t in malformed) invalid(t)
    }

    @Test
    fun `B-AGENT-059 bounds mapped messages, total persisted items and text size`() {
        invalid(thread(listOf(turn((0 until 5001).map { answer("Answer", id = "item-$it") }))))
        invalid(thread((0 until 2).map { t -> turn((0 until 5001).map { jsonOf("id" to "reason-$t-$it", "type" to "reasoning") }, id = "turn-$t") }))
        invalid(thread(listOf(turn(listOf(user("x".repeat(1024 * 1024 + 1)))))))
    }
}
