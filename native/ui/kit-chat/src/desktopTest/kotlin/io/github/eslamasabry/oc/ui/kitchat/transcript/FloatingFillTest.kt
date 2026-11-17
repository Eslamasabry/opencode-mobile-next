@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFloatingFill
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * B-438 (owner 2026-11-17, dark): the parts that float over a chat (the header capsule, the message box and what rides in
 * the box) are one surface step away from the content bubbles and never the same fill, in every pack, style and theme;
 * the box's own controls are one step away from the box.
 */
class FloatingFillTest {
    @Composable
    private fun Probe(sink: (KitColors) -> Unit) {
        sink(LocalKitColors.current)
    }

    @Test
    fun `B-CHAT-EDGE-017 the floating fill is never the bubble fill, and the box's controls are never the box's fill, in any pack, style and theme`() {
        for (pack in PackId.entries) for (style in Style.entries) for (dark in listOf(true, false)) {
            var colors: KitColors? = null
            runDesktopComposeUiTest(200, 200) {
                setContent {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = style, pack = pack, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) { Probe { colors = it } }
                }
                waitForIdle()
            }
            val c = colors!!
            val floating = kitFloatingFill(c)
            assertNotEquals(kitPromptBubbleFill(c), floating, "the floating fill equals the bubble fill ($pack, $style, dark=$dark)")
            val control = if (c.dark) c.surface2 else c.surface3
            assertNotEquals(control, floating, "the box's controls equal the box's fill ($pack, $style, dark=$dark)")
        }
    }
}
