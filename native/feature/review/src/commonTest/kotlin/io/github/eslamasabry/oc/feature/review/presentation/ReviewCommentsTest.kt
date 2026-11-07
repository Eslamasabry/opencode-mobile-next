package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
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
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import kotlinx.collections.immutable.persistentListOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SES = SessionId("ses_c")

private fun anchor(path: String = "src/a.kt", start: Int = 42, end: Int = start, quote: List<String> = listOf("val x = 1"), side: CommentSide = CommentSide.New) =
    CommentAnchor(path, side, start, end, quote.toImmutableList())

private fun ask(id: String, at: Long, text: String = "make it so") =
    MessageWithParts(MessageInfo(id, SES.raw, Role.User, time = MsgTime(at, at)), persistentListOf(Part("p$id", id, SES.raw, PartType.Text, "text", text = text)))

private fun editPart(id: String, path: String, before: String, after: String) =
    Part(
        "t$id",
        id,
        SES.raw,
        PartType.Tool,
        "tool",
        toolName = "edit",
        tool =
            ToolState(
                ToolStatus.Completed,
                input = JsonObject(mapOf("filePath" to JsonPrimitive(path))),
                metadata = JsonObject(mapOf("filediff" to JsonObject(mapOf("before" to JsonPrimitive(before), "after" to JsonPrimitive(after))))),
                completedAtMs = 1,
            ),
    )

private fun edited(id: String, at: Long, path: String, before: String, after: String) =
    MessageWithParts(MessageInfo(id, SES.raw, Role.Assistant, time = MsgTime(at, at)), persistentListOf(editPart(id, path, before, after)))

/** One chronological page of history the test rewrites as the agent "edits". */
private class HistoryFake(val base: FakeServerGateway = FakeServerGateway(listOf(Session(SES)))) : ServerGateway by base {
    var history: List<MessageWithParts> = emptyList()

    override suspend fun session(id: SessionId) = Session(id).ok()

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> =
        ServerPage(history.toImmutableList(), null).ok()
}

private class Sent : ReviewPromptSender {
    val texts = mutableListOf<String>()
    var fail: Failure? = null

    override suspend fun send(text: String): Outcome<Unit> {
        texts += text
        return fail?.let { Outcome.Failed(it) } ?: Outcome.Ok(Unit)
    }
}

class ReviewCommentsTest {
    @Test
    fun `B-156 one comment quotes the file, the line and its text, then the ask`() {
        val c = LineComment("c1", anchor(), "  rename this ")
        assertEquals("In src/a.kt line 42 `val x = 1`: rename this", ReviewCommentPrompt.format(listOf(c)))
    }

    @Test
    fun `B-156 a range and a removed line say so`() {
        val range = LineComment("c1", anchor(start = 3, end = 5, quote = listOf("fun a() {", "  b()", "}")), "split it")
        assertEquals("In src/a.kt lines 3-5 `fun a() { …`: split it", ReviewCommentPrompt.one(range))
        val removed = LineComment("c2", anchor(start = 7, side = CommentSide.Old, quote = listOf("log(token)")), "keep this")
        assertEquals("In src/a.kt removed line 7 `log(token)`: keep this", ReviewCommentPrompt.one(removed))
    }

    @Test
    fun `B-156 a long line is cut and a backtick cannot close the quote`() {
        val long = "x".repeat(ReviewCommentPrompt.QUOTE_CHARS + 10)
        assertEquals("x".repeat(ReviewCommentPrompt.QUOTE_CHARS) + " …", ReviewCommentPrompt.quote(listOf(long)))
        val tick = LineComment("c1", anchor(quote = listOf("val s = `a`")), "why")
        assertEquals("In src/a.kt line 42 `` val s = `a` ``: why", ReviewCommentPrompt.one(tick))
    }

