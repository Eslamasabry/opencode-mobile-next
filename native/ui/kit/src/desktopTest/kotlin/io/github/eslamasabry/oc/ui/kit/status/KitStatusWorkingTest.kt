package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A status that is a wait shows the kit's working mark: it turns when motion is on, and stands still when it is off. */
@OptIn(ExperimentalTestApi::class)
class KitStatusWorkingTest {
    private fun frames(level: MotionLevel, still: Boolean): Pair<List<Int>, List<Int>> {
        var a = emptyList<Int>()
        var b = emptyList<Int>()
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = level),
                    stillFrame = still,
                ) {
                    KitStatusLine(KitStatus(KitStatusKind.Connection, "Connecting to Claude Code…", working = true))
                }
            }
            fun grab(): List<Int> {
                val map = onNodeWithTag("kit-status-working", useUnmergedTree = true).captureToImage().toPixelMap()
                return List(map.width * map.height) { map[it % map.width, it / map.width].hashCode() }
            }
            mainClock.advanceTimeBy(100)
            a = grab()
            mainClock.advanceTimeBy(300)
            b = grab()
        }
        return a to b
    }

    @Test
    fun `CA-K1 the working mark turns when motion is on`() {
        val (a, b) = frames(MotionLevel.Full, still = false)
        assertNotEquals(a, b, "the arc turned between two frames")
    }

    @Test
    fun `CA-K2 the working mark stands still when motion is off`() {
        val (a, b) = frames(MotionLevel.Off, still = false)
        assertEquals(a, b, "a quiet static mark")
    }
}
