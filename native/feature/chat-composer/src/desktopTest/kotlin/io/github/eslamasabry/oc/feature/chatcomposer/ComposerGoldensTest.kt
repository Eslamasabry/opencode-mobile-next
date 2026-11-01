package io.github.eslamasabry.oc.feature.chatcomposer

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerNote
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EffortWord
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ReadOnlyReason
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import kotlinx.collections.immutable.persistentListOf
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
 * Composer goldens on the desktop (Skiko software raster), light/dark × en plus dark ar. The feature module has no
 * Robolectric host (plan 14 §3: feature captures belong in `:ui:screenshots`, which does not exist yet), so these run
 * as desktop captures. Record: `./gradlew :feature:chat-composer:desktopTest -Poc.recordGoldens=true --tests
 * '*ComposerGoldensTest*'`. Files: `src/desktopTest/goldens/<scene>-<variant>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String)

    private val variants =
        listOf(Variant("light-en", false, "en"), Variant("dark-en", true, "en"), Variant("dark-ar", true, "ar"))
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val model = ComposerChip.Model(ModelLabel.Named("Sonnet 5"), isNew = true, effort = EffortWord.Known.High)

    private fun scene(scene: String, state: ComposerUi, text: String = "") {
        for (variant in variants) {
            // The words follow the JVM locale, as the app's locale applier sets it on a device.
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runComposeUiTest {
                setContent {
                    ComposerThemed(dark = variant.dark, locale = variant.locale) {
                        val field = rememberTextFieldState(text)
                        ComposerScreen(state, RecordingActions(), field)
                    }
                }
                waitForIdle()
                // Settle any colour easing (KitButton eases its ink) before the capture.
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
        val differing = differingPixels(golden, image)
        val share = differing.toDouble() / (image.width * image.height)
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
            val far = (0..24 step 8).any { shift -> abs((p shr shift and 0xFF) - (q shr shift and 0xFF)) > CHANNEL_SLACK }
            if (far) count++
        }
        return count
    }

    @Test
    fun `P09-10_2 idle empty shows the mic`() =
        scene("idle-empty", ComposerUi(button = FieldButton.Mic, chips = persistentListOf(model, ComposerChip.Approval(ApprovalMode.AsksFirst))))

    @Test
    fun `P09-10_2 idle with words shows Send`() =
        scene(
            "idle-text",
            ComposerUi(button = FieldButton.Send, chips = persistentListOf(model, ComposerChip.Approval(ApprovalMode.AutoApprove))),
            text = "Fix the failing login test and run it again",
        )

    @Test
    fun `B-AGENT-069 a running turn steers and Stop sits at the row's end`() =
        scene(
            "running-steer",
            ComposerUi(
                button = FieldButton.AddToTurn,
                chips = persistentListOf(model, ComposerChip.Approval(ApprovalMode.AsksFirst), ComposerChip.Stop(stuck = false)),
                glow = true,
            ),
            text = "Also check the docs",
        )

    @Test
    fun `P09-4_4 a stuck step turns Stop amber and a restarting agent says Stop and send`() =
        scene(
            "running-stuck-restart",
            ComposerUi(
                button = FieldButton.StopAndSend,
                chips = persistentListOf(model, ComposerChip.Approval(ApprovalMode.Paused), ComposerChip.Stop(stuck = true)),
            ),
            text = "Start over",
        )

    @Test
    fun `P09-10_2 a failed send and the offline button`() =
        scene(
            "failed-offline",
            ComposerUi(button = FieldButton.SendWhenOnline, chips = persistentListOf(model, ComposerChip.Approval(ApprovalMode.AsksFirst)), note = ComposerNote.SendFailed),
            text = "Ship it",
        )

    @Test
    fun `P09-10_2 read-only says why and has no button`() =
        scene(
            "read-only",
            ComposerUi(button = FieldButton.None, note = ComposerNote.ReadOnly(ReadOnlyReason.SubAgent), fieldEnabled = false),
        )

    private companion object {
        /** Anti-aliasing slack across hosts: a pixel counts when a channel moves more than this. */
        const val CHANNEL_SLACK = 24
        const val MAX_SHARE = 0.005
    }
}
