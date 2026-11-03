package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotBuilder
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotWords
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetText
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The live feed reaches the widget snapshot through the `:android:widgets` builder. */
class WidgetSnapshotWiringTest {
    private val a = ProfileId("server-a")
    private val b = ProfileId("server-b")
    private val redactor = Redactor()
    private val words = WidgetSnapshotWords()

    private fun item(profile: ProfileId, id: String, status: ChatStatus = ChatStatus.Idle, at: Long = 1, preview: String = "") =
        ChatFeedItem("oc", profile, SessionId(id), "Work $id", "/srv/app", "app", status, at, preview = preview)

    private fun build(items: List<ChatFeedItem>, waiting: List<Waiting>, since: MutableMap<ProfileId, Long> = mutableMapOf(), now: Long = 5): JsonObject {
        val input =
            WidgetSnapshotWiring.input(
                ChatFeedSnapshot(items.toImmutableList()),
                waiting,
                a,
                mapOf(a to "Laptop", b to "Studio"),
                setOf(a),
                since,
                now,
                WidgetText(redactor),
                words,
            )
        return WidgetSnapshotBuilder(redactor, words).build(input)
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `B-ANDR-111 a pending shell permission reaches the widget as its question and command only`() {
        val request =
            PermissionRequest(
                RequestId("per_1"),
                SessionId("a1"),
                "bash",
                persistentListOf("touch /root/x"),
                JsonObject(mapOf("command" to JsonPrimitive("touch /root/x"), "secretInput" to JsonPrimitive("hidden-value"))),
                persistentListOf(),
                null,
                false,
                null,
            )
        val snapshot = build(listOf(item(a, "a1")), listOf(Waiting(a, PendingRequest.Permission(request))))
        val row = snapshot.getValue("sessions").jsonArray.single().jsonObject
        assertEquals("per_1", row.str("requestID"))
        val ask = row.getValue("ask").jsonObject
        assertEquals(setOf("kind", "q", "file", "path", "dir"), ask.keys)
        assertEquals("run", ask.str("kind"))
        assertEquals("Run this command?", ask.str("q"))
        assertEquals("touch /root/x", ask.str("file"))
        assertFalse("hidden-value" in snapshot.toString())
    }

    @Test
    fun `B-ANDR-116 another server's rows are plain, name their server and order needs-you first`() {
        val snapshot =
            build(
                listOf(item(a, "a1", at = 100, preview = "**Done**"), item(b, "b1", ChatStatus.NeedsYou, at = 1)),
                emptyList(),
            )
        val rows = snapshot.getValue("sessions").jsonArray.map { it.jsonObject }
        assertEquals(listOf("b1", "a1"), rows.map { it.str("id") })
        assertEquals("Studio", rows[0].str("server"))
        assertEquals("server-b", rows[0].str("profileID"))
        assertFalse("preview" in rows[0])
        assertEquals("Laptop", rows[1].str("server"))
        assertEquals("Done", rows[1].str("preview"))
        assertEquals("app", rows[1].str("project"))
    }

    @Test
    fun `B-ANDR-115 an offline server keeps the time it went offline across publishes`() {
        val since = mutableMapOf<ProfileId, Long>()
        build(emptyList(), emptyList(), since, now = 10)
        val later = build(emptyList(), emptyList(), since, now = 99)
        val servers = later.getValue("servers").jsonArray.map { it.jsonObject }
        assertEquals("online", servers.first { it.str("name") == "Laptop" }.str("state"))
        val studio = servers.first { it.str("name") == "Studio" }
        assertEquals("offline", studio.str("state"))
        assertEquals("10", studio.str("since"))
    }
}
