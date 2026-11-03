package io.github.eslamasabry.oc.feature.about.managespace

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ManageSpacePresenterTest {
    private class FakePort(
        var facts: Outcome<ManageSpaceFacts> = ManageSpaceFacts(cacheBytes = 3 * 1024 * 1024, savedServers = 2).ok(),
        var cleared: Outcome<Long> = 3_145_728L.ok(),
        var acceptClearAll: Boolean = true,
    ) : ManageSpacePort {
        val calls = mutableListOf<String>()

        override suspend fun measure(): Outcome<ManageSpaceFacts> = facts.also { calls += "measure" }

        override suspend fun clearCache(): Outcome<Long> = cleared.also { calls += "clearCache" }

        override suspend fun clearAllData(): Boolean = acceptClearAll.also { calls += "clearAll" }
    }

    @Test
    fun `B-ANDR-035 the guard page measures first and clears everything only after the confirm`() = runTest {
        val port = FakePort()
        var closed = false
        val page = ManageSpacePresenter(port, this) { closed = true }
        assertNull(page.state.value.facts, "measuring")
        page.start()
        testScheduler.advanceUntilIdle()
        assertEquals(2, page.state.value.facts?.savedServers)

        page.onDeleteAll()
        assertTrue(page.state.value.confirmingDelete)
        assertEquals(listOf("measure"), port.calls, "asking never clears")
        page.onCancelDelete()
        assertFalse(page.state.value.confirmingDelete)

        page.onDeleteAll()
        page.onConfirmDelete()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("measure", "clearAll"), port.calls)
        page.onClose()
        assertTrue(closed)
    }

    @Test
    fun `B-ANDR-035 a refused clear-all leaves the page usable`() = runTest {
        val port = FakePort(acceptClearAll = false)
        val page = ManageSpacePresenter(port, this) {}
        page.start()
        page.onDeleteAll()
        page.onConfirmDelete()
        testScheduler.advanceUntilIdle()
        assertFalse(page.state.value.busy)
    }

    @Test
    fun `B-ANDR-035 clearing the cache says what it freed or offers try again`() = runTest {
        val port = FakePort()
        val page = ManageSpacePresenter(port, this) {}
        page.start()
        testScheduler.advanceUntilIdle()
        page.onClearCache()
        testScheduler.advanceUntilIdle()
        assertEquals(CacheNotice.Cleared(3_145_728L), page.state.value.notice)
        assertEquals(listOf("measure", "clearCache", "measure"), port.calls, "re-measures after clearing")

        port.cleared = Outcome.Failed(Failure.StorageRefused())
        page.onClearCache()
        testScheduler.advanceUntilIdle()
        assertEquals(CacheNotice.Failed, page.state.value.notice)
        assertFalse(page.state.value.busy)
    }

    @Test
    fun `B-ANDR-035 an unmeasurable phone still shows the page`() = runTest {
        val page = ManageSpacePresenter(FakePort(facts = Outcome.Failed(Failure.StorageRefused())), this) {}
        page.start()
        testScheduler.advanceUntilIdle()
        assertEquals(ManageSpaceFacts(cacheBytes = null, savedServers = 0), page.state.value.facts)
    }

    @Test
    fun `B-ANDR-035 sizes read like the Flutter page`() {
        assertEquals("512 B", formatPhoneStorage(512))
        assertEquals("1.0 KB", formatPhoneStorage(1024))
        assertEquals("3.0 MB", formatPhoneStorage(3L * 1024 * 1024))
        assertEquals("1.5 GB", formatPhoneStorage(3L * 512 * 1024 * 1024))
    }
}
