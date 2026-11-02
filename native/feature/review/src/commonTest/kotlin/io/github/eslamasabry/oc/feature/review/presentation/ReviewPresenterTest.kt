package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.StagedRevertGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RevertKind
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val S = SessionId("ses_1")

private fun prompt(id: String, at: Long, text: String = "make it blue") =
    MessageWithParts(MessageInfo(id, S.raw, Role.User, time = MsgTime(at, at)), persistentListOf(Part("p$id", id, S.raw, PartType.Text, "text", text = text)))

private fun reply(id: String, at: Long, vararg edits: Pair<String, Int>) =
    MessageWithParts(
        MessageInfo(id, S.raw, Role.Assistant, time = MsgTime(at, at)),
        edits.map { (path, n) ->
            val diff = JsonObject(mapOf("before" to JsonPrimitive("a\n"), "after" to JsonPrimitive("b\n"), "additions" to JsonPrimitive(n), "deletions" to JsonPrimitive(1)))
            Part("t$id$path", id, S.raw, PartType.Tool, "tool", toolName = "edit", tool = ToolState(ToolStatus.Completed, input = JsonObject(mapOf("filePath" to JsonPrimitive(path))), metadata = JsonObject(mapOf("filediff" to diff))))
        }.toImmutableList(),
    )

private class Fake(val base: FakeServerGateway = FakeServerGateway(listOf(Session(S)))) : ServerGateway by base, StagedRevertGateway {
    var history = listOf(prompt("u1", 1), reply("a1", 2, "src/a.kt" to 3), prompt("u2", 3), reply("a2", 4, "src/b.kt" to 2)) // one chronological page
    var staged: SessionRevert? = null
    var stageFails: Failure? = null
    val calls = mutableListOf<String>()

    override suspend fun session(id: SessionId) = Session(id, reverted = staged != null, stagedRevert = staged).ok()

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> = ServerPage(history.toImmutableList(), null).ok()

    override suspend fun sessionRevertPrompt(session: SessionId, message: MessageId): Outcome<String?> =
        history.firstOrNull { it.info.id == message.raw && it.info.role == Role.User }?.parts?.first()?.text.ok()

    override suspend fun stageSessionRevert(session: SessionId, message: MessageId, applyFiles: Boolean): Outcome<SessionRevert> {
        calls += "stage ${message.raw} $applyFiles"
        stageFails?.let { return it.failed() }
        val revert = SessionRevert(message, null, "snap", persistentListOf(FileDiff("src/a.kt", patch = "@@ -1 +1 @@\n-a\n+b", additions = 1, deletions = 1)))
        staged = revert
        return revert.ok()
    }

    override suspend fun clearSessionRevert(session: SessionId): Outcome<Unit> = Unit.ok().also { calls += "clear"; staged = null }

    override suspend fun commitSessionRevert(session: SessionId): Outcome<Unit> = Unit.ok().also { calls += "commit"; staged = null }
}

private class Nav : ReviewNavigation {
    val went = mutableListOf<String>()

    override fun back() {
        went += "back"
    }

    override fun openFile(path: String, staged: Boolean) {
        went += "file $path $staged"
    }

    override fun openStaged() {
        went += "staged"
    }
}

