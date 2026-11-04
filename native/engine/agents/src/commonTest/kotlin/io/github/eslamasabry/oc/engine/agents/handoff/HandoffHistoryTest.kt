package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports test/agent_handoff_history_test.dart. */
class HandoffHistoryTest {
    private class PagedSource(val pages: List<HandoffHistoryPage>) : HandoffHistorySource {
        override var directory: String? = "/project"
        override var workspace: String? = "workspace"
        override var isClosed: Boolean = false
        val cursors = mutableListOf<String?>()
        val limits = mutableListOf<Int>()
        var beforeResponse: (suspend () -> Unit)? = null
        var error: Throwable? = null
        var reads = 0
        override val historyReader: HandoffHistoryReader = HandoffHistoryReader.Paged { _, cursor, limit ->
            cursors += cursor
            limits += limit
            val page = pages[reads++]
            beforeResponse?.invoke()
            error?.let { throw it }
            page.ok()
        }
    }

    private class Unsupported : HandoffHistorySource {
        override val directory: String? = "/project"
        override val workspace: String? = null
        override val isClosed: Boolean = false
        override val historyReader: HandoffHistoryReader? = null
    }

    private class SnapshotSource(val snapshot: List<HandoffMessage>) : HandoffHistorySource {
        override val directory: String? = "/project"
        override val workspace: String? = null
        override val isClosed: Boolean = false
        override val historyReader: HandoffHistoryReader = HandoffHistoryReader.Snapshot { snapshot.ok() }
    }

    private fun message(id: String, session: String = "source", text: String? = null) =
        HandoffMessage(id, session, "user", mutableListOf(HandoffPart("text", text ?: id)))

    private fun page(items: List<HandoffMessage>, cursor: String? = null, more: Boolean? = null) =
        HandoffHistoryPage(items, cursor, more ?: (cursor != null))

    private fun assertFailure(kind: HandoffHistoryFailureKind, read: HandoffHistoryRead) =
        assertEquals(kind, assertIs<HandoffHistoryRead.Failed>(read).kind)

    private fun messages(read: HandoffHistoryRead) = assertIs<HandoffHistoryRead.Read>(read).messages

    @Test
    fun `B-AGENT-018 complete snapshots preserve order and apply the common message budget`() = runTest {
        val source = SnapshotSource(List(51) { message("$it") })
        val read = messages(readHandoffHistory(source, "source", { true }))
        assertEquals(List(51) { "$it" }, read.map { it.id })
        assertFalse((read as List<HandoffMessage>) is MutableList<*>)
        assertFailure(HandoffHistoryFailureKind.PageLimit, readHandoffHistory(source, "source", { true }, maxPages = 1))
    }

    @Test
    fun `B-AGENT-019 complete snapshots reject duplicate or foreign message identities`() = runTest {
        for (items in listOf(listOf(message("same"), message("same")), listOf(message("id", session = "different")))) {
            assertFailure(HandoffHistoryFailureKind.InvalidMessage, readHandoffHistory(SnapshotSource(items), "source", { true }))
        }
    }

    @Test
    fun `B-AGENT-018 prepends older pages, retaining newest copy of overlapping messages`() = runTest {
        val source = PagedSource(
            listOf(
                page(listOf(message("c", text = "new copy"), message("d")), cursor = "older"),
                page(listOf(message("a"), message("b"), message("c", text = "old copy"))),
            ),
        )
        val read = messages(readHandoffHistory(source, "source", { true }))
        assertEquals(listOf("a", "b", "c", "d"), read.map { it.id })
        assertEquals("new copy", read[2].parts.single().text)
        assertEquals(listOf(null, "older"), source.cursors)
        assertEquals(listOf(50, 50), source.limits)
        assertFalse((read as List<HandoffMessage>) is MutableList<*>)
    }

    @Test
    fun `B-AGENT-018 snapshots part lists before fetching older pages`() = runTest {
        val newest = message("newest")
        lateinit var source: PagedSource
        source = PagedSource(listOf(page(listOf(newest), cursor = "older"), page(listOf(message("oldest")))))
        source.beforeResponse = { if (source.reads == 2) (newest.parts as MutableList).clear() }
        val read = messages(readHandoffHistory(source, "source", { true }))
        assertEquals("newest", read.last().parts.single().text)
    }

