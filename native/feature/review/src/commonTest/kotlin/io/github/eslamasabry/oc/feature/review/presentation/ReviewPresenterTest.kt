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

/** The fake gateway starts with no confirmed capability (ServerCapabilities.Unknown); these servers undo. */
private fun FakeServerGateway.offersRevert(): FakeServerGateway =
    apply { capabilities.value = capabilities.value.copy(sessionRevert = true) }

private class Fake(val base: FakeServerGateway = FakeServerGateway(listOf(Session(S))).offersRevert()) : ServerGateway by base, StagedRevertGateway {
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

/** A server with the folder's uncommitted changes (OC2 `/vcs/diff`), on top of [Fake]. */
private class VcsFake(val inner: Fake, var tree: List<FileDiff>) :
    ServerGateway by inner, StagedRevertGateway by inner, io.github.eslamasabry.oc.core.domain.LocatedVcsGateway {
    val at = mutableListOf<io.github.eslamasabry.oc.core.model.ServerLocation>()

    override suspend fun session(id: SessionId) = Session(id, directory = "/work/a").ok()

    override suspend fun workingTreeAt(location: io.github.eslamasabry.oc.core.model.ServerLocation): Outcome<kotlinx.collections.immutable.ImmutableList<FileDiff>> {
        at += location
        return tree.toImmutableList().ok()
    }
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

    override fun openWorkingTreeFile(path: String) {
        went += "working $path"
    }

    override fun openBranchFile(path: String) {
        went += "branch $path"
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
        // Flutter showKitConfirm(action:) keeps the question open with the failure and Try again.
        assertTrue(p.state.value.confirming)
        assertFalse(p.state.value.working)
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
        gateways.value = ReviewGateways(FakeServerGateway(listOf(Session(S))).offersRevert(), FakeOperationsGateway())
        assertEquals(UndoAllUi.Unavailable, ChangesPresenter(review(), null, nav, backgroundScope).state.value.undoAll)
    }

    @Test
    fun `B-CHAT-058 a changed file opens its diff from the turn's records`() = runTest(UnconfinedTestDispatcher()) {
        val ui = FileDiffPresenter(review(), "src/a.kt", "a1", FileDiffSource.Changes, backgroundScope).state.value
        assertEquals("a\n", ui.diff?.before)
        assertEquals("b\n", ui.diff?.after)
        assertEquals("a.kt", ui.name)
        val gone = FileDiffPresenter(review(), "src/zzz.kt", "a1", FileDiffSource.Changes, backgroundScope).state.value
        assertTrue(gone.missing)
    }

    @Test
    fun `B-PROTO-031 a staged file opens the server's preview, never a new working-tree diff`() = runTest(UnconfinedTestDispatcher()) {
        fake.staged = SessionRevert(MessageId("u1"), null, null, persistentListOf(FileDiff("x.kt", patch = "@@ -1 +1 @@\n-a\n+b")))
        val ui = FileDiffPresenter(review(), "x.kt", null, FileDiffSource.Staged, backgroundScope).state.value
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

    @Test
    fun `B-CHAT-105 another client staging while Undo from here is open makes it stale and sends nothing`() = runTest(UnconfinedTestDispatcher()) {
        val p = UndoFromHerePresenter(review(), "u2", nav, backgroundScope)
        assertNull(p.state.value.block)
        fake.staged = SessionRevert(MessageId("u1"), null, null, null)
        fake.base.events.emit(GatewayEvent.RevertChanged(S, RevertKind.Staged))
        runCurrent()
        assertEquals(UndoBlock.Stale, p.state.value.block)
        p.confirm()
        assertTrue(fake.calls.none { it.startsWith("stage") })
    }

    @Test
    fun `B-CHAT-105 a missed remote stage is caught by the read before dispatch`() = runTest(UnconfinedTestDispatcher()) {
        val p = UndoFromHerePresenter(review(), "u2", nav, backgroundScope)
        fake.staged = SessionRevert(MessageId("u1"), null, null, null) // no event reaches this phone
        p.confirm()
        assertTrue(fake.calls.none { it.startsWith("stage") })
        assertEquals(UndoBlock.Stale, p.state.value.block)
        assertNull(p.state.value.failure)
        assertTrue(nav.went.isEmpty())
    }

    @Test
    fun `B-CHAT-098 Send back with a note puts a review comment into the conversation's message and returns`() = runTest(UnconfinedTestDispatcher()) {
        val sent = mutableListOf<String>()
        val p = ChangesPresenter(review(), null, nav, backgroundScope, ReviewSendBack { sent += it; Outcome.Ok(Unit) })
        assertTrue(p.state.value.canSendBack)
        p.sendBack()
        assertTrue(p.state.value.noting)
        p.sendBackNote("  please keep the old name  ")
        val text = sent.single()
        assertTrue(text.startsWith("Reference:"), text)
        assertTrue("`src/a.kt`" in text, text)
        assertTrue("please keep the old name" in text, text)
        assertEquals(listOf("back"), nav.went)
        assertTrue(fake.calls.isEmpty(), "nothing is sent to the server from here")
    }

    @Test
    fun `B-CHAT-098 a blank or cancelled note sends nothing, a failed one stays with the reason`() = runTest(UnconfinedTestDispatcher()) {
        var answer: Outcome<Unit> = Outcome.Ok(Unit)
        var sends = 0
        val p = ChangesPresenter(review(), null, nav, backgroundScope, ReviewSendBack { sends++; answer })
        p.sendBack()
        p.sendBackNote(null)
        p.sendBack()
        p.sendBackNote("   ")
        assertEquals(0, sends)
        answer = Outcome.Failed(Failure.StorageRefused())
        p.sendBack()
        p.sendBackNote("look again")
        assertEquals(Failure.StorageRefused(), p.state.value.sendBackFailure)
        assertTrue(nav.went.isEmpty())
        assertFalse(ChangesPresenter(review(), null, nav, backgroundScope).state.value.canSendBack)
    }

    @Test
    fun `B-NAV-053 a conversation whose agent changed files by command shows the folder's uncommitted changes`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(prompt("u1", 1), reply("a1", 2)) // no edit tools: the agent used a command
        val vcs = VcsFake(fake, listOf(FileDiff("lib/x.kt", patch = "@@ -1 +1 @@\n-a\n+b", additions = 1, deletions = 1)))
        gateways.value = ReviewGateways(vcs, FakeOperationsGateway())
        val p = ChangesPresenter(review(), null, nav, backgroundScope)
        val ui = p.state.value
        assertTrue(ui.workingTree)
        assertEquals(listOf("lib/x.kt"), ui.files.map { it.path })
        assertEquals("/work/a", vcs.at.single().directory)
        p.openFile("lib/x.kt")
        assertEquals(listOf("working lib/x.kt"), nav.went)
        val file = FileDiffPresenter(review(), "lib/x.kt", null, FileDiffSource.WorkingTree, scope = backgroundScope)
        assertEquals("lib/x.kt", file.state.value.diff?.file)
    }

    @Test
    fun `B-NAV-053 tool changes stay the view when there are any`() = runTest(UnconfinedTestDispatcher()) {
        val vcs = VcsFake(fake, listOf(FileDiff("other.kt", patch = "x")))
        gateways.value = ReviewGateways(vcs, FakeOperationsGateway())
        val ui = ChangesPresenter(review(), null, nav, backgroundScope).state.value
        assertFalse(ui.workingTree)
        assertTrue(vcs.at.isEmpty())
    }

    // Parity audit 2026-11-08 rows 263 and 266 (Flutter review_workspace.dart:45,165,275,362).

    private fun branchOf(vararg files: FileDiff) = ReviewBranchRead { files.toList().toImmutableList().ok() }

    @Test
    fun `AUDIT-263 the whole branch is a third scope where the server answers it, and its files open from it`() = runTest(UnconfinedTestDispatcher()) {
        val vcs = VcsFake(fake, listOf(FileDiff("lib/x.kt", patch = "@@ -1 +1 @@\n-a\n+b", additions = 1, deletions = 1)))
        gateways.value = ReviewGateways(vcs, FakeOperationsGateway())
        val p = ChangesPresenter(review(), null, nav, backgroundScope, branch = branchOf(FileDiff("lib/y.kt", patch = "@@ -1 +1 @@\n-c\n+d", additions = 4, deletions = 2)))
        assertEquals(listOf(ReviewScope.Conversation, ReviewScope.WorkingTree, ReviewScope.Branch), p.state.value.scopes)
        assertEquals(ReviewScope.Conversation, p.state.value.scope)
        p.chooseScope(ReviewScope.Branch)
        val ui = p.state.value
        assertEquals(ReviewScope.Branch, ui.scope)
        assertFalse(ui.workingTree)
        assertEquals(listOf("lib/y.kt"), ui.files.map { it.path })
        assertEquals(4, ui.added)
        p.openFile("lib/y.kt")
        assertEquals(listOf("branch lib/y.kt"), nav.went)
        val file = FileDiffPresenter(review(), "lib/y.kt", null, FileDiffSource.Branch, scope = backgroundScope, branch = branchOf(FileDiff("lib/y.kt", patch = "@@ -1 +1 @@\n-c\n+d")))
        assertEquals("lib/y.kt", file.state.value.diff?.file)
        p.chooseScope(ReviewScope.Conversation)
        assertEquals(listOf("src/a.kt", "src/b.kt"), p.state.value.files.map { it.path }.sorted())
    }

    @Test
    fun `AUDIT-263 a server without a branch diff never offers the branch scope`() = runTest(UnconfinedTestDispatcher()) {
        val none = ReviewBranchRead { Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("sessionDiff")).failed() }
        val p = ChangesPresenter(review(), null, nav, backgroundScope, branch = none)
        assertEquals(listOf(ReviewScope.Conversation), p.state.value.scopes)
        assertFalse(p.state.value.canPickScope)
        p.chooseScope(ReviewScope.Branch)
        assertEquals(ReviewScope.Conversation, p.state.value.scope)
    }

