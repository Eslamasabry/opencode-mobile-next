package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Approval modes (plan 09 §5.3, plan 17 §1.10, map CHAT-GENUI-45; Dart `session_auto_approval.dart`, `permissions.dart`). */
class AutoApprovalTest {
    private fun ask(id: String, session: String) =
        PermissionRequest(
            id = RequestId(id), sessionId = SessionId(session), permission = "bash",
            patterns = persistentListOf("npm test"), metadata = JsonObject(emptyMap()), always = persistentListOf(),
            tool = null, canAlwaysAllow = true, message = null,
        )

    private val parents = mapOf("child" to "parent", "grandchild" to "child", "loopA" to "loopB", "loopB" to "loopA")
    private val parentOf: (SessionId) -> SessionId? = { s -> parents[s.raw]?.let(::SessionId) }

    private fun mode(settings: Map<String, SessionApproval>, session: String) =
        ApprovalModes.effective(settings, SessionId(session), parentOf)

    @Test
    fun `B-AGENT-223 subagents inherit through child and grandchild only when the parent inherits`() {
        val inherit = mapOf("parent" to SessionApproval(ApprovalMode.AutoOnce, inheritToChildren = true))
        assertTrue(mode(inherit, "grandchild").automatic)
        assertEquals(SessionId("parent"), mode(inherit, "grandchild").inheritedFrom)
        val noInherit = mapOf("parent" to SessionApproval(ApprovalMode.AutoOnce))
        assertFalse(mode(noInherit, "child").automatic, "children keep asking")
        val override = inherit + ("child" to SessionApproval(ApprovalMode.Ask))
        assertFalse(mode(override, "grandchild").automatic, "an explicit child override stops inherited approval")
        assertFalse(mode(inherit, "loopA").automatic, "cycles end the walk")
    }

    @Test
    fun `B-AGENT-225 a conversation set to ask keeps asking under approve-everything, which reaches never-seen ones`() {
        val all = mapOf(ApprovalModes.SERVER_WIDE to SessionApproval(ApprovalMode.AutoOnce, inheritToChildren = true))
        assertTrue(mode(all, "never-seen").serverWide)
        assertTrue(mode(all, "never-seen").automatic)
        assertFalse(mode(all + ("mine" to SessionApproval(ApprovalMode.Ask)), "mine").automatic)
        assertFalse(mode(emptyMap(), "x").automatic, "B-AGENT-221: nothing is auto-answered without a setting")
    }

    @Test
    fun `B-AGENT-222 automatic approval answers once, never always, and records it`() = runTest {
        val replies = mutableListOf<RequestId>()
        val sweep = AutoApprovalSweep({ it.raw == "auto" }, { replies += it.id; Unit.ok() }, TestClock())
        assertTrue(sweep.answer(ask("per_1", "auto")))
        assertFalse(sweep.answer(ask("per_1", "auto")), "an already-answered ask is not answered or logged again")
        assertFalse(sweep.answer(ask("per_2", "manual")))
        assertEquals(listOf(RequestId("per_1")), replies)
        assertEquals(listOf("per_1"), sweep.history.value[SessionId("auto")]!!.map { it.requestId.raw })
    }

    @Test
    fun `B-AGENT-225 switching on answers asks already waiting, including ones a reconnect found`() = runTest {
        var on = false
        val replies = mutableListOf<String>()
        val sweep = AutoApprovalSweep({ on }, { replies += it.id.raw; Unit.ok() }, TestClock())
        val waiting = listOf(ask("per_1", "s"), ask("per_2", "s"))
        assertEquals(emptyList(), sweep.sweep(waiting))
        on = true
        assertEquals(listOf(RequestId("per_1"), RequestId("per_2")), sweep.sweep(waiting))
        // Reconnect hydration finds the same asks plus one new one: only the new one is answered.
        assertEquals(listOf(RequestId("per_3")), sweep.sweep(waiting + ask("per_3", "s")))
        assertEquals(listOf("per_1", "per_2", "per_3"), replies)
    }

    @Test
    fun `B-AGENT-224 a refused automatic reply stays visible and explained, and the record is bounded and cleared`() =
        runTest {
            var next: Outcome<Unit> = Failure.Network().failed()
            val sweep = AutoApprovalSweep({ true }, { next }, TestClock())
            assertFalse(sweep.answer(ask("per_x", "s")))
            assertIs<Failure.Network>(sweep.failures.value[RequestId("per_x")])
            assertFalse(sweep.answer(ask("per_x", "s")), "a failed ask waits for the person, not another automatic try")
            sweep.settled(RequestId("per_x"))
            next = Unit.ok()
            repeat(7) { sweep.answer(ask("per_$it", "s")) }
            assertEquals((2..6).map { "per_$it" }, sweep.history.value[SessionId("s")]!!.map { it.requestId.raw })
            sweep.clear()
            assertTrue(sweep.history.value.isEmpty())
        }
}
