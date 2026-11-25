@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
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
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val STAGE = "before"

class PolishMessageBoxRenderTest {
    @Test
    fun `render Stop and approval shield scenes`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        val modes = listOf(AppearanceMode.Dark to "dark", AppearanceMode.Light to "light")
        val scales = listOf(1.0f to "1.0", 1.3f to "1.3")
        for ((mode, modeName) in modes) {
            for ((scale, scaleName) in scales) {
                renderStop(out, mode, modeName, scale, scaleName)
                renderShield(out, mode, modeName, scale, scaleName)
            }
        }
    }

    private fun renderStop(
        out: File,
        mode: AppearanceMode,
        modeName: String,
        fontScale: Float,
        scaleName: String,
    ) {
        runDesktopComposeUiTest(360, 240) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        Box(
                            Modifier.size(360.dp, 240.dp)
                                .background(LocalKitColors.current.ground)
                                .padding(16.dp),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    KitComposerStop("Stop", "Stop", {}, state = KitStopState.Running)
                                    KitComposerStop("Stop", "Stop", {}, state = KitStopState.Stuck)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    KitComposerStop("Stopping", "Stopping", {}, state = KitStopState.Working)
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        KitComposerStop("Stop", "Stop", {}, state = KitStopState.Running)
                                        KitComposerStop("Stop", "Stop", {}, state = KitStopState.Stuck)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val file = File(out, "7-$STAGE-stop-$modeName-$scaleName.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
    }

    private fun renderShield(
        out: File,
        mode: AppearanceMode,
        modeName: String,
        fontScale: Float,
        scaleName: String,
    ) {
        runDesktopComposeUiTest(360, 180) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        Box(
                            Modifier.size(360.dp, 180.dp)
                                .background(LocalKitColors.current.ground)
                                .padding(16.dp),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                KitComposerChip(
                                    label = "Auto-approve on",
                                    onClick = {},
                                    icon = KitIcons.shield,
                                    opensPicker = false,
                                    active = true,
                                    wordless = true,
                                    semanticsLabel = "Approval",
                                )
                                KitComposerChip(
                                    label = "Auto-approve on",
                                    onClick = {},
                                    icon = KitIcons.shield,
                                    opensPicker = false,
                                    active = true,
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val file = File(out, "40-$STAGE-shield-$modeName-$scaleName.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
    }
}
