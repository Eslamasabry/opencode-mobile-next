package io.github.eslamasabry.oc.feature.voice.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.FakeHub
import io.github.eslamasabry.oc.feature.voice.presentation.NaturalVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val PHASE = "before"

private const val W_PX = 1080
private const val H_PX = 2400

/**
 * B-548 (sweep 2026-11-25, finding 5): the Voice typing page's Delete speech model never wears the "go" green, and
 * its pictures at 360 dp, in Solid, dark and light, at text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PolishSheetsColoursTest {
    private var previousLocale: Locale = Locale.getDefault()

    private val out = File("build/polish-render").also { it.mkdirs() }

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private fun installed(state: VoiceSettingsUi) =
        state.copy(
            packs = state.packs.map { if (it.id == PackId("base")) it.copy(status = PackStatus.Installed) else it },
            natural =
                NaturalVoiceUi(
                    bytes = 64_000_000,
                    installed = true,
                    chosen = true,
                    downloading = false,
                    available = true,
                ),
        )

    private fun ComposeUiTest.show(
        state: VoiceSettingsUi,
        presenter: VoiceSettingsPresenter,
        mode: AppearanceMode,
        fontScale: Float,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        VoiceSettingsScreen(state, presenter)
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("voice-delete-base").performScrollTo()
        waitForIdle()
    }

    private fun ComposeUiTest.greenCount(node: SemanticsNodeInteraction): Int {
        val map = node.captureToImage().toPixelMap()
        var green = 0
        for (x in 0 until map.width)
            for (y in 0 until map.height) {
                val p = map[x, y].toArgb()
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                if (g - r >= 40 && g - b >= 40) green++
            }
        return green
    }

    private fun shot(state: VoiceSettingsUi, presenter: VoiceSettingsPresenter, mode: AppearanceMode, scale: Float) {
        val look = if (mode == AppearanceMode.Dark) "dark" else "light"
        val text = if (scale == 1.3f) "text13" else "text10"
        val file = File(out, "5-$PHASE-voice-delete-$look-$text.png")
        runDesktopComposeUiTest(W_PX, H_PX) {
            show(state, presenter, mode, scale)
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
        assertTrue(file.isFile, "the picture ${file.name} was written")
    }

    @Test
    fun `B-548 Delete speech model is not green`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light))
            runDesktopComposeUiTest(W_PX, H_PX) {
                show(state, presenter, mode, 1f)
                val delete = onNodeWithTag("voice-delete-base")
                delete.assertIsDisplayed()
                val green = greenCount(delete)
                assertEquals(0, green, "[$mode] Delete speech model is not green")
            }
    }

    @Test
    fun pictures() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            shot(state, presenter, mode, 1f)
            shot(state, presenter, mode, 1.3f)
        }
    }
}
