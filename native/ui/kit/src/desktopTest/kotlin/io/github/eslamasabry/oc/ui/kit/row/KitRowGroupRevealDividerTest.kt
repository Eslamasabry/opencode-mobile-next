package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A row group's divider belongs to the row it separates: when a `KitReveal` row leaves or enters, its divider fades
 * and collapses with it, while staying rows keep their dividers still and resting groups draw unchanged.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowGroupRevealDividerTest {
    private val width = 412
    private val height = 600

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KitTheme(
                KitAppearance(mode = AppearanceMode.Light, style = Style.Solid, motion = MotionLevel.Full),
                FakeKitPlatform(),
                "en",
                stillFrame = true,
            ) {
                InWindow(width, height) { content() }
            }
        }
    }

    private fun maxDelta(map: PixelMap, ground: Color, x: Int, yRange: IntRange): Float =
        yRange
            .filter { it in 0 until height }
            .maxOfOrNull { y ->
                val p = map[x, y]
                abs(p.red - ground.red) + abs(p.green - ground.green) + abs(p.blue - ground.blue)
            }
            ?: 0f

    private fun ComposeUiTest.bottomPx(tag: String): Int =
        onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value.toInt()

    private fun ComposeUiTest.topPx(tag: String): Int =
        onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value.toInt()

    @Test
    fun `B-KIT-R10-032 mid-fold no divider is drawn without its leaving row`() =
        runDesktopComposeUiTest(width, height) {
            var ground = Color.Unspecified
            var shown by mutableStateOf(true)
            setContent {
                Themed {
                    ground = LocalKitColors.current.ground
                    Column(Modifier.fillMaxSize().background(ground)) {
                        KitRowGroup {
                            KitReveal(visible = shown) {
                                KitRow("Alpha", Modifier.testTag("r0"), supporting = "First line")
                            }
                            KitReveal(visible = shown) {
                                KitRow("Beta", Modifier.testTag("r1"), supporting = "Second line")
                            }
                            KitRow("Gamma", Modifier.testTag("r2"), supporting = "Third line")
                            KitRow("Delta", Modifier.testTag("r3"), supporting = "Fourth line")
                        }
                    }
                }
            }
            waitForIdle()
            val x = width - 40
            val rest = onRoot().captureToImage().toPixelMap()
            val fullDelta = maxDelta(rest, ground, x, bottomPx("r2") - 1..bottomPx("r2") + 1)
            assertTrue(fullDelta > 0.02f, "expected visible hairline at rest (delta=$fullDelta)")
            assertTrue(maxDelta(rest, ground, x, bottomPx("r0") - 1..bottomPx("r0") + 1) > fullDelta * 0.9f)
            assertTrue(maxDelta(rest, ground, x, bottomPx("r1") - 1..bottomPx("r1") + 1) > fullDelta * 0.9f)

            mainClock.autoAdvance = false
            shown = false
            val start = mainClock.currentTime
            var checkedMidFold = 0
            repeat(30) {
                mainClock.advanceTimeByFrame()
                val elapsed = mainClock.currentTime - start
                val gammaTop = topPx("r2")
                if (elapsed >= MotionTokens.QUICK / 2 && gammaTop > 4) {
                    checkedMidFold++
                    val map = onRoot().captureToImage().toPixelMap()
                    val aboveGamma = maxDelta(map, ground, x, 0..gammaTop + 1)
                    val limit = if (elapsed > MotionTokens.QUICK) 0.01f else fullDelta * 0.35f
                    assertTrue(
                        aboveGamma <= limit,
                        "divider above Gamma not faded at elapsed=${elapsed}ms (delta=$aboveGamma, full=$fullDelta)",
                    )
                    val staying = maxDelta(map, ground, x, bottomPx("r2") - 1..bottomPx("r2") + 1)
                    assertTrue(
                        staying >= fullDelta * 0.9f,
                        "staying Gamma-Delta divider changed at elapsed=${elapsed}ms (delta=$staying, full=$fullDelta)",
                    )
                }
            }
            assertTrue(checkedMidFold > 0, "expected at least one mid-fold frame during collapse")
        }

    @Test
    fun `B-KIT-R10-033 middle leaving row fades its divider while staying neighbour keeps one`() =
        runDesktopComposeUiTest(width, height) {
            var ground = Color.Unspecified
            var shown by mutableStateOf(true)
            setContent {
                Themed {
                    ground = LocalKitColors.current.ground
                    Column(Modifier.fillMaxSize().background(ground)) {
                        KitRowGroup {
                            KitRow("Top", Modifier.testTag("r0"), supporting = "First line")
                            KitReveal(visible = shown) {
                                KitRow("Middle", Modifier.testTag("r1"), supporting = "Second line")
                            }
                            KitRow("Bottom", Modifier.testTag("r2"), supporting = "Third line")
                        }
                    }
                }
            }
            waitForIdle()
            val x = width - 40
            val rest = onRoot().captureToImage().toPixelMap()
            val fullDelta = maxDelta(rest, ground, x, bottomPx("r0") - 1..bottomPx("r0") + 1)
            assertTrue(fullDelta > 0.02f, "expected visible hairline at rest (delta=$fullDelta)")

            mainClock.autoAdvance = false
            shown = false
            val start = mainClock.currentTime
            var checkedMidFold = 0
            repeat(30) {
                mainClock.advanceTimeByFrame()
                val elapsed = mainClock.currentTime - start
                val topBottom = bottomPx("r0")
                val bottomTop = topPx("r2")
                if (elapsed >= MotionTokens.QUICK / 2 && bottomTop > topBottom + 4) {
                    checkedMidFold++
                    val map = onRoot().captureToImage().toPixelMap()
                    val leaving = maxDelta(map, ground, x, topBottom - 1..topBottom + 1)
                    val limit = if (elapsed > MotionTokens.QUICK) 0.01f else fullDelta * 0.35f
                    assertTrue(
                        leaving <= limit,
                        "leaving Middle's divider not faded at elapsed=${elapsed}ms (delta=$leaving, full=$fullDelta)",
                    )
                    val staying = maxDelta(map, ground, x, bottomTop - 1..bottomTop + 1)
                    assertTrue(
                        staying >= fullDelta * 0.9f,
                        "staying Bottom's divider changed at elapsed=${elapsed}ms (delta=$staying, full=$fullDelta)",
                    )
                }
            }
            assertTrue(checkedMidFold > 0, "expected at least one mid-fold frame during collapse")
        }

    @Test
    fun `B-KIT-R10-034 at rest KitRowGroup dividers draw unchanged`() =
        runDesktopComposeUiTest(width, height) {
            var ground = Color.Unspecified
            setContent {
                Themed {
                    ground = LocalKitColors.current.ground
                    Column(Modifier.fillMaxSize().background(ground)) {
                        KitRowGroup {
                            KitRow("Alpha", Modifier.testTag("r0"))
                            KitRow("Beta", Modifier.testTag("r1"))
                            KitRow("Gamma", Modifier.testTag("r2"))
                        }
                    }
                }
            }
            waitForIdle()
            val x = width - 40
            val map = onRoot().captureToImage().toPixelMap()
            assertTrue(maxDelta(map, ground, x, bottomPx("r0") - 1..bottomPx("r0") + 1) > 0.02f, "Alpha-Beta divider")
            assertTrue(maxDelta(map, ground, x, bottomPx("r1") - 1..bottomPx("r1") + 1) > 0.02f, "Beta-Gamma divider")
        }
}
