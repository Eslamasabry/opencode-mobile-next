@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PolishMessageBoxTest {
    @Test
    fun `B-550 a stuck Stop is an amber pill`() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            runDesktopComposeUiTest(360, 800) {
                var attention: Color? = null
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f, 1f),
                        LocalLayoutDirection provides LayoutDirection.Ltr,
                    ) {
                        KitTheme(
                            KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                            DesktopKitPlatform(),
                            "en",
                            stillFrame = true,
                        ) {
                            attention = LocalKitColors.current.attention
                            Box(Modifier.size(360.dp, 800.dp)) {
                                KitComposerStop(
                                    label = "Stop",
                                    targetLabel = "Stop Claude Code",
                                    onStop = {},
                                    state = KitStopState.Stuck,
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                val image = onNodeWithTag(STOP_TAG, useUnmergedTree = true).captureToImage().toAwtImage()
                val hits = countMatching(image, requireNotNull(attention))
                assertTrue(
                    hits >= 30,
                    "expected >= 30 attention pixels in $mode Stuck Stop, got $hits",
                )
            }
        }
    }

    @Test
    fun `B-550 a running Stop keeps a closed pill face`() {
        runDesktopComposeUiTest(360, 800) {
            var surface3: Color? = null
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        surface3 = LocalKitColors.current.surface3
                        Box(Modifier.size(360.dp, 800.dp)) {
                            KitComposerStop(
                                label = "Stop",
                                targetLabel = "Stop Claude Code",
                                onStop = {},
                                state = KitStopState.Running,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val image = onNodeWithTag(STOP_TAG, useUnmergedTree = true).captureToImage().toAwtImage()
            val total = image.width * image.height
            val hits = countMatching(image, requireNotNull(surface3))
            assertTrue(
                hits * 100 >= total * 15,
                "expected >= 15% surface3 pixels in Running Stop, got $hits / $total",
            )
        }
    }

    @Test
    fun `B-POLISH-040 an on approval chip's shield is accent with or without words`() {
        runDesktopComposeUiTest(600, 800) {
            var accent: Color? = null
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        accent = LocalKitColors.current.accent
                        Box(Modifier.size(600.dp, 800.dp)) {
                            Column {
                                KitComposerChip(
                                    label = "Auto-approve on",
                                    onClick = {},
                                    icon = KitIcons.shield,
                                    opensPicker = false,
                                    active = true,
                                    modifier = Modifier.testTag("a"),
                                )
                                KitComposerChip(
                                    label = "Auto-approve on",
                                    onClick = {},
                                    icon = KitIcons.shield,
                                    opensPicker = false,
                                    active = true,
                                    wordless = true,
                                    semanticsLabel = "Approval",
                                    modifier = Modifier.testTag("b"),
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val target = requireNotNull(accent)
            val worded =
                onNode(
                    hasTestTag(COMPOSER_CHIP_FACE_TAG) and hasAnyAncestor(hasTestTag("a")),
                    useUnmergedTree = true,
                ).captureToImage().toAwtImage()
            val wordless =
                onNode(
                    hasTestTag(COMPOSER_CHIP_FACE_TAG) and hasAnyAncestor(hasTestTag("b")),
                    useUnmergedTree = true,
                ).captureToImage().toAwtImage()
            val wordedHits = countMatching(worded, target, 12..31)
            val wordlessHits = countMatching(wordless, target)
            assertTrue(
                wordedHits >= 20,
                "expected >= 20 accent pixels in columns 12..31 of worded shield, got $wordedHits",
            )
            assertTrue(
                wordlessHits >= 20,
                "expected >= 20 accent pixels in wordless shield, got $wordlessHits",
            )
        }
    }

    private fun countMatching(
        image: BufferedImage,
        target: Color,
        xRange: IntRange = 0 until image.width,
    ): Int {
        var count = 0
        val startX = maxOf(0, xRange.first)
        val endX = minOf(xRange.last, image.width - 1)
        for (y in 0 until image.height) {
            for (x in startX..endX) {
                if (matchesColor(image.getRGB(x, y), target)) count++
            }
        }
        return count
    }

    private fun matchesColor(argb: Int, target: Color): Boolean {
        val expected = target.toArgb()
        return SHIFTS.all { shift ->
            abs(((argb ushr shift) and 0xFF) - ((expected ushr shift) and 0xFF)) <= CHANNEL_TOLERANCE
        }
    }

    private companion object {
        const val CHANNEL_TOLERANCE = 40
        val SHIFTS = listOf(24, 16, 8, 0)
    }
}
