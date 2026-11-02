package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Port of test/background_tasks_test.dart (runningBackgroundTasks, backgroundTaskName, backgroundTaskWaitsOn,
// BackgroundTaskBook) and test/chat_background_result_test.dart:57,69,78. The Paseo mapper groups are out of scope:
// the shapes below are what that mapper produces (marker on the step metadata, notice input with toolUseId/taskId).
class BackgroundTasksTest {
    private val callId = "toolu_01BuildOwner"
    private val command = "cd /root/projects/IPTV_King && bash tool/build_tv_release.sh --owner"
    private val started = 1_793_085_000_000L
    private val minute = 60_000L

    private fun obj(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    private fun bash(
        callId: String = this.callId,
        background: Boolean = true,
        status: ToolStatus = ToolStatus.Completed,
        at: Long = started,
        command: String = this.command,
        label: String? = null,
    ): MessageWithParts {
        val marker =
            if (!background) null else JsonObject(listOfNotNull("id" to JsonPrimitive("b1x2y3"), label?.let { "label" to JsonPrimitive(it) }).toMap())
        val part =
            Part(
                id = "$callId:0",
                messageId = callId,
                sessionId = "agent-1",
                type = PartType.Tool,
                rawType = "tool",
                callId = callId,
                toolName = "bash",
                tool =
                    ToolState(
                        status = status,
                        input = obj("command" to command),
                        metadata = marker?.let { JsonObject(mapOf(BACKGROUND_TASK_METADATA_KEY to it)) },
                    ),
            )
        return message(callId, Role.Assistant, at, part)
    }

    private fun notice(toolUseId: String? = callId, taskId: String? = "b1x2y3"): MessageWithParts {
        val id = "task_notification_${taskId ?: "x"}"
        val input = listOfNotNull(toolUseId?.let { "toolUseId" to it }, taskId?.let { "taskId" to it }, "status" to "completed")
        val part =
            Part(
                id = "$id:0",
                messageId = id,
                sessionId = "agent-1",
                type = PartType.Tool,
                rawType = "tool",
                callId = id,
                toolName = "task_notification",
                tool = ToolState(status = ToolStatus.Completed, input = obj(*input.toTypedArray())),
            )
        return message(id, Role.Assistant, started + 9 * minute, part)
    }

    private fun message(id: String, role: Role, at: Long?, vararg parts: Part, errorText: String? = null) =
        MessageWithParts(
            MessageInfo(id = id, sessionId = "agent-1", role = role, time = MsgTime(at, at), errorText = errorText),
            parts.toList().toImmutableList(),
        )

    private fun said(text: String, id: String = "a9"): MessageWithParts =
        // The Paseo mapper turns Claude Code's "API Error" line into an error with no parts (AgentNotices).
        if (classifyAgentError(text) != AgentErrorClass.Other) {
            MessageWithParts(
                MessageInfo(id = id, sessionId = "agent-1", role = Role.Assistant, errorText = text),
                persistentListOf(),
            )
        } else {
            message(id, Role.Assistant, started, Part("$id:0", id, "agent-1", PartType.Text, "text", text = text))
        }

    private val now = started + 2 * minute

    @Test
    fun `B-CHAT-007 a background command runs until its notice names it`() {
        val running = runningBackgroundTasks(listOf(bash()), now)
        assertEquals(1, running.size)
        val task = running.single()
        assertEquals(callId, task.callId)
        assertEquals(callId, task.messageId)
        assertEquals("build_tv_release.sh", task.name)
        assertEquals(started, task.sinceMs)
        assertEquals("b1x2y3", task.taskId)
        assertTrue(runningBackgroundTasks(listOf(bash(), notice()), now).isEmpty())
    }

    @Test
    fun `B-CHAT-007 a notice that names only the task id ends it too`() {
        assertTrue(runningBackgroundTasks(listOf(bash(), notice(toolUseId = null)), now).isEmpty())
    }

    @Test
    fun `B-CHAT-007 another tasks notice leaves it running`() {
        assertEquals(1, runningBackgroundTasks(listOf(bash(), notice(toolUseId = "toolu_other", taskId = "zz9")), now).size)
    }

    @Test
    fun `B-CHAT-007 several tasks, oldest first`() {
        val second = bash(callId = "toolu_02Tests", at = started + minute)
        assertEquals(listOf(callId, "toolu_02Tests"), runningBackgroundTasks(listOf(bash(), second), now).map { it.callId })
    }

    @Test
    fun `B-CHAT-007 older than six hours it stops showing`() {
        assertEquals(1, runningBackgroundTasks(listOf(bash()), started + 5 * 60 * minute + 59 * minute).size)
        assertTrue(runningBackgroundTasks(listOf(bash()), started + BACKGROUND_TASK_MAX_AGE_MS).isEmpty())
    }

    @Test
    fun `B-CHAT-007 the agent process seen down after it started ends it`() {
        assertTrue(runningBackgroundTasks(listOf(bash()), now, runtimeGapAtMs = started + minute).isEmpty())
        // A process that came back before the work started keeps it.
        assertEquals(1, runningBackgroundTasks(listOf(bash()), now, runtimeGapAtMs = started - minute).size)
    }

    @Test
    fun `B-CHAT-007 a step still running is the live turn, not background work`() {
        assertTrue(runningBackgroundTasks(listOf(bash(status = ToolStatus.Running)), now).isEmpty())
    }

    @Test
    fun `B-CHAT-007 an ordinary step carries no marker and is no background work`() {
        assertTrue(runningBackgroundTasks(listOf(bash(background = false)), now).isEmpty())
    }

    @Test
    fun `B-CHAT-008 the step label names the task, a blank one is no name`() {
        val task = runningBackgroundTasks(listOf(bash(label = "  Wait for the owner build  ")), now).single()
        assertEquals("Wait for the owner build", task.name)
        assertFalse(task.waiting)
        assertEquals("build_tv_release.sh", runningBackgroundTasks(listOf(bash(label = " ")), now).single().name)
    }

    @Test
    fun `B-CHAT-008 a shell loop names what it waits on`() {
        val loop = "until grep -q \"BUILD SUCCESSFUL\" /tmp/build.log; do sleep 30; done"
        val task = runningBackgroundTasks(listOf(bash(command = loop)), now).single()
        assertTrue(task.waiting)
        assertEquals("grep", task.name)
        assertEquals(loop, task.command)
        assertFalse(runningBackgroundTasks(listOf(bash()), now).single().waiting)
    }

    @Test
    fun `B-CHAT-008 the program or script, never its folder`() {
        assertEquals("build_tv_release.sh", backgroundTaskName(command))
        assertEquals("flutter build apk", backgroundTaskName("flutter build apk --release"))
        assertEquals("gradlew assembleRelease", backgroundTaskName("FOO=1 nohup ./gradlew assembleRelease > log 2>&1"))
        assertEquals("serve.py", backgroundTaskName("timeout 600 python3 -u tools/serve.py --port 8"))
        assertEquals("sleep", backgroundTaskName("sleep 300"))
        assertEquals("a command", backgroundTaskName(""))
    }

    @Test
    fun `B-CHAT-008 the program a loop at the end of a command waits on`() {
        assertEquals("grep", backgroundTaskWaitsOn("until grep -q ready log; do sleep 30; done"))
        assertEquals("curl", backgroundTaskWaitsOn("while ! curl -sf localhost:8080 >/dev/null; do sleep 5; done"))
        assertEquals("grep", backgroundTaskWaitsOn("timeout 600 bash -c 'until grep -q X f; do sleep 30; done'"))
        assertEquals(
            "adb",
            backgroundTaskWaitsOn("cd app && until adb shell getprop sys.boot_completed | grep 1; do sleep 2; done"),
        )
        // A bracket test names what it looks at, else the body's command.
        assertEquals("app.apk", backgroundTaskWaitsOn("while [ ! -f build/out/app.apk ]; do sleep 10; done"))
        assertEquals("flutter", backgroundTaskWaitsOn("while true; do flutter test; sleep 9; done"))
    }

    @Test
    fun `B-CHAT-008 a command that does not end in a loop waits on nothing`() {
        assertNull(backgroundTaskWaitsOn(command))
        assertNull(backgroundTaskWaitsOn("until grep -q ready log; do sleep 3; done; ls"))
        assertNull(backgroundTaskWaitsOn(""))
    }

    @Test
    fun `B-CHAT-007 the book - a full read, a streamed notice and a process gap`() {
        var at = started + 2 * minute
        val book = BackgroundTaskBook()
        book.replace("agent-1", listOf(bash()))
        assertTrue(book.running("agent-1", at))
        book.observe("agent-1", notice())
        assertFalse(book.running("agent-1", at))

        book.replace("agent-1", listOf(bash(callId = "toolu_03")))
        assertTrue(book.running("agent-1", at))
        book.runtimeGap("agent-1", at)
        assertFalse(book.running("agent-1", at))
        assertEquals(at, book.gapAt("agent-1"))

        // Work started after the process came back runs again.
        at += 5 * minute
        book.observe("agent-1", bash(callId = "toolu_04", at = at - minute))
        assertTrue(book.running("agent-1", at))
        assertFalse(book.running("agent-2", at))
        assertTrue(book.revision > 0)
    }

    @Test
    fun `B-CHAT-007 the book holds the turn failed until the next prompt`() {
        val book = BackgroundTaskBook()
        book.replace("agent-1", listOf(bash(), said("API Error: Can't reach the API server (EAI_AGAIN)")))
        assertTrue(book.turnFailed("agent-1"))
        assertTrue(book.running("agent-1", started))
        // A task's notice is not the agent going on.
        book.observe("agent-1", notice())
        assertTrue(book.turnFailed("agent-1"))
        book.observe("agent-1", message("u2", Role.User, null, Part("u2:0", "u2", "agent-1", PartType.Text, "text", text = "Continue")))
        assertFalse(book.turnFailed("agent-1"))
        assertTrue(lastTurnFailedIn(listOf(bash(), said("API Error: Request timed out."))))
        assertFalse(
            lastTurnFailedIn(listOf(said("API Error: Request timed out."), said("Picked up again; the build is done.", id = "a10"))),
        )
    }

    // ---- chat_background_result_test.dart ----

    private fun result(
        state: String = "completed",
        description: String = "Check the checkout validation",
        body: String = "Validation now reports missing fields clearly.",
        child: String = "ses_child",
        text: String? = null,
        metadata: JsonObject? = null,
    ): Part =
        Part(
            id = "msg_completion:0",
            messageId = "msg_completion",
            sessionId = "ses_parent",
            type = PartType.Notice,
            rawType = "v2:notice",
            toolName = "synthetic",
            text = text ?: "<subagent sessionID=\"$child\" state=\"$state\" description=\"$description\">\n$body\n</subagent>",
            filename = description,
            noticeMetadata =
                subagentNoticeMetadata(
                    metadata
                        ?: obj(
                            "source" to "subagent",
                            "childID" to child,
                            "agent" to "explore",
                            "state" to state,
                            "ignored-private-field" to "never copied",
                        ),
                ).toImmutableMap(),
        )

    @Test
    fun `B-CHAT-011 canonical mapper preserves only result identity and status metadata`() {
        val part = result()
        assertEquals(setOf("source", "childID", "agent", "state"), part.noticeMetadata.keys)
        val parsed = BackgroundAgentResult.fromPart(part)!!
        assertEquals("Validation now reports missing fields clearly.", parsed.body)
        assertEquals("ses_child", parsed.childId)
        assertEquals("completed", parsed.state)
        assertEquals("explore", parsed.agent)
        assertTrue(parsed.isKnownChild("ses_parent", mapOf("ses_child" to "ses_parent")))
        assertFalse(parsed.isKnownChild("ses_parent", emptyMap()))
        assertFalse(parsed.isKnownChild("ses_parent", mapOf("ses_child" to "ses_someone_else")))
    }

    @Test
    fun `B-CHAT-011 quoted and multiline task names preserve exact result boundaries`() {
        val body = "Keep <subagent>inside user output</subagent> unchanged."
        val parsed = BackgroundAgentResult.fromPart(result(description = "Inspect \"quotes\"\nand paths", body = body))
        assertEquals("Inspect \"quotes\"\nand paths", parsed?.description)
        assertEquals(body, parsed?.body)
    }

    @Test
    fun `B-CHAT-011 unknown, mismatched or malformed envelopes never become a result`() {
        val canonical = result().text
        for (part in
            listOf(
                result(state = "running"),
                result(text = "Prefix\n$canonical"),
                result(text = canonical.replaceFirst("ses_child", "ses_other")),
                result(text = canonical.replaceFirst("completed", "error")),
                result(text = canonical.replaceFirst("</subagent>", "")),
                result(metadata = obj("source" to "not-subagent")),
                result(child = "../not-a-session"),
                Part("t:0", "t", "ses_parent", PartType.Text, "text", text = canonical),
            )
        ) {
            assertNull(BackgroundAgentResult.fromPart(part))
        }
    }
}
