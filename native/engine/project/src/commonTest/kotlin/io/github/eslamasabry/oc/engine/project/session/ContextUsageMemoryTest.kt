package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextUsageMemoryTest {
    private val alpha = ProfileId("alpha")

    private fun TestScope.fresh(disk: SessionTestDisk = SessionTestDisk()) = storeOver(disk, StandardTestDispatcher(testScheduler))

    @Test
    fun `B-STATE-012 remembers a value per session under oc contextUsage profile`() = runTest {
        val disk = SessionTestDisk()
        val store = fresh(disk)
        val memory = ContextUsageMemory(store, WriterLane("alpha"), alpha)
        assertNull(memory.usageOf(SessionId("s1")))
        memory.remember(SessionId("s1"), 0.8123456)
        assertEquals(0.812, memory.usageOf(SessionId("s1")))
        assertTrue("\"s1\":0.812" in store.string("oc.contextUsage.alpha")!!)
        // A fresh owner (a restart) reads what an earlier install left.
        val seeded = SessionTestDisk(mapOf(FlutterPrefsCodec.KEY_PREFIX + "oc.contextUsage.beta" to RawPref.Str("{\"s9\":0.5}")))
        assertEquals(0.5, ContextUsageMemory(fresh(seeded), WriterLane("beta"), ProfileId("beta")).usageOf(SessionId("s9")))
    }

    @Test
    fun `B-STATE-012 null forgets and nonsense is not kept`() = runTest {
        val memory = ContextUsageMemory(fresh(), WriterLane("alpha"), alpha)
        memory.remember(SessionId("s1"), 0.9)
        memory.remember(SessionId("s1"), null)
        assertNull(memory.usageOf(SessionId("s1")))
        memory.remember(SessionId("s2"), Double.NaN)
        assertNull(memory.usageOf(SessionId("s2")))
        memory.remember(SessionId("s3"), 7.0)
        assertEquals(1.0, memory.usageOf(SessionId("s3")))
    }

    @Test
    fun `B-STATE-012 keeps the newest 200 sessions`() = runTest {
        val memory = ContextUsageMemory(fresh(), WriterLane("alpha"), alpha)
        repeat(ContextUsageMemory.MAX_ENTRIES + 5) { memory.remember(SessionId("s$it"), 0.5) }
        assertNull(memory.usageOf(SessionId("s0")))
        assertNull(memory.usageOf(SessionId("s4")))
        assertEquals(0.5, memory.usageOf(SessionId("s5")))
        assertEquals(0.5, memory.usageOf(SessionId("s204")))
    }

    @Test
    fun `B-STATE-012 the profile sweep finds the key and a late write cannot undo it`() = runTest {
        val store = fresh()
        val lane = WriterLane("alpha")
        val memory = ContextUsageMemory(store, lane, alpha)
        memory.remember(SessionId("s1"), 0.9)
        val key = ContextUsageMemory.keyFor(alpha)
        val doomed = ProfileKeyRule.scopedKeys("alpha", store.keys("oc."), emptySet())
        assertTrue(key in doomed)
        // The deletion: close admission (memory and lane), drain, sweep on the app lane.
        memory.close()
        lane.stopAdmission()
        store.commit(WriterLane("app")) { doomed.forEach(::remove) }
        assertNull(store.get(key))
        // The deleted server's chat still open: nothing comes back.
        memory.remember(SessionId("s2"), 0.95)
        assertFalse(key in store.keys("oc."))
        assertNull(memory.usageOf(SessionId("s2")))
    }
}
