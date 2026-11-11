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

/** The shown time is built from the steps' own sizes, so it counts down and never sits still while steps finish. */
class SetupEtaCountdownTest {
    private val registry =
        listOf(
            SetupComponent("a", "A", "A", "", "", estimatedSeconds = 120),
            SetupComponent("b", "B", "B", "", "", estimatedSeconds = 120),
            SetupComponent("c", "C", "C", "", "", estimatedSeconds = 120),
        )

    private fun at(states: List<ComponentState>, onStep: Int, pace: Int? = 400, fraction: Double? = null) =
        SetupProgress(
            state = SetupState.Running,
            components =
                states.mapIndexed { i, s ->
                    ComponentProgress(
                        "abc"[i].toString(),
                        s,
                        percent = if (s == ComponentState.Running) fraction?.times(100) else null,
                    )
                },
            overall = 0.3,
            etaSeconds = pace,
            stepElapsedSeconds = onStep,
        )

    @Test
    fun `it goes down as seconds pass and as steps complete`() {
        val m = JourneyProgressMemory()
        val run = ComponentState.Running
        val wait = ComponentState.Pending
        val done = ComponentState.Done
        val first = m.eta(at(listOf(run, wait, wait), 10), registry)!!
        val later = m.eta(at(listOf(run, wait, wait), 70), registry)!!
        val next = m.eta(at(listOf(done, run, wait), 5), registry)!!
        val last = m.eta(at(listOf(done, done, run), 100), registry)!!
        assertEquals(350, first)
        assertEquals(290, later)
        assertEquals(235, next)
        assertEquals(20, last)
    }

    @Test
    fun `a pace guess that stays high does not hold the number still`() {
        val m = JourneyProgressMemory()
        val a = m.eta(at(listOf(ComponentState.Running, ComponentState.Pending, ComponentState.Pending), 10, pace = 120), registry)!!
        val b = m.eta(at(listOf(ComponentState.Done, ComponentState.Running, ComponentState.Pending), 10, pace = 900), registry)!!
        assert(b < a)
    }

    @Test
    fun `a measured fraction shortens the running step`() {
        val m = JourneyProgressMemory()
        val s = listOf(ComponentState.Running, ComponentState.Pending, ComponentState.Pending)
        assertEquals(240 + 60, m.eta(at(s, 10, fraction = 0.5), registry))
    }

    @Test
    fun `nothing is shown before the first guess and nothing when a step has no size`() {
        val m = JourneyProgressMemory()
        val s = listOf(ComponentState.Running, ComponentState.Pending, ComponentState.Pending)
        assertNull(m.eta(at(s, 3, pace = null), registry))
        assertNull(JourneyProgressMemory().eta(at(s, 3), registry.take(2)))
    }
}
