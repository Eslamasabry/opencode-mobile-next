package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

/** Ids agents really send that the app must never show as they are (Flutter test/tool_label_surfaces_test.dart). */
private val RAW_IDS = listOf("oc-ui_show", "mcp__oc-ui__show", "task_notification", "render_mermaid_diagram")

/** Flutter `_state(id)`: the server's title is the id itself. */
private fun idState(id: String, status: ToolStatus = ToolStatus.Completed): ToolState =
    ToolState(
        status = status,
        title = id,
        input = obj("""{"id":"plan","title":"Choose a fix","status":"completed"}"""),
        output = if (status == ToolStatus.Completed) "ok" else null,
    )

private fun ToolFacts.titleWords(): List<String> = titleArgs + phrase.name

class ToolFactsTest {
    @Test
    fun `B-CHAT-110 the child session id is read from the server key and legacy spellings`() {
        assertEquals("a", taskChildSessionId(obj("""{"sessionId":"a"}""")))
        assertEquals("b", taskChildSessionId(obj("""{"sessionID":"b"}""")))
        assertEquals("c", taskChildSessionId(obj("""{"session_id":"c"}""")))
        assertNull(taskChildSessionId(obj("""{"jobId":"j"}""")))
        assertNull(taskChildSessionId(null))
    }

