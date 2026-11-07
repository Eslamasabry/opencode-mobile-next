package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import io.github.eslamasabry.oc.ui.kit.state.KitWindowTone
import io.github.eslamasabry.oc.ui.kit.state.fill
import io.github.eslamasabry.oc.ui.kit.state.valueTone
import io.github.eslamasabry.oc.ui.kit.state.windowRowLabel
import io.github.eslamasabry.oc.ui.kit.state.windowRowStacks
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_window_row.dart at 8302de537: tone colours, value tone, stacking and the spoken label. */
@OptIn(ExperimentalTestApi::class)
class KitWindowRowTest {
    @Test
    fun `B-KIT-230 each tone fills the bar with Flutter's role`() {
        val c = Graphite.dark.toKitColors()
        assertEquals(c.accent, KitWindowTone.Normal.fill(c))
        assertEquals(c.text1, KitWindowTone.Low.fill(c))
        assertEquals(c.text1, KitWindowTone.Out.fill(c))
        assertEquals(c.text3, KitWindowTone.Calm.fill(c))
    }

    @Test
    fun `B-KIT-231 the value is strong only when low or out`() {
        assertEquals(TextTone.Secondary, KitWindowTone.Normal.valueTone())
        assertEquals(TextTone.Primary, KitWindowTone.Low.valueTone())
        assertEquals(TextTone.Primary, KitWindowTone.Out.valueTone())
        assertEquals(TextTone.Secondary, KitWindowTone.Calm.valueTone())
    }

    @Test
    fun `B-KIT-232 the inline line stacks from 1·3x text`() {
        assertFalse(windowRowStacks(1f))
        assertFalse(windowRowStacks(1.29f))
        assertTrue(windowRowStacks(1.3f))
        assertTrue(windowRowStacks(2f))
    }

    @Test
    fun `B-KIT-233 the row reads as one label with the note last`() {
        assertEquals("Week, 18% left, resets Thu", windowRowLabel("Week", "18% left, resets Thu", null))
        assertEquals("Week, 18% left · Thu, Resets in 14 h", windowRowLabel("Week", "18% left · Thu", "Resets in 14 h"))
    }

    @Test
    fun `B-KIT-234 a screen reader hears title value and note on one node`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitWindowRow("Week", .18f, "18% left · Thu", "18% left, resets Thu", note = "Resets in 14 h", tone = KitWindowTone.Low)
            }
        }
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Week, 18% left, resets Thu, Resets in 14 h")))
            .assertCountEquals(1)
    }
}
