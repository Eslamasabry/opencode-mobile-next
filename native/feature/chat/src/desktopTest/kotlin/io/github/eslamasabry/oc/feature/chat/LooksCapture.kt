package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/** Capture-only: writes PNGs of a screen in light and dark at 412 x 915 dp, density 2.625 (1081 x 2402 px). No comparison. */
@OptIn(ExperimentalTestApi::class)
internal object Looks {
    private val out = File("/home/eslam/wt/lane-looks/looks-out/screens").also { it.mkdirs() }

    fun shoot(name: String, advanceMs: Long = 1_000, setup: ComposeUiTest.() -> Unit = {}, content: @Composable (dark: Boolean) -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(1081, 2402) {
                setContent {
                  CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Calm),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content(dark) }
                    }
                  }
                }
                waitForIdle()
                mainClock.advanceTimeBy(advanceMs)
                waitForIdle()
                setup()
                waitForIdle()
                val image = onRoot().captureToImage().toAwtImage()
                ImageIO.write(image, "png", File(out, "$name-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
