package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideItem
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideNext
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** kit_guide.dart's rules: chips per row, stacking, pinning, dots, tones, labels and the next bar's contract. */
class KitGuideParityTest {
    @Test fun `B-KIT-GD-001 start chips go two by two and one from 1_6x`() {
        assertEquals(2, KitGuideRules.startPerRow(1f))
        assertEquals(2, KitGuideRules.startPerRow(1.5f))
        assertEquals(1, KitGuideRules.startPerRow(1.6f))
    }

    @Test fun `B-KIT-GD-002 a group's questions stack from 1_3x and so does the next bar`() {
        assertFalse(KitGuideRules.questionsStacked(1.29f))
        assertTrue(KitGuideRules.questionsStacked(1.3f))
        assertFalse(KitGuideRules.nextStacked(1f))
        assertTrue(KitGuideRules.nextStacked(2f))
    }

    @Test fun `B-KIT-GD-003 the rail stays on top only at ordinary text in a tall window`() {
        assertTrue(KitGuideRules.railPinned(1f, 820f))
        assertFalse(KitGuideRules.railPinned(1f, 400f))
        assertFalse(KitGuideRules.railPinned(2f, 820f))
    }

    @Test fun `B-KIT-GD-004 crumb dots light up to the open question`() {
        assertTrue(KitGuideRules.dotLit(0, 1))
        assertTrue(KitGuideRules.dotLit(1, 1))
        assertFalse(KitGuideRules.dotLit(2, 1))
        assertFalse(KitGuideRules.dotLit(0, -1))
    }

    @Test fun `B-KIT-GD-005 held reads quieter, chosen reads primary`() {
        val item = KitGuideItem("a", "Background", "Off in Saver")
        assertEquals(TextTone.Tertiary, KitGuideRules.valueTone(item.copy(held = true), selected = true))
        assertEquals(TextTone.Primary, KitGuideRules.valueTone(item, selected = true))
        assertEquals(TextTone.Secondary, KitGuideRules.valueTone(item, selected = false))
    }

    @Test fun `B-KIT-GD-006 a chip says its label and value unless given its own words`() {
        val item = KitGuideItem("theme", "Theme", "Nord")
        assertEquals("Theme, Nord", KitGuideRules.chipLabel(item))
        assertEquals("Theme, Nord, question 2 of 3", KitGuideRules.chipLabel(item.copy(semanticsLabel = "Theme, Nord, question 2 of 3")))
    }

    @Test fun `B-KIT-GD-007 a next step without an action is refused`() {
        assertFailsWith<IllegalArgumentException> { KitGuideNext(count = "3 of 10", label = "Next: Theme") }
        KitGuideNext(count = "3 of 10")
        KitGuideNext(count = "3 of 10", label = "Next: Theme", onPressed = {}, ready = true)
    }
}
