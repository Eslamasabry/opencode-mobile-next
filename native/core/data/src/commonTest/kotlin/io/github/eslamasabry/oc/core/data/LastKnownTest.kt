package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.FileLastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Cache-first (CF): the one last-known store every screen draws its first frame from. */
class LastKnownTest {
    @Serializable data class Row(val id: String, val title: String)

    @Serializable data class Leaky(val id: String, val token: String)

    private val root = Path(SystemTemporaryDirectory, "last-known-test-${Random.nextLong().toULong()}")
    private val alpha = ProfileId("alpha")
    private val beta = ProfileId("beta")
    private val home = ServerMark.of("openCode", "https://box.example", "opencode", "/work")
    private val list = CacheShelf("chatList", ListSerializer(Row.serializer()), maxBytes = 4096)
    private val pages = CacheShelf("transcript", ListSerializer(Row.serializer()), maxBytes = 4096, maxEntries = 2)

    private fun TestScope.store() = FileLastKnown(root, StandardTestDispatcher(testScheduler), TestClock())

    @AfterTest
    fun cleanUp() {
        fun wipe(path: Path) {
            val meta = SystemFileSystem.metadataOrNull(path) ?: return
            if (meta.isDirectory) SystemFileSystem.list(path).forEach(::wipe)
            SystemFileSystem.delete(path, mustExist = false)
        }
        wipe(root)
    }

    @Test
    fun `CF-01 a value written survives the process and a fresh store reads it back`() = runTest {
        val rows = listOf(Row("s1", "Fix the login bug"), Row("s2", "Plan the release"))
        assertIs<Outcome.Ok<Unit>>(store().write(alpha, home, list, value = rows))

        val fresh = store()
        assertNull(fresh.peek(alpha, home, list), "nothing is in memory before a read")
        assertEquals(rows, fresh.read(alpha, home, list))
        assertEquals(rows, fresh.peek(alpha, home, list), "a read fills memory for the next first frame")
    }

    @Test
    fun `CF-02 data saved for another server never shows`() = runTest {
        store().write(alpha, home, list, value = listOf(Row("s1", "old server")))
        val moved = ServerMark.of("openCode", "https://other.example", "opencode", "/work")

        val fresh = store()
        assertNull(fresh.read(alpha, moved, list))
        assertNull(fresh.read(alpha, home, list), "the other server's entry was dropped on sight")
    }

    @Test
    fun `CF-03 a shelf keeps its newest entries and lets the oldest go`() = runTest {
        val store = store()
        store.write(alpha, home, pages, "ses_1", listOf(Row("m1", "one")))
        store.write(alpha, home, pages, "ses_2", listOf(Row("m2", "two")))
        store.write(alpha, home, pages, "ses_1", listOf(Row("m1", "one again")))
        store.write(alpha, home, pages, "ses_3", listOf(Row("m3", "three")))

        val fresh = store()
        assertNull(fresh.read(alpha, home, pages, "ses_2"), "the least recently written entry went")
        assertEquals("one again", fresh.read(alpha, home, pages, "ses_1")?.single()?.title)
        assertEquals("three", fresh.read(alpha, home, pages, "ses_3")?.single()?.title)
        assertNull(store.peek(alpha, home, pages, "ses_2"), "memory forgets the evicted entry too")
    }

    @Test
    fun `CF-04 an entry over its cap or carrying a credential is refused`() = runTest {
        val store = store()
        val huge = List(200) { Row("s$it", "x".repeat(40)) }
        assertIs<Outcome.Failed>(store.write(alpha, home, list, value = huge))
        assertNull(store.read(alpha, home, list))

        val leaky = CacheShelf("leaky", Leaky.serializer(), maxBytes = 4096)
        assertIs<Outcome.Failed>(store.write(alpha, home, leaky, value = Leaky("a", "not-for-disk")))
        assertNull(store().read(alpha, home, leaky))
    }

    @Test
    fun `CF-05 deleting a profile clears its data and only its data`() = runTest {
        val store = store()
        store.write(alpha, home, list, value = listOf(Row("a", "alpha's")))
        store.write(alpha, home, pages, "ses_1", listOf(Row("m", "alpha's page")))
        store.write(beta, home, list, value = listOf(Row("b", "beta's")))

        assertEquals(Outcome.Ok(2), store.clear(alpha))
        assertNull(store.peek(alpha, home, list))
        val fresh = store()
        assertNull(fresh.read(alpha, home, list))
        assertNull(fresh.read(alpha, home, pages, "ses_1"))
        assertEquals("beta's", fresh.read(beta, home, list)?.single()?.title)
        assertEquals(Outcome.Ok(0), store.clear(alpha), "a second clear removes nothing")
    }

    @Test
    fun `CF-06 warming fills memory so the next screen's first frame needs no read`() = runTest {
        store().write(alpha, home, pages, "ses_1", listOf(Row("m1", "one")))
        val fresh = store()
        fresh.warm(alpha, home, pages)
        assertEquals("one", fresh.peek(alpha, home, pages, "ses_1")?.single()?.title)
        assertTrue(fresh.peek(alpha, ServerMark.of("elsewhere"), pages, "ses_1") == null)
    }
}
