package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SubAgent
import io.github.eslamasabry.oc.core.model.SubAgentOpen
import io.github.eslamasabry.oc.core.model.SubAgentState
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2Gateway
import io.github.eslamasabry.oc.protocol.oc2.live.RowServer
import io.github.eslamasabry.oc.protocol.oc2.live.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest

/**
 * OpenCode 2 sub-agents: children are sessions listed by `GET /session?parentID=` (Flutter
 * `lib/api2/gateway_operations/ops_sessions.dart` listSessionChildren), mapped from the captured session page
 * (`fixtures/oc2/flutter/api2/sessions_page.json`, whose second row is an `@explore` child). Forks also carry
 * `parentID` and stay out by their `fork` field (protocol notes §4.1).
 */
class Oc2SubAgentsTest {
    private val parent = SessionId("ses_fb605903fffeXEvQTkm7ciT5wg")
    private val child = SessionId("ses_fb5fc9dcfffexGUXQHNpaU8or2")

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    /** A page with one sub-agent whose last run failed and one fork of the same parent (notes §4.1 shape). */
    private val childAndFork =
        """
        {"data":[
          {"id":"ses_child","parentID":"ses_p","agent":"general","title":"Check the tests","outcome":"failed",
           "time":{"created":5,"updated":6},"location":{"directory":"/srv/app"}},
          {"id":"ses_fork","parentID":"ses_p","fork":{"sessionID":"ses_p","boundary":{"type":"through"}},
           "title":"Copy of the parent","time":{"created":7,"updated":7},"location":{"directory":"/srv/app"}}
        ],"cursor":{}}
        """.trimIndent()

    @Test
    fun `S30-CHAT-003 OpenCode 2 lists sub-agents by parentID and maps the captured child`() = runTest {
        val server = RowServer { seen ->
            when (seen.path) {
                "/api/session/active" -> json("""{"data":{"${child.raw}":{"type":"running"}}}""")
                else -> json(Fixtures.text("api2/sessions_page.json"))
            }
        }
        val children: ImmutableList<Session> = server.gateway.sessionChildren(parent).ok()
        // The captured page holds two top-level sessions too: only the parent's child is its child.
        assertEquals(listOf(child), children.map { it.id })
        val list = server.seen.first { it.path == "/api/session" }
        assertEquals(parent.raw, list.query["parentID"])
        assertEquals("200", list.query["limit"])
        assertNull(list.query["directory"], "parentID names the family; no folder filter")

        val agents: ImmutableList<SubAgent> = server.gateway.subAgents(parent).ok()
        val one = agents.single()
        assertEquals(child, one.id)
        assertEquals(parent, one.parent)
        assertEquals("explore", one.name)
        assertEquals("Review sample_app providers and evaluators (@explore subagent)", one.title)
        assertEquals(SubAgentState.Working, one.state)
        assertEquals(SubAgentOpen.Conversation(child, readOnly = false), one.open)
        server.gateway.close()
    }

    @Test
    fun `S30-CHAT-003 a fork of the conversation is not one of its sub-agents`() = runTest {
        val server = RowServer { seen ->
            when (seen.path) {
                "/api/session/active" -> json("""{"data":{}}""")
                else -> json(childAndFork)
            }
        }
        val parent = SessionId("ses_p")
        assertEquals(listOf(SessionId("ses_child")), server.gateway.sessionChildren(parent).ok().map { it.id })
        val agents = server.gateway.subAgents(parent).ok()
        assertEquals(listOf(SessionId("ses_child")), agents.map { it.id })
        // Its last run's outcome is the state when it is not running.
        assertEquals(SubAgentState.Failed, agents.single().state)
        assertEquals("general", agents.single().name)
        server.gateway.close()
    }

    @Test
    fun `S30-CHAT-003 a read session keeps its fork mark for every list`() = runTest {
        val server = RowServer { json(childAndFork) }
        val page = server.gateway.sessionPage(null, 50).ok().items
        assertNull(page.first { it.id.raw == "ses_child" }.forkOf)
        assertEquals(SessionId("ses_p"), page.first { it.id.raw == "ses_fork" }.forkOf)
        server.gateway.close()
    }

    @Test
    fun `S30-CHAT-003 OpenCode 2 offers the Subagents list by its capability`() {
        assertTrue(Oc2Gateway.Capabilities.subagentSessions)
        assertTrue(Oc2Gateway.Capabilities.projectManagement)
        // An OpenCode 2 sub-agent takes replies, as in Flutter.
        assertTrue(Oc2Gateway.Capabilities.subagentReplies)
    }

    @Test
    fun `S30-CHAT-003 a failed list read fails, it never answers an empty list`() = runTest {
        val server = RowServer { json("""{"code":"boom"}""", io.ktor.http.HttpStatusCode.InternalServerError) }
        assertFalse(server.gateway.subAgents(parent) is Outcome.Ok)
        server.gateway.close()
    }
}
