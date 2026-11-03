package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionInventoryCacheTest {
    private val one = ProfileId("one")
    private val two = ProfileId("two")

    private fun scope(directory: String?, workspace: String? = null): String =
        SessionInventoryCache.scopeFor("opencode", "v2", "https://fixture.invalid", null, directory, workspace)

    @Test
    fun `B-STATE-103 a bounded restart preview is exact to profile and location and read-only`() = runTest {
        val storage = FakeStorage()
        val cache = SessionInventoryCache(storage, clock = { 1_000L })
        val here = scope(null)
        cache.save(one, here, List(500) { session("$it", title = "Label $it") }, isCurrent = { true })

        // A restarted process: a new cache over the same file, nothing read from the network.
        val restarted = SessionInventoryCache(storage, clock = { 2_000L })
        val preview = restarted.read(one, here)!!
        assertEquals(SessionInventoryCache.MAX_SESSIONS, preview.sessions.size)
        assertEquals("Label 0", preview.sessions.first().title)
        assertEquals(1_000L, preview.fetchedAtMs)
        assertSame(preview, restarted.read(one, here))
        assertFalse(preview.sessions is MutableList<*>, "the preview cannot be written")
        // Another profile, or another location of the same profile, sees nothing.
        assertNull(restarted.read(two, here))
        assertNull(restarted.read(one, scope("/different")))
        assertNull(restarted.read(one, scope(null, "different")))
        assertTrue(DartJson.utf8(storage.values[SessionInventoryCache.keyFor(one)]!!).size <= SessionInventoryCache.MAX_BYTES)
    }

    @Test
    fun `B-STATE-104 a refresh persists labels and the profile sweep removes them without resurrection`() = runTest {
        val storage = FakeStorage()
        val cache = SessionInventoryCache(storage, clock = { 1L })
        val source = FakeSource().apply { page = { Outcome.Ok(page(session("fresh", title = "Fresh title", directory = null))) } }
        val inv = SessionInventory(backgroundScope).apply { connect(source, ServerLocation()) }
        var deleted = false
        val here = scope(null)
        inv.onRefreshed = { sorted -> backgroundScope.launch { cache.save(one, here, sorted, isCurrent = { !deleted }) } }
        inv.refresh()
        runCurrent()
        assertEquals("fresh", cache.read(one, here)!!.sessions.single().id.raw)
        val key = SessionInventoryCache.keyFor(one)
        assertEquals("oc.sessionInventory.one", key)
        // The deletion: the profile is closed, its keys swept, the in-memory copy forgotten.
        deleted = true
        storage.values.remove(key)
        cache.forget(one)
        assertNull(cache.read(one, here))
        // A retired connection's late refresh must not bring the deleted profile's labels back.
        inv.refresh()
        runCurrent()
        assertFalse(key in storage.values)
    }

    @Test
    fun `B-STATE-104 corrupt and foreign previews are empty and revoked writes are skipped`() = runTest {
        val storage = FakeStorage()
        val cache = SessionInventoryCache(storage, clock = { 1L })
        val key = SessionInventoryCache.keyFor(one)
        storage.values[key] = "{"
        assertNull(cache.read(one, "scope"))
        cache.save(one, "scope", listOf(session("safe")), isCurrent = { false })
        assertEquals("{", storage.values[key])
        cache.save(one, "scope", listOf(session("safe")), isCurrent = { true })
        assertNull(cache.read(one, "other"))
        assertEquals("safe", cache.read(one, "scope")!!.sessions.single().id.raw)
        assertNull(cache.read(two, "scope"))
    }

    @Test
    fun `B-STATE-104 the scope matches the Flutter encoding and titles never split a character`() {
        val expected = DartJson.sha256Hex("[\"opencode\",\"v2\",\"https://fixture.invalid\",null,\"/p\",null]")
        assertEquals(expected, scope("/p"))
        assertEquals("ab🙂", firstCodePoints("ab🙂c", 3))
    }
}
