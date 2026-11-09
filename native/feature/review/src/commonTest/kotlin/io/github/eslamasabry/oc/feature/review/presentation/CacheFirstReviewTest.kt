package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class CacheFirstReviewTest {
    private val sid = SessionId("ses_1")
    private val ref = ConversationRef(ProfileId("phone"), sid)

    private class Nav : ReviewNavigation {
        override fun back() {}
        override fun openFile(path: String, staged: Boolean) {}
        override fun openStaged() {}
    }

    /** A server whose message page waits for [gate] and then answers [page]. */
    private class Slow(
        val base: FakeServerGateway,
        val gate: CompletableDeferred<Unit>,
        val page: () -> Outcome<ServerPage<MessageWithParts>>,
    ) : ServerGateway by base {
        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            gate.await()
            return page()
        }

        override suspend fun session(id: SessionId): Outcome<Session> {
            gate.await()
            return Session(id).ok()
        }
    }

    private class Shelf : ReviewLastKnown {
        val items = mutableMapOf<String, KeptReview>()
        override fun peek(ref: ConversationRef, item: String) = items[item]
        override suspend fun read(ref: ConversationRef, item: String) = items[item]
        override suspend fun keep(ref: ConversationRef, item: String, kept: KeptReview) {
            items[item] = Json.decodeFromString(KeptReview.serializer(), Json.encodeToString(KeptReview.serializer(), kept))
        }
    }

    private fun TestScope.review(slow: Slow) =
        ConversationReview(sid, MutableStateFlow<ReviewGateways?>(ReviewGateways(slow, FakeOperationsGateway())), backgroundScope)

    private fun slow(page: () -> Outcome<ServerPage<MessageWithParts>> = { ServerPage(emptyList<MessageWithParts>().toImmutableList(), null).ok() }) =
        Slow(FakeServerGateway(listOf(Session(sid))), CompletableDeferred(), page)

    private val keptChanges =
        KeptReview(changes = KeptChanges(listOf(KeptChanges.Row("src/a.kt", "a.kt", "src", 3, 1, true))))

    @Test
    fun `CF-74 the first frame of Changes with kept rows is the file list, and Undo all is not offered`() = runTest(UnconfinedTestDispatcher()) {
        val shelf = Shelf().also { it.items[ReviewShelf.changes("a1")] = keptChanges }
        val slow = slow()
        val p = ChangesPresenter(review(slow), "a1", Nav(), backgroundScope, shelf = ReviewShelf(shelf, ref))
        val first = p.state.value
        assertEquals(ReviewLoad.Ready, first.load)
        assertEquals(listOf("src/a.kt"), first.files.map { it.path })
        assertTrue(first.updating)
        assertNotEquals(UndoAllUi.Offered, first.undoAll)
    }

    @Test
    fun `CF-75 the live read replaces the kept rows in place and is kept for next time`() = runTest(UnconfinedTestDispatcher()) {
        val shelf = Shelf().also { it.items[ReviewShelf.changes(null)] = keptChanges }
        val slow = slow()
        val p = ChangesPresenter(review(slow), null, Nav(), backgroundScope, shelf = ReviewShelf(shelf, ref))
        slow.gate.complete(Unit)
        assertEquals(false, p.state.value.updating)
        assertTrue(p.state.value.files.isEmpty())
        assertTrue(shelf.items[ReviewShelf.changes(null)]!!.changes!!.rows.isEmpty())
    }

    @Test
    fun `CF-76 a failed live read keeps the kept rows and does not show a failure page`() = runTest(UnconfinedTestDispatcher()) {
        val shelf = Shelf().also { it.items[ReviewShelf.changes("a1")] = keptChanges }
        val slow = slow { Failure.Network().failed() }
        val p = ChangesPresenter(review(slow), "a1", Nav(), backgroundScope, shelf = ReviewShelf(shelf, ref))
        slow.gate.complete(Unit)
        assertEquals(ReviewLoad.Ready, p.state.value.load)
        assertEquals(listOf("src/a.kt"), p.state.value.files.map { it.path })
        assertFalse(p.state.value.updating)
    }

    @Test
    fun `CF-77 the first frame of a file diff with a kept diff is the diff itself`() = runTest(UnconfinedTestDispatcher()) {
        val shelf = Shelf()
        val key = ReviewShelf.file(FileDiffSource.Changes, "a1", "src/a.kt")
        shelf.items[key] = KeptReview(file = KeptFile("src/a.kt", patch = "@@ -1 +1 @@\n-a\n+b", additions = 1, deletions = 1))
        val p = FileDiffPresenter(review(slow()), "src/a.kt", "a1", FileDiffSource.Changes, backgroundScope, shelf = ReviewShelf(shelf, ref))
        val first = p.state.value
        assertEquals(ReviewLoad.Ready, first.load)
        assertEquals("@@ -1 +1 @@\n-a\n+b", first.diff?.patch)
        assertTrue(first.updating)
    }

    @Test
    fun `CF-78 the first frame of the staged undo with kept files lists them, and Keep waits for the live read`() = runTest(UnconfinedTestDispatcher()) {
        val shelf = Shelf()
        shelf.items[ReviewShelf.STAGED] = KeptReview(staged = KeptStaged(listOf(KeptStaged.File("src/a.kt", 1, 1)), prompt = "make it blue", messagesAfter = 2))
        val p = StagedPresenter(review(slow()), Nav(), backgroundScope, ReviewShelf(shelf, ref))
        val first = p.state.value
        assertEquals(StagedPhase.Review, first.phase)
        assertEquals(listOf("src/a.kt"), first.files?.map { it.file })
        assertTrue(first.updating)
        p.askKeep()
        assertNull(p.state.value.confirm)
    }

    @Test
    fun `CF-79 with nothing kept the first-ever load is loading`() = runTest(UnconfinedTestDispatcher()) {
        val p = ChangesPresenter(review(slow()), null, Nav(), backgroundScope, shelf = ReviewShelf(Shelf(), ref))
        assertEquals(ReviewLoad.Loading, p.state.value.load)
    }
}
