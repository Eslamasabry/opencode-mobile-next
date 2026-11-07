package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dart `KitFloatingAction` (lib/ui/kit/kit_floating_action.dart): placement, the disabled state and the Solid face. */
@OptIn(ExperimentalTestApi::class)
class KitFabTest {
    @Test
    fun `B-KIT-107 the floating action sits on the gutter, space 3 above the bottom, over its list`() =
        runDesktopComposeUiTest(360, 320) {
            var taps = 0
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    Box(Modifier.size(360.dp, 320.dp)) {
                        KitFloatingAction(label = "New conversation", onClick = { taps++ }) { Box(Modifier.fillMaxSize()) }
                    }
                }
            }
            val b = onNodeWithTag("kit-fab").getBoundsInRoot()
            assertEquals(360.dp - 16.dp, b.right) // gutter, kit_floating_action.dart:184
            assertEquals(320.dp - 12.dp, b.bottom) // space 3, kit_floating_action.dart:185
            assertEquals(56.dp, b.right - b.left)
            onNodeWithContentDescription("New conversation").performClick()
            assertEquals(1, taps)
        }

    @Test
    fun `B-KIT-107 with no action the floating action is not floated, and the list stays`() =
        runDesktopComposeUiTest(360, 320) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitFloatingAction(label = "New conversation", onClick = null) { Box(Modifier.fillMaxSize()) }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-fab").assertCountEquals(0)
        }

    @Test
    fun `B-KIT-107 under Solid the round face casts no shadow (its golden is the bare 56 dp disc)`() =
        runDesktopComposeUiTest(200, 200) {
            var ground = 0
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = "en",
                ) {
                    ground = LocalKitColors.current.ground.toArgb()
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        KitFloatingAction(label = "New conversation", onClick = {}) { Box(Modifier.fillMaxSize()) }
                    }
                }
            }
            waitForIdle()
            val img = onNode(isRoot()).captureToImage().toAwtImage()
            // The disc spans x 128..184, y 132..188; just under and beside it the page keeps its own colour.
            assertEquals(ground, img.getRGB(156, 191), "no shadow under the disc")
            assertEquals(ground, img.getRGB(126, 160), "no shadow beside the disc")
        }
}
