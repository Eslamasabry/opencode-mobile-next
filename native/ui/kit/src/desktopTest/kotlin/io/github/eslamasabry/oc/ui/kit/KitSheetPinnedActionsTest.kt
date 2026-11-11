@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** A sheet with pinned actions: scrolled to the end, its last content element lies clear above the pinned row. */
class KitSheetPinnedActionsTest {
    @Test
    fun `the last content element scrolls clear of the pinned actions`() =
        runDesktopComposeUiTest(412, 800) {
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), stillFrame = true) {
                    KitSheetHost {
                        KitSheet(
                            true,
                            {},
                            "Read?",
                            height = KitSheetHeight.Half,
                            primary = KitAction("Allow", {}),
                            secondary = KitAction("Deny", {}),
                        ) {
                            for (i in 0 until 12) Box(Modifier.height(60.dp).size(300.dp, 60.dp).testTag("row-$i"))
                            Box(Modifier.size(300.dp, 80.dp).testTag("last"))
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("last", useUnmergedTree = true).performScrollTo()
            waitForIdle()
            val last = onNodeWithTag("last", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val pinned = onNodeWithText("Allow", useUnmergedTree = true).getUnclippedBoundsInRoot()
            println("PINNED last bottom ${last.bottom} pinned top ${pinned.top}")
            assertTrue(last.bottom <= pinned.top, "the last element (bottom ${last.bottom}) reaches the pinned row (top ${pinned.top})")
        }
}