    @Test
    fun `B-AGENT-018 accepts complete empty history`() = runTest {
        assertTrue(messages(readHandoffHistory(PagedSource(listOf(page(emptyList()))), "source", { true })).isEmpty())
    }

    @Test
    fun `B-AGENT-019 rejects empty session, unsupported reader and invalid budget`() = runTest {
        assertFailure(HandoffHistoryFailureKind.Unsupported, readHandoffHistory(Unsupported(), "source", { true }))
        assertFailure(HandoffHistoryFailureKind.InvalidSession, readHandoffHistory(Unsupported(), " ", { true }))
        assertFailsWith<IllegalArgumentException> { readHandoffHistory(PagedSource(emptyList()), "source", { true }, maxPages = 0) }
    }

    @Test
    fun `B-AGENT-018 never returns partial history when the page budget expires`() = runTest {
        val source = PagedSource(listOf(page(listOf(message("newest")), cursor = "older")))
        assertFailure(HandoffHistoryFailureKind.PageLimit, readHandoffHistory(source, "source", { true }, maxPages = 1))
        assertEquals(1, source.reads)
    }

    @Test
    fun `B-AGENT-019 rejects missing or empty older cursor`() = runTest {
        for (cursor in listOf(null, "", " ")) {
            val source = PagedSource(listOf(page(listOf(message("newest")), cursor = cursor, more = true)))
            assertFailure(HandoffHistoryFailureKind.InvalidCursor, readHandoffHistory(source, "source", { true }))
        }
    }

    @Test
    fun `B-AGENT-019 rejects cursor cycles`() = runTest {
        val source = PagedSource(
            listOf(
                page(listOf(message("c")), cursor = "one"),
                page(listOf(message("b")), cursor = "two"),
                page(listOf(message("a")), cursor = "one"),
            ),
        )
        assertFailure(HandoffHistoryFailureKind.InvalidCursor, readHandoffHistory(source, "source", { true }))
    }

    @Test
    fun `B-AGENT-019 rejects invalid message identity`() = runTest {
        for (invalid in listOf(message(""), message(" "), message("id", session = "foreign"))) {
            val source = PagedSource(listOf(page(listOf(invalid))))
            assertFailure(HandoffHistoryFailureKind.InvalidMessage, readHandoffHistory(source, "source", { true }))
        }
    }

    @Test
    fun `B-AGENT-019 rejects stale scope before reading`() = runTest {
        val source = PagedSource(emptyList())
        assertFailure(HandoffHistoryFailureKind.StaleScope, readHandoffHistory(source, "source", { false }))
        assertEquals(0, source.reads)
    }

    @Test
    fun `B-AGENT-019 rejects profile, directory, workspace or closed change while a page is in flight`() = runTest {
        for (change in listOf("profile", "directory", "workspace", "closed")) {
            val gate = CompletableDeferred<Unit>()
            val source = PagedSource(listOf(page(listOf(message("newest")))))
            source.beforeResponse = { gate.await() }
            var current = true
            val read = async { readHandoffHistory(source, "source", { current }) }
            yield()
            when (change) {
                "profile" -> current = false
                "directory" -> source.directory = "/other"
                "workspace" -> source.workspace = "other"
                "closed" -> source.isClosed = true
            }
            gate.complete(Unit)
            assertFailure(HandoffHistoryFailureKind.StaleScope, read.await())
        }
    }

    @Test
    fun `B-AGENT-019 transport errors become content-free failures`() = runTest {
        val source = PagedSource(listOf(page(emptyList()))).apply { error = IllegalStateException("sensitive-token-value") }
        val read = readHandoffHistory(source, "source", { true })
        assertFailure(HandoffHistoryFailureKind.ReadFailed, read)
        assertFalse(read.toString().contains("sensitive-token-value"))
        // A failed Outcome from the port is just as content-free.
        val refused = object : HandoffHistorySource {
            override val directory: String? = "/project"
            override val workspace: String? = null
            override val isClosed: Boolean = false
            override val historyReader: HandoffHistoryReader =
                HandoffHistoryReader.Paged { _, _, _ -> Outcome.Failed(Failure.Server(500)) }
        }
        assertFailure(HandoffHistoryFailureKind.ReadFailed, readHandoffHistory(refused, "source", { true }))
    }
}
