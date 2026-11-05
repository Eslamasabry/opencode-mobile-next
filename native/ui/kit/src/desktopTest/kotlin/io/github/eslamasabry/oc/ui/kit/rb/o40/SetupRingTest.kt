package io.github.eslamasabry.oc.ui.kit.rb.o40

import io.github.eslamasabry.oc.ui.kit.setup.ringFraction
import kotlin.test.Test
import kotlin.test.assertEquals

class SetupRingTest {
    @Test fun `a stage without a measurement uses completed steps`() {
        assertEquals(.5f, ringFraction(null, 3, 6, false))
        assertEquals(0f, ringFraction(null, 0, 0, false))
    }
    @Test fun `measurements are finite and bounded and completion is explicit`() {
        assertEquals(0f, ringFraction(Float.NaN, 0, 6, false))
        assertEquals(1f, ringFraction(1.4f, 0, 6, false))
        assertEquals(1f, ringFraction(null, 0, 6, true))
    }
}
