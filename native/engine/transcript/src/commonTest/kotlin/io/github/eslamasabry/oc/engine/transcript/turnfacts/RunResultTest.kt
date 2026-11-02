package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun msg(
    id: String,
    role: Role,
    created: Long,
    completed: Long? = null,
    finish: String? = null,
    error: String? = null,
    errorKind: MessageErrorKind? = null,
    parts: List<Part> = emptyList(),
): MessageWithParts =
    MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = "ses_1",
            role = role,
            time = MsgTime(created, completed),
            errorText = error,
            errorKind = errorKind,
            finish = finish,
        ),
        parts.toImmutableList(),
    )

private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((k, v) in pairs) {
        when (v) {
            null -> put(k, JsonNull)
            is String -> put(k, v)
            is Number -> put(k, v)
            is Boolean -> put(k, v)
            is JsonElement -> put(k, v)
            else -> error("unsupported")
        }
    }
}

private fun tool(
    id: String,
    name: String,
    status: ToolStatus = ToolStatus.Completed,
    input: JsonObject = obj(),
    metadata: JsonObject? = null,
    pruned: Boolean = false,
    executed: Boolean = true,
): Part =
    Part(
        id = id,
        messageId = "m",
        sessionId = "ses_1",
        type = PartType.Tool,
        rawType = "tool",
        callId = id,
        toolName = name,
        tool = ToolState(status = status, input = input, metadata = metadata, pruned = pruned, executedFlag = executed),
    )

private fun run(vararg messages: MessageWithParts, historyComplete: Boolean = false): RunResult =
    assertNotNull(runResult(messages.toList(), historyComplete))

class RunResultTest {
    @Test
    fun `B-CHAT-100 equal timestamps preserve server turn order and the latest assistant step`() {
        val result =
            run(
                msg("z-user", Role.User, 100),
                msg("x-step", Role.Assistant, 100, completed = 110, finish = "tool-calls"),
                msg("a-final", Role.Assistant, 100, completed = 120, finish = "stop"),
            )
        assertEquals("z-user", result.userMessageId)
        assertEquals("x-step", result.runId)
        assertEquals("a-final", result.lastStepId)
        assertEquals(2, result.stepCount)
        assertEquals(RunOutcomeKind.Completed, result.outcome.kind)
    }

    @Test
    fun `B-CHAT-100 an unfinished newest step never inherits an earlier finished timestamp`() {
        val result =
            run(
                msg("u", Role.User, 1),
                msg("old", Role.Assistant, 2, completed = 20, finish = "stop"),
                msg("new", Role.Assistant, 3),
            )
        assertEquals(RunOutcomeKind.Running, result.outcome.kind)
        assertNull(result.finishedAtMs)
    }

    @Test
    fun `B-CHAT-100 the run is the assistant steps after the latest user message`() {
        val result =
            run(
                msg("a3", Role.Assistant, 30, completed = 31, finish = "stop"),
                msg("u2", Role.User, 20),
                msg(
                    "a1",
                    Role.Assistant,
                    11,
                    completed = 12,
                    finish = "stop",
                    parts = listOf(tool("t-old", "edit", input = obj("filePath" to "old.dart"))),
                ),
                msg("u0", Role.User, 10),
                msg(
                    "a2",
                    Role.Assistant,
                    25,
                    completed = 26,
                    finish = "tool-calls",
                    parts = listOf(tool("t-new", "write", input = obj("filePath" to "new.dart"))),
                ),
            )
        assertEquals("a2", result.runId)
        assertEquals("a3", result.lastStepId)
        assertEquals("u2", result.userMessageId)
        assertEquals(2, result.stepCount)
        assertTrue(result.boundaryKnown)
        assertEquals(listOf("new.dart"), result.changedFiles.map { it.path })
        assertEquals(20L, result.startedAtMs)
        assertEquals(31L, result.finishedAtMs)
        assertEquals("ses_1", result.sessionId)
    }

