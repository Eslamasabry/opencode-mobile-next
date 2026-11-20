package io.github.eslamasabry.oc.ui.kit.motion

import kotlin.test.Test
import kotlin.test.assertEquals

/** The waiting marks' arrival curves keep the values approved when they lived in the marks' own file. */
class KitWaitEasingTest {
    private val points = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)

    @Test
    fun `B-MOT-WAIT-001 the round hand's arrival passes its mark by about a tenth, then settles`() {
        val approved = listOf(0f, 0.4087f, 0.8171875f, 1.0875f, 1.0640625f, 1.0143f, 1f)
        points.zip(approved).forEach { (x, y) -> assertEquals(y, KitEasing.WaitArrive.transform(x), 1e-4f, "at $x") }
    }

    @Test
    fun `B-MOT-WAIT-002 the square hand's arrival is the cubic ease-out and never passes its mark`() {
        val approved = listOf(0f, 0.271f, 0.578125f, 0.875f, 0.984375f, 0.999f, 1f)
        points.zip(approved).forEach { (x, y) -> assertEquals(y, KitEasing.WaitArriveSquare.transform(x), 1e-4f, "at $x") }
    }
}
