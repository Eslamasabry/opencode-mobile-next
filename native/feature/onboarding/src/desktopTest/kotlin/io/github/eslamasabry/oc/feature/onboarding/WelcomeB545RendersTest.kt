package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WelcomeB545RendersTest {
    private val out = File("build/b545-renders").also { it.mkdirs() }

    private fun actions(calls: MutableList<String>): WelcomeActions =
        object : WelcomeActions {
            override fun connect() {
                calls += "connect"
            }

            override fun openSettings() {
                calls += "settings"
            }
        }

    @Test
    fun `B-545 welcome screen renders review picture at 360 dp`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val calls = mutableListOf<String>()
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 800f)) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    WelcomeScreen(
                                        WelcomeUi(
                                            phone = PhoneWelcomeUi(available = true, seconds = 240, bytes = 208_000_000),
                                            place = WelcomePlace.Phone,
                                        ),
                                        actions(calls),
                                    )
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                val img = onRoot().captureToImage().toAwtImage()
                ImageIO.write(img, "png", File(out, "b545-before-welcome-dark.png"))
            }
        } finally {
            Locale.setDefault(prev)
        }
    }
}
