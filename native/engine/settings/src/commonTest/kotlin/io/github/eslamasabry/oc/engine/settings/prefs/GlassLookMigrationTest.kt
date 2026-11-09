package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferenceKeys as K
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `oc.glassEdges` moves to `oc.glassLook` once, at load (docs/designs/glass-looks-2026-11-09.md §3): write, then
 * delete, in one commit. `Preferences` drops default-valued keys, so no fallback read may outlive the old key.
 * Extends B-STATE-003 (every device choice persists under its key and survives a restart).
 */
class GlassLookMigrationTest {
    private val app = WriterLane("app")

    private fun TestScope.fresh(disk: TestDisk) = storeOver(disk, StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.loaded(disk: TestDisk): Preferences {
        val owner = Preferences(fresh(disk), app, backgroundScope)
        owner.glassLookMigrated()
        return owner
    }

    private suspend fun TestScope.seeded(vararg pairs: Pair<String, Any>): TestDisk {
        val disk = TestDisk()
        fresh(disk).seed(*pairs)
        return TestDisk(disk.persisted())
    }

    @Test
    fun `B-STATE-003 no stored glass choice reads Glossy and writes nothing`() = runTest {
        val disk = TestDisk()
        val owner = loaded(disk)
        assertEquals(GlassLookChoice.Glossy, owner.value.value.glassLook)
        assertEquals(0, disk.commits.size, "a launch without the old key is a no-op")
    }

    @Test
    fun `B-STATE-003 the old Liquid edges become Glossy and the old key goes`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "liquid")
        val owner = loaded(disk)
        assertEquals(GlassLookChoice.Glossy, owner.value.value.glassLook)
        val store = fresh(TestDisk(disk.persisted()))
        assertNull(store.get(K.GLASS_EDGES))
        assertNull(store.get(K.GLASS_LOOK), "Glossy is the default: never stored")
    }

    @Test
    fun `B-STATE-003 the old Frosted edges become the Frosted look, written before the old key is deleted, in one commit`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "frosted")
        val before = disk.commits.size
        val owner = loaded(disk)
        assertEquals(GlassLookChoice.Frosted, owner.value.value.glassLook)
        assertEquals(before + 1, disk.commits.size, "one commit")
        val store = fresh(TestDisk(disk.persisted()))
        assertEquals("frosted", store.string(K.GLASS_LOOK))
        assertNull(store.get(K.GLASS_EDGES))
    }

    @Test
    fun `B-STATE-003 an unknown old value reads Glossy`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "melted")
        val owner = loaded(disk)
        assertEquals(GlassLookChoice.Glossy, owner.value.value.glassLook)
        assertNull(fresh(TestDisk(disk.persisted())).get(K.GLASS_EDGES))
    }

    @Test
    fun `B-STATE-003 frosted, then Glossy picked, survives a restart as Glossy`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "frosted")
        val owner = loaded(disk)
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Look(GlassLookChoice.Glossy)))
        assertEquals(GlassLookChoice.Glossy, owner.value.value.glassLook)
        val restarted = loaded(TestDisk(disk.persisted()))
        assertEquals(GlassLookChoice.Glossy, restarted.value.value.glassLook, "the old key never comes back")
    }

    @Test
    fun `B-STATE-003 a refused migration keeps the old choice and a look write still removes the old key`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "frosted")
        disk.refuse = { key, _ -> key == K.GLASS_EDGES }
        val owner = loaded(disk)
        assertEquals(GlassLookChoice.Frosted, owner.value.value.glassLook, "the old key's choice reads until it is gone")
        disk.refuse = null
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Look(GlassLookChoice.Glossy)))
        val store = fresh(TestDisk(disk.persisted()))
        assertNull(store.get(K.GLASS_EDGES), "a look write deletes the old key with it")
        assertEquals(GlassLookChoice.Glossy, loaded(TestDisk(disk.persisted())).value.value.glassLook)
    }

    @Test
    fun `B-STATE-003 a second launch after the migration writes nothing`() = runTest {
        val disk = seeded(K.GLASS_EDGES to "frosted")
        loaded(disk)
        val second = TestDisk(disk.persisted())
        val owner = loaded(second)
        assertEquals(GlassLookChoice.Frosted, owner.value.value.glassLook)
        assertEquals(0, second.commits.size)
    }

    @Test
    fun `B-STATE-003 every look persists under oc glassLook and survives a restart`() = runTest {
        for (look in GlassLookChoice.entries) {
            val disk = TestDisk()
            val owner = loaded(disk)
            assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Look(look)))
            assertEquals(look, loaded(TestDisk(disk.persisted())).value.value.glassLook, "$look")
            assertEquals(look.wire.takeIf { look != GlassLookChoice.Glossy }, fresh(TestDisk(disk.persisted())).string(K.GLASS_LOOK))
        }
    }
}
