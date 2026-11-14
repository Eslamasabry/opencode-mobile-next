@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cut audit 2, finding 10 (a phone on its side, Solid light): the clock was drawn over the rail's chosen lens, and the
 * home's large title, chips and notice left the list half a row. The rail floats inside the system's room on its side
 * (status bar above, navigation bar under, a cutout at its start); in a short window the page heading drops to the
 * title size on one line.
 */
class KitShortWindowTest {
    private val destinations =
        listOf(
            KitNavDestination("chats", "Conversations", KitIcons.chat),
            KitNavDestination("settings", "Settings", KitIcons.settings),
        )

    @Test
    fun `B-KIT-SHORT-101 the rail keeps out of the status bar, the navigation bar and a cutout at its side`() = runDesktopComposeUiTest(914, 411) {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(914, 411) {
                    Row {
                        KitNavRail(
                            destinations,
                            selectedId = "chats",
                            onSelect = {},
                            modifier = Modifier.fillMaxHeight(),
                            insets = WindowInsets(left = 30.dp, top = 24.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        val rail = onNodeWithTag("kit-nav-rail").getBoundsInRoot()
        assertEquals(30.dp, rail.left, "the rail starts after the cutout")
        // The tagged part is the rail's inside, a step (8 dp) in from its pane's top and bottom.
        assertEquals(24.dp + 8.dp, rail.top, "the rail's pane starts under the status bar, so the clock is never drawn over the lens")
        assertEquals(411.dp - 16.dp - 8.dp, rail.bottom, "the rail's pane ends above the navigation bar")
    }

    @Test
    fun `B-KIT-SHORT-102 in a short window the page heading is the title size on one line`() = runComposeUiTest {
        var height by mutableIntStateOf(915)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(914, height) { KitPageHeading("Conversations") }
            }
        }
        waitForIdle()
        val tall = onNodeWithTag("kit-page-heading").getBoundsInRoot().let { it.bottom - it.top }
        runOnIdle { height = 411 }
        waitForIdle()
        val short = onNodeWithTag("kit-page-heading").getBoundsInRoot().let { it.bottom - it.top }
        assertTrue(short < tall, "the short window's heading ($short) is lower than the tall one's ($tall)")
    }
}