    @Test
    fun `B-156 several comments go as one numbered prompt`() {
        val text =
            ReviewCommentPrompt.format(
                listOf(
                    LineComment("c1", anchor(), "rename this"),
                    LineComment("c2", anchor(path = "b.kt", start = 2, quote = listOf("call()")), "check null\nand log"),
                    LineComment("c3", anchor(), " "), // blank: never sent
                ),
            )
        assertEquals(
            "Please address these review comments on your changes:\n" +
                "1. In src/a.kt line 42 `val x = 1`: rename this\n" +
                "2. In b.kt line 2 `call()`: check null\n   and log",
            text,
        )
    }

    @Test
    fun `B-156 a selection is placed on the lines it came from`() {
        val file = DiffModel.fromBeforeAfter("a.kt", "one\ntwo\nthree\n", "one\nTWO\nthree\nfour\n", null)
        assertEquals(CommentAnchor("a.kt", CommentSide.New, 2, 2, persistentListOf("TWO")), CommentLines.anchor(file, "TWO"))
        assertEquals(listOf(3, 4), CommentLines.anchor(file, "three\nfour")?.let { listOf(it.start, it.end) })
        val removed = CommentLines.anchor(file, "two")
        assertEquals(CommentSide.Old, removed?.side)
        assertEquals(2, removed?.start)
        assertNull(CommentLines.anchor(file, "not there"))
        // The view shows redacted text: the selection matches through the same redactor.
        assertEquals(2, CommentLines.anchor(file, "***") { if (it == "TWO") "***" else it }?.start)
    }

    @Test
    fun `B-156 a sent comment is fixed once the agent's edit changes its line, not before`() {
        val sent = LineComment("c1", anchor(start = 1, quote = listOf("val x = 1")), "rename", CommentStatus.Pending, sentVersion = "v1")
        val same = DiffModel.fromBeforeAfter("src/a.kt", "", "val x = 1\nval y = 2\n", null)
        val elsewhere = DiffModel.fromBeforeAfter("src/a.kt", "", "val x = 1\nval y = 3\n", null)
        val touched = DiffModel.fromBeforeAfter("src/a.kt", "", "val count = 1\nval y = 2\n", null)
        assertFalse(CommentResolve.resolved(sent, "v1", touched), "the version it was sent against never resolves it")
        assertFalse(CommentResolve.resolved(sent, "v2", same))
        assertFalse(CommentResolve.resolved(sent, "v2", elsewhere), "an edit elsewhere leaves it waiting")
        assertTrue(CommentResolve.resolved(sent, "v2", touched))
        assertFalse(CommentResolve.resolved(sent.copy(status = CommentStatus.Draft), "v2", touched), "a draft is never fixed")
        // Lines moving down (an insert above) do not count as touching it.
        val moved = DiffModel.fromBeforeAfter("src/a.kt", "", "import a\nval x = 1\nval y = 2\n", null)
        assertFalse(CommentResolve.resolved(sent, "v3", moved))
    }