    @Test
    fun `B-CHAT-110 a task step carries its child session`() {
        val facts =
            toolFacts(
                "task",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"subagent_type":"explore","description":"Find the login code"}"""),
                    metadata = obj("""{"sessionId":"ses_child"}"""),
                ),
            )
        assertEquals(ToolKind.Task, facts.kind)
        assertEquals(ToolPhrase.AgentType, facts.phrase)
        assertEquals(listOf("explore"), facts.titleArgs)
        assertEquals("Find the login code", facts.subject)
        assertEquals("ses_child", facts.childSessionId)
    }

    @Test
    fun `B-CHAT-111 a step row never titles a raw id, finished or running`() {
        for (status in listOf(ToolStatus.Completed, ToolStatus.Running)) {
            for (id in RAW_IDS) {
                val facts = toolFacts(id, idState(id, status))
                assertTrue(facts.titleWords().none { it.contains(id) }, "$id is the title: $facts")
                assertFalse(facts.idWords.contains(id), "$id is in its words")
            }
        }
    }

    @Test
    fun `B-CHAT-111 each raw id has its own plain words`() {
        val card = toolFacts("oc-ui_show", idState("oc-ui_show"))
        assertEquals(ToolKind.Card, card.kind)
        assertEquals(ToolPhrase.ShowCard, card.phrase)
        assertEquals("Choose a fix", card.subject)
        assertEquals(ToolPhrase.ShowCard, toolFacts("mcp__oc-ui__show", idState("mcp__oc-ui__show")).phrase)

        val notice = toolFacts("task_notification", idState("task_notification"))
        assertEquals(ToolKind.BackgroundNotice, notice.kind)
        assertEquals(ToolPhrase.BackgroundTaskFinished, notice.phrase)

        val generic = toolFacts("render_mermaid_diagram", idState("render_mermaid_diagram"))
        assertEquals(ToolPhrase.Generic, generic.phrase)
        assertEquals(listOf("Render mermaid diagram"), generic.titleArgs)
    }

    @Test
    fun `B-CHAT-111 a permission request with an id alone is worded`() {
        for (id in RAW_IDS) {
            val facts = toolFacts(Part("p", "m", "s", PartType.Tool, "tool", toolName = id))
            assertTrue(facts.titleWords().none { it.contains(id) }, "$id is the title: $facts")
        }
    }

    @Test
    fun `B-CHAT-111 tool ids become lower-case words`() {
        assertEquals("render mermaid diagram", toolIdWords("render_mermaid_diagram"))
        assertEquals("exit worktree", toolIdWords("ExitWorktree"))
        assertEquals("create issue", toolIdWords("mcp__github__create_issue"))
        assertEquals("task notification", toolIdWords("task_notification"))
        assertEquals("", toolIdWords("__"))
        val mcp = toolFacts("mcp__github__create_issue", ToolState(ToolStatus.Completed))
        assertEquals(ToolPhrase.McpTool, mcp.phrase)
        assertEquals(listOf("create issue", "github"), mcp.titleArgs)
        assertEquals(ToolPhrase.Tool, toolFacts("__", ToolState(ToolStatus.Completed)).phrase)
        val own = toolFacts("render_mermaid_diagram", ToolState(ToolStatus.Completed, title = "Login flow"))
        assertEquals(ToolPhrase.ServerTitle, own.phrase)
        assertEquals(listOf("Login flow"), own.titleArgs)
    }

    @Test
    fun `B-CHAT-111 known tools name what they touched`() {
        val read =
            toolFacts(
                "read",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"filePath":"/workspace/lib/main.dart","offset":12,"limit":30}"""),
                    metadata = obj("""{"display":{"type":"file","lineStart":12,"lineEnd":41}}"""),
                ),
            )
        assertEquals(ToolPhrase.Read, read.phrase)
        assertEquals("main.dart", read.subject)
        assertTrue(read.technical)
        assertEquals(
            listOf(
                ToolDetail.Count(ToolCount(ToolCountKind.FromLine, 12)),
                ToolDetail.Count(ToolCount(ToolCountKind.Lines, 30)),
                ToolDetail.LineRange(12, 41),
            ),
            read.details,
        )

        val bash =
            toolFacts(
                "bash",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"command":"npm test","description":"Run the tests"}"""),
                    metadata = obj("""{"exit":1,"truncated":true}"""),
                ),
            )
        assertEquals(ToolKind.Shell, bash.kind)
        assertEquals(ToolPhrase.Shell, bash.phrase)
        assertEquals("npm test", bash.subject)
        assertEquals("Run the tests", bash.detail)
        assertEquals(1, bash.exitCode)
        assertEquals(listOf(ToolDetail.ServerText("Run the tests"), ToolDetail.Truncated), bash.details)

        val patch =
            toolFacts(
                "apply_patch",
                ToolState(
                    ToolStatus.Completed,
                    metadata = obj("""{"files":[{"additions":3,"deletions":1},{"additions":2,"deletions":0}]}"""),
                ),
            )
        assertEquals(ToolPhrase.ApplyPatch, patch.phrase)
        assertEquals(ToolCount(ToolCountKind.Files, 2), patch.subjectCount)
        assertEquals(5, patch.added)
        assertEquals(1, patch.removed)

        val edit =
            toolFacts(
                "edit",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"file_path":"C:\\src\\app.kt"}"""),
                    metadata = obj("""{"filediff":{"additions":4,"deletions":0}}"""),
                ),
            )
        assertEquals("app.kt", edit.subject)
        assertEquals(4, edit.added)
        assertNull(edit.removed)

        val search =
            toolFacts(
                "websearch",
                ToolState(ToolStatus.Completed, input = obj("""{"query":"kotlin"}"""), metadata = obj("""{"provider":"Exa"}""")),
            )
        assertEquals(ToolPhrase.ProviderSearch, search.phrase)
        assertEquals(listOf("Exa"), search.titleArgs)

        val question =
            toolFacts(
                "question",
                ToolState(ToolStatus.Completed, input = obj("""{"questions":[{},{}]}"""), metadata = obj("""{"answers":[["a"]]}""")),
            )
        assertEquals(ToolCount(ToolCountKind.Answered, 2), question.subjectCount)
        assertEquals(ToolPhrase.LoadTools, toolFacts("ToolSearch", ToolState(ToolStatus.Completed)).phrase)
        assertEquals(ToolPhrase.UpdatedTasks, toolFacts("todowrite", ToolState(ToolStatus.Completed)).phrase)
    }

    @Test
    fun `B-CHAT-111 background notices say the task name and how it ended`() {
        assertEquals(BackgroundTaskEnd.Failed, backgroundTaskEnd(obj("""{"status":" Error "}""")))
        assertEquals(BackgroundTaskEnd.Stopped, backgroundTaskEnd(obj("""{"status":"killed"}""")))
        assertEquals(BackgroundTaskEnd.Ended, backgroundTaskEnd(obj("{}")))
        assertTrue(isBackgroundTaskNotice(" Task_Notification "))

        val named =
            toolFacts(
                "task_notification",
                ToolState(ToolStatus.Completed, input = obj("""{"summary":"Background agent \"Fix login\" completed","status":"completed"}""")),
            )
        assertEquals(ToolPhrase.BackgroundNamedFinished, named.phrase)
        assertEquals(listOf("Fix login"), named.titleArgs)
        assertNull(named.subject)
        assertFalse(named.notice!!.summaryInBody)

        val cutOff =
            toolFacts(
                "task_notification",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"summary":"Background agent \"Fix login\" didn't finish before the previous session ended"}"""),
                ),
            )
        assertEquals(ToolPhrase.BackgroundNamedCutOff, cutOff.phrase)
        assertEquals(BackgroundTaskEnd.Stopped, cutOff.notice!!.end)

        val unnamed =
            toolFacts(
                "task_notification",
                ToolState(ToolStatus.Completed, input = obj("""{"summary":"Release build","status":"failed"}""")),
            )
        assertEquals(ToolPhrase.BackgroundTaskFailed, unnamed.phrase)
        assertEquals("Release build", unnamed.subject)
    }

    @Test
    fun `B-CHAT-111 an agent message names its recipient in words, never an id`() {
        fun send(input: String) = toolFacts("SendMessage", ToolState(ToolStatus.Completed, input = obj(input)))
        assertEquals(ToolPhrase.MessageToEveryone, send("""{"to":"*","message":"hi"}""").phrase)
        assertEquals(ToolPhrase.MessageToMain, send("""{"to":"main","message":"hi"}""").phrase)
        val named = send("""{"to":"reviewer","summary":"assign task 1","message":"hi"}""")
        assertEquals(ToolPhrase.MessageTo, named.phrase)
        assertEquals(listOf("reviewer"), named.titleArgs)
        assertEquals("assign task 1", named.subject)
        val byId = send("""{"to":"agent-7f3e9a01","summary":"assign task 1"}""")
        assertEquals(ToolPhrase.MessageSummary, byId.phrase)
        assertEquals(listOf("assign task 1"), byId.titleArgs)
        assertNull(byId.subject)
        assertEquals(ToolPhrase.MessageToAgent, send("""{"to":"a1b2c3d4e5f6"}""").phrase)
    }

    @Test
    fun `B-CHAT-111 a connector search says what it found`() {
        val found =
            toolFacts(
                "mcp__oc-ui__find_connectors",
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"query":"github"}"""),
                    output = """{"status":"ok","matches":[{"name":"GitHub"},{"catalogId":"gh-2"},{"name":" "}]}""",
                ),
            )
        assertEquals(ToolKind.ConnectorSearch, found.kind)
        assertEquals(ToolPhrase.SearchedConnectors, found.phrase)
        assertEquals(listOf<ToolDetail>(ToolDetail.Count(ToolCount(ToolCountKind.Found, 2))), found.details)
        val off =
            toolFacts(
                "oc-ui_find_connectors",
                ToolState(ToolStatus.Completed, outputValue = obj("""{"status":"catalogue_off"}""")),
            )
        assertEquals(listOf<ToolDetail>(ToolDetail.ConnectorSearch(ConnectorSearchOutcome.Off)), off.details)
    }
}
