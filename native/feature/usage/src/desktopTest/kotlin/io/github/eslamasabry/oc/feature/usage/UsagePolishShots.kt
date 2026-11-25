package io.github.eslamasabry.oc.feature.usage

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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageProject
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageScope
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTools
import io.github.eslamasabry.oc.feature.usage.presentation.UsageUi
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

private const val PHASE = "before"

/**
 * Polish sweep, Usage: #16 the Cost page with its spend card (B-559) and #29 the Limits page (B-572) at 360 dp, light
 * and dark, text 1.0 and 1.3, English and Arabic, in `build/polish-render/`. Not a check: a person compares the
 * before and after pictures.
 */
@OptIn(ExperimentalTestApi::class)
class UsagePolishShots {
    private data class Variant(val dark: Boolean, val locale: String, val scale: Float) {
        val name: String
            get() = "${if (dark) "dark" else "light"}-$locale-$scale"
    }

    private val variants =
        listOf(false, true).flatMap { dark ->
            listOf("en", "ar").flatMap { locale -> listOf(1f, 1.3f).map { scale -> Variant(dark, locale, scale) } }
        }

    private object NoActions : UsageActions {
        override fun back() = Unit

        override fun refresh() = Unit

        override fun chooseRange(range: UsageRange) = Unit

        override fun chooseScope(scope: UsageScope) = Unit

        override fun chooseServer(id: ProfileId) = Unit

        override fun openServers() = Unit

        override fun editBudget(unit: UsageBudgetUnit) = Unit

        override fun budgetChanged(value: String) = Unit

        override fun saveBudget() = Unit

        override fun removeBudget() = Unit

        override fun closeBudget() = Unit

        override fun clearBudgets() = Unit
    }

    private object NoRefresh : LimitsActions {
        override fun refresh() = Unit
    }

    private val home = ProfileId("home")

    private fun ready() =
        UsageUi(
            servers = persistentListOf(UsageServer(home, "Home")),
            server = home,
            phase = UsagePhase.Ready,
            report =
                UsageReport(
                    fromMs = 1_761_955_200_000,
                    toMs = 1_762_084_800_001,
                    sessions = 12,
                    subagents = 2,
                    prompts = 40,
                    steps = 120,
                    activeDays = 3,
                    streak = 2,
                    cost = 4.5,
                    tokens = UsageTokens(input = 12_000, output = 3_000),
                    tools = UsageTools(calls = 10, succeeded = 6, failed = 2, unfinished = 2),
                    models =
                        persistentListOf(
                            UsageModel("anthropic", "claude-x", null, 100, UsageTokens(input = 12_000), 4.5)
                        ),
                    activity = persistentListOf(UsageDay("2026-11-01", 80), UsageDay("2026-11-02", 40)),
                ),
            timezone = "UTC",
            projects = persistentListOf(UsageProject("prj_app", "app")),
        )

    @Composable
    private fun Themed(dark: Boolean, locale: String, scale: Float, content: @Composable () -> Unit) {
        KitTheme(
            appearance =
                KitAppearance(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                ),
            platform = DesktopKitPlatform(),
            localeTag = locale,
            stillFrame = true,
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalKitRedactor provides Redactor(),
            ) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    private fun shot(out: File, name: String, content: @Composable () -> Unit) {
        runDesktopComposeUiTest(360, 2600) {
            setContent { content() }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `16 cost and 29 limits`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        val before = Locale.getDefault()
        try {
            for (v in variants) {
                Locale.setDefault(Locale.forLanguageTag(v.locale))
                shot(out, "16-$PHASE-cost-${v.name}") {
                    Themed(v.dark, v.locale, v.scale) { UsageScreen(ready(), NoActions) }
                }
                shot(out, "29-$PHASE-limits-${v.name}") {
                    Themed(v.dark, v.locale, v.scale) { LimitsContent(LimitsFixtures.state(), NoRefresh) }
                }
            }
        } finally {
            Locale.setDefault(before)
        }
    }
}
