package io.github.eslamasabry.oc.feature.settings

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
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.BackgroundState
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

private const val PHASE = "after"

/**
 * Polish sweep, Settings: #39 the blocked Notifications page (B-582) and #22 the Colours tab's empty Theme step
 * (B-565) at 360 dp, light and dark, text 1.0 and 1.3, in `build/polish-render/`. Not a check: a person compares
 * the before and after pictures.
 */
@OptIn(ExperimentalTestApi::class)
class PolishSettingsShots {
    private data class Variant(val dark: Boolean, val scale: Float) {
        val name: String
            get() = "${if (dark) "dark" else "light"}-en-$scale"
    }

    private val variants =
        listOf(false, true).flatMap { dark -> listOf(1f, 1.3f).map { scale -> Variant(dark, scale) } }

    /** [SettingsThemed]'s set-up at the picture's whole size, so one picture holds the whole page. */
    @Composable
    private fun Themed(v: Variant, locale: String = "en", height: Float = 2600f, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, v.scale)) {
            KitTheme(
                appearance =
                    KitAppearance(
                        mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                        motion = MotionLevel.Off,
                    ),
                platform = DesktopKitPlatform(),
                localeTag = locale,
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalKitWindow provides KitWindowInfo(360f, height),
                ) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    private val blocked =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Notifications),
            notifications =
                NotificationFacts(
                    blocked = true,
                    quietHours = QuietHours(22 * 60, 8 * 60),
                    background = BackgroundState.Running,
                ),
        )

    private fun emptyThemes(dark: Boolean) =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    guide = AppearanceGuideRecord(seen = listOf(AppearanceQuestion.Mode)),
                    question = AppearanceQuestion.Theme,
                    questions =
                        listOf(
                            AppearanceQuestion.Mode,
                            AppearanceQuestion.Theme,
                            AppearanceQuestion.Text,
                            AppearanceQuestion.Motion,
                        ),
                ),
        )

    private val whatWeCollect =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Privacy, SettingsPage.WhatWeCollect),
            privacy =
                PrivacyUi(
                    WhatWeCollectSource.build(ConsentState(), RemoteSink),
                    crashReports = false,
                    usage = false,
                ),
        )

    private val updates =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Updates),
            updates = UpdateFacts("3.0.0", "10001", state = UpdateState.Available("3.1.0", "41")),
        )

    private fun shot(out: File, name: String, height: Int = 2600, content: @Composable () -> Unit) {
        runDesktopComposeUiTest(360, height) {
            setContent { content() }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `39 blocked notifications and 22 empty theme step`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        val before = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            for (v in variants) {
                shot(out, "39-$PHASE-notifications-${v.name}") {
                    Themed(v) { SettingsScreen(blocked, RecordingActions()) }
                }
                shot(out, "22-$PHASE-theme-${v.name}") {
                    Themed(v) { SettingsScreen(emptyThemes(v.dark), RecordingActions()) }
                }
                shot(out, "23-$PHASE-collect-${v.name}") {
                    Themed(v) { SettingsScreen(whatWeCollect, RecordingActions()) }
                }
                shot(out, "23-$PHASE-updates-${v.name}") {
                    Themed(v) { SettingsScreen(updates, RecordingActions()) }
                }
                shot(out, "43-$PHASE-wizard-${v.name}", height = 915) {
                    Themed(v, height = 915f) { SettingsScreen(emptyThemes(v.dark), RecordingActions()) }
                }
            }
        } finally {
            Locale.setDefault(before)
        }
    }
}
