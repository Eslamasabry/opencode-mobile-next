package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * One row per conversation (w4-paseo audit; port of test/paseo_archive_copies_test.dart's list half): Paseo keeps one
 * record per resume or import of a runtime session, and the list shows the newest with the title the older one had.
 * Titles stay app-side: nothing is renamed on the daemon (`update_agent_request` moves the row to "Just now").
 */
class PaseoListCopiesTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun agent(id: String, session: String, title: String?, updatedAt: String): JsonObject {
        val base = agentJson(id, title = title, updatedAt = updatedAt, loaded = false)
        return JsonObject(base + ("persistence" to jsonOf("provider" to "claude", "sessionId" to session)))
    }

    /** Pages of `fetch_agents_request`, in order (the last repeats); `fetch_agent_request` answers any listed record. */
    private fun FakeDaemon.listing(vararg pages: List<JsonObject>) {
        val byId = pages.flatMap { it }.associateBy { it.str("id") }
        handlers["fetch_agent_request"] = { m -> "fetch_agent_response" to jsonOf("agent" to byId[m.str("agentId")]) }
        var read = 0
        handlers["fetch_agents_request"] = {
            val page = pages[read.coerceAtMost(pages.size - 1)]
            val more = read < pages.size - 1
            read++
            "fetch_agents_response" to jsonOf(
                "entries" to page.map { mapOf("agent" to it) },
                "pageInfo" to mapOf("nextCursor" to if (more) "next-$read" else null, "prevCursor" to null, "hasMore" to more),
            )
        }
    }

    // ---- list: one row per conversation

    @Test
    fun `W4-PASEO older records of one conversation leave the list and give the kept one their title`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.listing(
            listOf(
                agent("new", "native-1", title = null, updatedAt = "2026-10-04T08:00:00Z"),
                agent("old", "native-1", title = "Fix the login bug", updatedAt = "2026-10-03T08:00:00Z"),
                agent("other", "native-2", title = "Another chat", updatedAt = "2026-10-02T08:00:00Z"),
            ),
        )
        val h = GatewayHarness(this, daemon)
        val page = h.gateway.sessionPage(null, 50).ok()
        assertEquals(listOf("new", "other"), page.items.map { it.id.raw })
        assertEquals("Fix the login bug", page.items.first().title)
        // The kept record's own header says the same; nothing is written to the daemon to restore it.
        assertEquals("Fix the login bug", h.gateway.session(SessionId("new")).ok().title)
        assertTrue(daemon.of("update_agent_request").isEmpty())
        assertTrue(daemon.of("archive_agent_request").isEmpty())
        assertTrue(daemon.of("resume_agent_request").isEmpty())
    }

    @Test
    fun `W4-PASEO a newer record listed after an older one replaces it and a later page's older copy stays out`() =
        runTest {
            val daemon = FakeDaemon().seedDefaults()
            daemon.listing(
                listOf(
                    agent("old", "native-1", title = "Kept title", updatedAt = "2026-10-03T08:00:00Z"),
                    agent("new", "native-1", title = null, updatedAt = "2026-10-04T08:00:00Z"),
                ),
                listOf(agent("oldest", "native-1", title = "Oldest", updatedAt = "2026-10-01T08:00:00Z")),
            )
            val h = GatewayHarness(this, daemon)
            val first = h.gateway.sessionPage(null, 2).ok()
            assertEquals(listOf("new"), first.items.map { it.id.raw })
            assertEquals("Kept title", first.items.single().title)
            val second = h.gateway.sessionPage(first.nextCursor, 2).ok()
            assertTrue(second.items.isEmpty())
            // A conversation's own title is never replaced by an older record's.
            assertEquals("Kept title", h.gateway.session(SessionId("new")).ok().title)
        }

    @Test
    fun `W4-PASEO a record without a title of its own shows the title the app saved, never written back`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.listing(listOf(agent("a1", "native-a1", title = null, updatedAt = "2026-10-04T08:00:00Z")))
        val h = GatewayHarness(this, daemon)
        h.gateway.savedTitle = { key -> if (key == "claude:native-a1") "Saved on this phone" else null }
        assertEquals("Saved on this phone", h.gateway.sessionPage(null, 50).ok().items.single().title)
        assertTrue(daemon.of("update_agent_request").isEmpty())
    }

    /**
     * The daemon's own paging over [all] (newest first): `limit` records from the cursor `at-<n>`, so readers with
     * different page sizes interleave as they do on a phone (the home list, the monitors, the projects pages).
     */
    private fun FakeDaemon.records(all: () -> List<JsonObject>) {
        handlers["fetch_agent_request"] = { m ->
            "fetch_agent_response" to jsonOf("agent" to all().firstOrNull { it.str("id") == m.str("agentId") })
        }
        handlers["fetch_agents_request"] = { m ->
            val page = m.obj("page")
            val from = page?.str("cursor")?.removePrefix("at-")?.toInt() ?: 0
            val limit = (page?.get("limit") as? JsonPrimitive)?.content?.toInt() ?: 50
            val list = all()
            val more = from + limit < list.size
            "fetch_agents_response" to jsonOf(
                "entries" to list.drop(from).take(limit).map { mapOf("agent" to it) },
                "pageInfo" to mapOf("nextCursor" to if (more) "at-${from + limit}" else null, "prevCursor" to null, "hasMore" to more),
            )
        }
    }

    @Test
    fun `W4-PASEO-GATE records without a persistence handle are never folded together`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        val bare = { id: String -> JsonObject(agentJson(id, title = null, loaded = false) - "persistence") }
        daemon.records {
            listOf(
                bare("draft-a"),
                bare("draft-b"),
                agent("blank-1", "", title = null, updatedAt = "2026-10-02T08:00:00Z"),
                agent("blank-2", "", title = null, updatedAt = "2026-10-01T08:00:00Z"),
            )
        }
        val h = GatewayHarness(this, daemon)
        assertEquals(listOf("draft-a", "draft-b", "blank-1", "blank-2"), h.gateway.sessionPage(null, 50).ok().items.map { it.id.raw })
    }

    @Test
    fun `W4-PASEO-GATE another reader's first page does not undo the fold of a listing still paging`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        var records = listOf(
            agent("a", "native-a", title = "Another chat", updatedAt = "2026-10-05T08:00:00Z"),
            agent("new", "native-1", title = null, updatedAt = "2026-10-04T08:00:00Z"),
            agent("old", "native-1", title = "Fix the login bug", updatedAt = "2026-10-03T08:00:00Z"),
        )
        daemon.records { records }
        val h = GatewayHarness(this, daemon)
        val first = h.gateway.sessionPage(null, 2).ok()
        assertEquals(listOf("a", "new"), first.items.map { it.id.raw })
        // A monitor reads the newest conversation between the list's two pages.
        assertEquals(listOf("a"), h.gateway.sessionPage(null, 1).ok().items.map { it.id.raw })
        val second = h.gateway.sessionPage(first.nextCursor, 2).ok()
        assertTrue(second.items.isEmpty(), "the older record came back as a second row: ${second.items.map { it.id.raw }}")
        assertEquals(null, second.nextCursor)
        // A refresh after a newer record appeared keeps that one: nothing from the earlier listing hides it.
        records = listOf(agent("newest", "native-1", title = null, updatedAt = "2026-10-06T08:00:00Z")) + records
        val again = h.gateway.sessionPage(null, 50).ok()
        assertEquals(listOf("newest", "a"), again.items.map { it.id.raw })
        assertEquals("Fix the login bug", again.items.first().title)
    }

    @Test
    fun `W4-PASEO-GATE a push for an older record adds no second row, and the kept row still updates`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        val new = agent("new", "native-1", title = null, updatedAt = "2026-10-04T08:00:00Z")
        val old = agent("old", "native-1", title = "Fix the login bug", updatedAt = "2026-10-03T08:00:00Z")
        daemon.records { listOf(new, old) }
        val h = GatewayHarness(this, daemon)
        assertEquals(listOf("new"), h.gateway.sessionPage(null, 50).ok().items.map { it.id.raw })
        h.events.clear()
        daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to old))
        h.pump()
        assertTrue(h.events.filterIsInstance<GatewayEvent.SessionUpserted>().isEmpty(), "older record pushed: ${h.types()}")
        daemon.push(
            "agent_update",
            jsonOf("kind" to "upsert", "agent" to JsonObject(new + ("updatedAt" to JsonPrimitive("2026-10-04T09:00:00Z")))),
        )
        h.pump()
        val row = h.events.filterIsInstance<GatewayEvent.SessionUpserted>().single().session
        assertEquals("new", row.id.raw)
        assertEquals("Fix the login bug", row.title)
        // A resume elsewhere makes a newer record: it is the conversation's row now, with the title it had.
        h.events.clear()
        daemon.push(
            "agent_update",
            jsonOf("kind" to "upsert", "agent" to agent("resumed", "native-1", title = null, updatedAt = "2026-10-05T08:00:00Z")),
        )
        h.pump()
        val resumed = h.events.filterIsInstance<GatewayEvent.SessionUpserted>().single().session
        assertEquals("resumed", resumed.id.raw)
        assertEquals("Fix the login bug", resumed.title)
    }

    @Test
    fun `W4-PASEO-GATE archiving a conversation archives its older records too, never a newer one`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.records {
            listOf(
                agent("newest", "native-1", title = null, updatedAt = "2026-10-05T08:00:00Z"),
                agent("mid", "native-1", title = "Fix the login bug", updatedAt = "2026-10-04T08:00:00Z"),
                agent("other", "native-2", title = "Another chat", updatedAt = "2026-10-03T08:00:00Z"),
                agent("oldest", "native-1", title = null, updatedAt = "2026-10-02T08:00:00Z"),
            )
        }
        daemon.handlers["archive_agent_request"] = { m -> "agent_archived" to jsonOf("agentId" to m.str("agentId")) }
        val h = GatewayHarness(this, daemon)
        h.gateway.sessionPage(null, 50).ok()
        h.gateway.deleteSession(SessionId("mid")).ok()
        assertEquals(listOf("mid", "oldest"), daemon.of("archive_agent_request").map { it.str("agentId") })
        h.gateway.deleteSession(SessionId("newest")).ok()
        assertEquals(listOf("mid", "oldest", "newest"), daemon.of("archive_agent_request").map { it.str("agentId") })
    }
}
