package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.FakeHub
import io.github.eslamasabry.oc.feature.voice.presentation.NaturalVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * P-026 (auditor sweep of a2e7d3fa5, `voice_typing.png`): the installed model's two acts look alike (rows, the delete
 * one last in the destructive slot) and no description runs past two lines on a phone. Goldens at text size 1.0 and
 * 1.3 in `src/desktopTest/goldens` (a missing one is recorded and the run fails: look at it, then run again).
 */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoicePolishTest {
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")

    private fun installed(state: VoiceSettingsUi) =
        state.copy(
            packs = state.packs.map { if (it.id == PackId("base")) it.copy(status = PackStatus.Installed) else it },
            natural = NaturalVoiceUi(bytes = 64_000_000, installed = true, chosen = true, downloading = false, available = true),
        )

    private fun ComposeUiTest.show(state: VoiceSettingsUi, presenter: VoiceSettingsPresenter, fontScale: Float = 1f) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    // The auditor's phone: 1080 px at 2.625 = 412 dp wide.
                    Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) {
                        VoiceSettingsScreen(state, presenter)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun SemanticsNode.layout(): TextLayoutResult =
        config[SemanticsActions.GetTextLayoutResult].action!!.let { get ->
            mutableListOf<TextLayoutResult>().also { get(it) }.first()
        }

    private fun ComposeUiTest.text(words: String): TextLayoutResult =
        onNode(hasText(words, substring = true), useUnmergedTree = true).fetchSemanticsNode().layout()

    @Test
    fun `P-026 Download again and Delete are worded and coloured alike`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        runComposeUiTest {
            show(state, presenter)
            onNodeWithTag("voice-redownload-base").assertExists()
            onNodeWithTag("voice-delete-base").assertExists()
            val again = text("speech model again").layoutInput.style
            val delete = onNode(hasText("Delete", substring = true), useUnmergedTree = true).fetchSemanticsNode().layout().layoutInput.style
            // Before: a green tertiary link above a bold white one.
            assertEquals(again.color, delete.color)
            assertEquals(again.fontWeight, delete.fontWeight)
        }
    }

    @Test
    fun `P-026 no description on Voice typing runs past two lines on a phone`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        runComposeUiTest {
            show(state, presenter)
            onAllNodesWithText("Holding the mic never sends by itself", substring = true).assertCountEquals(0)
            for (words in listOf("Sends after a short pause", "More natural and works offline")) {
                val lines = onNode(hasText(words, substring = true), useUnmergedTree = true).fetchSemanticsNode().layout().lineCount
                assertTrue(lines <= 2, "\"$words…\" takes $lines lines")
            }
        }
    }

    @Test
    fun looks() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        Locale.setDefault(Locale.ENGLISH)
        for ((suffix, scale) in listOf("" to 1f, "-text13" to 1.3f)) {
            runComposeUiTest {
                show(state, presenter, scale)
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                compare("p026-voice-typing-installed$suffix")
            }
        }
    }

    private fun ComposeUiTest.compare(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (System.getProperty("oc.goldens.record") == "true" || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (System.getProperty("oc.goldens.record") != "true")
                fail("$name: no golden yet; recorded ${file.path}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(golden.width == image.width && golden.height == image.height, "$name: size differs")
        var differ = 0
        for (y in 0 until golden.height) for (x in 0 until golden.width) {
            val p = golden.getRGB(x, y)
            val q = image.getRGB(x, y)
            if (p != q && (0..24 step 8).any { s -> abs((p shr s and 0xFF) - (q shr s and 0xFF)) > 8 }) differ++
        }
        if (differ.toDouble() / (image.width * image.height) > 0.002) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: $differ pixels differ; actual: $actual")
        }
    }
}
