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

/**
 * S31 QA shots (capture only, no comparison): the welcome at Flutter's golden size, 412 x 915 dp at density 1, so
 * it sits beside `test/goldens/servers_welcome_dark.png`. Runs only with OC_S31_SHOTS set to the output folder.
 */
@OptIn(ExperimentalTestApi::class)
class S31Shots {
    private object A : WelcomeActions {
        override fun connect() {}

        override fun openSettings() {}
    }

    private fun shoot(name: String, locale: String, content: @Composable () -> Unit) {
        val out = System.getenv("OC_S31_SHOTS")?.let(::File) ?: return
        out.mkdirs()
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(locale))
        runDesktopComposeUiTest(412, 915) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = locale,
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
        Locale.setDefault(before)
    }

    @Test
    fun welcome() {
        val phone = PhoneWelcomeUi(available = true, seconds = 240, bytes = 208_000_000)
        shoot("native-welcome-dark", "en") { WelcomeScreen(WelcomeUi(phone, WelcomePlace.Phone), A) }
        shoot("native-welcome-computer-dark", "en") { WelcomeScreen(WelcomeUi(phone, WelcomePlace.Computer), A) }
        shoot("native-welcome-dark-ar", "ar") { WelcomeScreen(WelcomeUi(phone, WelcomePlace.Phone), A) }
    }
}
