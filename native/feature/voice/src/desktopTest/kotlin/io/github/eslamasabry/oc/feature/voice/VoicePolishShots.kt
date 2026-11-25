package io.github.eslamasabry.oc.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.presentation.NaturalVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val PHASE = "before"

/**
 * Polish sweep #14 (B-557): the Voice typing page while the phone's own recogniser types, a model installed, at
 * 360 dp, light and dark, text 1.0 and 1.3, in `build/polish-render/`. Not a check: a person compares the before
 * and after pictures.
 */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoicePolishShots {
    private data class Variant(val dark: Boolean, val scale: Float) {
        val name: String
            get() = "${if (dark) "dark" else "light"}-en-$scale"
    }

    private val variants =
        listOf(false, true).flatMap { dark -> listOf(1f, 1.3f).map { scale -> Variant(dark, scale) } }

    /** The auditor's page: the Balanced model and the natural voice installed (the picture of finding #14). */
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

    @Test
    fun `14 the phone recogniser page`() = runTest {
        val out = File("build/polish-render").also { it.mkdirs() }
        val before = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state = installed(presenter.state.value)
        try {
            for (v in variants) {
                val name = "14-$PHASE-voice-${v.name}.png"
                runDesktopComposeUiTest(360, 2600) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, v.scale)) {
                            KitTheme(
                                appearance =
                                    KitAppearance(
                                        mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                        motion = MotionLevel.Off,
                                    ),
                                platform = DesktopKitPlatform(),
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
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, name))
                }
            }
        } finally {
            Locale.setDefault(before)
        }
    }
}
