package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search's matched words: where they are, and that a row marks them in the accent ink with no fill behind. */
@OptIn(ExperimentalTestApi::class)
class KitHighlightTest {
    @Test
    fun `SCREENS-NAV-34 B1 each typed word is marked once, at a word start when it has one, case ignored`() {
        assertEquals(listOf(0..2), KitHighlight.ranges("Debug log", "deb"))
        assertEquals(listOf(13..15), KitHighlight.ranges("…install the Debian image, deb", "DEB"))
        assertEquals(listOf(9..9), KitHighlight.ranges("Keyboard shortcuts", "s"))
        assertEquals(listOf(0..2, 6..8, 10..12), KitHighlight.ranges("Fix login flow", "fix gin flo"))
        assertEquals(listOf(0..5), KitHighlight.ranges("debian", "deb bian"))
        assertEquals(emptyList(), KitHighlight.ranges("Settings", "  "))
        assertEquals(emptyList(), KitHighlight.ranges("Settings", "zzz"))
    }

    @Test
    fun `SCREENS-NAV-34 B1 a row marks the matched words of its title and supporting line in the accent ink`() =
        runComposeUiTest {
        var accent = Color.Unspecified
        setContent {
            val look = KitAppearance(motion = MotionLevel.Off)
            KitTheme(platform = DesktopKitPlatform(), appearance = look, stillFrame = true) {
                accent = LocalKitColors.current.accent
                Box(Modifier.width(400.dp)) {
                    KitRow("Debug log", supporting = "Help › Diagnostics debug", highlight = "deb", onClick = {})
                }
            }
        }
        fun words(tag: String) =
            onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val title = words("kit-row-title")
        assertEquals("Debug log", title.text)
        val span = title.spanStyles.single()
        assertEquals(0 to 3, span.start to span.end)
        assertEquals(accent, span.item.color)
        assertEquals(Color.Unspecified, span.item.background)
        val supporting = words("kit-row-supporting")
        assertTrue(supporting.spanStyles.any { supporting.text.substring(it.start, it.end) == "deb" })
    }
}