class ReviewPresenterTest {
    private val fake = Fake()
    private val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(fake, FakeOperationsGateway()))
    private val nav = Nav()

    private fun TestScope.review() = ConversationReview(S, gateways, backgroundScope)

    @Test
    fun `B-CHAT-068 a turn's changes list its files with the tools' counts and sum them`() = runTest(UnconfinedTestDispatcher()) {
        val p = ChangesPresenter(review(), "a1", nav, backgroundScope)
        val ui = p.state.value
        assertEquals(ReviewLoad.Ready, ui.load)
        assertTrue(ui.turn)
        assertEquals(listOf("src/a.kt"), ui.files.map { it.path })
        assertEquals(ChangeRowUi("src/a.kt", "a.kt", "src", 3, 1, hasDiff = true), ui.files.single())
        assertEquals(3, ui.added)
        assertEquals(UndoAllUi.Offered, ui.undoAll)
    }

    @Test
    fun `B-CHAT-068 the whole conversation's changes span every turn`() = runTest(UnconfinedTestDispatcher()) {
        val ui = ChangesPresenter(review(), null, nav, backgroundScope).state.value
        assertFalse(ui.turn)
        assertEquals(listOf("src/a.kt", "src/b.kt"), ui.files.map { it.path })
        assertEquals(5, ui.added)
        assertEquals(2, ui.removed)
    }

    @Test
    fun `B-CHAT-105 undo all asks first, stages from the first prompt with files back, then opens the review`() = runTest(UnconfinedTestDispatcher()) {
        val p = ChangesPresenter(review(), null, nav, backgroundScope)
        p.undoAll()
        assertTrue(p.state.value.confirming)
        assertTrue(fake.calls.isEmpty())
        p.confirmUndoAll()
        assertEquals(listOf("stage u1 true"), fake.calls)
        assertEquals(listOf("back", "staged"), nav.went)
    }

    @Test
    fun `B-CHAT-105 a failed undo all keeps the page with the reason and stages nothing new`() = runTest(UnconfinedTestDispatcher()) {
        fake.stageFails = Failure.Server(500)
        val p = ChangesPresenter(review(), "u2", nav, backgroundScope)
        p.undoAll()
        p.confirmUndoAll()
        assertEquals(Failure.Server(500), p.state.value.actionFailure)
        assertTrue(nav.went.isEmpty())
        assertFalse(p.state.value.confirming)
    }

    @Test
    fun `B-CHAT-106 a waiting undo replaces undo all with its review`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u2"), null, null, null)
        val p = ChangesPresenter(review(), null, nav, backgroundScope)
        assertEquals(UndoAllUi.Waiting, p.state.value.undoAll)
        p.undoAll()
        assertFalse(p.state.value.confirming)
    }

    @Test
    fun `B-CHAT-120 undo all is never offered where the server cannot stage`() = runTest(UnconfinedTestDispatcher()) {
        gateways.value = ReviewGateways(FakeServerGateway(listOf(Session(S))), FakeOperationsGateway())
        assertEquals(UndoAllUi.Unavailable, ChangesPresenter(review(), null, nav, backgroundScope).state.value.undoAll)
    }

    @Test
    fun `B-CHAT-058 a changed file opens its diff from the turn's records`() = runTest(UnconfinedTestDispatcher()) {
        val ui = FileDiffPresenter(review(), "src/a.kt", "a1", staged = false, backgroundScope).state.value
        assertEquals("a\n", ui.diff?.before)
        assertEquals("b\n", ui.diff?.after)
        assertEquals("a.kt", ui.name)
        val gone = FileDiffPresenter(review(), "src/zzz.kt", "a1", staged = false, backgroundScope).state.value
        assertTrue(gone.missing)
    }

    @Test
    fun `B-PROTO-031 a staged file opens the server's preview, never a new working-tree diff`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u1"), null, null, persistentListOf(FileDiff("x.kt", patch = "@@ -1 +1 @@\n-a\n+b")))
        val ui = FileDiffPresenter(review(), "x.kt", null, staged = true, backgroundScope).state.value
        assertEquals("@@ -1 +1 @@\n-a\n+b", ui.diff?.patch)
    }

    @Test
    fun `B-CHAT-120 undo from here quotes the prompt and stages with the files choice`() = runTest(UnconfinedTestDispatcher()) {
        val p = UndoFromHerePresenter(review(), "u2", nav, backgroundScope)
        assertEquals("make it blue", p.state.value.prompt)
        assertTrue(p.state.value.staged)
        p.putFilesBack(false)
        p.confirm()
        assertEquals(listOf("stage u2 false"), fake.calls)
        assertEquals(listOf("back", "staged"), nav.went)
    }

    @Test
    fun `B-CHAT-120 one-step servers list the edited files and the message count, then undo at once`() = runTest(UnconfinedTestDispatcher()) {
        val plain = object : ServerGateway by fake.base {
            override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int) = fake.messagePage(id, cursor, limit)
        }
        val undone = mutableListOf<String>()
        gateways.value = ReviewGateways(plain, object : FakeOperationsGateway() {
            override suspend fun revertSession(id: SessionId, to: MessageId): Outcome<Unit> = Unit.ok().also { undone += to.raw }
        })
        val p = UndoFromHerePresenter(review(), "u1", nav, backgroundScope)
        val ui = p.state.value
        assertFalse(ui.staged)
        assertEquals("make it blue", ui.prompt)
        assertEquals(3, ui.messagesAfter)
        assertEquals(listOf("src/a.kt", "src/b.kt"), ui.edited)
        p.confirm()
        assertEquals(listOf("u1"), undone)
        assertEquals(listOf("back"), nav.went)
    }

    @Test
    fun `B-CHAT-106 a running conversation turns undo off and says why`() = runTest(UnconfinedTestDispatcher()) {
        fake.base.statuses = persistentMapOf(S to SessionStatus.Busy)
        val p = UndoFromHerePresenter(review(), "u2", nav, backgroundScope)
        p.confirm() // the engine reads the status and refuses
        assertIs<Failure.Busy>(p.state.value.failure)
        assertEquals(UndoBlock.Busy, p.state.value.block)
        assertTrue(nav.went.isEmpty())
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `B-CHAT-103 the staged review shows the prompt, the files and the exact count`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u2"), null, "snap", persistentListOf(FileDiff("src/b.kt", additions = 2, deletions = 1)))
        val ui = StagedPresenter(review(), nav, backgroundScope).state.value
        assertEquals(StagedPhase.Review, ui.phase)
        assertEquals("make it blue", ui.prompt)
        assertEquals(1, ui.messagesAfter)
        assertEquals(listOf("src/b.kt"), ui.files?.map { it.file })
    }

    @Test
    fun `B-CHAT-105 keep asks first, commits the reviewed undo and says it is kept`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u2"), null, null, null)
        val p = StagedPresenter(review(), nav, backgroundScope)
        assertNull(p.state.value.files)
        p.askKeep()
        assertEquals(StagedConfirm.Keep, p.state.value.confirm)
        assertTrue(fake.calls.isEmpty())
        p.confirm()
        assertEquals(listOf("commit"), fake.calls)
        assertEquals(StagedPhase.Kept, p.state.value.phase)
    }

    @Test
    fun `B-PROTO-031 put everything back clears the staged undo`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u2"), null, null, null)
        val p = StagedPresenter(review(), nav, backgroundScope)
        p.askPutBack()
        p.confirm()
        assertEquals(listOf("clear"), fake.calls)
        assertEquals(StagedPhase.Restored, p.state.value.phase)
    }

    @Test
    fun `B-CHAT-105 a remote change makes the open review stale and closes its question`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u2"), null, null, null)
        val p = StagedPresenter(review(), nav, backgroundScope)
        p.askKeep()
        fake.staged = SessionRevert(MessageId("u1"), null, null, null)
        fake.base.events.emit(GatewayEvent.RevertChanged(S, RevertKind.Staged))
        runCurrent()
        assertEquals(StagedPhase.Stale, p.state.value.phase)
        assertNull(p.state.value.confirm)
        p.reviewLatest()
        assertEquals(StagedPhase.Review, p.state.value.phase)
        assertEquals(4 - 1, p.state.value.messagesAfter) // u1 is followed by a1, u2, a2
    }

    @Test
    fun `B-CHAT-103 nothing staged says so instead of an empty review`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(StagedPhase.None, StagedPresenter(review(), nav, backgroundScope).state.value.phase)
    }
}
