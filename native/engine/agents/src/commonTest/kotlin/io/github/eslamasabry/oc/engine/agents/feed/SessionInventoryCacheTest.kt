package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** B-STATE-103 / B-STATE-104, ported from Flutter test/session_inventory_cache_test.dart. */
class SessionInventoryCacheTest {
    private class MemoryStorage : SessionInventoryStorage {
        val values = mutableMapOf<String, String>()
        var refuse = false

        override fun read(key: String): String? = values[key]

        override suspend fun write(key: String, raw: String): Outcome<Unit> {
            if (refuse) return Outcome.Failed(Failure.StorageRefused())
            values[key] = raw
            return Outcome.Ok(Unit)
        }
    }

    private val storage = MemoryStorage()
    private val cache = SessionInventoryCache(storage)
    private val here = SessionInventoryCache.scopeFor("openCode", "v2", "https://fixture.invalid", null, null, null)
    private val elsewhere = SessionInventoryCache.scopeFor("openCode", "v2", "https://fixture.invalid", null, "/different", null)

    private fun s(id: String, title: String? = "Label $id") = Session(id = SessionId(id), title = title)

    @Test
    fun `B-STATE-103 the restart preview is bounded, exact to profile and location and read-only`() = runTest {
        cache.save("one", here, (0 until 500).map { s("$it") }, fetchedAtMs = 1_000, isCurrent = { true })
        val restarted = SessionInventoryCache(storage)
        val preview = restarted.read("one", here)!!
        assertEquals(SessionInventoryCache.MAX_SESSIONS, preview.sessions.size)
        assertEquals("Label 0", preview.sessions.first().title)
        assertEquals(1_000, preview.fetchedAtMs)
        assertSame(preview, restarted.read("one", here)) // one decode, one object
        assertIs<ImmutableList<SessionPreview>>(preview.sessions) // the type has no mutators: it cannot be written
        assertNull(restarted.read("two", here)) // another profile sees nothing
        assertNull(restarted.read("one", elsewhere)) // another location sees nothing
        assertNull(restarted.read("one", elsewhere)) // and still nothing after a cached decode
        assertTrue(storage.values.getValue(SessionInventoryCache.keyFor("one")).encodeToByteArray().size <= SessionInventoryCache.MAX_BYTES)
    }

    @Test
    fun `B-STATE-103 the scope is the Dart digest of backend, flavor, address, user and location`() {
        // sha256 of jsonEncode(["openCode","v2","https://fixture.invalid",null,null,null]) as Dart writes it.
        assertEquals(64, here.length)
        assertTrue(here != elsewhere)
        assertEquals(here, SessionInventoryCache.scopeFor("openCode", "v2", "https://fixture.invalid", null, null, null))
    }

    @Test
    fun `B-STATE-104 refreshing persists labels, and the key is the profile-swept oc dot what dot id shape`() = runTest {
        cache.save("one", here, listOf(s("fresh", "Fresh title")), fetchedAtMs = 5, isCurrent = { true })
        assertEquals("fresh", cache.read("one", here)!!.sessions.single().id.raw)
        assertEquals("oc.sessionInventory.one", SessionInventoryCache.keyFor("one"))
        assertEquals(setOf("oc.sessionInventory.one"), storage.values.keys)
    }

    @Test
    fun `B-STATE-104 a profile deleted meanwhile is not written back`() = runTest {
        var current = true
        cache.save("one", here, listOf(s("a")), fetchedAtMs = 1, isCurrent = { current })
        storage.values.clear() // the deletion sweep removed the key
        cache.forget("one")
        current = false
        cache.save("one", here, listOf(s("late")), fetchedAtMs = 2, isCurrent = { current })
        assertTrue(storage.values.isEmpty())
        assertNull(cache.read("one", here))
    }

    @Test
    fun `B-STATE-104 corrupt or foreign previews read as nothing and revoked writes are skipped`() = runTest {
        val key = SessionInventoryCache.keyFor("one")
        storage.values[key] = "{"
        assertNull(cache.read("one", "scope"))
        cache.save("one", "scope", listOf(s("safe")), fetchedAtMs = 1, isCurrent = { false })
        assertEquals("{", storage.values[key])
        cache.save("one", "scope", listOf(s("safe")), fetchedAtMs = 1, isCurrent = { true })
        assertNull(cache.read("one", "other"))
        assertEquals("safe", cache.read("one", "scope")!!.sessions.single().id.raw)
        assertNull(cache.read("two", "scope"))
        for (bad in listOf("""{"v":2,"scope":"scope","at":1,"rows":[]}""", """{"v":1,"scope":"scope","at":1,"rows":[{"id":5}]}""", "[]")) {
            val fresh = SessionInventoryCache(MemoryStorage().also { it.values[key] = bad })
            assertNull(fresh.read("one", "scope"), bad)
        }
    }

    @Test
    fun `B-STATE-104 titles are masked and capped, ids are kept whole and empty ids are skipped`() = runTest {
        val masking = SessionInventoryCache(storage, mask = { it.replace("sk-secret", "•••") })
        masking.save("one", here, listOf(s("", "x"), s("k", "use sk-secret " + "y".repeat(400))), fetchedAtMs = 1, isCurrent = { true })
        val row = masking.read("one", here)!!.sessions.single()
        assertEquals("k", row.id.raw)
        assertEquals(256, row.title.length)
        assertTrue(row.title.startsWith("use ••• "))
    }

    @Test
    fun `B-STATE-104 a refused write leaves the stored preview as it was`() = runTest {
        cache.save("one", here, listOf(s("old")), fetchedAtMs = 1, isCurrent = { true })
        storage.refuse = true
        assertIs<Outcome.Failed>(cache.save("one", here, listOf(s("new")), fetchedAtMs = 2, isCurrent = { true }))
        assertEquals("old", cache.read("one", here)!!.sessions.single().id.raw)
    }
}
