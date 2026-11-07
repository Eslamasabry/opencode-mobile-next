package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
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
 * E19-S04: Report a problem with a crash kept and nothing described. The preview opens with a plain headline; the
 * raw exit record appears only under Diagnostics. The text has the exact shape `ProblemReport.build` produces with
 * the headline `ReportHeadlines` picks (the platform wiring resolves it). Record: `./gradlew
 * :feature:settings:desktopTest -Poc.recordGoldens=true --tests '*E19S04ReportHeadlineGoldenTest*'`.
 */
@OptIn(ExperimentalTestApi::class)
class E19S04ReportHeadlineGoldenTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String)

    private val variants = listOf(Variant("light-en", false, "en"), Variant("dark-ar", true, "ar"))
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val report =
        listOf(
                "The app closed unexpectedly",
                "(not described)\n\nEnvironment\nApp: 3.0.0+10002\nPlatform: Android 16 (API 36)\n" +
                    "Connection: Connected to a server · 1 saved · 1 connected",
                "Diagnostics\n[2026-11-07 10:00:00Z] androidExit · android.exit\n" +
                    "Crash reason=4 subReason=-1 status=0 importance=125",
            )
            .joinToString("\n\n")

    @Test
    fun `E19-S04 the report preview leads with plain words and keeps the exit record under Diagnostics`() {
        val headline = report.lineSequence().first()
        assertTrue("reason=" !in headline, "headline must be plain words: $headline")
        val diagnostics = report.substringAfter("Diagnostics\n")
        assertTrue("reason=4" in diagnostics)
        val state =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.ReportProblem),
                report = ReportUi.Ready(report),
            )
        for (variant in variants) {
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runComposeUiTest {
                setContent {
                    SettingsThemed(dark = variant.dark, locale = variant.locale) {
                        SettingsScreen(state, RecordingActions())
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                // P-022: the report sits in a closed Details fold headed by its plain first line; opened, the text is there.
                onNodeWithText("The app closed unexpectedly").assertExists()
                onNodeWithTag("report-text", useUnmergedTree = true).assertDoesNotExist()
                onNodeWithTag("report-details").performClick()
                waitForIdle()
                onNodeWithTag("report-text", useUnmergedTree = true).assertExists()
                compare("report-problem-headline-${variant.name}")
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

    private companion object {
        const val MAX_SHARE = 0.002
        const val CHANNEL_SLACK = 8
    }
}