    private val fake = HistoryFake()
    private val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(fake, FakeOperationsGateway()))

    private fun TestScope.review() = ConversationReview(SES, gateways, backgroundScope)

    @Test
    fun `B-156 comment, send in one prompt, wait, and resolve when the agent edits that line`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(ask("u1", 1), edited("a1", 2, "src/a.kt", "val x = 1\nval y = 2\n", "val x = 10\nval y = 2\n"))
        val book = ReviewCommentBook()
        val sent = Sent()
        val page = FileDiffPresenter(review(), "src/a.kt", null, staged = false, scope = backgroundScope, comments = book, sender = sent)
        assertTrue(page.state.value.canComment)
        val file = CommentResolve.diffFile(assertNotNull(page.state.value.diff))
        page.comment(CommentLines.anchor(file, "val x = 10"))
        assertNotNull(page.state.value.commenting)
        page.commentText("call it count")
        page.comment(CommentLines.anchor(file, "val y = 2"))
        page.commentText("and this one")
        assertEquals(2, page.state.value.drafts)
        assertTrue(sent.texts.isEmpty(), "nothing goes before Send")

        page.sendComments()
        assertEquals(
            listOf(
                "Please address these review comments on your changes:\n" +
                    "1. In src/a.kt line 1 `val x = 10`: call it count\n" +
                    "2. In src/a.kt line 2 `val y = 2`: and this one",
            ),
            sent.texts,
        )
        assertEquals(listOf(CommentStatus.Pending, CommentStatus.Pending), page.state.value.comments.map { it.status })

        // The agent edits line 2 only: line 1 waits, line 2 is fixed.
        fake.history += listOf(ask("u2", 3), edited("a2", 4, "src/a.kt", "val x = 10\nval y = 2\n", "val x = 10\nval y = 20\n"))
        fake.base.events.emit(GatewayEvent.PartUpserted(editPart("a2", "src/a.kt", "", "").copy(sessionId = SES.raw)))
        runCurrent()
        assertEquals(listOf(CommentStatus.Pending, CommentStatus.Resolved), page.state.value.comments.map { it.status })
        assertEquals("val x = 10\nval y = 20\n", page.state.value.diff?.after, "the diff follows the agent's edit")

        fake.history += edited("a3", 5, "src/a.kt", "val x = 10\nval y = 20\n", "val count = 10\nval y = 20\n")
        fake.base.events.emit(GatewayEvent.PartUpserted(editPart("a3", "src/a.kt", "", "").copy(sessionId = SES.raw)))
        runCurrent()
        assertEquals(listOf(CommentStatus.Resolved, CommentStatus.Resolved), page.state.value.comments.map { it.status })
    }

    @Test
    fun `B-156 a failed send keeps the comments as drafts with the reason`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(ask("u1", 1), edited("a1", 2, "src/a.kt", "a\n", "b\n"))
        val sent = Sent().apply { fail = Failure.Server(500) }
        val page = FileDiffPresenter(review(), "src/a.kt", null, staged = false, scope = backgroundScope, comments = ReviewCommentBook(), sender = sent)
        page.comment(CommentLines.anchor(CommentResolve.diffFile(page.state.value.diff!!), "b"))
        page.commentText("why b")
        page.sendComments()
        assertEquals(Failure.Server(500), page.state.value.sendFailure)
        assertEquals(listOf(CommentStatus.Draft), page.state.value.comments.map { it.status })
        assertEquals(1, page.state.value.drafts)
    }

    @Test
    fun `B-156 the undo's preview takes no comments, and a blank comment is not added`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(ask("u1", 1), edited("a1", 2, "src/a.kt", "a\n", "b\n"))
        val book = ReviewCommentBook()
        assertFalse(FileDiffPresenter(review(), "src/a.kt", null, staged = true, scope = backgroundScope, comments = book, sender = Sent()).state.value.canComment)
        val page = FileDiffPresenter(review(), "src/a.kt", null, staged = false, scope = backgroundScope, comments = book, sender = Sent())
        page.comment(CommentLines.anchor(CommentResolve.diffFile(page.state.value.diff!!), "b"))
        page.commentText("   ")
        assertTrue(book.comments.value.isEmpty())
        assertNull(page.state.value.commenting)
    }

    @Test
    fun `B-156 the Changes page sends every draft of the conversation and counts the waiting ones`() = runTest(UnconfinedTestDispatcher()) {
        fake.history = listOf(ask("u1", 1), edited("a1", 2, "src/a.kt", "a\n", "b\n"), edited("a2", 3, "b.kt", "c\n", "d\n"))
        val book = ReviewCommentBook()
        book.add(anchor(path = "src/a.kt", start = 1, quote = listOf("b")), "one", listOf("b"))
        book.add(anchor(path = "b.kt", start = 1, quote = listOf("d")), "two", listOf("d"))
        book.add(anchor(path = "b.kt", start = 1, quote = listOf("d")), "three", listOf("d"))
        val sent = Sent()
        val page = ChangesPresenter(review(), null, NoNav, backgroundScope, comments = book, sender = sent)
        assertEquals(3, page.state.value.drafts)
        page.sendComments()
        assertEquals(1, sent.texts.size, "one prompt for all three")
        assertEquals(0, page.state.value.drafts)
        assertEquals(3, page.state.value.waiting)
    }
}

internal object NoNav : ReviewNavigation {
    override fun back() {}

    override fun openFile(path: String, staged: Boolean) {}

    override fun openStaged() {}
}
