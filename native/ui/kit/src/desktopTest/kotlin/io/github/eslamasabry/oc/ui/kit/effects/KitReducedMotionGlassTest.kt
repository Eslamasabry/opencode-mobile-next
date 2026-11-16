@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassFrostAvailable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * glass-truth 2026-11-16, item 3: the system's reduced motion (motion Off) used to drop Glass's lens, its clear panes and
 * even the frost blur, so the panes sat opaque on the ground. A still pane needs no animation: under motion Off the
 * material stays as chosen, while what moves (the touch swell) stays still. Battery saver keeps its hold.
 */
class KitReducedMotionGlassTest {
    private data class Seen(val lens: Boolean, val clear: Boolean, val frost: Boolean, val swells: Boolean)

    private fun seen(motion: MotionLevel, mode: AppearanceMode = AppearanceMode.Dark): Seen {
        var out: Seen? = null
        runComposeUiTest {
            setContent {
                KitTheme(
                    KitAppearance(style = Style.Glass, mode = mode, motion = motion),
                    FakeKitPlatform(frost = true, liquidGlass = true),
                    "en",
                    stillFrame = true,
                ) {
                    val press = KitLiquidPress()
                    out = Seen(kitLiquidGlassOn(), kitClearPanesOn(), kitGlassFrostAvailable(), Modifier.kitLiquidSwell(press) !== Modifier)
                }
            }
            waitForIdle()
        }
        return out!!
    }

    @Test
    fun `GLASS-TRUTH-3 under reduced motion the glass stays, only what moves is still`() {
        assertEquals(Seen(lens = true, clear = true, frost = true, swells = true), seen(MotionLevel.Full))
        assertEquals(Seen(lens = true, clear = true, frost = true, swells = false), seen(MotionLevel.Off))
    }

    @Test
    fun `GLASS-TRUTH-3 battery saver still holds the glass`() {
        val saver = seen(MotionLevel.Full, AppearanceMode.Saver)
        assertEquals(false, saver.lens)
        assertEquals(false, saver.clear)
    }
}
