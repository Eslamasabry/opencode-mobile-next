package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.BackgroundState
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Settings goldens on the desktop (Skiko software raster), light/dark × en plus dark ar, at 412 × 915 dp (the top of
 * each page). Feature modules have no Robolectric host (plan 14 §3: feature captures belong in `:ui:screenshots`,
 * which does not exist yet). Record: `./gradlew :feature:settings:desktopTest -Poc.recordGoldens=true --tests
 * '*SettingsGoldensTest*'`. Files: `src/desktopTest/goldens/<scene>-<variant>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String)

    private val variants =
        listOf(Variant("light-en", false, "en"), Variant("dark-en", true, "en"), Variant("dark-ar", true, "ar"))
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun scene(scene: String, state: SettingsUi) {
        for (variant in variants) {
            // The words follow the JVM locale, as the app's locale applier sets it on a device.
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runComposeUiTest {
                setContent { SettingsThemed(dark = variant.dark, locale = variant.locale) { SettingsScreen(state, RecordingActions()) } }
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
        assertTrue(golden.width == image.width && golden.height == image.height, "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}")
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

    private fun on(page: SettingsPage, ui: SettingsUi = SettingsUi()): SettingsUi =
        ui.copy(stack = persistentListOf(SettingsPage.Hub, page))

    @Test
    fun `B-NAV-068 the hub with its five groups and the server line`() =
        scene(
            "hub",
            SettingsUi(hub = HubUi(groups = SettingsGroupId.entries.toImmutableList(), server = ServerLine("laptop", onPhone = false))),
        )

    @Test
    fun `SCREENS-NAV-91 appearance with the most used packs, the styles and motion`() =
        scene(
            "appearance",
            on(
                SettingsPage.Appearance,
                SettingsUi(
                    appearance =
                        AppearanceUi(
                            mode = AppearanceMode.Dark, pack = PackId.Nord, packs = Packs.mostUsed.toImmutableList(), seeAllCount = 31,
                            style = SurfaceStyle.Soft, motion = MotionLevel.Calm,
                        ),
                ),
            ),
        )

    @Test
    fun `plan 07 I05 notifications blocked, with quiet hours and the background row`() =
        scene(
            "notifications",
            on(
                SettingsPage.Notifications,
                SettingsUi(
                    notifications =
                        NotificationFacts(blocked = true, quietHours = QuietHours(22 * 60, 8 * 60), background = BackgroundState.Running),
                ),
            ),
        )

    @Test
    fun `plan 07 I20 an update is ready`() =
        scene(
            "updates",
            on(SettingsPage.Updates, SettingsUi(updates = UpdateFacts("3.0.0", "10001", state = UpdateState.Available("3.1.0", "41")))),
        )

    @Test
    fun `B-SEC-191 what we collect with the detailed-usage switch`() =
        scene(
            "what-we-collect",
            on(SettingsPage.WhatWeCollect, SettingsUi(privacy = PrivacyUi(WhatWeCollectSource.build(ConsentState(), RemoteSink)))),
        )

    private companion object {
        const val MAX_SHARE = 0.005
        const val CHANNEL_SLACK = 24
    }
}
