package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.ui.JourneyProgressMemory
import io.github.eslamasabry.oc.feature.phonesetup.ui.journeyMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The "time left" never grows, is dropped when a step is far past its own estimate, and survives re-entry. */
class SetupEtaHonestyTest {
    private val registry =
        listOf(
            SetupComponent("a", "A", "A", "", "", estimatedSeconds = 60),
            SetupComponent("b", "B", "B", "", "", estimatedSeconds = 60),
        )

    private fun running(eta: Int?, onStep: Int) =
        SetupProgress(
            state = SetupState.Running,
            components =
                listOf(
                    ComponentProgress("a", ComponentState.Running),
                    ComponentProgress("b", ComponentState.Pending),
                ),
            overall = 0.2,
            etaSeconds = eta,
            stepElapsedSeconds = onStep,
        )

    @Test
    fun `the time left only goes down`() {
        val memory = JourneyProgressMemory()
        assertEquals(180, memory.eta(running(180, 20), registry))
        val later = memory.eta(running(360, 40), registry)
        assertNotNull(later)
        assertEquals(180, later)
    }

    @Test
    fun `a step far past its estimate shows no minutes`() {
        val memory = JourneyProgressMemory()
        assertNotNull(memory.eta(running(180, 20), registry))
        assertNull(memory.eta(running(180, 400), registry))
    }

    @Test
    fun `the memory of a job is the same one when the page is opened again`() {
        assertSame(journeyMemory("job-1"), journeyMemory("job-1"))
    }
}
