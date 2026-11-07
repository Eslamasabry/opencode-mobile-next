package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.kit.choice.KitFineTuneKnob
import io.github.eslamasabry.oc.ui.kit.choice.KitFineTuneRules
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** kit_fine_tune_panel.dart: a held knob says why; the reset shows only while a knob differs; Saver hides the knobs. */
class KitFineTunePanelParityTest {
    private val segs = listOf(KitSegment("a", "A"), KitSegment("b", "B"))

    @Test fun `B-KIT-FT-001 a held knob without a reason is refused`() {
        assertFailsWith<IllegalArgumentException> {
            KitFineTuneKnob("Edges", segs, "a", "Default: A", onChanged = null)
        }
        KitFineTuneKnob("Edges", segs, "a", "Default: A", onChanged = null, disabledReason = "High contrast")
        KitFineTuneKnob("Edges", segs, "a", "Default: A", onChanged = {})
    }

    @Test fun `B-KIT-FT-002 the reset shows only while a reset is offered and Saver is off`() {
        assertTrue(KitFineTuneRules.resetShown({}, null))
        assertFalse(KitFineTuneRules.resetShown(null, null))
        assertFalse(KitFineTuneRules.resetShown({}, "Saver keeps surfaces solid"))
    }

    @Test fun `B-KIT-FT-003 a disabled reason replaces the knobs`() {
        assertTrue(KitFineTuneRules.knobsShown(null))
        assertFalse(KitFineTuneRules.knobsShown("Saver keeps surfaces solid"))
    }
}
