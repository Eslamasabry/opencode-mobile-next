package io.github.eslamasabry.oc.engine.runtime.services.kept

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-147 (run-5 upgrade gate): a plain start adds no files to the built-in Linux. */
class KeptServiceSupervisorTest {
    @Test
    fun `B-147 with no kept service on and no supervisor running nothing is written into Linux`() {
        assertFalse(KeptServiceSupervisor.hasWork(running = false, anyWanted = false))
    }

    @Test
    fun `B-147 a kept service that is on, or a running supervisor, still gets its supervisor`() {
        assertTrue(KeptServiceSupervisor.hasWork(running = false, anyWanted = true))
        assertTrue(KeptServiceSupervisor.hasWork(running = true, anyWanted = false))
        assertTrue(KeptServiceSupervisor.hasWork(running = true, anyWanted = true))
    }
}
