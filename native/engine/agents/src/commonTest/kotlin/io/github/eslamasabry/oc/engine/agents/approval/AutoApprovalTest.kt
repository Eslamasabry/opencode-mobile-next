package io.github.eslamasabry.oc.engine.agents.approval

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Port of test/session_auto_approval_controller_test.dart (engine-side decisions; the wire is the requests engine's). */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoApprovalTest {
    private class FakePort : PermissionReplyPort {
        val replies = mutableListOf<RequestId>()
        var hold: CompletableDeferred<Unit>? = null
        var fail = false
        var gone = false

        override suspend fun replyOnce(request: PendingRequest): Outcome<AutoReplyReceipt> {
            hold?.await()
            if (gone) return AutoReplyReceipt.AlreadyResolved.ok()
            if (fail) return Failure.Unknown().failed()
            replies += request.id
            return AutoReplyReceipt.Confirmed.ok()
        }
    }

    private val parents = mapOf("child" to "parent", "grandchild" to "child")
    private val port = FakePort()
    private val prefs = FakeAgentPrefs()
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
    }

    private fun TestScope.boot(connected: Boolean = true) =
        AutoApproval(ProfileId("p"), AutoApprovalStore(prefs), port, { parents[it.raw]?.let(::SessionId) }, clock, backgroundScope)
            .also { it.setConnected(connected) }

    private fun ask(id: String, session: String, kind: PendingKind = PendingKind.Permission, action: String = "bash") =
        PendingRequest(RequestId(id), SessionId(session), kind, action, persistentListOf("git status"))

    private fun ids(list: List<RequestId>) = list.map { it.raw }
    private val auto = SessionAutoApproval(AutoApprovalMode.AutoOnce)

    @Test
    fun `B-AGENT-221 new sessions ask - nothing is answered without a setting`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.onAsked(ask("req-1", "parent"))
        approval.onAsked(ask("per_shell", "parent", action = "shell"))
        assertTrue(port.replies.isEmpty())
        assertEquals(2, approval.state.value.awaitingFor(SessionId("parent")).size)
    }

    @Test
    fun `B-AGENT-221 questions and forms are never answered automatically`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), SessionAutoApproval.AutoInherit)
        approval.onAsked(ask("q-1", "parent", PendingKind.Question))
        approval.onAsked(ask("q-2", "child", PendingKind.Question))
        approval.onAsked(ask("f-1", "parent", PendingKind.Form))
        assertTrue(port.replies.isEmpty())
        assertEquals(3, approval.state.value.awaiting.size)
    }

    @Test
    fun `B-AGENT-222 a request is answered once and recorded, and is not offered to a person while in flight`() =
        runTest(UnconfinedTestDispatcher()) {
            val approval = boot()
            approval.setSession(SessionId("parent"), auto)
            port.hold = CompletableDeferred()
            approval.onAsked(ask("req-1", "parent"))
            val inFlight = approval.state.value
            assertTrue(RequestId("req-1") in inFlight.approving)
            assertTrue(RequestId("req-1") in inFlight.pending)
            assertTrue(inFlight.awaitingFor(SessionId("parent")).isEmpty())
            port.hold!!.complete(Unit)
            val done = approval.state.value
            assertEquals(listOf("req-1"), ids(port.replies))
            assertFalse(RequestId("req-1") in done.approving)
            assertFalse(RequestId("req-1") in done.pending)
            val record = done.recentFor(SessionId("parent")).single()
            assertEquals("bash", record.action)
            assertEquals(listOf("git status"), record.patterns)
            assertEquals(1_000, record.atEpochMillis)
            assertNull(done.failures[RequestId("req-1")])
        }

    @Test
    fun `B-AGENT-222 an already resolved request is not logged as our approval`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), auto)
        port.gone = true
        approval.onAsked(ask("gone", "parent"))
        assertTrue(approval.state.value.pending.isEmpty())
        assertTrue(approval.state.value.recentFor(SessionId("parent")).isEmpty())
    }

    @Test
    fun `B-AGENT-223 a session that does not inherit keeps its subagents asking`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), auto)
        approval.onAsked(ask("req-child", "child"))
        assertTrue(port.replies.isEmpty())
        assertEquals(1, approval.state.value.awaitingFor(SessionId("child")).size)
    }

    @Test
    fun `B-AGENT-223 subagents inherit through child and grandchild`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), SessionAutoApproval.AutoInherit)
        approval.onAsked(ask("req-child", "child"))
        approval.onAsked(ask("req-grandchild", "grandchild"))
        approval.onAsked(ask("req-other", "other"))
        assertEquals(listOf("req-child", "req-grandchild"), ids(port.replies))
        assertEquals(SessionId("parent"), approval.effectiveFor(SessionId("grandchild")).inheritedFrom)
        assertEquals(1, approval.state.value.awaitingFor(SessionId("other")).size)
        assertEquals(1, approval.state.value.recentFor(SessionId("grandchild")).size)
    }

    @Test
    fun `B-AGENT-223 an explicit child override stops inherited approval until cleared`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), SessionAutoApproval.AutoInherit)
        approval.setSession(SessionId("child"), SessionAutoApproval.Ask)
        approval.onAsked(ask("req-child", "child"))
        approval.onAsked(ask("req-grandchild", "grandchild"))
        assertTrue(port.replies.isEmpty())
        approval.setSession(SessionId("child"), null)
        approval.onAsked(ask("req-child-2", "child"))
        assertEquals(setOf("req-child", "req-child-2", "req-grandchild"), ids(port.replies).toSet())
        assertTrue(approval.state.value.awaitingFor(SessionId("child")).isEmpty())
    }

    @Test
    fun `B-AGENT-224 not connected - the request stays pending and visible`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot(connected = false)
        approval.setSession(SessionId("parent"), SessionAutoApproval.AutoInherit)
        approval.onAsked(ask("req-1", "parent"))
        approval.onAsked(ask("perm-2", "child"))
        assertTrue(port.replies.isEmpty())
        assertTrue(approval.state.value.approving.isEmpty())
        assertEquals(2, approval.state.value.awaiting.size)
        assertTrue(approval.state.value.recentFor(SessionId("parent")).isEmpty())
    }

    @Test
    fun `B-AGENT-224 a refused reply leaves the request pending, visible and explained until answered by hand`() =
        runTest(UnconfinedTestDispatcher()) {
            val approval = boot()
            approval.setSession(SessionId("parent"), auto)
            port.fail = true
            approval.onAsked(ask("perm-1", "parent"))
            val state = approval.state.value
            assertTrue(state.approving.isEmpty())
            assertEquals(listOf("perm-1"), state.awaitingFor(SessionId("parent")).map { it.id.raw })
            assertEquals(AutoApprovalFailure.Unconfirmed, state.failures[RequestId("perm-1")])
            assertTrue(state.recentFor(SessionId("parent")).isEmpty())
            approval.onResolved(RequestId("perm-1")) // the person's own reply landed
            assertNull(approval.state.value.failures[RequestId("perm-1")])
            assertTrue(approval.state.value.pending.isEmpty())
        }

    @Test
    fun `B-AGENT-224 turning approval off mid-flight does not recall a sent reply, and later requests ask`() =
        runTest(UnconfinedTestDispatcher()) {
            val approval = boot()
            approval.setSession(SessionId("parent"), auto)
            port.hold = CompletableDeferred()
            approval.onAsked(ask("req-1", "parent"))
            approval.setSession(SessionId("parent"), SessionAutoApproval.Ask)
            approval.onAsked(ask("req-2", "parent"))
            port.hold!!.complete(Unit)
            assertEquals(listOf("req-1"), ids(port.replies))
            assertEquals(listOf("req-2"), approval.state.value.awaitingFor(SessionId("parent")).map { it.id.raw })
        }

    @Test
    fun `B-AGENT-224 the record is bounded and cleared with the connection`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), auto)
        repeat(25) { approval.onAsked(ask("req-$it", "parent")) }
        assertEquals(25, port.replies.size)
        val record = approval.state.value.recentFor(SessionId("parent"))
        assertEquals(20, record.size)
        assertEquals("req-24", record.last().requestId.raw)
        approval.setConnected(false)
        assertTrue(approval.state.value.recentFor(SessionId("parent")).isEmpty())
        assertTrue(approval.effectiveFor(SessionId("parent")).automatic) // the stored setting outlives the connection
    }

    @Test
    fun `B-AGENT-225 turning a conversation to auto answers its waiting request only`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.onAsked(ask("wait-1", "parent"))
        approval.onAsked(ask("wait-2", "other"))
        approval.setSession(SessionId("parent"), auto)
        assertEquals(listOf("wait-1"), ids(port.replies))
        assertEquals(1, approval.state.value.awaitingFor(SessionId("other")).size)
    }

    @Test
    fun `B-AGENT-225 approve everything answers waiting requests on every session`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.onAsked(ask("wait-1", "parent"))
        approval.onAsked(ask("wait-2", "other"))
        approval.setApprovesEverything(true)
        assertEquals(setOf("wait-1", "wait-2"), ids(port.replies).toSet())
        assertTrue(approval.state.value.pending.isEmpty())
        assertTrue(approval.approvesEverything)
    }

    @Test
    fun `B-AGENT-225 a conversation set to ask keeps its waiting request under approve everything`() =
        runTest(UnconfinedTestDispatcher()) {
            val approval = boot()
            approval.setSession(SessionId("parent"), SessionAutoApproval.Ask)
            approval.onAsked(ask("wait-1", "parent"))
            approval.setApprovesEverything(true)
            assertTrue(port.replies.isEmpty())
            assertEquals(1, approval.state.value.awaitingFor(SessionId("parent")).size)
        }

    @Test
    fun `B-AGENT-225 requests found after a reconnect are answered too`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.setSession(SessionId("parent"), auto)
        approval.onHydrated(listOf(ask("found-1", "parent", action = "external_directory")))
        assertEquals(listOf("found-1"), ids(port.replies))
        assertTrue(approval.state.value.awaitingFor(SessionId("parent")).isEmpty())
    }

    @Test
    fun `B-AGENT-225 a refused setting change answers nothing`() = runTest(UnconfinedTestDispatcher()) {
        val approval = boot()
        approval.onAsked(ask("wait-1", "parent"))
        prefs.refuseWrites = true
        assertTrue(approval.setSession(SessionId("parent"), auto) is Outcome.Failed)
        assertTrue(port.replies.isEmpty())
    }
}
