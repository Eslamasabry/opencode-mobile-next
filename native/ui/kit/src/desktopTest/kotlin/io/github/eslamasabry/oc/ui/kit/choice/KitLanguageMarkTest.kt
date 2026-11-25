package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitLanguageMarkTest {
    private fun near(a: Color, b: Color, tol: Float = 0.12f): Boolean =
        abs(a.red - b.red) < tol && abs(a.green - b.green) < tol && abs(a.blue - b.blue) < tol

    private fun boxAccentCount(px: PixelMap, bounds: Rect, accent: Color): Int {
        val left = bounds.left.roundToInt().coerceIn(0, px.width - 1)
        val right = bounds.right.roundToInt().coerceIn(0, px.width - 1)
        val top = bounds.top.roundToInt().coerceIn(0, px.height - 1)
        val bottom = bounds.bottom.roundToInt().coerceIn(0, px.height - 1)
        var count = 0
        for (x in left..right) {
            for (y in top..bottom) {
                if (near(px[x, y], accent)) count++
            }
        }
        return count
    }

    @Test
    fun `B-545 chosen language flag mark draws no accent ring`() =
        runDesktopComposeUiTest(360, 260) {
            var accent = Color.Unspecified
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    val colors = LocalKitColors.current
                    accent = colors.accent
                    val choices =
                        listOf(
                            KitChoice(
                                "en",
                                "English",
                                leading = {
                                    KitLanguageMark(
                                        KitLanguageMark.Flag("US", "Aa"),
                                        Modifier.testTag("lang-flag-chosen"),
                                    )
                                },
                            ),
                            KitChoice(
                                "ar",
                                "Arabic",
                                leading = {
                                    KitLanguageMark(
                                        KitLanguageMark.Flag("EG", "أب"),
                                        Modifier.testTag("lang-flag-unchosen"),
                                    )
                                },
                            ),
                        )
                    Column(Modifier.fillMaxSize().background(colors.ground)) {
                        KitChoiceList(choices, selected = "en", semanticsLabel = "Language", onSelect = {})
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-choice-tick", useUnmergedTree = true).assertCountEquals(1)
            val px = onRoot().captureToImage().toPixelMap()
            val flagNode = onNodeWithTag("lang-flag-chosen", useUnmergedTree = true).fetchSemanticsNode()
            val ringPx = boxAccentCount(px, flagNode.boundsInRoot, accent)
            assertEquals(0, ringPx, "chosen language flag must draw no accent ring (found $ringPx accent px)")
        }

    @Test
    fun `B-545 chosen language glyph and fallback letters draw no second selection mark`() =
        runDesktopComposeUiTest(240, 200) {
            var accent = Color.Unspecified
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    val colors = LocalKitColors.current
                    accent = colors.accent
                    Column(
                        Modifier.fillMaxSize().background(colors.ground).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KitLanguageMark(
                            KitLanguageMark.Glyph(KitIcons.phone),
                            Modifier.testTag("lang-glyph-chosen"),
                        )
                        KitLanguageMark(
                            KitLanguageMark.Flag("ZZ", "Zz"),
                            Modifier.testTag("lang-letters-chosen"),
                        )
                    }
                }
            }
            waitForIdle()
            val px = onRoot().captureToImage().toPixelMap()
            val glyphNode = onNodeWithTag("lang-glyph-chosen", useUnmergedTree = true).fetchSemanticsNode()
            val glyphAccent = boxAccentCount(px, glyphNode.boundsInRoot, accent)
            assertEquals(0, glyphAccent, "chosen language glyph must draw no accent ring or accent tint")
            val lettersNode = onNodeWithTag("lang-letters-chosen", useUnmergedTree = true).fetchSemanticsNode()
            val area = (lettersNode.boundsInRoot.width * lettersNode.boundsInRoot.height).roundToInt()
            val lettersAccent = boxAccentCount(px, lettersNode.boundsInRoot, accent)
            assertTrue(
                lettersAccent < area / 3,
                "chosen fallback letters must draw no solid accent disc ($lettersAccent/$area)",
            )
        }
}
