package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.world.RecentModels
import io.github.eslamasabry.oc.feature.chat.world.RecentModelsBacking
import kotlin.test.Test
import kotlin.test.assertEquals

/** P12-BUILD-AGENTS: the picker's Recent group survives a restart when the app binds a store (Flutter ModelLibrary). */
class SavedRecentModelsTest {
    private class Store(var ready: Boolean = true) : RecentModelsBacking {
        val saved = HashMap<String, List<Pair<String, String>>>()
        var loads = 0

        override fun load(profile: String): List<Pair<String, String>>? {
            loads++
            return if (ready) saved[profile].orEmpty() else null
        }

        override fun save(profile: String, recent: List<Pair<String, String>>) {
            saved[profile] = recent
        }
    }

    @Test
    fun `B-STATE-032 recent picks are saved per server newest first and read back after a restart`() {
        val store = Store()
        val before = RecentModels(max = 8, backing = store)
        before.record("p1", "openai", "gpt-5")
        before.record("p1", "anthropic", "sonnet")
        before.record("p1", "openai", "gpt-5")
        before.record("p2", "google", "gemini")
        assertEquals(listOf("openai" to "gpt-5", "anthropic" to "sonnet"), store.saved["p1"], "deduplicated, newest first")
        val after = RecentModels(max = 8, backing = store)
        assertEquals(listOf("openai" to "gpt-5", "anthropic" to "sonnet"), after.of("p1"))
        assertEquals(listOf("google" to "gemini"), after.of("p2"), "storage is per server")
    }

    @Test
    fun `B-STATE-032 only the eight newest picks are kept and a store that cannot answer yet is asked again`() {
        val store = Store(ready = false)
        val recent = RecentModels(max = 8, backing = store)
        assertEquals(emptyList(), recent.of("p1"))
        store.ready = true
        store.saved["p1"] = listOf("a" to "1")
        assertEquals(listOf("a" to "1"), recent.of("p1"), "read again once the store opens")
        for (i in 2..10) recent.record("p1", "a", "$i")
        assertEquals(8, recent.of("p1").size)
        assertEquals("a" to "10", recent.of("p1").first())
        assertEquals(8, store.saved.getValue("p1").size)
    }

    @Test
    fun `B-STATE-032 without a store recents stay in memory for this app run`() {
        val recent = RecentModels()
        recent.record("p1", "openai", "gpt-5")
        assertEquals(listOf("openai" to "gpt-5"), recent.of("p1"))
        assertEquals(emptyList(), RecentModels().of("p1"))
    }
}
