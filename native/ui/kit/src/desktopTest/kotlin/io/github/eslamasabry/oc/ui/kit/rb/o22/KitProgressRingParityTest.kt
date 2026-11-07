package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.kit.state.KitRingFigure
import io.github.eslamasabry.oc.ui.kit.state.kitRingFraction
import io.github.eslamasabry.oc.ui.kit.state.kitRingPercent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ring's maths as kit_progress_ring.dart at 8302de537 (`fraction`, the floored percent). */
class KitProgressRingParityTest {
    @Test fun measured_fraction_clamps_and_reads_nan_as_zero() {
        assertEquals(.64, kitRingFraction(KitRingFigure.Measured(.64)))
        assertEquals(1.0, kitRingFraction(KitRingFigure.Measured(1.7)))
        assertEquals(0.0, kitRingFraction(KitRingFigure.Measured(-.2)))
        assertEquals(0.0, kitRingFraction(KitRingFigure.Measured(Double.NaN)))
    }

    @Test fun steps_fraction_is_done_of_all_and_zero_without_steps() {
        assertEquals(.6, kitRingFraction(KitRingFigure.Steps(3, 5)), 1e-12)
        assertEquals(0.0, kitRingFraction(KitRingFigure.Steps(3, 0)))
        assertEquals(1.0, kitRingFraction(KitRingFigure.Steps(9, 5)))
    }

    @Test fun finished_is_a_full_ring() {
        assertEquals(1.0, kitRingFraction(KitRingFigure.Finished))
    }

    @Test fun percent_floors_so_it_never_claims_more_than_arrived() {
        assertEquals(64, kitRingPercent(.64))
        assertEquals(99, kitRingPercent(.999))
        assertEquals(100, kitRingPercent(1.0))
        assertEquals(0, kitRingPercent(0.0))
        assertEquals(29, kitRingPercent(.29)) // .29 * 100 is 28.999…; the 1e-9 nudge keeps it 29
    }
}

