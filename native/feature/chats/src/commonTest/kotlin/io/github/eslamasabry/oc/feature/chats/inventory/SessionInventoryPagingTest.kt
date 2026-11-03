package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionInventoryPagingTest {
    private fun TestScope.inventory(source: FakeSource): SessionInventory =
        SessionInventory(backgroundScope).apply { connect(source, ServerLocation(directory = "/one")) }

    private fun ids(inventory: SessionInventory): List<String> = inventory.sortedSessions().map { it.id.raw }

    @Test
    fun `B-STATE-105 detail hydration does not suppress a concurrent busy snapshot`() = runTest {
        val statusStarted = CompletableDeferred<Unit>()
        val status = CompletableDeferred<Map<SessionId, SessionStatus>>()
        val source = FakeSource().apply {
            statuses = {
                statusStarted.complete(Unit)
                Outcome.Ok(status.await())
            }
        }
        val inv = inventory(source)
        val refresh = async { inv.refresh() }
        statusStarted.await()
        inv.ensureSession(sid("running"))
        status.complete(mapOf(sid("running") to SessionStatus.Busy))
        refresh.await()
        assertTrue(sid("running") in inv.busy)
    }

    @Test
    fun `B-STATE-105 a pending page cannot resurrect deletes or overwrite newer live metadata`() = runTest {
        val pending = CompletableDeferred<ServerPage<Session>>()
        val source = FakeSource().apply {
            page = { c -> if (c == null) Outcome.Ok(page(session("recent"), next = "older")) else Outcome.Ok(pending.await()) }
        }
        val inv = inventory(source)
        inv.refresh()
        val more = async { inv.loadMore() }
        runCurrent()
        inv.applyDeleted(sid("deleted"))
        inv.applyUpdated(session("old", title = "live title"))
        pending.complete(page(session("deleted"), session("old", title = "stale")))
        more.await()
        assertFalse(sid("deleted") in inv.sessionsById)
        assertEquals("live title", inv.sessionsById[sid("old")]!!.title)
        assertTrue(sid("recent") in inv.sessionsById)
    }

    @Test
    fun `B-STATE-105 a direct lookup protects newer details from a pending inventory page`() = runTest {
        val pending = CompletableDeferred<ServerPage<Session>>()
        val source = FakeSource().apply {
            page = { Outcome.Ok(pending.await()) }
            one = { Outcome.Ok(session(it.raw, title = "fresh details")) }
        }
        val inv = inventory(source)
        val refresh = async { inv.refresh() }
        runCurrent()
        inv.ensureSession(sid("recent"))
        pending.complete(page(session("recent", title = "old details")))
        refresh.await()
        assertEquals("fresh details", inv.sessionsById[sid("recent")]!!.title)
    }

    @Test
    fun `B-STATE-106 a partial refresh keeps cached older metadata and resets the continuation`() = runTest {
        val source = FakeSource().apply {
            page = { c -> Outcome.Ok(if (c == null) page(session("recent"), next = "older") else page(session("old"))) }
        }
        val inv = inventory(source)
        inv.refresh()
        inv.loadMore()
        inv.refresh()
        assertTrue(inv.sessionsById.keys.containsAll(listOf(sid("recent"), sid("old"))))
        assertTrue(inv.hasMore)
        assertEquals(listOf(null, "older", null), source.cursors)
    }

    @Test
    fun `B-STATE-106 a new head refresh invalidates an older request without clearing new state`() = runTest {
        val pending = CompletableDeferred<ServerPage<Session>>()
        var refreshed = false
        val source = FakeSource().apply {
            page = { c ->
                if (c == null) Outcome.Ok(page(session(if (refreshed) "new-head" else "recent"), next = "older"))
                else Outcome.Ok(pending.await())
            }
        }
        val inv = inventory(source)
        inv.refresh()
        val more = async { inv.loadMore() }
        runCurrent()
        refreshed = true
        inv.refresh()
        pending.complete(page(session("stale-page")))
        more.await()
        assertFalse(sid("stale-page") in inv.sessionsById)
        assertTrue(sid("new-head") in inv.sessionsById)
        assertFalse(inv.loadingMore)
        assertTrue(inv.hasMore)
    }

    @Test
    fun `B-STATE-106 an expired cursor reloads and a transient failure retries the same token`() = runTest {
        var failure = 500
        val source = FakeSource().apply {
            page = { c ->
                if (c != null) Outcome.Failed(if (failure >= 500) Failure.Server(failure) else Failure.Rejected(failure))
                else Outcome.Ok(page(session("recent"), next = "older"))
            }
        }
        val inv = inventory(source)
        inv.refresh()
        inv.loadMore()
        assertFalse(inv.needsReload)
        failure = 410
        inv.loadMore()
        assertTrue(inv.needsReload)
        inv.loadMore()
        assertEquals(listOf(null, "older", "older", null), source.cursors)
        assertNull(inv.moreError)
    }

    @Test
    fun `B-STATE-106 a looping cursor asks for a refresh from the newest page`() = runTest {
        val source = FakeSource().apply {
            page = { c -> Outcome.Ok(if (c == null) page(session("recent"), next = "loop") else page(session("again"), next = "loop")) }
        }
        val inv = inventory(source)
        inv.refresh()
        inv.loadMore()
        assertTrue(inv.needsReload)
        source.page = { Outcome.Ok(page(session("fresh"))) }
        inv.loadMore()
        assertNull(source.cursors.last())
        assertFalse(inv.needsReload)
        assertNull(inv.moreError)
    }

    @Test
    fun `B-STATE-107 direct reads coalesce and keep out-of-location metadata out of the list`() = runTest {
        val details = CompletableDeferred<Session>()
        val source = FakeSource().apply {
            page = { Outcome.Ok(page(session("recent"), next = "older")) }
            one = { Outcome.Ok(details.await()) }
        }
        val inv = inventory(source)
        inv.refresh()
        val first = async { inv.ensureSession(sid("foreign")) }
        val second = async { inv.ensureSession(sid("foreign")) }
        runCurrent()
        details.complete(session("foreign", directory = "/elsewhere"))
        first.await()
        second.await()
        assertEquals(1, source.detailReads.size)
        assertTrue(sid("foreign") in inv.sessionsById)
        assertEquals(listOf("recent"), ids(inv))
        source.one = { Outcome.Ok(session(it.raw, directory = "/elsewhere")) }
        inv.ensureSession(sid("recent"))
        assertTrue(inv.sortedSessions().isEmpty())
        assertTrue(sid("recent") in inv.sessionsById)
        source.page = { Outcome.Ok(page()) }
        inv.refresh()
        assertTrue(inv.sessionsById.keys.containsAll(listOf(sid("recent"), sid("foreign"))))
    }

    @Test
    fun `B-STATE-107 a confirmed local delete and an authoritative 404 clear cached sessions for good`() = runTest {
        val source = FakeSource().apply { page = { Outcome.Ok(page(session("delete"), session("missing"), next = "older")) } }
        val inv = inventory(source)
        inv.refresh()
        assertEquals(Outcome.Ok(Unit), inv.deleteSession(sid("delete")))
        assertFalse(sid("delete") in inv.sessionsById)
        source.page = { Outcome.Ok(page(session("delete"), session("missing"))) }
        inv.loadMore()
        assertFalse(sid("delete") in inv.sessionsById)
        source.one = { Outcome.Failed(Failure.NotFound()) }
        inv.ensureSession(sid("missing"))
        assertTrue(inv.sessionsById.isEmpty())
    }

    @Test
    fun `B-STATE-107 a stale detail failure cannot replace a newer successful read`() = runTest {
        val old = CompletableDeferred<Outcome<Session>>()
        val source = FakeSource()
        source.one = { if (source.detailReads.size == 1) old.await() else Outcome.Ok(session("target", title = "Fresh title")) }
        val inv = inventory(source)
        val pending = async { inv.ensureSession(sid("target")) }
        runCurrent()
        inv.invalidate(sid("target")) // session.compacted: a newer revision starts a fresh read
        inv.ensureSession(sid("target"))
        old.complete(Outcome.Failed(Failure.NotFound()))
        pending.await()
        assertEquals("Fresh title", inv.sessionsById[sid("target")]!!.title)
        assertTrue(inv.detailsErrors.isEmpty())
        assertEquals(2, source.detailReads.size)
    }
}
