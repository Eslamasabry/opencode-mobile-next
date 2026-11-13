package io.github.eslamasabry.oc.engine.delivery

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoppedTurnMemoryTest {
    @Test
    fun `a stop survives a restart and a new turn clears it`() = runTest {
        val disk = Disk()
        val first = World(disk, this)
        val memory = StoppedTurnMemory(first.kv, first.lanes.app, P1, backgroundScope)
        memory.add(REF)
        memory.settled()

        val after = World(disk, this)
        val reopened = StoppedTurnMemory(after.kv, after.lanes.app, P1, backgroundScope)
        assertEquals(setOf(REF), reopened.refs.value)

        reopened.remove(REF)
        reopened.settled()
        val again = World(disk, this)
        assertTrue(StoppedTurnMemory(again.kv, again.lanes.app, P1, backgroundScope).refs.value.isEmpty())
    }
}
