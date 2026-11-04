package io.github.eslamasabry.oc.engine.attention.widget

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSnapshotWriterTest {
    private val disk = FakeAttentionKeyStore()
    private val redactor = Redactor()
    private val host = FakeHost()
    private val writer = WidgetSnapshotWriter(disk, host, redactor, TestClock())
    private val en = "New conversation"

    class FakeHost : WidgetHost {
        var refreshes = 0
        var clears = mutableListOf<String>()
        var answer: Boolean? = null
        var throwOnClear = false

        override suspend fun refresh() {
            refreshes++
        }

        override suspend fun clearProfile(profileId: String): Boolean? {
            clears += profileId
            if (throwOnClear) error("Synthetic host failure")
            return answer
        }
    }

    private fun snapshot(): JsonObject = Json.parseToJsonElement(disk.data.getValue(WidgetSnapshotWriter.SNAPSHOT_KEY)).jsonObject

    private fun rows() = snapshot()["sessions"]!!.jsonArray.map { it.jsonObject }

    private fun s(id: String, title: String? = null, updated: Long? = null) = WidgetSessionRow(id, title, updated, createdAt = 1)

    @Test
    fun `B-SEC-181 project and assistant excerpt are redacted bounded display fields and raw fields never cross`() =
        runTest {
            redactor.registerSecret("registered-private-secret")
            writer.update(
                sessions = listOf(s("s1")),
                busySessions = emptySet(),
                connected = false,
                untitled = en,
                details =
                    mapOf(
                        "s1" to
                            mapOf(
                                "id" to "replaced-id",
                                "title" to "replaced-title",
                                "project" to "/private/routing/My project",
                                "model" to "big-pickle",
                                "preview" to
                                    "Done registered-private-secret password=synthetic-password\nمرحبا؜‎‏‮⁧\u0001 ${"😀".repeat(250)}",
                                "prompt" to "user-private-text",
                                "toolInput" to "tool-private-text",
                                "reasoning" to "reasoning-private-text",
                                "error" to "error-private-text",
                            ),
                    ),
            )
            val raw = disk.data.getValue(WidgetSnapshotWriter.SNAPSHOT_KEY)
            val row = rows().single()
            assertEquals("s1", row["id"]!!.jsonPrimitive.content)
            assertEquals(en, row["title"]!!.jsonPrimitive.content)
            assertEquals("My project", row["project"]!!.jsonPrimitive.content)
            assertEquals("big-pickle", row["model"]!!.jsonPrimitive.content)
            val preview = row["preview"]!!.jsonPrimitive.content
            assertEquals(240, WidgetText.codePointCount(preview))
            assertTrue(preview.contains("مرحبا"))
            for (forbidden in listOf(
                "/private/routing", "registered-private-secret", "synthetic-password", "user-private-text", "tool-private-text",
                "reasoning-private-text", "error-private-text", "replaced-", "؜", "‎", "‏", "‮", "⁧", "\u0001", "\n",
            )) {
                assertFalse(preview.contains(forbidden), forbidden)
                assertFalse(raw.contains(forbidden), forbidden)
            }
        }

    @Test
    fun `B-SEC-181 titles are presented in the app language, redacted, one line and at most 80 long`() =
        runTest {
            writer.update(
                sessions =
                    listOf(
                        s("default", "New session - 2026-10-15T04:39:03.218Z"),
                        s("empty"),
                        s("custom", "إصلاح تسجيل الدخول"),
                        s("stamp", "Fix login 2026-10-15T04:39:03.218Z"),
                        s("markup", "Fix auth…<tool_call>private input"),
                        s("secret", "password=synthetic-password\nTitle"),
                        s("long", "a".repeat(200)),
                    ),
                busySessions = emptySet(),
                connected = true,
                untitled = "محادثة جديدة",
            )
            val raw = disk.data.getValue(WidgetSnapshotWriter.SNAPSHOT_KEY)
            val titles = rows().map { it["title"]!!.jsonPrimitive.content }
            assertEquals(listOf("محادثة جديدة", "محادثة جديدة", "إصلاح تسجيل الدخول", "Fix login", "Fix auth"), titles.take(5))
            assertFalse(raw.contains("synthetic-password"))
            assertFalse(raw.contains("private input"))
            assertTrue(titles[5].contains("Title"))
            assertFalse(titles[5].contains("\n"))
            assertEquals(80, titles[6].length)
            assertFalse(WidgetText(redactor).title("password=synthetic-password\nTitle").contains("synthetic-password"))
        }

    @Test
    fun `B-SEC-181 at most twelve plain rows with busy flags, other projects named by display name only`() =
        runTest {
            writer.update(
                sessions = (1..13).map { s("s$it", "Row $it", 100L - it) },
                busySessions = setOf("s1", "s3"),
                connected = true,
                untitled = en,
                profileId = "server-1",
            )
            val decoded = snapshot()
            assertTrue(decoded["connected"]!!.jsonPrimitive.boolean)
            assertEquals("server-1", decoded["profileID"]!!.jsonPrimitive.content)
            assertEquals(2, decoded["version"]!!.jsonPrimitive.int)
            assertEquals(12, rows().size)
            assertEquals(setOf("id", "title", "busy", "updatedAt"), rows().first().keys)
            assertEquals(1, host.refreshes)
            writer.update(listOf(s("old", "Old here", 100)), emptySet(), true, en,
                others =
                    listOf(
                        WidgetOtherConversation("new", "UI review", "IPTV_King", 900, busy = false, needsYou = false),
                        WidgetOtherConversation("run", "Running one", "IPTV_King", 500, busy = true, needsYou = false),
                        WidgetOtherConversation("ask", "Needs you", "/home/secret/dir", 1, busy = false, needsYou = true),
                    ),
            )
            assertEquals(listOf("ask", "run", "new", "old"), rows().map { it["id"]!!.jsonPrimitive.content })
            assertEquals(1, snapshot()["runningCount"]!!.jsonPrimitive.int)
            assertEquals(1, snapshot()["pendingCount"]!!.jsonPrimitive.int)
            val foreign = rows().first()
            assertEquals("dir", foreign["project"]!!.jsonPrimitive.content)
            assertNull(foreign["preview"])
            assertNull(foreign["requestID"])
        }

    @Test
    fun `B-SEC-181 unchanged payloads neither rewrite nor redraw and non-Android writes nothing`() =
        runTest {
            val rows = listOf(s("s1", "Fix auth", 50))
            writer.update(rows, emptySet(), true, en)
            writer.update(rows, emptySet(), true, en)
            assertEquals(1, host.refreshes)
            writer.update(rows, setOf("s1"), true, en)
            assertEquals(2, host.refreshes)
            writer.update(rows, setOf("s1"), true, en, profileId = "server-2")
            assertEquals(3, host.refreshes)
            val desktop = FakeAttentionKeyStore()
            WidgetSnapshotWriter(desktop, host, redactor, TestClock(), isAndroid = false).update(rows, emptySet(), true, en)
            assertTrue(desktop.data.isEmpty())
            assertEquals(3, host.refreshes)
        }

    @Test
    fun `B-SEC-182 the snapshot keeps routing private and profile deletion removes the target`() =
        runTest {
            writer.update(
                listOf(s("s1")), emptySet(), true, en,
                profileId = "server-1", scopeToken = "opaque-scope",
                onDemand = WidgetOnDemandTarget("opaque-fingerprint", "/private/project", "private-workspace"),
            )
            val raw = disk.data.getValue(WidgetSnapshotWriter.SNAPSHOT_KEY)
            assertFalse(raw.contains("/private/project"))
            assertFalse(raw.contains("private-workspace"))
            assertEquals(buildJsonObject { put("fingerprint", "opaque-fingerprint") }, snapshot()["onDemand"])
            val target = Json.parseToJsonElement(disk.data.getValue(WidgetSnapshotWriter.targetKey("server-1"))).jsonObject
            assertEquals("/private/project", target["directory"]!!.jsonPrimitive.content)
            assertEquals("opaque-scope", target["scopeToken"]!!.jsonPrimitive.content)
            assertEquals("oc.widgetTarget.server-1", WidgetSnapshotWriter.targetKey("server-1"))
            assertEquals(WidgetSnapshotClear.Cleared, writer.clearForProfile("server-1"))
            assertFalse(disk.data.containsKey(WidgetSnapshotWriter.targetKey("server-1")))
            assertFalse(disk.data.containsKey(WidgetSnapshotWriter.SNAPSHOT_KEY))
        }

    @Test
    fun `B-SEC-182 suspension migrates legacy raw routing out of the snapshot`() =
        runTest {
            disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY] =
                """{"profileID":"server-1","scopeToken":"scope","onDemand":{"fingerprint":"fp","directory":"/private/project","workspace":"workspace"}}"""
            writer.update(emptyList(), emptySet(), false, en, profileId = "server-1", preserveForOnDemand = true)
            val raw = disk.data.getValue(WidgetSnapshotWriter.SNAPSHOT_KEY)
            assertEquals(buildJsonObject { put("fingerprint", "fp") }, snapshot()["onDemand"])
            assertFalse(raw.contains("/private/project"))
            assertFalse(snapshot()["connected"]!!.jsonPrimitive.boolean)
            assertNotNull(snapshot()["pausedAt"])
            val target = Json.parseToJsonElement(disk.data.getValue(WidgetSnapshotWriter.targetKey("server-1"))).jsonObject
            assertEquals("/private/project", target["directory"]!!.jsonPrimitive.content)
            // Suspension never resurrects a removed snapshot nor touches another profile's.
            disk.data.remove(WidgetSnapshotWriter.SNAPSHOT_KEY)
            writer.update(emptyList(), emptySet(), false, en, profileId = "server-1", preserveForOnDemand = true)
            assertFalse(disk.data.containsKey(WidgetSnapshotWriter.SNAPSHOT_KEY))
        }

    @Test
    fun `B-SEC-182 a host with nothing to say still clears and native clear is asked even with no cached snapshot`() =
        runTest {
            assertEquals(WidgetSnapshotClear.NothingToClear, writer.clearForProfile("p0"))
            assertEquals(listOf("p0"), host.clears)
            writer.update(listOf(s("s1", "A")), emptySet(), true, en, profileId = "p1")
            assertEquals(WidgetSnapshotClear.Cleared, writer.clearForProfile("p1"))
            assertNull(disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY])
            // Another profile's snapshot is left alone.
            writer.update(listOf(s("s1", "A")), emptySet(), true, en, profileId = "p2")
            assertEquals(WidgetSnapshotClear.NothingToClear, writer.clearForProfile("p3"))
            assertNotNull(disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY])
        }

    @Test
    fun `B-SEC-182 a native refusal is reported as failure and keeps the snapshot`() =
        runTest {
            writer.update(listOf(s("s1", "A")), emptySet(), true, en, profileId = "p1")
            host.answer = false
            assertEquals(WidgetSnapshotClear.Failed, writer.clearForProfile("p1"))
            assertNotNull(disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY])
            host.answer = null
            host.throwOnClear = true
            assertEquals(WidgetSnapshotClear.Failed, writer.clearForProfile("p1"))
            assertNotNull(disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY])
            host.throwOnClear = false
            disk.refuse = true
            assertEquals(WidgetSnapshotClear.Failed, writer.clearForProfile("p1"))
            assertNotNull(disk.data[WidgetSnapshotWriter.SNAPSHOT_KEY])
        }

    @Test
    fun `B-SEC-182 profile deletion drains queued widget writes first and leaves no routing behind`() =
        runTest {
            disk.gate = CompletableDeferred()
            disk.writeStarted = CompletableDeferred()
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                writer.update(
                    listOf(s("s1", "A")), emptySet(), true, en, profileId = "p1", scopeToken = "scope",
                    onDemand = WidgetOnDemandTarget("fp", "/private/project"),
                )
            }
            disk.writeStarted!!.await()
            val clearing = async(start = CoroutineStart.UNDISPATCHED) { writer.clearForProfile("p1") }
            assertTrue(host.clears.isEmpty()) // waits for the queued write
            disk.gate!!.complete(Unit)
            queued.await()
            assertEquals(WidgetSnapshotClear.Cleared, clearing.await())
            assertFalse(disk.data.containsKey(WidgetSnapshotWriter.SNAPSHOT_KEY))
            assertFalse(disk.data.containsKey(WidgetSnapshotWriter.targetKey("p1")))
            assertFalse(disk.data.values.any { it.contains("/private/project") })
        }
}
