package io.github.eslamasabry.oc.engine.agents.providers

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Issue 147: a saved, changed or removed own agent reaches the helper at once when idle, never under work. */
class HelperReloadTest {
    private class Helper(var running: Boolean, var busy: Boolean, var stopRefused: Boolean = false) {
        val steps = mutableListOf<String>()
        val reload =
            HelperReload(
                running = { running },
                busy = { busy },
                stopGuarded = { steps += "stop"; !stopRefused },
                start = { steps += "start" },
            )
    }

    @Test
    fun `B-AGENT-OWN-080 an idle running helper is stopped guarded and started again with the change`() = runTest {
        val helper = Helper(running = true, busy = false)
        assertTrue(helper.reload.tryNow())
        assertEquals(listOf("stop", "start"), helper.steps)
    }

    @Test
    fun `B-AGENT-OWN-081 a busy helper is left alone until idle, a refused stop starts nothing, a stopped helper needs nothing`() =
        runTest {
            val busy = Helper(running = true, busy = true)
            assertFalse(busy.reload.tryNow())
            assertTrue(busy.steps.isEmpty())
            busy.busy = false
            assertTrue(busy.reload.tryNow())
            assertEquals(listOf("stop", "start"), busy.steps)

            val refused = Helper(running = true, busy = false, stopRefused = true)
            assertFalse(refused.reload.tryNow())
            assertEquals(listOf("stop"), refused.steps)

            val stopped = Helper(running = false, busy = true)
            assertTrue(stopped.reload.tryNow())
            assertTrue(stopped.steps.isEmpty())
        }
}
