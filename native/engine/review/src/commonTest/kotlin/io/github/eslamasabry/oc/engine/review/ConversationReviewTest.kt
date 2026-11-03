package io.github.eslamasabry.oc.engine.review

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RevertKind
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationReviewTest {
    private val fake = StagedFake()
    private val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(fake, FakeOperationsGateway()))

    private fun TestScope.review(): ConversationReview = ConversationReview(S, gateways, backgroundScope, pageSize = 2)

    private fun history() {
        // Pages of two, newest page first, each chronological (the gateway contract).
        fake.pages = listOf(
            listOf(prompt("u2", 5), reply("a3", 6)),
            listOf(reply("a1", 3, edit("a.kt", 2, 1)), reply("a2", 4, edit("b.kt", 1, 0))),
            listOf(prompt("u1", 1)),
        )
    }

    @Test
    fun `B-CHAT-120 the undo shape follows the server, never its name`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(UndoShape.Staged, review().shape())
        val plain = FakeServerGateway()
        gateways.value = ReviewGateways(plain, FakeOperationsGateway())
        assertEquals(UndoShape.OneStep, review().shape())
        plain.capabilities.value = plain.capabilities.value.copy(sessionRevert = false)
        assertEquals(UndoShape.Unavailable, review().shape())
    }

    @Test
    fun `B-CHAT-103 the message count follows the prompt across pages and kinds, excluding the prompt`() = runTest(UnconfinedTestDispatcher()) {
        history()
        val r = review()
        assertEquals(4, (r.messagesAfter("u1") as Outcome.Ok).value)
        assertEquals(1, (r.messagesAfter("u2") as Outcome.Ok).value)
        assertEquals(0, (r.messagesAfter("a3") as Outcome.Ok).value)
    }

    @Test
    fun `B-CHAT-103 an unreached prompt is unknown, never zero`() = runTest(UnconfinedTestDispatcher()) {
        history()
        assertNull((ConversationReview(S, gateways, backgroundScope, pageSize = 2, maxPages = 1).messagesAfter("u1") as Outcome.Ok).value)
        assertNull((review().messagesAfter("missing") as Outcome.Ok).value)
    }

    @Test
    fun `B-CHAT-068 a turn's changes and the conversation's come from history pages oldest first`() = runTest(UnconfinedTestDispatcher()) {
        history()
        val r = review()
        val turn = (r.changes("a2") as Outcome.Ok).value
        assertEquals(listOf("a.kt", "b.kt"), turn.files.map { it.path })
        assertEquals("u1", turn.undoFrom)
        val all = (r.changes(null) as Outcome.Ok).value
        assertEquals(listOf("a.kt", "b.kt"), all.files.map { it.path })
        assertEquals("u1", all.undoFrom)
        assertTrue(all.complete)
    }

    @Test
    fun `B-CHAT-068 undo all is not offered from an incomplete history`() = runTest(UnconfinedTestDispatcher()) {
        history()
        val all = (ConversationReview(S, gateways, backgroundScope, pageSize = 2, maxPages = 2).changes(null) as Outcome.Ok).value
        assertFalse(all.complete)
        assertNull(all.undoFrom)
    }

    @Test
    fun `B-CHAT-105 staging returns the preview at once and keeps its metadata`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val staged = (r.stage("u1", applyFiles = false) as Outcome.Ok).value
        assertEquals(listOf("statuses", "stage u1 false"), fake.calls)
        assertEquals("snap", staged.revert.snapshot)
        assertTrue(r.isCurrent(staged))
        assertEquals(staged.revert, r.state.value.staged)
    }

    @Test
    fun `B-CHAT-105 a remote stage makes a reviewed undo stale and blocks its commit`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val reviewed = (r.stage("u1", true) as Outcome.Ok).value
        fake.staged = SessionRevert(MessageId("u2"), null, "other", null)
        fake.base.events.emit(GatewayEvent.RevertChanged(S, RevertKind.Staged))
        runCurrent()
        assertFalse(r.isCurrent(reviewed))
        val result = r.keep(reviewed)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (result as Outcome.Failed).failure)
        assertFalse("commit" in fake.calls)
    }

    @Test
    fun `B-CHAT-105 fresh server boundaries block commit even when the event was missed`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val reviewed = (r.stage("u1", true) as Outcome.Ok).value
        fake.staged = SessionRevert(MessageId("u2"), null, "other", null) // no event
        assertIs<Outcome.Failed>(r.keep(reviewed))
        assertFalse("commit" in fake.calls)
        assertFalse(r.isCurrent(reviewed))
    }

    @Test
    fun `B-PROTO-031 commit and clear use distinct endpoints and both remove the stage`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val first = (r.stage("u1", true) as Outcome.Ok).value
        assertEquals(Outcome.Ok(Unit), r.putBack(first))
        assertNull(r.state.value.staged)
        val second = (r.stage("u1", true) as Outcome.Ok).value
        assertEquals(Outcome.Ok(Unit), r.keep(second))
        assertNull(r.state.value.staged)
        assertEquals(1, fake.calls.count { it == "clear" })
        assertEquals(1, fake.calls.count { it == "commit" })
    }

    @Test
    fun `B-CHAT-106 an ambiguous commit reconciles with the server before anyone retries`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val reviewed = (r.stage("u1", true) as Outcome.Ok).value
        // The server committed but the answer was lost.
        fake.commitResult = { fake.staged = null; Failure.UnreadableReply().failed() }
        assertEquals(Outcome.Ok(Unit), r.keep(reviewed))
        assertNull(r.state.value.staged)
        // Lost and not committed: the failure stays and the stage is kept.
        val again = (r.stage("u1", true) as Outcome.Ok).value
        fake.commitResult = { Failure.UnreadableReply().failed() }
        assertIs<Outcome.Failed>(r.keep(again))
        assertTrue(r.state.value.staged != null)
    }

    @Test
    fun `B-CHAT-106 concurrent mutations are rejected, never queued`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        var hold = true
        val slow = StagedFake()
        // The first stage waits in its preflight read; the second arrives meanwhile.
        gateways.value = ReviewGateways(
            object : io.github.eslamasabry.oc.core.domain.ServerGateway by slow, io.github.eslamasabry.oc.core.domain.StagedRevertGateway by slow {
                override suspend fun sessionStatuses() = run { if (hold) gate.await(); slow.sessionStatuses() }
            },
            FakeOperationsGateway(),
        )
        val r = review()
        val first = async { r.stage("u1", true) }
        runCurrent()
        assertTrue(r.state.value.working)
        val second = r.stage("u1", true)
        assertEquals(Failure.Busy(BusyKind.SessionBusy), (second as Outcome.Failed).failure)
        hold = false
        gate.complete(Unit)
        assertIs<Outcome.Ok<*>>(first.await())
        assertEquals(1, slow.calls.count { it.startsWith("stage") })
        assertFalse(r.state.value.working)
    }

    @Test
    fun `B-CHAT-106 a busy conversation cannot stage`() = runTest(UnconfinedTestDispatcher()) {
        fake.base.statuses = persistentMapOf(S to SessionStatus.Busy)
        val result = review().stage("u1", true)
        assertEquals(Failure.Busy(BusyKind.SessionBusy), (result as Outcome.Failed).failure)
        assertFalse(fake.calls.any { it.startsWith("stage") })
    }

    @Test
    fun `B-CHAT-106 a connection swap mid-preflight dispatches to neither server`() = runTest(UnconfinedTestDispatcher()) {
        val other = StagedFake()
        fake.onStatuses = { gateways.value = ReviewGateways(other, FakeOperationsGateway()) }
        val result = review().stage("u1", true)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (result as Outcome.Failed).failure)
        assertFalse(fake.calls.any { it.startsWith("stage") })
        assertFalse(other.calls.any { it.startsWith("stage") })
    }

    @Test
    fun `B-CHAT-104 closed connections do no history reads and session actions stay unavailable`() = runTest(UnconfinedTestDispatcher()) {
        gateways.value = null
        val r = review()
        assertEquals(UndoShape.Unavailable, r.shape())
        assertIs<Outcome.Failed>(r.history())
        assertIs<Outcome.Failed>(r.stage("u1", true))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `B-CHAT-120 one-step servers undo at once through the operations gateway`() = runTest(UnconfinedTestDispatcher()) {
        val plain = FakeServerGateway(listOf(io.github.eslamasabry.oc.core.model.Session(S)))
        val reverted = mutableListOf<String>()
        val ops = object : FakeOperationsGateway() {
            override suspend fun revertSession(id: io.github.eslamasabry.oc.core.common.ids.SessionId, to: MessageId): Outcome<Unit> {
                reverted += to.raw
                return Unit.ok()
            }
        }
        gateways.value = ReviewGateways(plain, ops)
        val r = review()
        assertEquals(UndoShape.OneStep, r.shape())
        assertEquals(Outcome.Ok(Unit), r.undoNow("u1"))
        assertEquals(listOf("u1"), reverted)
        assertIs<Outcome.Failed>(r.stage("u1", true))
    }

    @Test
    fun `B-CHAT-120 the prompt is quoted from the server and edited paths come after it`() = runTest(UnconfinedTestDispatcher()) {
        history()
        val r = review()
        assertEquals("do it", (r.prompt("u1") as Outcome.Ok).value)
        assertNull((r.prompt("a1") as Outcome.Ok).value)
        assertEquals(listOf("a.kt", "b.kt"), (r.editedAfter("u1") as Outcome.Ok).value)
        assertEquals(persistentListOf(), (r.editedAfter("u2") as Outcome.Ok).value)
    }

    @Test
    fun `B-CHAT-103 equal times keep page order, so a reply at the prompt's time still follows it`() = runTest(UnconfinedTestDispatcher()) {
        fake.pages = listOf(listOf(prompt("u1", 7), reply("a1", 7)))
        assertEquals(1, (review().messagesAfter("u1") as Outcome.Ok).value)
    }

    @Test
    fun `B-CHAT-105 a remote stage after the page read refuses Undo from here before anything is sent`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        r.refresh()
        val basis = r.basis()
        fake.staged = SessionRevert(MessageId("u2"), null, "other", null) // another client, no event seen here
        val result = r.stage("u1", true, reviewed = basis)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (result as Outcome.Failed).failure)
        assertFalse(fake.calls.any { it.startsWith("stage") })
        // An unchanged boundary still stages.
        fake.staged = null
        r.refresh()
        assertIs<Outcome.Ok<*>>(r.stage("u1", true, reviewed = r.basis()))
    }

    @Test
    fun `B-CHAT-104 a review read on one connection never commits through its replacement`() = runTest(UnconfinedTestDispatcher()) {
        val r = review()
        val reviewed = (r.stage("u1", true) as Outcome.Ok).value
        // The replacement connection reports the very same staged undo.
        val other = StagedFake().also { it.staged = fake.staged }
        gateways.value = ReviewGateways(other, FakeOperationsGateway())
        assertFalse(r.isCurrent(reviewed))
        val result = r.keep(reviewed)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (result as Outcome.Failed).failure)
        assertFalse("commit" in other.calls)
        assertFalse("commit" in fake.calls)
    }
}
