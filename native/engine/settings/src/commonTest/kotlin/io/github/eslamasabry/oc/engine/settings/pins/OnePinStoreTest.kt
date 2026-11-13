package io.github.eslamasabry.oc.engine.settings.pins

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** One pin store for the home list and the dock: order, either side, restart. */
class OnePinStoreTest {
    private val a = ProfileId("a")
    private val here = SessionPinStore.scope("/project", null)
    private val there = SessionPinStore.scope("/other", null)

    @Test
    fun `pins made before the order existed read in their saved order and a new pin goes last`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val seeded = """{${JsonPrimitive(here)}:["s1","s2"],${JsonPrimitive(there)}:["s3"]}"""
        kv.commit(WriterLane("seed")) { putString(SessionPinStore.keyFor(a), seeded) }
        val pins = SessionPinStore(kv, DefaultWriterLanes())
        assertEquals(listOf("s1", "s2", "s3"), pins.orderedIds(a))
        assertEquals(setOf("s1", "s2"), pins.ids(a, here), "the home reads exactly what it did")
        assertIs<Outcome.Ok<Unit>>(pins.setPinned(a, here, "s4", true))
        assertEquals(listOf("s1", "s2", "s3", "s4"), pins.orderedIds(a))
    }

    @Test
    fun `a pin made on the dock shows on the home list in every location and unpinning there removes it`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val pins = SessionPinStore(kv, DefaultWriterLanes())
        assertIs<Outcome.Ok<Unit>>(pins.replaceOrder(a, listOf("d1", "d2")))
        assertEquals(setOf("d1", "d2"), pins.ids(a, here))
        assertEquals(setOf("d1", "d2"), pins.ids(a, there))
        assertEquals(listOf("d1", "d2"), pins.orderedIds(a))
        pins.setPinned(a, here, "h1", true)
        assertEquals(listOf("d1", "d2", "h1"), pins.orderedIds(a), "a home pin lands after the dock's")
        assertEquals(setOf("d1", "d2"), pins.ids(a, there), "a home pin stays in its own location, the dock's show everywhere")
        // The home unpins a pin the dock made.
        pins.setPinned(a, here, "d1", false)
        assertEquals(listOf("d2", "h1"), pins.orderedIds(a))
        assertEquals(setOf("d2", "h1"), pins.ids(a, here))
    }

    @Test
    fun `the dock can pin, unpin and reorder what the home pinned`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val pins = SessionPinStore(kv, DefaultWriterLanes())
        pins.setPinned(a, here, "h1", true)
        pins.setPinned(a, here, "h2", true)
        pins.setPinned(a, there, "h3", true)
        assertEquals(listOf("h1", "h2", "h3"), pins.orderedIds(a))
        pins.replaceOrder(a, listOf("h3", "h1", "h2")) // reorder on the dock
        assertEquals(listOf("h3", "h1", "h2"), pins.orderedIds(a))
        pins.replaceOrder(a, listOf("h3", "h2")) // unpin h1 on the dock
        assertEquals(listOf("h3", "h2"), pins.orderedIds(a))
        assertEquals(setOf("h2"), pins.ids(a, here), "the home no longer shows h1 as pinned")
        assertTrue("h1" !in pins.ids(a, here))
        assertEquals(setOf("h3"), pins.ids(a, there), "each pin stays in the location the home made it in")
    }

    @Test
    fun `an unchanged order writes nothing and a changed one tells the other surface`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val pins = SessionPinStore(kv, DefaultWriterLanes())
        var told = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { pins.changes.collect { told++ } }
        pins.replaceOrder(a, listOf("x", "y"))
        assertEquals(1, told)
        pins.replaceOrder(a, listOf("x", "y"))
        assertEquals(1, told, "the same list is not written again")
        pins.setPinned(a, here, "z", true)
        assertEquals(2, told)
        job.cancel()
    }

    @Test
    fun `pins and their order survive a restart and the object stays one key per profile`() = runTest {
        val disk = TestDisk()
        val kv = storeOver(disk, StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        val before = SessionPinStore(kv, lanes)
        before.setPinned(a, here, "h1", true)
        before.replaceOrder(a, listOf("d1", "h1", "d2"))
        val after = SessionPinStore(kv, lanes) // the app restarted: nothing cached
        assertEquals(listOf("d1", "h1", "d2"), after.orderedIds(a))
        assertEquals(setOf("d1", "h1", "d2"), after.ids(a, here))
        assertIs<PrefValue.Str>(kv.get("oc.sessionPins.a"), "still the one per-profile key the deletion sweep knows")
        assertEquals(null, kv.get("oc.sessionPins.a.order"))
    }

    @Test
    fun `no pins at all is an empty object and an empty order`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val pins = SessionPinStore(kv, DefaultWriterLanes())
        assertEquals(emptyList(), pins.orderedIds(a))
        pins.setPinned(a, here, "h1", true)
        pins.setPinned(a, here, "h1", false)
        assertEquals(emptyList(), pins.orderedIds(a))
        assertEquals("{}", (kv.get("oc.sessionPins.a") as PrefValue.Str).value)
    }
}
