package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.collectJourney
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Proof pictures of Settings › Privacy and data › What we collect at 360 dp, text 1.0 and 1.3, scrolled to the
 * `collectJourney` row so the owner-approved first-message ending line is visible and readable. Writes PNGs to
 * `WHAT_WE_COLLECT_OUT`, `~/render-out/what-we-collect-journey` on the picture server, or
 * `build/what-we-collect-journey`; skips on GitHub Actions.
 */
@OptIn(ExperimentalTestApi::class)
class WhatWeCollectRenders {
    private val state =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.WhatWeCollect),
            privacy =
                PrivacyUi(
                    WhatWeCollectSource.build(ConsentState(), RemoteSink),
                    crashReports = false,
                    usage = false,
                ),
        )

    private fun shoot(out: File, mode: AppearanceMode, locale: String, scale: Float, name: String) {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(locale))
        try {
            runDesktopComposeUiTest(WIDTH_PX, HEIGHT_PX) {
                var journeyText = ""
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
                        KitTheme(
                            appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = locale,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitRedactor provides Redactor(),
                                LocalKitWindow provides KitWindowInfo(WIDTH_DP, HEIGHT_DP),
                            ) {
                                journeyText = stringResource(Res.string.collectJourney)
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    SettingsScreen(state, RecordingActions())
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                onNodeWithText(journeyText, useUnmergedTree = true).performScrollTo()
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `what we collect renders at 360dp`() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val out =
            (System.getenv("WHAT_WE_COLLECT_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("what-we-collect-journey")
                ?: File("build/what-we-collect-journey"))
                .also(File::mkdirs)
        for ((scale, label) in listOf(SCALE_DEFAULT to "t10", SCALE_LARGE to "t13")) {
            shoot(out, AppearanceMode.Light, "en", scale, "what-we-collect-en-light-$label")
            shoot(out, AppearanceMode.Dark, "en", scale, "what-we-collect-en-dark-$label")
            shoot(out, AppearanceMode.Dark, "ar", scale, "what-we-collect-ar-dark-$label")
        }
    }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_DP = 360f
        const val HEIGHT_DP = 800f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 2100
        const val SCALE_DEFAULT = 1.0f
        const val SCALE_LARGE = 1.3f
    }
}
