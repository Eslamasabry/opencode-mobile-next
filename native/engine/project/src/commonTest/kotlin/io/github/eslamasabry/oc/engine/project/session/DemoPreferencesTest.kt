package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoPreferencesTest {
    @Test
    fun `B-STATE-014 demo preferences are independent maps with defensive list copies`() = runTest {
        val first = DemoPreferences()
        val second = DemoPreferences()
        val lane = WriterLane("demo")
        val values = mutableListOf("saved")
        first.commit(lane) { putStringList("example", values) }
        values += "external edit"
        (first.stringList("example") as MutableList<String>).add("read edit")
        assertEquals(listOf("saved"), first.stringList("example"))
        assertTrue(second.keys().isEmpty())
        assertNull(first.get("oc.profiles"))
        assertNull(first.get("oc.activeProfile"))
        assertEquals("offline-demo", DemoProfile.profiles().single().raw)
        first.clear()
        assertTrue(first.keys().isEmpty())
    }
}
