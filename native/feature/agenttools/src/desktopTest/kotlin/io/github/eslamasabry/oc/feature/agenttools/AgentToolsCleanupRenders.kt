package io.github.eslamasabry.oc.feature.agenttools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agenttools.ui.AgentToolsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val PHASE = "after"
private const val WIDTH_DP = 360
private const val HEIGHT_DP = 800
private const val DENSITY = 2.625f

/** B-482 before/after 360 dp review renders for Agent tools (light & dark, text 1.0 & 1.3). */
@OptIn(ExperimentalTestApi::class)
class AgentToolsCleanupRenders {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `B-482 renders agent tools at 360 dp`() {
        val out = File("build/renders/agent-tools-page-cleanup").also(File::mkdirs)
        val modes = listOf(AppearanceMode.Light to "light", AppearanceMode.Dark to "dark")
        val scales = listOf(1.0f to "t10", 1.3f to "t13")
        for ((mode, modeName) in modes) {
            for ((scale, scaleTag) in scales) {
                shoot(File(out, "$PHASE-$modeName-$scaleTag.png"), mode, scale, scrollBottom = false)
            }
        }
        shoot(File(out, "$PHASE-dark-t10-lower.png"), AppearanceMode.Dark, 1.0f, scrollBottom = true)
        shoot(File(out, "$PHASE-dark-t13-lower.png"), AppearanceMode.Dark, 1.3f, scrollBottom = true)
    }

    private fun shoot(file: File, mode: AppearanceMode, fontScale: Float, scrollBottom: Boolean) {
        runDesktopComposeUiTest((WIDTH_DP * DENSITY).toInt(), (HEIGHT_DP * DENSITY).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(
                            LocalKitWindow provides KitWindowInfo(WIDTH_DP.toFloat(), HEIGHT_DP.toFloat())
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                AgentToolsScreen(
                                    AgentToolsTestFixture.fullState,
                                    AgentToolsTestFixture.RecordingActions(),
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            if (scrollBottom) {
                onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Refresh agent tools"))
                waitForIdle()
            }
            val image = onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage()
            ImageIO.write(image, "png", file)
            assertTrue(file.isFile, "expected render file ${file.name}")
        }
    }
}
