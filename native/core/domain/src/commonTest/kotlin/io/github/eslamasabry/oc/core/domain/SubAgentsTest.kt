@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.SubAgent
import io.github.eslamasabry.oc.core.model.SubAgentOpen
import io.github.eslamasabry.oc.core.model.SubAgentState
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The provider-neutral sub-agent source (SubAgents) and the dock's backfill (SubAgentBackfill). */
class SubAgentsTest {
    private val p = SessionId("p")

    private fun child(
        id: String,
        parent: SessionId? = p,
        agent: String? = null,
        title: String? = "Task $id",
        forkOf: SessionId? = null,
    ) = Session(
        SessionId(id),
        title,
        parentId = parent,
        agent = agent,
        forkOf = forkOf,
        time = SessionTime(createdMs = 1),
    )

    @Test
    fun `S30-CHAT-003 only direct children are sub-agents, never a fork, each once`() {
        val list =
            SubAgents.of(
                p,
                listOf(
                    child("a", agent = "explore"),
                    child("fork", forkOf = p),
                    child("other", parent = SessionId("q")),
                    child("a", agent = "explore"),
                    child("p"),
                    child("b", agent = " ", title = " Review "),
                ),
                statuses = persistentMapOf(SessionId("a") to SessionStatus.Busy),
                readOnly = true,
            )
        assertEquals(listOf("a", "b"), list.map { it.id.raw })
        assertEquals("explore", list[0].name, "the agent's name when the server says it")
        assertEquals("Review", list[1].name, "else the title")
        assertEquals(SubAgentState.Working, list[0].state)
        assertEquals(SubAgentState.Done, list[1].state, "a known idle child is done")
        assertEquals(SubAgentOpen.Conversation(SessionId("a"), readOnly = true), list[0].open)
        assertTrue(list.all { it.parent == p })
    }

    @Test
    fun `S30-CHAT-003 a sub-agent's state reads running first, then how its last run ended`() {
        val c = child("c")
        assertEquals(SubAgentState.Unknown, SubAgents.stateOf(c, statuses = null))
        assertEquals(SubAgentState.Done, SubAgents.stateOf(c, statuses = emptyMap()))
        val retrying = mapOf(c.id to SessionStatus.Retry)
        assertEquals(SubAgentState.Working, SubAgents.stateOf(c, retrying, ended = SubAgentState.Failed))
        assertEquals(SubAgentState.Stopped, SubAgents.stateOf(c, emptyMap(), ended = SubAgentState.Stopped))
        assertEquals(SubAgentState.Failed, SubAgents.stateOf(c.copy(lastRunFailed = true), statuses = null))
    }

    @Test
    fun `S30-CHAT-003 no list is longer than the cap`() {
        val many = (1..(SubAgents.MAX_CHILDREN + 20)).map { child("c$it") }
        assertEquals(SubAgents.MAX_CHILDREN, SubAgents.of(p, many, null, readOnly = false).size)
    }

    @Test
    fun `S30-CHAT-003 the sub-agent read is gated on subagentSessions and wraps sessionChildren`() = runTest {
        val none = FakeOperationsGateway()
        val refused = assertIs<Outcome.Failed>(none.subAgents(p))
        assertEquals(Failure.Unavailable(FeatureId("subagentSessions")), refused.failure)
        val some =
            object : FakeOperationsGateway() {
                override suspend fun sessionChildren(id: SessionId): Outcome<ImmutableList<Session>> =
                    Outcome.Ok(persistentListOf(child("a"), child("f", forkOf = p)))
            }
        val read = assertIs<Outcome.Ok<ImmutableList<SubAgent>>>(some.subAgents(p)).value
        assertEquals(listOf("a"), read.map { it.id.raw })
        assertEquals(SubAgentState.Unknown, read.single().state, "the default read knows no run state")
    }

    @Test
    fun `S30-CHAT-003 withStates reads the statuses once and marks read-only without sub-agent replies`() = runTest {
        val live = FakeServerGateway()
        live.capabilities.value = live.capabilities.value.copy(subagentReplies = false)
        live.statuses = persistentMapOf(SessionId("a") to SessionStatus.Busy)
        val list = SubAgents.withStates(live, p, listOf(child("a"), child("b")))
        assertEquals(listOf(SubAgentState.Working, SubAgentState.Done), list.map { it.state })
        assertTrue(list.all { (it.open as SubAgentOpen.Conversation).readOnly })
        assertEquals(1, live.calls.count { it == "sessionStatuses" })
    }

    private class Ops : FakeOperationsGateway() {
        val asked = mutableListOf<SessionId>()

        override suspend fun sessionChildren(id: SessionId): Outcome<ImmutableList<Session>> {
            asked += id
            return when (id.raw) {
                "p1" -> Outcome.Ok(persistentListOf(Session(SessionId("c1"), "Look around", parentId = id)))
                "bad" -> Failure.Network(NetworkWhy.Refused).failed()
                else -> Outcome.Ok(persistentListOf())
            }
        }
    }

    @Test
    fun `S30-CHAT-003 the backfill reads on attach, connect and run start, never a running child`() = runTest {
        val live = FakeServerGateway()
        live.capabilities.value = live.capabilities.value.copy(subagentSessions = true)
        live.statuses = persistentMapOf(SessionId("p1") to SessionStatus.Busy, SessionId("idle") to SessionStatus.Idle)
        val ops = Ops()
        val out = mutableListOf<SubAgentsRead>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SubAgentBackfill.reads(live, ops) { listOf(SessionId("p2")) }.collect { out += it }
        }
        runCurrent()
        // On attach: the running conversations and the watched ones, never an idle one.
        assertEquals(listOf(SessionId("p1"), SessionId("p2")), ops.asked)
        assertEquals(listOf("c1"), out.first { it.parent == SessionId("p1") }.subAgents.map { it.id.raw })

        live.events.emit(GatewayEvent.RunStarted(SessionId("p3")))
        runCurrent()
        assertEquals(SessionId("p3"), ops.asked.last())

        // A reconnect: c1 is running now, but it is a known sub-agent, not a conversation to read.
        ops.asked.clear()
        live.statuses = persistentMapOf(SessionId("p1") to SessionStatus.Busy, SessionId("c1") to SessionStatus.Busy)
        live.events.connect()
        runCurrent()
        assertEquals(listOf(SessionId("p1"), SessionId("p2")), ops.asked)
    }

    @Test
    fun `S30-CHAT-003 the backfill reads nothing without the capability and emits no failed read`() = runTest {
        val live = FakeServerGateway()
        val ops = Ops()
        val out = mutableListOf<SubAgentsRead>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SubAgentBackfill.reads(live, ops).collect { out += it }
        }
        live.events.emit(GatewayEvent.RunStarted(SessionId("p1")))
        runCurrent()
        assertEquals(emptyList(), ops.asked, "the capability is off")

        live.capabilities.value = live.capabilities.value.copy(subagentSessions = true)
        live.events.emit(GatewayEvent.RunStarted(SessionId("bad")))
        runCurrent()
        assertEquals(listOf(SessionId("bad")), ops.asked)
        assertEquals(emptyList(), out)
    }
}
