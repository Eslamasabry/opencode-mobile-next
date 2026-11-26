package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.SendingState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.CollectGroup
import io.github.eslamasabry.oc.feature.settings.presentation.CollectItem
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsFrame
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.collectWords
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.collectCrashesLabel
import io.github.eslamasabry.oc.ui.words.res.collectJourney
import io.github.eslamasabry.oc.ui.words.res.collectNever
import io.github.eslamasabry.oc.ui.words.res.collectNeverLabel
import io.github.eslamasabry.oc.ui.words.res.collectOwnLabel
import io.github.eslamasabry.oc.ui.words.res.collectOwnTogether
import io.github.eslamasabry.oc.ui.words.res.collectReportLabel
import io.github.eslamasabry.oc.ui.words.res.collectRules
import io.github.eslamasabry.oc.ui.words.res.collectTitle
import io.github.eslamasabry.oc.ui.words.res.collectUsageLabel
import io.github.eslamasabry.oc.ui.words.res.collectUsageNotSentYet
import io.github.eslamasabry.oc.ui.words.res.privacyCrashSendTitle
import io.github.eslamasabry.oc.ui.words.res.telemetryUsageTitle
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Before/after proof pictures of Settings › Privacy and data › What we collect at 360 dp, text 1.0 and 1.3,
 * scrolled to the `collectJourney` row so the owner-approved first-message ending line is visible and readable.
 * Writes all 12 PNGs to `build/renders/what-we-collect` (`native/feature/settings/build/renders/what-we-collect/`);
 * skips on GitHub Actions.
 */
@OptIn(ExperimentalTestApi::class)
class WhatWeCollectRenders {
    private data class Scene(
        val mode: AppearanceMode,
        val locale: String,
        val scale: Float,
        val name: String,
        val previousJourney: String? = null,
    )

    private val privacy =
        PrivacyUi(
            WhatWeCollectSource.build(ConsentState(), RemoteSink),
            crashReports = false,
            usage = false,
        )

