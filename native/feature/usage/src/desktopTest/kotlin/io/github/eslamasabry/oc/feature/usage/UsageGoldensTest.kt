package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageUi
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.feature.usage.ui.UsageContent
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.collections.immutable.persistentListOf

/**
 * Usage › Limits and Cost goldens (B-159, B-160) on the desktop: light and dark, text at 1.0 and 1.3, a 360 dp phone,
 * and Arabic right to left. Record: `./gradlew :feature:usage:desktopTest -Poc.recordGoldens=true --tests
 * '*UsageGoldensTest*'`, then look at every picture before committing it. A missing golden is recorded and the test
 * fails once, so a picture nobody looked at can never pass.
 */
@OptIn(ExperimentalTestApi::class)
class UsageGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String, val fontScale: Float)

    private val variants =
        listOf(
            Variant("light-en-1.0", false, "en", 1.0f),
            Variant("dark-en-1.0", true, "en", 1.0f),
            Variant("light-en-1.3", false, "en", 1.3f),
            Variant("dark-ar-1.0", true, "ar", 1.0f),
            Variant("light-ar-1.3", false, "ar", 1.3f),
        )
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun scene(scene: String, height: Int, content: @Composable () -> Unit) {
        for (variant in variants) {
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runComposeUiTest {
                setContent {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = variant.locale,
                        stillFrame = true,
                    ) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                            Box(
                                Modifier.width(360.dp).height(height.dp).background(LocalKitColors.current.ground)
                            ) {
                                content()
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                compare("$scene-${variant.name}")
            }
        }
    }

    private fun ComposeUiTest.compare(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(
            golden.width == image.width && golden.height == image.height,
            "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}",
        )
        val share = differingPixels(golden, image).toDouble() / (image.width * image.height)
        if (share > MAX_SHARE) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ (limit ${MAX_SHARE * 100} %); actual: $actual")
        }
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            if ((0..24 step 8).any { s -> abs((p shr s and 0xFF) - (q shr s and 0xFF)) > CHANNEL_SLACK }) count++
        }
        return count
    }

    private val noLimitActions = object : LimitsActions { override fun refresh() = Unit }

    private val noUsageActions =
        object : UsageActions {
            override fun back() = Unit
            override fun refresh() = Unit
            override fun chooseRange(range: UsageRange) = Unit
            override fun chooseScope(scope: io.github.eslamasabry.oc.feature.usage.presentation.UsageScope) = Unit
            override fun chooseServer(id: ProfileId) = Unit
            override fun editBudget(unit: UsageBudgetUnit) = Unit
            override fun budgetChanged(value: String) = Unit
            override fun saveBudget() = Unit
            override fun removeBudget() = Unit
            override fun closeBudget() = Unit
            override fun clearBudgets() = Unit
            override fun openServers() = Unit
        }

    private val home = ProfileId("home")

    private fun cost(range: UsageRange): UsageUi {
        val days = (1..7).map { UsageDay("2026-11-0$it", listOf(12L, 40, 8, 0, 64, 30, 22)[it - 1]) }
        val report =
            UsageReport(
                fromMs = 1_793_491_200_000, // 2026-11-01T00:00Z
                toMs = 1_794_052_800_001, // 2026-11-07T12:00Z
                sessions = 31,
                subagents = 4,
                prompts = 88,
                steps = 176,
                activeDays = 6,
                streak = 3,
                cost = 12.84,
                tokens = UsageTokens(input = 410_000, output = 96_000, reasoning = 21_000, cacheRead = 2_300_000, cacheWrite = 180_000),
                tools = null,
                models =
                    persistentListOf(
                        UsageModel("anthropic", "claude-sonnet", null, 120, UsageTokens(input = 300_000, cacheRead = 2_000_000), 9.6),
                        UsageModel("openai", "gpt-5", "high", 56, UsageTokens(input = 110_000, cacheRead = 300_000), 3.24),
                    ),
                activity = persistentListOf(*days.toTypedArray()),
            )
        return UsageUi(
            servers = persistentListOf(UsageServer(home, "Home")),
            server = home,
            range = range,
            phase = UsagePhase.Ready,
            report = report,
            timezone = "UTC",
        )
    }

    @Test
    fun `B-159 limits - headline windows, compact model limits, no-limits provider and stale age`() =
        scene("limits", 1_500) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                LimitsContent(LimitsFixtures.state(), noLimitActions)
            }
        }

    @Test
    fun `B-160 cost - 7 day segment, total, daily chart and provider split`() =
        scene("cost-7d", 2_200) { UsageContent(cost(UsageRange.SevenDays), noUsageActions) }

    private companion object {
        const val CHANNEL_SLACK = 24
        const val MAX_SHARE = 0.002
    }
}
