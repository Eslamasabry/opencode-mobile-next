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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
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
    fun `B-561 usable title width is equal in Solid and Glass`() {
        fun measureTitleWidth(style: Style): Float {
            var width = 0f
            runDesktopComposeUiTest(360, 240) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            KitAppearance(style = style, mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            if (style == Style.Glass) LensPlatform() else FakeKitPlatform(),
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
                                                title = "A very long conversation title that trims identically",
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
                val bounds = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getBoundsInRoot()
                width = (bounds.right - bounds.left).value
            }
            return width
        }
        val solid = measureTitleWidth(Style.Solid)
        val glass = measureTitleWidth(Style.Glass)
        assertTrue(abs(solid - glass) <= 1f, "title width must match in Solid ($solid) and Glass ($glass)")
    }
}