    private val state =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.WhatWeCollect),
            privacy = privacy,
        )

    private fun shoot(out: File, scene: Scene) {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(scene.locale))
        try {
            runDesktopComposeUiTest(WIDTH_PX, HEIGHT_PX) {
                var journeyText = ""
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, scene.scale)) {
                        KitTheme(
                            appearance =
                                KitAppearance(mode = scene.mode, style = Style.Solid, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = scene.locale,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitRedactor provides Redactor(),
                                LocalKitWindow provides KitWindowInfo(WIDTH_DP, HEIGHT_DP),
                            ) {
                                journeyText = scene.previousJourney ?: stringResource(Res.string.collectJourney)
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    RenderPage(scene.previousJourney)
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                onNodeWithText(journeyText, useUnmergedTree = true).performScrollTo()
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "${scene.name}.png"))
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Composable
    private fun RenderPage(previousJourney: String?) {
        val actions = RecordingActions()
        if (previousJourney == null) {
            SettingsScreen(state, actions)
        } else {
            SettingsFrame(stringResource(Res.string.collectTitle), { actions.onBack() }, state, actions) {
                BeforeWhatWeCollectBody(privacy, actions, previousJourney)
            }
        }
    }

    @Composable
    private fun ColumnScope.BeforeWhatWeCollectBody(
        privacy: PrivacyUi,
        actions: SettingsActions,
        previousJourney: String,
    ) {
        val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
        if (privacy.collect?.sending != SendingState.Sending && !privacy.vendorSends) {
            KitNotice(
                stringResource(Res.string.collectUsageNotSentYet),
                gutter.testTag("collect-not-sent"),
                liveRegion = false,
            )
        }
        KitGroupNote(stringResource(Res.string.collectRules))
        BeforeUsageGroup(privacy, actions, previousJourney)
        BeforeCrashesGroup(privacy, actions)
        KitRowGroup(
            label = stringResource(Res.string.collectOwnLabel),
            modifier = Modifier.testTag("collect-own-server"),
        ) {
            HealthContract.items.getValue(CollectGroup.OwnServer).forEach {
                KitRow(title = stringResource(collectWords(it)), titleMaxLines = Int.MAX_VALUE)
            }
            KitRow(title = stringResource(Res.string.collectOwnTogether), titleMaxLines = Int.MAX_VALUE)
        }
        KitRowGroup(label = stringResource(Res.string.collectReportLabel)) {
            HealthContract.items.getValue(CollectGroup.Report).forEach {
                KitRow(title = stringResource(collectWords(it)), titleMaxLines = Int.MAX_VALUE)
            }
        }
        KitRowGroup(label = stringResource(Res.string.collectNeverLabel)) {
            KitRow(title = stringResource(Res.string.collectNever), titleMaxLines = Int.MAX_VALUE)
        }
    }

    @Composable
    private fun BeforeUsageGroup(
        privacy: PrivacyUi,
        actions: SettingsActions,
        previousJourney: String,
    ) {
        KitRowGroup(
            label = stringResource(Res.string.collectUsageLabel),
            modifier = Modifier.testTag("collect-usage"),
        ) {
            privacy.usage?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.telemetryUsageTitle),
                    checked = on,
                    onCheckedChange = actions::onUsage,
                )
            }
            HealthContract.items.getValue(CollectGroup.Usage).forEach { item ->
                val text =
                    if (item == CollectItem.Journey) previousJourney else stringResource(collectWords(item))
                KitRow(title = text, titleMaxLines = Int.MAX_VALUE)
            }
        }
    }

    @Composable
    private fun BeforeCrashesGroup(privacy: PrivacyUi, actions: SettingsActions) {
        KitRowGroup(
            label = stringResource(Res.string.collectCrashesLabel),
            modifier = Modifier.testTag("collect-crashes"),
        ) {
            privacy.crashReports?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.privacyCrashSendTitle),
                    checked = on,
                    onCheckedChange = actions::onCrashReports,
                )
            }
            HealthContract.items.getValue(CollectGroup.Crashes).forEach {
                KitRow(title = stringResource(collectWords(it)), titleMaxLines = Int.MAX_VALUE)
            }
        }
    }

    @Test
    fun `what we collect renders at 360dp`() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val out = File("build/renders/what-we-collect").also(File::mkdirs)
        val scenes =
            listOf(SCALE_DEFAULT to "t10", SCALE_LARGE to "t13").flatMap { (scale, label) ->
                listOf(
                    Scene(
                        AppearanceMode.Light,
                        "en",
                        scale,
                        "before-what-we-collect-en-light-$label",
                        PREVIOUS_JOURNEY_EN,
                    ),
                    Scene(
                        AppearanceMode.Dark,
                        "en",
                        scale,
                        "before-what-we-collect-en-dark-$label",
                        PREVIOUS_JOURNEY_EN,
                    ),
                    Scene(
                        AppearanceMode.Dark,
                        "ar",
                        scale,
                        "before-what-we-collect-ar-dark-$label",
                        PREVIOUS_JOURNEY_AR,
                    ),
                    Scene(AppearanceMode.Light, "en", scale, "after-what-we-collect-en-light-$label"),
                    Scene(AppearanceMode.Dark, "en", scale, "after-what-we-collect-en-dark-$label"),
                    Scene(AppearanceMode.Dark, "ar", scale, "after-what-we-collect-ar-dark-$label"),
                )
            }
        scenes.forEach { shoot(out, it) }
    }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_DP = 360f
        const val HEIGHT_DP = 800f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 2100
        const val SCALE_DEFAULT = 1.0f
        const val SCALE_LARGE = 1.3f
        const val PREVIOUS_JOURNEY_EN =
            "How far you got in your first steps (opened the app, set up, connected, sent a message, " +
                "got a first reply) and roughly how long after you agreed. Each step is counted once"
        const val PREVIOUS_JOURNEY_AR =
            "إلى أي مرحلة وصلت في خطواتك الأولى (فتحت التطبيق، أعددت، اتصلت، أرسلت رسالة، تلقيت أول رد) " +
                "وبعد كم من الوقت تقريباً من موافقتك. تُحسب كل خطوة مرة واحدة"
    }
}
