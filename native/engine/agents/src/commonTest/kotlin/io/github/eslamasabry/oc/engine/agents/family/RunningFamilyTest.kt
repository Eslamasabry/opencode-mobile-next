package io.github.eslamasabry.oc.engine.agents.family

import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of test/running_agents_strip_test.dart. */
class RunningFamilyTest {
    private fun s(id: String, parent: String? = null, created: Long = 0) =
        SessionId(id) to FamilySession(SessionId(id), parent?.let(::SessionId), created)

    private val sessions = mapOf(s("p", created = 1), s("a", "p", 2), s("b", "p", 3))
    private fun ids(vararg id: String) = id.map(::SessionId).toSet()

    @Test
    fun `B-AGENT-214 the strip is empty unless another family member is running`() {
        assertTrue(RunningFamily.entries(SessionId("a"), sessions, ids("a")).isEmpty())
        val entries = RunningFamily.entries(SessionId("a"), sessions, ids("b"))
        assertEquals(listOf("b", "a", "p"), entries.map { it.session.id.raw })
        assertTrue(entries.first().busy)
        assertTrue(entries[1].current)
        assertEquals(RunningAgentRelation.Parent, entries.last().relation)
        assertTrue(RunningFamily.entries(SessionId("missing"), sessions, ids("b")).isEmpty())
    }

    @Test
    fun `B-AGENT-214 a parent session lists its running children`() {
        val entries = RunningFamily.entries(SessionId("p"), sessions, ids("p", "a"))
        assertEquals(listOf("a", "p", "b"), entries.map { it.session.id.raw })
        assertEquals(
            listOf(RunningAgentRelation.Child, RunningAgentRelation.Current, RunningAgentRelation.Child),
            entries.map { it.relation },
        )
    }
}
