package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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
}
