@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A destructive [KitAction] drawn as a tertiary [KitButton] uses the kit's warning (danger) ink rather than the green
 * accent, both alone and inside [KitActionBlock]. Measured on the render at 360 dp (945 px at 2.625 px/dp).
 */
class KitDestructiveTertiaryInkTest {
    private fun check(dark: Boolean, subjectLabel: String, subject: @Composable () -> Unit) {
        runDesktopComposeUiTest(W, H) {
            var ground = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
                    KitTheme(
                        KitAppearance(mode = mode, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        ground = LocalKitColors.current.ground.copy(alpha = 1f)
                        Column(Modifier.fillMaxSize().background(ground).padding(32.dp)) {
                            subject()
                            KitButton("Stop now", {}, role = KitButtonRole.Tertiary, danger = true)
                            KitButton(KitAction("Keep going", {}), role = KitButtonRole.Tertiary)
                        }
                    }
                }
            }
            waitForIdle()
            val danger = ink("Stop now", ground)
            val plain = ink("Keep going", ground)
            val drawn = ink(subjectLabel, ground)
            assertTrue(
                levels(danger, plain) >= 24f,
                "danger and accent inks differ: $danger vs $plain",
            )
            assertTrue(
                levels(drawn, danger) <= 8f,
                "$subjectLabel is drawn in the danger ink: $drawn, expected $danger (accent is $plain)",
            )
        }
    }

    private fun ComposeUiTest.ink(label: String, ground: Color): Color {
        val bounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val map = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        var best = ground
        for (x in bounds.left.toInt() until bounds.right.toInt()) {
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                val pixel = map[x, y]
                if (contrast(pixel, ground) > contrast(best, ground)) best = pixel
            }
        }
        return best
    }

    @Test
    fun `B-KIT-011 dark - a destructive tertiary KitAction button is drawn in the danger ink`() =
        check(dark = true, subjectLabel = "Stop work") {
            KitButton(KitAction("Stop work", {}, destructive = true), role = KitButtonRole.Tertiary)
        }

    @Test
    fun `B-KIT-011 light - a destructive tertiary KitAction button is drawn in the danger ink`() =
        check(dark = false, subjectLabel = "Stop work") {
            KitButton(KitAction("Stop work", {}, destructive = true), role = KitButtonRole.Tertiary)
        }

    @Test
    fun `B-KIT-011 dark - a destructive tertiary in an action block is drawn in the danger ink`() =
        check(dark = true, subjectLabel = "Stop work") {
            KitActionBlock(
                primary = KitAction("Fix it", {}),
                tertiary = listOf(KitAction("Stop work", {}, destructive = true)),
            )
        }

    @Test
    fun `B-KIT-011 light - a destructive tertiary in an action block is drawn in the danger ink`() =
        check(dark = false, subjectLabel = "Stop work") {
            KitActionBlock(
                primary = KitAction("Fix it", {}),
                tertiary = listOf(KitAction("Stop work", {}, destructive = true)),
            )
        }

    private fun levels(a: Color, b: Color): Float =
        max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) * 255f

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + .05
        val lb = b.luminance() + .05
        return max(la, lb) / min(la, lb)
    }

    private companion object {
        const val W = 945
        const val H = 1300
        const val D = 2.625f
    }
}
