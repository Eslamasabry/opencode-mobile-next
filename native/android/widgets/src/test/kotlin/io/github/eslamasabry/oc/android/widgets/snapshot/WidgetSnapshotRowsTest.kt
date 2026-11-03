package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.android.widgets.PublishResult
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Flutter `widget_snapshot_test.dart` and `widget_v2_contract_test.dart` facts, on the native builder. */
class WidgetSnapshotRowsTest {
    private val redactor = Redactor()
    private val words = WidgetSnapshotWords()
    private val text = WidgetText(redactor)
    private val builder = WidgetSnapshotBuilder(redactor, words)

    private fun session(id: String, title: String? = null, updated: Long = 1) = WidgetSession(id, title, updated)

    private fun build(input: WidgetSnapshotInput): JsonObject = builder.build(input)

    private fun rows(snapshot: JsonObject): List<JsonObject> = snapshot.getValue("sessions").jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    // ---- B-ANDR-112 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-112 an outside write names the file and its folder`() {
        assertEquals(
            mapOf(
                "kind" to "write",
                "q" to "Write outside this project?",
                "file" to "outside-test.txt",
                "path" to "/root/outside-test.txt",
                "dir" to "/root",
            ),
            text.askFor("external_directory", listOf("/root/*"), words, tool = "write", target = "/root/outside-test.txt"),
        )
    }

    @Test
    fun `B-ANDR-112 a command is text, never split into a folder and a file`() {
        val ask = text.askFor("external_directory", listOf("/root/*"), words, tool = "shell", target = "touch /root/outside-test.txt")
        assertEquals("run", ask["kind"])
        assertEquals("touch /root/outside-test.txt", ask["file"])
        assertEquals("/root", ask["dir"])
        assertFalse("path" in ask)
    }

    @Test
    fun `B-ANDR-112 a shell ask without a seen call uses its pattern as the command`() {
        val ask = text.askFor("shell", listOf("touch /root/x"), words)
        assertEquals("run", ask["kind"])
        assertEquals("touch /root/x", ask["file"])
        assertFalse("dir" in ask)
    }

    @Test
    fun `B-ANDR-112 only a folder pattern gives a folder, never an invented file`() {
        val ask = text.askFor("external_directory", listOf("/root/*"), words)
        assertEquals("/root", ask["dir"])
        assertFalse("file" in ask)
    }

    // ---- B-ANDR-113 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-113 preview excludes inline reasoning and keeps the following reply`() {
        assertEquals("Intro Done.", text.preview("Intro <think>private reasoning</think>Done."))
        assertEquals("Reply", text.preview("< THINKING >private\nreasoning< / Thinking >Reply"))
        assertEquals("Reply", text.preview("<think>outer<thinking>inner</thinking>private</think>Reply"))
        assertEquals("One Two", text.preview("<think>first</think>One<thinking>second</thinking>Two"))
    }

    @Test
    fun `B-ANDR-113 preview hides unclosed reasoning and orphan closing prefixes`() {
        assertEquals("Public reply", text.preview("Public reply <think>still private"))
        assertEquals("", text.preview("<thinking>still private"))
        assertEquals("done", text.preview("</think>done"))
        assertEquals("Real reply", text.preview("truncated reasoning</THINKING>Real reply"))
    }

    @Test
    fun `B-ANDR-113 preview shows plain words, never raw Markdown`() {
        assertEquals("Done — sleep 40 completed.", text.preview("Done — `sleep 40` completed."))
        assertEquals(
            "Summary Fixed the bug in the docs old new step print(1);",
            text.preview(
                "## Summary\n- **Fixed** the _bug_ in [the docs](https://x.test/a)\n" +
                    "1. ~~old~~ new *step*\n```dart\nprint(1);\n```",
            ),
        )
        assertEquals("snake_case_name and 2 * 3 * 4", text.preview("snake_case_name and 2 * 3 * 4"))
    }

    @Test
    fun `B-ANDR-113 preview leaves unrelated markup and code alone`() {
        assertEquals(
            "Use <div> and a < b; <thinker> is a name.",
            text.preview("Use `<div>` and `a < b`; <thinker> is a name."),
        )
        assertEquals("&lt;think&gt; is escaped.", text.preview("&lt;think&gt; is escaped."))
    }

    private fun message(role: Role, vararg parts: Part, id: String = role.name) =
        MessageWithParts(MessageInfo(id = id, sessionId = "s1", role = role), parts.toList().toImmutableList())

    private fun part(type: PartType, value: String, synthetic: Boolean = false, tool: ToolState? = null, name: String? = null) =
        Part(
            id = "p" + value.hashCode(),
            messageId = "m",
            sessionId = "s1",
            type = type,
            rawType = type.name.lowercase(),
            text = value,
            synthetic = synthetic,
            tool = tool,
            toolName = name,
        )

    @Test
    fun `B-ANDR-113 bounded assistant reads use the same inline reasoning boundary`() {
        assertEquals(
            "Final reply",
            text.assistantPreview(listOf(message(Role.Assistant, part(PartType.Text, "<think>private</think>Final reply")))),
        )
    }

    @Test
    fun `B-ANDR-113 bounded read preview uses assistant prose only`() {
        val preview =
            text.assistantPreview(
                listOf(
                    message(Role.Assistant, part(PartType.Text, "Old reply"), id = "a0"),
                    message(
                        Role.Assistant,
                        part(PartType.Reasoning, "Private reasoning"),
                        part(PartType.Tool, "Private output"),
                        part(PartType.Text, "Synthetic prompt", synthetic = true),
                        part(PartType.Text, "Latest reply password=secret-value"),
                        id = "a1",
                    ),
                    message(Role.User, part(PartType.Text, "Private user prompt")),
                ),
            )
        assertEquals("Latest reply password=•••", preview)
    }

    // ---- B-ANDR-114 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-114 the project line is a real folder name or nothing`() {
        assertEquals("", text.project("/tmp/run/project"))
        assertEquals("oc_app", text.project("/home/me/oc_app"))
        assertEquals("", text.project(""))
        assertEquals("My project", text.project("/private/routing/My project"))
    }

    // ---- B-ANDR-115 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-115 the Status widget gets each server as a name, a state and since when`() {
        val snapshot =
            build(
                WidgetSnapshotInput(
                    connected = true,
                    servers =
                        listOf(
                            WidgetServerLine("This phone", "online", phone = true),
                            WidgetServerLine("Laptop", "offline", since = 1_700_000_000_000),
                            WidgetServerLine("Studio", "weird"),
                        ),
                ),
            )
        val expected =
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("name", "This phone")
                        put("state", "online")
                        put("phone", true)
                    },
                    buildJsonObject {
                        put("name", "Laptop")
                        put("state", "offline")
                        put("since", 1_700_000_000_000)
                    },
                    // Anything unknown is not claimed to be online.
                    buildJsonObject {
                        put("name", "Studio")
                        put("state", "offline")
                    },
                ),
            )
        assertEquals(expected, snapshot["servers"])
    }

    @Test
    fun `B-ANDR-115 at most six servers are listed`() {
        val snapshot = build(WidgetSnapshotInput(servers = (1..9).map { WidgetServerLine("S$it", "online") }))
        assertEquals(WidgetSnapshotBuilder.MAX_SERVERS, snapshot.getValue("servers").jsonArray.size)
        assertEquals(6, WidgetSnapshotBuilder.MAX_SERVERS)
    }

    // ---- B-ANDR-111 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-111 a permission ask carries only its question, kind, file and folder`() {
        val secrets = Redactor().apply { registerSecret("registered-private-secret") }
        val snapshot =
            WidgetSnapshotBuilder(secrets, words)
                .build(
                    WidgetSnapshotInput(
                        sessions = listOf(session("s1")),
                        connected = true,
                        details =
                            mapOf(
                                "s1" to
                                    WidgetRowDetails(
                                        requestId = "perm-1",
                                        ask =
                                            mapOf(
                                                "kind" to "write",
                                                "q" to "Write outside this project?",
                                                "file" to "outside-test.txt",
                                                "path" to "/root/outside-test.txt",
                                                "dir" to "/root",
                                                "toolInput" to "registered-private-secret",
                                            ),
                                    ),
                            ),
                    ),
                )
        val row = rows(snapshot).single()
        assertEquals(
            buildJsonObject {
                put("kind", "write")
                put("q", "Write outside this project?")
                put("file", "outside-test.txt")
                put("path", "/root/outside-test.txt")
                put("dir", "/root")
            },
            row["ask"],
        )
        assertFalse("registered-private-secret" in row.toString())
    }

    @Test
    fun `B-ANDR-111 an unknown ask kind reads other and the excerpt fields are redacted and bounded`() {
        val secrets = Redactor().apply { registerSecret("registered-private-secret") }
        val preview =
            "Done registered-private-secret password=synthetic-password\n" +
                "مرحبا؜‎‏‮⁧\u0001 " + "😀".repeat(250)
        val snapshot =
            WidgetSnapshotBuilder(secrets, words)
                .build(
                    WidgetSnapshotInput(
                        sessions = listOf(session("s1")),
                        details =
                            mapOf(
                                "s1" to
                                    WidgetRowDetails(
                                        project = "/private/routing/My project",
                                        model = "big-pickle",
                                        preview = preview,
                                        ask = mapOf("kind" to "delete-everything", "q" to "Which?"),
                                    ),
                            ),
                    ),
                )
        val raw = snapshot.toString()
        val row = rows(snapshot).single()
        assertEquals("My project", row.str("project"))
        assertEquals("big-pickle", row.str("model"))
        val shown = row.str("preview")!!
        assertEquals(240, shown.codePointCount(0, shown.length))
        assertTrue("مرحبا" in shown)
        assertEquals("other", row.getValue("ask").jsonObject.str("kind"))
        for (forbidden in
            listOf(
                "/private/routing",
                "registered-private-secret",
                "synthetic-password",
                "؜",
                "‎",
                "‏",
                "‮",
                "⁧",
                "\u0001",
                "\n",
            )) {
            assertFalse(forbidden in shown)
            assertFalse(forbidden in raw)
        }
    }

    // ---- B-ANDR-116 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-116 writes at most twelve sessions with busy flags and the title fallback`() {
        val sessions =
            listOf(
                session("s1", "Fix auth", 50),
                session("s2", updated = 40),
                session("s3", "Refactor", 30),
                session("s4", "Docs", 20),
                session("s5", "Fifth", 10),
                session("s6", "Sixth", 9),
            ) + (7..12).map { session("s$it", "Row $it", 9L - it) } + session("s13", "Never shown", -10)
        val snapshot =
            build(WidgetSnapshotInput(sessions = sessions, busy = setOf("s1", "s3"), connected = true, profileId = "server-1"))
        assertEquals(true, snapshot.getValue("connected").jsonPrimitive.boolean)
        assertEquals("server-1", snapshot.str("profileID"))
        val rows = rows(snapshot)
        assertEquals(WidgetSnapshotBuilder.MAX_SESSIONS, rows.size)
        assertEquals(12, WidgetSnapshotBuilder.MAX_SESSIONS)
        assertEquals(
            buildJsonObject {
                put("id", "s1")
                put("title", "Fix auth")
                put("busy", true)
                put("updatedAt", 50)
            },
            rows[0],
        )
        assertEquals("New conversation", rows[2].str("title"))
        assertEquals(false, rows[2].getValue("busy").jsonPrimitive.boolean)
        // No prompt text, tool input or file paths: exactly these four keys per row.
        for (row in rows) assertEquals(setOf("id", "title", "busy", "updatedAt"), row.keys)
        assertFalse(rows.any { it.str("id") == "s13" })
    }

    @Test
    fun `B-ANDR-116 the untitled fallback is the caller's words, English if none`() {
        val arabic = build(WidgetSnapshotInput(sessions = listOf(session("s1", updated = 50)), untitledLabel = "محادثة بلا عنوان"))
        assertEquals("محادثة بلا عنوان", rows(arabic).single().str("title"))
        val english = build(WidgetSnapshotInput(sessions = listOf(session("s1", updated = 50))))
        assertEquals("New conversation", rows(english).single().str("title"))
    }

    @Test
    fun `B-ANDR-116 publishes presented titles instead of server placeholders`() {
        val snapshot =
            build(
                WidgetSnapshotInput(
                    sessions =
                        listOf(
                            session("default", "New session - 2026-10-15T04:39:03.218Z"),
                            session("child", "Child session - 2026-10-15T04:39:03.218Z"),
                            session("custom", "Fix login 2026-10-15T04:39:03.218Z"),
                            session("markup", "Fix auth…<tool_call>private input"),
                        ),
                ),
            )
        assertEquals(listOf("New conversation", "New conversation", "Fix login", "Fix auth"), rows(snapshot).map { it.str("title") })
    }

    @Test
    fun `B-ANDR-116 presented titles use the app language and stay redacted`() {
        val arabic = WidgetSnapshotBuilder(redactor, WidgetSnapshotWords(newConversation = "محادثة جديدة"))
        val snapshot =
            arabic.build(
                WidgetSnapshotInput(
                    sessions =
                        listOf(
                            session("default", "New session - 2026-10-15T04:39:03.218Z"),
                            session("empty"),
                            session("custom", "إصلاح تسجيل الدخول"),
                            session("secret", "password=synthetic-password\nTitle"),
                        ),
                ),
            )
        val titles = rows(snapshot).map { it.str("title")!! }
        assertEquals(listOf("محادثة جديدة", "محادثة جديدة", "إصلاح تسجيل الدخول"), titles.take(3))
        assertFalse("synthetic-password" in snapshot.toString())
        assertTrue("Title" in titles.last())
        assertFalse("\n" in titles.last())
    }

    @Test
    fun `B-ANDR-116 the four rows a widget shows keep needs-you and running conversations`() {
        val sessions = (1..8).map { session("idle$it", "Idle $it", 1_000L - it) } + session("run", "Running", 1) + session("ask", "Asks", 0)
        val snapshot =
            build(
                WidgetSnapshotInput(
                    sessions = sessions,
                    busy = setOf("run"),
                    details = mapOf("ask" to WidgetRowDetails(requestId = "perm-1")),
                ),
            )
        val firstFour = rows(snapshot).take(4).map { it.str("id") }
        assertEquals(listOf("ask", "run", "idle1", "idle2"), firstFour)
    }

    @Test
    fun `B-ANDR-116 conversations of other projects join the rows plain and ordered needs-you, running, newest`() {
        val snapshot =
            build(
                WidgetSnapshotInput(
                    sessions = listOf(session("old", "Old here", 100)),
                    connected = true,
                    details = mapOf("old" to WidgetRowDetails(project = "here", preview = "kept preview")),
                    others =
                        listOf(
                            WidgetOtherConversation("new", "UI review", "IPTV_King", 900, busy = false, needsYou = false),
                            WidgetOtherConversation("run", "Running one", "IPTV_King", 500, busy = true, needsYou = false),
                            WidgetOtherConversation("ask", "Needs you", "/home/secret/dir", 1, busy = false, needsYou = true),
                        ),
                ),
            )
        val rows = rows(snapshot)
        assertEquals(listOf("ask", "run", "new", "old"), rows.map { it.str("id") })
        assertEquals(1L, snapshot.getValue("runningCount").jsonPrimitive.long)
        assertEquals(1L, snapshot.getValue("pendingCount").jsonPrimitive.long)
        val foreign = rows.first()
        assertEquals("dir", foreign.str("project"))
        assertFalse("preview" in foreign)
        assertFalse("requestID" in foreign)
        assertEquals(JsonArray(emptyList()), foreign["onDemandActions"])
        assertFalse("/home/secret" in JsonArray(rows).toString())
        assertEquals("kept preview", rows.last().str("preview"))
    }

    @Test
    fun `B-ANDR-116 rows name their server and mark what waits on the person`() {
        val snapshot =
            build(
                WidgetSnapshotInput(
                    sessions = listOf(session("mine", "Mine", 100)),
                    connected = true,
                    server = "This phone",
                    details =
                        mapOf(
                            "mine" to
                                WidgetRowDetails(
                                    project = "neon-breaker",
                                    needsYou = true,
                                    ask = mapOf("kind" to "other", "q" to "Which colour?"),
                                ),
                        ),
                    others =
                        listOf(
                            WidgetOtherConversation(
                                "far",
                                "Far away",
                                "api-server",
                                1,
                                busy = false,
                                needsYou = true,
                                server = "MacBook",
                                profileId = "server-b",
                            ),
                        ),
                ),
            )
        val rows = rows(snapshot).associateBy { it.str("id") }
        val mine = rows.getValue("mine")
        assertEquals("This phone", mine.str("server"))
        assertEquals(true, mine.getValue("needsYou").jsonPrimitive.boolean)
        assertFalse("requestID" in mine)
        assertEquals("Which colour?", mine.getValue("ask").jsonObject.str("q"))
        val far = rows.getValue("far")
        assertEquals("MacBook", far.str("server"))
        assertEquals(true, far.getValue("needsYou").jsonPrimitive.boolean)
        assertEquals(JsonArray(emptyList()), far["onDemandActions"])
        // A row of another server opens that server's conversation.
        assertEquals("server-b", far.str("profileID"))
    }

    // ---- B-ANDR-117 ------------------------------------------------------------------------------------------------

    private class Writes {
        val payloads = mutableListOf<String>()

        suspend fun write(json: String, profile: String): PublishResult {
            payloads += json
            return PublishResult.Written
        }
    }

    @Test
    fun `B-ANDR-117 unchanged payloads neither rewrite nor redraw`() = runTest {
        val writes = Writes()
        var now = 10L
        val publisher = WidgetSnapshotPublisher(PlatformCapabilities.android(), writes::write) { now++ }
        val sessions = listOf(session("s1", "Fix auth", 50))
        fun snapshot(busy: Set<String> = emptySet(), profile: String = "server-1") =
            build(WidgetSnapshotInput(sessions = sessions, busy = busy, connected = true, profileId = profile))
        assertEquals(PublishResult.Written, publisher.publish(snapshot(), "server-1"))
        assertEquals(PublishResult.Unchanged, publisher.publish(snapshot(), "server-1"))
        assertEquals(1, writes.payloads.size)
        publisher.publish(snapshot(busy = setOf("s1")), "server-1")
        assertEquals(2, writes.payloads.size)
        // A profile switch changes the payload: the widget redraws and its row intents pick up the new owner.
        publisher.publish(snapshot(busy = setOf("s1"), profile = "server-2"), "server-2")
        assertEquals(3, writes.payloads.size)
        // What was written carries when the app last looked; the comparison ignores that stamp.
        assertEquals(12L, Json.parseToJsonElement(writes.payloads.last()).jsonObject.getValue("lastSeenAt").jsonPrimitive.long)
    }

    @Test
    fun `B-ANDR-117 a platform without home widgets writes nothing`() = runTest {
        val writes = Writes()
        val publisher = WidgetSnapshotPublisher(PlatformCapabilities.desktop(), writes::write) { 1L }
        val result = publisher.publish(build(WidgetSnapshotInput(sessions = listOf(session("s1", "Fix auth", 50)))), "p1")
        assertEquals(PublishResult.Unchanged, result)
        assertTrue(writes.payloads.isEmpty())
    }

    @Test
    fun `B-ANDR-117 a refused write is tried again with the same content`() = runTest {
        var answer = PublishResult.Refused
        var calls = 0
        val publisher = WidgetSnapshotPublisher(PlatformCapabilities.android(), { _, _ -> calls++; answer }) { 1L }
        val snapshot = build(WidgetSnapshotInput(sessions = listOf(session("s1", "A"))))
        assertEquals(PublishResult.Refused, publisher.publish(snapshot, "p1"))
        answer = PublishResult.Written
        assertEquals(PublishResult.Written, publisher.publish(snapshot, "p1"))
        assertEquals(2, calls)
    }

    // ---- B-ANDR-120 ------------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-120 assistant replies publish a preview, never the prompt, reasoning or synthetic text`() {
        val live = WidgetLiveDetails(text, words)
        val noReply =
            live.details(
                messages =
                    listOf(
                        message(Role.User, part(PartType.Text, "Never expose this prompt"), id = "u1"),
                        message(
                            Role.Assistant,
                            part(PartType.Reasoning, "Never expose this reasoning"),
                            part(PartType.Text, "Synthetic content", synthetic = true),
                            id = "a1",
                        ),
                    ),
                busy = true,
                directory = "/private/atlas",
            )
        assertEquals("", noReply.preview)
        assertEquals("atlas", text.project(noReply.project!!))
        val reply =
            live.details(
                messages = listOf(message(Role.Assistant, part(PartType.Text, "The checks passed. password=secret-value"), id = "a1")),
                busy = false,
                directory = "/private/atlas",
            )
        assertEquals("The checks passed. password=•••", reply.preview)
        // Idle: no live timer, no step.
        assertEquals(0L, reply.startedAt)
        assertEquals("", reply.step)
        val latest =
            live.details(
                messages =
                    listOf(
                        message(Role.Assistant, part(PartType.Text, "Late old reply must not replace current reply"), id = "a1"),
                        message(Role.Assistant, part(PartType.Text, "Latest reply"), id = "a2"),
                    ),
                busy = false,
                directory = null,
            )
        assertEquals("Latest reply", latest.preview)
    }

    @Test
    fun `B-ANDR-120 a running tool publishes a generic step without its inputs`() {
        val live = WidgetLiveDetails(text, words)
        val input = buildJsonObject { put("command", "private command") }
        val details =
            live.details(
                messages =
                    listOf(
                        message(
                            Role.Assistant,
                            part(PartType.Tool, "", tool = ToolState(ToolStatus.Running, input = input), name = "bash"),
                            id = "a1",
                        ),
                    ),
                busy = true,
                directory = null,
                startedAt = 77,
            )
        assertEquals(words.toolCommand, details.preview)
        assertEquals(words.toolCommand, details.step)
        assertEquals(77L, details.startedAt)
        val snapshot = build(WidgetSnapshotInput(sessions = listOf(session("s1")), busy = setOf("s1"), details = mapOf("s1" to details)))
        assertFalse("private command" in snapshot.toString())
        assertEquals(words.toolCommand, rows(snapshot).single().str("step"))
    }

    @Test
    fun `B-ANDR-120 tool steps are words, never the raw tool id`() {
        assertEquals("Editing files…", WidgetToolStep.sentence("apply_patch", words))
        assertEquals("Searching files…", WidgetToolStep.sentence("grep", words))
        assertEquals("Showing a card…", WidgetToolStep.sentence("mcp__oc-ui__show", words))
        assertEquals("Checking a background task…", WidgetToolStep.sentence("task_notification", words))
        assertEquals("Running render mermaid diagram…", WidgetToolStep.sentence("render_mermaid_diagram", words))
        assertEquals("Running list issues…", WidgetToolStep.sentence("mcp__github__listIssues", words))
        assertEquals("Working…", WidgetToolStep.sentence("", words))
    }

    @Test
    fun `B-ANDR-120 the conversations widget names the model, never its raw id`() {
        val live = WidgetLiveDetails(text, words)
        val unnamed = live.details(messages = emptyList(), busy = false, directory = null, modelName = null)
        val snapshot = build(WidgetSnapshotInput(sessions = listOf(session("s1")), details = mapOf("s1" to unnamed)))
        assertFalse("model" in rows(snapshot).single())
        assertFalse("ll-alpha-free" in snapshot.toString())
        val named = live.details(messages = emptyList(), busy = false, directory = null, modelName = "LL Alpha Free")
        val withName = build(WidgetSnapshotInput(sessions = listOf(session("s1")), details = mapOf("s1" to named)))
        assertEquals("LL Alpha Free", rows(withName).single().str("model"))
    }

    @Test
    fun `B-ANDR-120 an empty preview field is still published so an old reply never lingers`() {
        val snapshot =
            build(WidgetSnapshotInput(sessions = listOf(session("s1")), details = mapOf("s1" to WidgetRowDetails(preview = ""))))
        assertEquals(JsonPrimitive(""), rows(snapshot).single()["preview"])
    }
}