    @Test
    fun `B-CHAT-100 no assistant step after the latest user message means no run`() {
        assertNull(runResult(listOf(msg("a1", Role.Assistant, 1, completed = 2), msg("u2", Role.User, 3))))
        assertNull(runResult(emptyList()))
    }

    @Test
    fun `B-CHAT-100 a missing user boundary is reported, not assumed`() {
        val partial = run(msg("a1", Role.Assistant, 1, completed = 2, finish = "stop"))
        assertFalse(partial.boundaryKnown)
        assertTrue(RunNote.BoundaryUnknown in partial.notes)
        val complete = run(msg("a1", Role.Assistant, 1, completed = 2, finish = "stop"), historyComplete = true)
        assertTrue(complete.boundaryKnown)
        assertEquals(1L, complete.startedAtMs)
    }

    private fun single(
        completed: Long? = 2,
        finish: String? = null,
        error: String? = null,
        kind: MessageErrorKind? = null,
    ): RunResult =
        run(
            msg("u", Role.User, 0),
            msg("a", Role.Assistant, 1, completed = completed, finish = finish, error = error, errorKind = kind),
        )

    @Test
    fun `B-CHAT-101 outcome is taken from server facts only`() {
        assertEquals(RunOutcomeKind.Completed, single(finish = "stop").outcome.kind)
        assertEquals(RunOutcomeKind.CutOff, single(finish = "length").outcome.kind)
        assertEquals(RunOutcomeKind.NotReported, single(finish = "tool-calls").outcome.kind)
        assertEquals(RunOutcomeKind.NotReported, single().outcome.kind)
        assertNull(single().outcome.finish)
        assertEquals(RunOutcomeKind.Running, single(completed = null, finish = null).outcome.kind)
        val failed = single(error = "ProviderError: rate limited\n  at x", kind = MessageErrorKind.Unknown)
        assertEquals(RunOutcomeKind.Failed, failed.outcome.kind)
        assertEquals("rate limited", failed.outcome.errorHeadline)
        assertEquals(RunOutcomeKind.Aborted, single(error = "aborted", kind = MessageErrorKind.Aborted).outcome.kind)
        assertEquals(
            RunOutcomeKind.CutOff,
            single(error = "too long", kind = MessageErrorKind.OutputLength).outcome.kind,
        )
    }

    @Test
    fun `B-CHAT-101 the newest step decides, earlier errors become a note`() {
        val result =
            run(
                msg("u", Role.User, 0),
                msg("a1", Role.Assistant, 1, completed = 2, error = "boom", errorKind = MessageErrorKind.Unknown),
                msg("a2", Role.Assistant, 3, completed = 4, finish = "stop"),
            )
        assertEquals(RunOutcomeKind.Completed, result.outcome.kind)
        assertEquals(1, result.earlierStepErrors)
        assertTrue(RunNote.EarlierStepErrors in result.notes)
        val reversed =
            run(
                msg("u", Role.User, 0),
                msg("a1", Role.Assistant, 1, completed = 2, finish = "stop"),
                msg("a2", Role.Assistant, 3, completed = 4, error = "boom", errorKind = MessageErrorKind.Unknown),
            )
        assertEquals(RunOutcomeKind.Failed, reversed.outcome.kind)
        assertEquals(0, reversed.earlierStepErrors)
    }

