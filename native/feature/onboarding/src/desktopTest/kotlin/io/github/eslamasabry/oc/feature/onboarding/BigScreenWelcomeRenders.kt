package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.domain.capabilities.DeviceClass
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/** Picture only, asserts nothing: first-run welcome across phone, split, portrait and wide tablet sizes. */
@OptIn(ExperimentalTestApi::class)
class BigScreenWelcomeRenders {
    private object NoActions : WelcomeActions {
        override fun connect() {}

        override fun openSettings() {}
    }

    private class Shot(val key: String, val w: Int, val h: Int, val scale: Float, val suffix: String)

    private val shots =
        listOf(
            Shot("phone", 360, 780, 1f, ""),
            Shot("phone", 360, 780, 1.3f, "-t13"),
            Shot("phone412", 412, 915, 1f, ""),
            Shot("split", 600, 800, 1f, ""),
            Shot("tabp", 800, 1280, 1f, ""),
            Shot("exp840", 840, 800, 1f, ""),
            Shot("tab1024", 1024, 768, 1f, ""),
            Shot("tabl", 1280, 800, 1f, ""),
            Shot("tabl", 1280, 800, 1.3f, "-t13"),
        )

    private val tabletShots =
        listOf(
            Shot("tabp", 800, 1280, 1f, ""),
            Shot("exp840", 840, 800, 1f, ""),
            Shot("tab1024", 1024, 768, 1f, ""),
            Shot("tabl", 1280, 800, 1f, ""),
            Shot("tabl", 1280, 800, 1.3f, "-t13"),
        )

    private val out = File("build/bigscreen").also { it.mkdirs() }

    private fun shoot(name: String, list: List<Shot> = shots, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (s in list) {
            runCatching {
                runDesktopComposeUiTest(s.w * 2, s.h * 2) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2f, s.scale)) {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    content()
                                }
                            }
                        }
                    }
                    waitForIdle()
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-${s.key}${s.suffix}.png"))
                }
            }.onFailure { println("RENDERFAIL $name ${s.key}${s.suffix} ${it::class.simpleName}: ${it.message?.take(200)}") }
        }
    }

    @Test
    fun render() {
        val phone = PhoneWelcomeUi(available = true, seconds = 240, bytes = 208_000_000)
        shoot("welcome-phone") {
            WelcomeScreen(WelcomeUi(phone, WelcomePlace.Phone), NoActions, deviceClass = DeviceClass.Phone)
        }
        shoot("welcome-tablet", tabletShots) {
            WelcomeScreen(WelcomeUi(phone, WelcomePlace.Phone), NoActions, deviceClass = DeviceClass.Tablet)
        }
    }
}
