package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.weight
import io.github.eslamasabry.oc.ui.kit.modal.ConfirmRules
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B-402: haptics keep their meaning. The strength order lives in common code so no phone is needed to pin it. */
class HapticMeaningTest {
    @Test
    fun `B-402 the strength order is Select then Send then the consequential Commit`() {
        assertTrue(KitHaptic.Select.weight < KitHaptic.Send.weight, "a selection tick is lighter than a send")
        assertTrue(KitHaptic.Send.weight < KitHaptic.Commit.weight, "a send is lighter than the consequential confirmation")
        assertTrue(KitHaptic.Attention.weight <= KitHaptic.Select.weight, "the edge light stays the lightest")
        assertEquals(KitHaptic.entries.maxBy { it.weight }, KitHaptic.Commit, "nothing is stronger than the consequential role")
        assertEquals(KitHaptic.entries.minBy { it.weight }.weight, KitHaptic.Attention.weight)
    }

    @Test
    fun `B-402 a destructive or stop confirm is the consequential role, a neutral one is silent`() {
        assertEquals(KitHaptic.Commit, ConfirmRules.haptic(KitConfirmKind.Stop))
        assertEquals(KitHaptic.Commit, ConfirmRules.haptic(KitConfirmKind.Destructive))
        assertEquals(null, ConfirmRules.haptic(KitConfirmKind.Neutral))
        assertNotNull(KitHaptic.Commit)
    }
}