    @Test
    fun `B-CHAT-101 changed files come only from completed, executed edit tools`() {
        val files = buildJsonArray {
            add(obj("path" to "lib/c.dart"))
            add(obj("path" to "lib/d.dart"))
        }
        val result =
            run(
                msg("u", Role.User, 0),
                msg(
                    "a",
                    Role.Assistant,
                    1,
                    completed = 2,
                    finish = "stop",
                    parts =
                        listOf(
                            tool("t1", "edit", input = obj("filePath" to "lib/a.dart")),
                            tool("t2", "write", input = obj("filePath" to "lib/b.dart")),
                            tool("t3", "patch", metadata = obj("files" to files)),
                            tool("t4", "edit", status = ToolStatus.Error, input = obj("filePath" to "lib/failed.dart")),
                            // Never ran: still pending with the beta `executed:false` stamp.
                            tool(
                                "t5",
                                "edit",
                                status = ToolStatus.Pending,
                                executed = false,
                                input = obj("filePath" to "lib/never-ran.dart"),
                            ),
                            tool("t6", "edit", input = obj("filePath" to "lib/a.dart")),
                            tool("t7", "read", input = obj("filePath" to "lib/read-only.dart")),
                        ),
                ),
            )
        assertEquals(listOf("lib/a.dart", "lib/b.dart", "lib/c.dart", "lib/d.dart"), result.changedFiles.map { it.path })
        assertEquals(RunFileChange.Edited, result.changedFiles[0].change)
        assertEquals("t6", result.changedFiles[0].partId)
        assertEquals(RunFileChange.Written, result.changedFiles[1].change)
        assertEquals(RunFileChange.Patched, result.changedFiles[2].change)
        assertEquals(7, result.toolCount)
        assertEquals(1, result.erroredToolCount)
        assertTrue(result.hasToolEvidence)
    }

    @Test
    fun `B-CHAT-101 a completed edit stamped executed false still counts, the outcome wins (trap T2)`() {
        val result =
            run(
                msg("u", Role.User, 0),
                msg(
                    "a",
                    Role.Assistant,
                    1,
                    completed = 2,
                    finish = "stop",
                    parts = listOf(tool("t", "edit", executed = false, input = obj("filePath" to "lib/x.dart"))),
                ),
            )
        assertEquals(listOf("lib/x.dart"), result.changedFiles.map { it.path })
    }

    @Test
    fun `B-CHAT-101 commands keep recorded exit codes and never invent them`() {
        val result =
            run(
                msg("u", Role.User, 0),
                msg(
                    "a",
                    Role.Assistant,
                    1,
                    completed = 2,
                    finish = "stop",
                    parts =
                        listOf(
                            tool("c1", "bash", input = obj("command" to "flutter test test/x.dart"), metadata = obj("exit" to 0)),
                            tool(
                                "c2",
                                "bash",
                                status = ToolStatus.Error,
                                input = obj("command" to "npm test"),
                                metadata = obj("exit" to 1),
                            ),
                            tool("c3", "bash", input = obj("command" to "ls test")),
                            tool(
                                "c4",
                                "shell",
                                input = obj("command" to "pytest -q"),
                                metadata = obj("exit" to "0"),
                                pruned = true,
                            ),
                            tool("c5", "bash", status = ToolStatus.Running, input = obj("command" to "sleep 100")),
                        ),
                ),
            )
        assertEquals(4, result.commands.size)
        assertEquals(0, result.commands[0].exitCode)
        assertTrue(result.commands[0].isTest)
        assertFalse(result.commands[0].failed)
        assertEquals(1, result.commands[1].exitCode)
        assertTrue(result.commands[1].failed)
        assertTrue(result.commands[1].isTest)
        assertNull(result.commands[2].exitCode)
        assertFalse(result.commands[2].isTest)
        assertEquals(0, result.commands[3].exitCode)
        assertTrue(result.commands[3].outputPruned)
        assertEquals(1, result.prunedToolCount)
    }

    @Test
    fun `B-CHAT-101 test-command detection is textual and conservative`() {
        assertTrue(looksLikeTestCommand("flutter test"))
        assertTrue(looksLikeTestCommand("cd app && npm run test"))
        assertTrue(looksLikeTestCommand("cargo test --all"))
        assertTrue(looksLikeTestCommand("go test ./..."))
        assertTrue(looksLikeTestCommand("pytest tests/"))
        assertTrue(looksLikeTestCommand("make test"))
        assertFalse(looksLikeTestCommand("git commit -m \"add test\""))
        assertFalse(looksLikeTestCommand("ls tests"))
        assertFalse(looksLikeTestCommand("echo testing"))
        assertFalse(looksLikeTestCommand("npm run build"))
        assertFalse(looksLikeTestCommand(""))
    }

