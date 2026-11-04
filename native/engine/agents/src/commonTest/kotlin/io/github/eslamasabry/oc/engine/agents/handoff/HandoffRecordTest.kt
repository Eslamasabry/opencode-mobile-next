package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports test/agent_handoff_record_test.dart. */
class HandoffRecordTest {
    private val redactor = Redactor()
    private val builder = HandoffRecordBuilder(redactor)

    private fun message(role: String, text: String, extra: List<HandoffPart> = emptyList(), error: String? = null) =
        HandoffMessage(id = role, sessionId = "source", role = role, parts = listOf(HandoffPart("text", text)) + extra, errorText = error)

    private fun result(messages: List<HandoffMessage>, maxBytes: Int = 98_304): HandoffRecordResult = builder.build(
        session = HandoffSession(id = "source", title = "Fix the parser", directory = "/repo"),
        messages = messages,
        lastState = "waiting for you",
        maxBytes = maxBytes,
    )

    private fun record(messages: List<HandoffMessage>, maxBytes: Int = 98_304): HandoffRecord =
        assertIs<HandoffRecordResult.Built>(result(messages, maxBytes)).record

    private fun tool(name: String, state: HandoffToolState) = HandoffPart(type = "tool", toolName = name, tool = state)

    private fun assertLacks(text: String, value: String) = assertFalse(text.contains(value), "unexpected: $value")