    @Test
    fun `AUDIT-263 with nothing from the tools and nothing uncommitted the page opens the branch`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(prompt("u1", 1), reply("a1", 2))
        gateways.value = ReviewGateways(VcsFake(fake, emptyList()), FakeOperationsGateway())
        val p = ChangesPresenter(review(), null, nav, backgroundScope, branch = branchOf(FileDiff("lib/y.kt", patch = "x")))
        assertEquals(ReviewScope.Branch, p.state.value.scope)
        assertEquals(listOf("lib/y.kt"), p.state.value.files.map { it.path })
    }

    @Test
    fun `AUDIT-266 an opened file is Viewed until it changes, and every file viewed is said once comments wait`() = runTest(UnconfinedTestDispatcher()) {
        val p = ChangesPresenter(review(), null, nav, backgroundScope)
        assertTrue(p.state.value.files.none { it.viewed })
        p.openFile("src/a.kt")
        assertEquals(listOf("src/a.kt"), p.state.value.files.filter { it.viewed }.map { it.path })
        p.retry() // read again, same changes: the mark stays
        assertEquals(listOf("src/a.kt"), p.state.value.files.filter { it.viewed }.map { it.path })
        assertFalse(p.state.value.allViewed)
        p.openFile("src/b.kt")
        assertTrue(p.state.value.allViewed)
        // The agent edits a.kt again: it is not viewed any more.
        fake.history = fake.history + listOf(prompt("u3", 5), reply("a3", 6, "src/a.kt" to 9))
        p.retry()
        assertEquals(listOf("src/b.kt"), p.state.value.files.filter { it.viewed }.map { it.path })
        assertFalse(p.state.value.allViewed)
    }

    @Test
    fun `AUDIT-266 Viewed marks belong to their scope`() = runTest(UnconfinedTestDispatcher()) {
        val vcs = VcsFake(fake, listOf(FileDiff("src/a.kt", patch = "@@ -1 +1 @@\n-a\n+b", additions = 1, deletions = 1)))
        gateways.value = ReviewGateways(vcs, FakeOperationsGateway())
        val p = ChangesPresenter(review(), null, nav, backgroundScope)
        p.openFile("src/a.kt")
        p.chooseScope(ReviewScope.WorkingTree)
        assertTrue(p.state.value.files.none { it.viewed })
        p.chooseScope(ReviewScope.Conversation)
        assertTrue(p.state.value.files.single { it.path == "src/a.kt" }.viewed)
    }
}