    @Test
    fun `B-CHAT-101 no tool parts is reported as no evidence, not as no changes`() {
        val text = Part(id = "p", messageId = "a", sessionId = "ses_1", type = PartType.Text, rawType = "text", text = "I edited three files.")
        val result = run(msg("u", Role.User, 0), msg("a", Role.Assistant, 1, completed = 2, finish = "stop", parts = listOf(text)))
        assertFalse(result.hasToolEvidence)
        assertTrue(RunNote.NoToolEvidence in result.notes)
        assertTrue(result.changedFiles.isEmpty())
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `B-CHAT-101 lists and command text are bounded`() {
        val long = "x".repeat(300)
        val parts =
            (0 until 60).map { tool("c$it", "bash", input = obj("command" to if (it == 0) long else "ls")) } +
                (0 until 60).map { tool("e$it", "edit", input = obj("filePath" to "lib/f$it.dart")) }
        val result = run(msg("u", Role.User, 0), msg("a", Role.Assistant, 1, completed = 2, finish = "stop", parts = parts))
        assertEquals(RunResult.MAX_COMMANDS, result.commands.size)
        assertEquals(RunResult.MAX_CHANGED_FILES, result.changedFiles.size)
        assertTrue(result.truncated)
        assertTrue(RunNote.Truncated in result.notes)
        assertEquals(RunResult.MAX_COMMAND_LENGTH + 1, result.commands.first().command.length)
        assertTrue(result.commands.first().command.endsWith("…"))
    }

    private fun edit(id: String, path: String, diff: JsonObject?): Part =
        tool(id, "edit", input = obj("filePath" to path), metadata = obj("filediff" to (diff ?: JsonNull)))

    private fun reply(vararg parts: Part): MessageWithParts = msg("m", Role.Assistant, 1, parts = parts.toList())

    @Test
    fun `B-CHAT-068 changed files sum the counts the tools reported, per file, in order`() {
        val files =
            editedFileStats(
                listOf(
                    reply(
                        edit("a", "/p/game.js", obj("additions" to 20, "deletions" to 5)),
                        edit("b", "/p/style.css", obj("additions" to 4, "deletions" to 0)),
                        edit("c", "/p/game.js", obj("additions" to 10, "deletions" to 0)),
                    )
                )
            )
        assertEquals(listOf("game.js", "style.css"), files.map { it.name })
        assertEquals(30, files.first().added)
        assertEquals(5, files.first().removed)
        assertEquals(4, files.last().added)
    }

    @Test
    fun `B-CHAT-068 a change without numbers leaves the file without numbers`() {
        val files =
            editedFileStats(
                listOf(reply(edit("a", "/p/game.js", obj("additions" to 20, "deletions" to 5)), edit("b", "/p/game.js", null)))
            )
        assertEquals(1, files.size)
        assertNull(files.single().added)
        assertNull(files.single().removed)
    }

    @Test
    fun `B-CHAT-068 a new file write counts its lines, a patch uses its per-file numbers`() {
        val patchFiles = buildJsonArray { add(obj("path" to "b.txt", "additions" to 3, "deletions" to 1)) }
        val files =
            editedFileStats(
                listOf(
                    reply(
                        tool("w", "write", input = obj("filePath" to "a.txt", "content" to "one\ntwo\n"), metadata = obj("exists" to false)),
                        tool("o", "write", input = obj("filePath" to "c.txt", "content" to "x"), metadata = obj("exists" to true)),
                        tool("p", "patch", metadata = obj("files" to patchFiles)),
                    )
                )
            )
        assertEquals(listOf(ChangedFile("a.txt", 2, 0), ChangedFile("c.txt", null, null), ChangedFile("b.txt", 3, 1)), files)
        val paths =
            editedPaths(
                listOf(
                    reply(
                        tool("w", "write", input = obj("filePath" to "a.txt")),
                        tool("o", "write", input = obj("filePath" to "c.txt")),
                        tool("p", "patch", metadata = obj("files" to patchFiles)),
                    )
                )
            )
        assertEquals(listOf("a.txt", "c.txt", "b.txt"), paths)
    }
}
