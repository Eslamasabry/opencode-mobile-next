@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PolishLoadingBarTest {
    private class LensPlatform : KitPlatform by FakeKitPlatform(frost = true, liquidGlass = true) {
        override fun liquidPane(
            spec: KitLiquidPaneSpec,
            backdrop: KitLiquidBackdrop,
            shape: CornerBasedShape,
            transform: () -> Float,
        ): Modifier = Modifier.clip(shape).drawWithCache { onDrawBehind { drawRect(spec.fill) } }
    }

    @Test
    fun `B-584 still loading bar spans the width`() =
        runDesktopComposeUiTest(360, 20) {
            var accent = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        accent = LocalKitColors.current.accent
                        Box(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                            KitLoadingBar(loading = true, label = "Loading")
                        }
                    }
                }
            }
            waitForIdle()
            val pixels = onRoot().captureToImage().toPixelMap()
            val x = (pixels.width * 0.9f).toInt()
            val c = pixels[x, 1]
            val diff = maxOf(abs(c.red - accent.red), abs(c.green - accent.green), abs(c.blue - accent.blue))
            assertTrue(diff < 0.08f, "pixel at 90 % width (x=$x) must be accent $accent, was $c")
        }

    @Test
    fun `B-584 still bar has no partial dash`() {
        val (runs300, spans300) = accentRunLengths(300)
        assertTrue(runs300.isNotEmpty(), "expected accent dashes at 300 dp")
        assertTrue(runs300.all { abs(it - 8) <= 1 }, "300 dp dashes must be 8 dp ± 1 px, got $runs300")
        assertTrue(spans300, "300 dp dashes must span both edges without a trailing gap: $runs300")
        val (runs292, spans292) = accentRunLengths(292)
        assertTrue(
            runs292.all { abs(it - 8) <= 1 },
            "292 dp dashes must be 8 dp ± 1 px (no partial dash), got $runs292",
        )
        assertTrue(spans292, "292 dp dashes must span both edges: $runs292")
    }

    private fun colorDiff(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private fun isAccent(c: Color, accent: Color, track: Color): Boolean =
        colorDiff(c, accent) < colorDiff(c, track)

    private fun accentRunLengths(widthDp: Int): Pair<List<Int>, Boolean> {
        var runs = emptyList<Int>()
        var spans = false
        runDesktopComposeUiTest(widthDp, 20) {
            var accent = Color.Unspecified
            var track = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        accent = LocalKitColors.current.accent
                        track = LocalKitColors.current.surface3
                        Box(Modifier.width(widthDp.dp).background(LocalKitColors.current.ground)) {
                            KitLoadingBar(loading = true, label = "Loading")
                        }
                    }
                }
            }
            waitForIdle()
            val pixels = onRoot().captureToImage().toPixelMap()
            val found = mutableListOf<Int>()
            var current = 0
            for (x in 0 until pixels.width) {
                if (isAccent(pixels[x, 1], accent, track)) {
                    current++
                } else if (current > 0) {
                    found += current
                    current = 0
                }
            }
            if (current > 0) found += current
            runs = found
            spans = isAccent(pixels[0, 1], accent, track) && isAccent(pixels[pixels.width - 1, 1], accent, track)
        }
        return runs to spans
    }

    @Test
    fun `B-561 KitTopBar without Back preserves Owner 8 layout`() =
        runDesktopComposeUiTest(360, 160) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 160f)) {
                            KitTopBar(
                                title = "Conversations",
                                subtitle = "This phone",
                                exit = KitTopBarExit.None,
                                backBesideTitle = true,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-top-bar-compact", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `B-561 Glass floatChrome keeps base bar width`() =
        runDesktopComposeUiTest(360, 240) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        LensPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 240f)) {
                            Box(Modifier.size(360.dp, 240.dp)) {
                                KitScreen(
                                    page = true,
                                    overlayChrome = true,
                                    floating = true,
                                    frost = rememberKitFrostState(),
                                    edges = KitScreenEdges.Bleed,
                                    topBar = {
                                        KitTopBar(
                                            title = "Chat",
                                            exit = KitTopBarExit.Back,
                                            exitLabel = "Back",
                                            onExit = {},
                                            backBesideTitle = true,
                                        )
                                    },
                                ) {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bar = onNodeWithTag("kit-top-bar", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(bar.left <= 5.dp, "Glass floatChrome must keep base bar width (no 14 dp pad), left=${bar.left}")
        }

    @Test
    fun `B-584 Glass light bar has no crisp 1 dp hairline`() =
        runDesktopComposeUiTest(360, 160) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        LensPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 160f)) {
                            Box(Modifier.size(360.dp, 160.dp).background(LocalKitColors.current.ground)) {
                                KitScreen(
                                    page = true,
                                    overlayChrome = true,
                                    floating = true,
                                    frost = rememberKitFrostState(),
                                    edges = KitScreenEdges.Bleed,
                                    topBar = {
                                        KitTopBar(
                                            title = "Chat",
                                            exit = KitTopBarExit.Back,
                                            exitLabel = "Back",
                                            onExit = {},
                                            backBesideTitle = true,
                                        )
                                    },
                                ) {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bar = onNodeWithTag("kit-liquid-bar", useUnmergedTree = true).getBoundsInRoot()
            val pixels = onRoot().captureToImage().toPixelMap()
            val x = ((bar.left + bar.right).value / 2f).toInt()
            val topY = bar.top.value.toInt()
            val diff = colorDiff(pixels[x, topY], pixels[x, topY + 4])
            assertTrue(diff < 0.01f, "Glass light bar must have no 1 dp crisp hairline (Owner #62), diff=$diff")
        }

    @Test
    fun `B-561 no-Back top bar keeps subtitle gap and bottom`() =
        runDesktopComposeUiTest(360, 160) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Solid, mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 160f)) {
                            KitTopBar(
                                title = "Conversations",
                                subtitle = "This phone",
                                exit = KitTopBarExit.None,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val bar = onNodeWithTag("kit-top-bar", useUnmergedTree = true).getBoundsInRoot()
            val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getBoundsInRoot()
            val sub = onNodeWithText("This phone", useUnmergedTree = true).getBoundsInRoot()
            val gap = (sub.top - title.bottom).value
            assertTrue(gap >= 0f, "no-Back subtitle stays under the title (base layout, Owner #8), was $gap dp")
            assertTrue(
                bar.bottom >= sub.bottom - 0.5.dp,
                "no-Back bar bottom (${bar.bottom}) must not be above subtitle bottom (${sub.bottom})",
            )
        }
}