    @Test
    fun `B-AGENT-020 the record stays the same when the writers mask it again`() {
        val built = record(
            listOf(
                message(
                    "assistant",
                    "Listed the files",
                    extra = listOf(
                        tool(
                            "bash",
                            HandoffToolState(
                                status = "completed",
                                output = "lib/state/app_state_premium.dart\n" +
                                    "lib/l10n/app_localizations_ar.dart\n" +
                                    "lib/l10n/app_localizations.dart\n",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(built.markdown, redactor.opaqueTokens(built.markdown))
        assertTrue(built.redacted)
        assertEquals(built.markdown.encodeToByteArray().size, built.byteLength)
    }

    @Test
    fun `B-AGENT-020 retains exact user text and assistant text in source order`() {
        val prompt = "  Fix this\n```dart\nfinal answer = 42;\n```\nالعربية 🚀"
        val r = record(
            listOf(message("user", prompt), message("assistant", "I changed the parser."), message("user", "Now check empty input.")),
        )
        assertContains(r.markdown, prompt)
        assertTrue(r.markdown.indexOf(prompt) < r.markdown.indexOf("I changed the parser."))
        assertTrue(r.markdown.indexOf("I changed the parser.") < r.markdown.indexOf("Now check empty input."))
        assertContains(r.markdown, "````\n$prompt\n````")
        assertEquals(r.markdown.encodeToByteArray().size, r.byteLength)
        assertEquals(0, r.omittedMessages)
        assertFalse(r.redacted)
    }

    @Test
    fun `B-AGENT-020 copies tool commands, paths and outcomes but no arbitrary fields`() {
        val r = record(
            listOf(
                message(
                    "assistant",
                    "Implemented.",
                    extra = listOf(
                        HandoffPart(type = "reasoning", text = "private chain of thought"),
                        HandoffPart(type = "text", text = "synthetic context", synthetic = true),
                        tool(
                            "bash",
                            HandoffToolState(
                                status = "error",
                                input = mapOf(
                                    "command" to "dart test",
                                    "filePath" to "test/parser_test.dart",
                                    "cwd" to "/repo",
                                    "config" to mapOf("unsafe" to "raw input must not leak"),
                                ),
                                inputJson = "raw input json must not leak",
                                output = "One assertion failed",
                                outputValue = mapOf("hidden" to "raw output must not leak"),
                                metadata = mapOf("exitCode" to 1, "private" to "metadata must not leak"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        for (included in listOf("dart test", "test/parser_test.dart", "/repo", "exitCode: 1", "One assertion failed", "status: \"error\"")) {
            assertContains(r.markdown, included)
        }
        for (excluded in listOf(
            "private chain of thought", "synthetic context", "raw input must not leak", "raw input json must not leak",
            "raw output must not leak", "metadata must not leak",
        )) {
            assertLacks(r.markdown, excluded)
        }
    }

    @Test
    fun `B-AGENT-020 records current task and file evidence without source or patch`() {
        val built = builder.build(
            session = HandoffSession(id = "source"),
            messages = listOf(message("assistant", "Stopped", error = "Build failed")),
            todos = listOf(HandoffTodo("Fix parsing", "completed"), HandoffTodo("Check empty input", "pending", "high")),
            files = listOf(
                HandoffFileDiff(
                    file = "lib/parser.dart", additions = 3, deletions = 1, before = "old source excluded",
                    after = "new source excluded", patch = "patch excluded", status = "modified",
                ),
            ),
            lastState = "failed",
            historyComplete = false,
        )
        val md = assertIs<HandoffRecordResult.Built>(built).record.markdown
        assertContains(md, "[x] \"Fix parsing\"")
        assertContains(md, "[ ] \"Check empty input\"")
        assertContains(md, "lib/parser.dart")
        assertContains(md, "+3 -1")
        assertContains(md, "Last state: \"failed\"")
        assertContains(md, "Build failed")
        assertContains(md, "History coverage: INCOMPLETE")
        assertLacks(md, "source excluded")
        assertLacks(md, "patch excluded")
    }

    @Test
    fun `B-AGENT-021 drops whole older turns and counts UTF-8 bytes, keeping newest full`() {
        val old = "a".repeat(3000)
        val newest = "界".repeat(200)
        val r = record(
            listOf(message("user", old), message("assistant", "Old answer"), message("user", newest), message("assistant", "Newest answer")),
            maxBytes = 2200,
        )
        assertEquals(2, r.omittedMessages)
        assertLacks(r.markdown, "Old answer")
        assertContains(r.markdown, newest)
        assertContains(r.markdown, "Newest answer")
        assertTrue(r.byteLength <= 2200)
        assertEquals(r.markdown.encodeToByteArray().size, r.byteLength)
        assertContains(r.markdown, "Older messages omitted for size: 2.")
    }

    @Test
    fun `B-AGENT-021 never silently truncates an oversized newest turn`() {
        val tooLarge = assertIs<HandoffRecordResult.TooLarge>(
            result(listOf(message("user", "界".repeat(2000)), message("assistant", "The answer belongs to that question.")), maxBytes = 2200),
        )
        assertTrue(tooLarge.requiredBytes > 2200)
        assertEquals(2200, tooLarge.maxBytes)
    }

    @Test
    fun `B-AGENT-021 exact byte boundary fits and one byte less cannot lose newest turn`() {
        val messages = listOf(message("user", "Keep all of this."))
        val length = record(messages).byteLength
        assertEquals(length, record(messages, maxBytes = length).byteLength)
        assertIs<HandoffRecordResult.TooLarge>(result(messages, maxBytes = length - 1))
        assertFailsWith<IllegalArgumentException> { result(emptyList(), maxBytes = 0) }
        assertIs<HandoffRecordResult.TooLarge>(result(emptyList(), maxBytes = 1))
    }

    @Test
    fun `B-SEC-004 masks every exported untrusted field, including user prompts`() {
        val secret = "registered-private-credential"
        val opaque = "a1bcdefghijklmnopqrstuvwxyz0123456789"
        redactor.registerSecret(secret)
        val built = builder.build(
            session = HandoffSession(id = secret, title = secret, directory = "/repo/$secret", agent = secret, model = secret),
            messages = listOf(
                message("user", "Use $secret and Bearer abc123 and $opaque"),
                message(
                    "assistant",
                    "api_key=abcdefghi",
                    error = secret,
                    extra = listOf(
                        HandoffPart(type = "file", filename = secret, url = "https://unsafe/$secret"),
                        tool(
                            secret,
                            HandoffToolState(
                                status = secret,
                                input = mapOf("command" to secret, "path" to secret),
                                output = "sk-ant-123456789abcdef and $opaque and $secret",
                            ),
                        ),
                    ),
                ),
            ),
            todos = listOf(HandoffTodo(secret, secret, secret)),
            files = listOf(HandoffFileDiff(file = secret, status = secret)),
            lastState = secret,
        )
        val r = assertIs<HandoffRecordResult.Built>(built).record
        assertTrue(r.redacted)
        for (value in listOf(secret, opaque, "abc123", "abcdefghi", "123456789abcdef")) assertLacks(r.markdown, value)
        assertContains(r.markdown, Redactor.MASK)
    }

    @Test
    fun `B-SEC-004 redacts before compacting long tool output`() {
        val secret = "a".repeat(700)
        val r = record(
            listOf(
                message(
                    "assistant",
                    "Tool failed",
                    extra = listOf(
                        tool(
                            "shell",
                            HandoffToolState(
                                status = "error",
                                output = "password=\"$secret\"",
                                input = mapOf("command" to "z".repeat(700)),
                                executed = false,
                                pruned = true,
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertLacks(r.markdown, "aaaa")
        assertContains(r.markdown, "[summary shortened]")
        assertContains(r.markdown, "not executed")
        assertContains(r.markdown, "original output was pruned")
        assertTrue(r.redacted)
    }

    @Test
    fun `B-AGENT-020 uses newest completed task snapshot with allowlisted redacted fields`() {
        fun tasks(rows: List<Any?>, status: String = "completed") =
            tool("TodoWrite", HandoffToolState(status = status, input = mapOf("todos" to rows)))
        val r = record(
            listOf(
                message("assistant", "", extra = listOf(tasks(listOf(mapOf("content" to "Old task", "status" to "pending"))))),
                message(
                    "assistant",
                    "",
                    extra = listOf(
                        tasks(
                            listOf(
                                mapOf(
                                    "content" to "Fix parser with api_key=abc12345",
                                    "status" to "in_progress",
                                    "priority" to "high",
                                    "raw" to "private map field",
                                ),
                                mapOf("content" to "Reviewed", "status" to "completed"),
                                mapOf("content" to 42, "status" to "pending"),
                            ),
                        ),
                    ),
                ),
                message(
                    "assistant",
                    "",
                    extra = listOf(tasks(listOf(mapOf("content" to "Not executed task", "status" to "pending")), status = "error")),
                ),
            ),
        )
        assertContains(r.markdown, "latest completed task-list tool snapshot")
        assertContains(r.markdown, "Fix parser with api_key=")
        assertContains(r.markdown, "in_progress")
        assertContains(r.markdown, "[x] \"Reviewed\"")
        for (omitted in listOf("Old task", "Not executed task", "abc12345", "private map field")) assertLacks(r.markdown, omitted)
        assertTrue(r.redacted)
    }

    @Test
    fun `B-AGENT-020 empty latest task snapshot clears older tasks`() {
        fun tasks(rows: List<Any?>) = tool("todowrite", HandoffToolState(status = "completed", input = mapOf("todos" to rows)))
        val r = record(
            listOf(
                message("assistant", "", extra = listOf(tasks(listOf(mapOf("content" to "Old task", "status" to "pending"))))),
                message("assistant", "", extra = listOf(tasks(emptyList()))),
            ),
        )
        assertContains(r.markdown, "No tasks reported.")
        assertLacks(r.markdown, "Old task")
    }

    @Test
    fun `B-AGENT-021 retains observed write paths from omitted turns without claiming ownership of diffs`() {
        val r = record(
            listOf(
                message("user", "a".repeat(5000)),
                message(
                    "assistant",
                    "",
                    extra = listOf(
                        tool("edit", HandoffToolState(status = "completed", input = mapOf("filePath" to "lib/committed_parser.dart"))),
                        tool("write", HandoffToolState(status = "error", input = mapOf("filePath" to "lib/failed_write.dart"))),
                    ),
                ),
                message("user", "Continue the remaining work."),
            ),
            maxBytes = 2800,
        )
        assertEquals(2, r.omittedMessages)
        assertContains(r.markdown, "lib/committed_parser.dart")
        assertLacks(r.markdown, "lib/failed_write.dart")
        assertContains(r.markdown, "does not establish which conversation changed a file")
        assertContains(r.markdown, "including older turns omitted below")
    }

    @Test
    fun `B-SEC-004 bounds observed path metadata and masks credentials in paths`() {
        val r = record(
            listOf(
                message(
                    "assistant",
                    "",
                    extra = (0 until 257).map { tool("write", HandoffToolState(status = "completed", input = mapOf("path" to "file_$it.dart"))) },
                ),
            ),
        )
        assertContains(r.markdown, "Older distinct tool paths omitted: 1.")
        val header = r.markdown.split("## Conversation").first()
        assertContains(header, "file_256.dart")
        assertLacks(header, "\"file_0.dart\"")
        redactor.registerSecret("private-file-name")
        val masked = record(
            listOf(
                message(
                    "assistant",
                    "",
                    extra = listOf(tool("edit", HandoffToolState(status = "completed", input = mapOf("path" to "/repo/private-file-name")))),
                ),
            ),
        )
        assertLacks(masked.markdown, "private-file-name")
    }

    @Test
    fun `B-AGENT-021 compaction notice does not split the newest actual user turn`() {
        val latestQuestion = "a".repeat(3000)
        assertIs<HandoffRecordResult.TooLarge>(
            result(
                listOf(
                    message("user", latestQuestion),
                    HandoffMessage("compact", "source", "user", listOf(HandoffPart("v2:compaction", "Conversation shortened."))),
                    message("assistant", "The answer belongs to the latest question."),
                ),
                maxBytes = 2200,
            ),
        )
        val r = record(
            listOf(message("user", "Keep this question."), HandoffMessage("compact", "source", "user", listOf(HandoffPart("v2:compaction")))),
        )
        assertContains(r.markdown, "Source notice")
        assertContains(r.markdown, "conversation compaction event")
    }
}
