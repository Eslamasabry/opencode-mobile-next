@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
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
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class PolishRenders {
    companion object {
        const val PHASE = "before"
    }

    @Test
    fun `render kit polish scenes`() {
        val dir = File("build/polish-render").also { it.mkdirs() }
        runDesktopComposeUiTest(360, 80) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        Column(
                            Modifier.width(360.dp)
                                .background(LocalKitColors.current.ground)
                                .padding(vertical = 24.dp),
                        ) {
                            KitLoadingBar(loading = true, label = "Loading")
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                File(dir, "584-$PHASE-loading-bar-360-1.0-light.png"),
            )
        }
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
                            Box(Modifier.size(360.dp, 160.dp)) {
                                KitScreen(
                                    page = true,
                                    overlayChrome = true,
                                    floating = true,
                                    frost = rememberKitFrostState(),
                                    edges = KitScreenEdges.Bleed,
                                    topBar = {
                                        KitTopBar(
                                            title = "Fix flaky checkout test",
                                            subtitle = "shopfront",
                                            exit = KitTopBarExit.Back,
                                            exitLabel = "Back",
                                            onExit = {},
                                            backBesideTitle = true,
                                        )
                                    },
                                ) {
                                    Spacer(Modifier.fillMaxSize().height(160.dp))
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                File(dir, "584-$PHASE-glass-light-header-360-1.0-light.png"),
            )
        }
    }
}
