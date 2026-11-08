package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupStage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.feature.voice.FakeHub
import io.github.eslamasabry.oc.feature.voice.SchedulerClock
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope

/**
 * S31 story 3: the composer's first-mic "Voice typing" sheet in Flutter's words (`_VoiceAutomaticSetupBody`). With
 * OC_S31_SHOTS set, each state is also written at Flutter's golden size (412 x 915 dp) for the side-by-side QA images.
 */
@OptIn(ExperimentalTestApi::class)
class S31VoiceSetupSheetTest {
    private val scope = TestScope()
    private val presenter = ComposerVoicePresenter(FakeHub(), SchedulerClock(scope), scope) {}

    private fun sheet(name: String, stage: VoiceSetupStage, check: ComposeUiTest.() -> Unit) {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en"))
        try {
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                KitSheetHost { Sheet(stage) }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                System.getenv("OC_S31_SHOTS")?.let(::File)?.let { out ->
                    out.mkdirs()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
                }
                check()
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Composable
    private fun Sheet(stage: VoiceSetupStage) {
        VoiceSetupSheet(stage, presenter)
    }

    /** The words are on screen: as text, or as what a screen reader says for a merged part (the progress bar). */
    private fun ComposeUiTest.shows(text: String) {
        val nodes =
            onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "\"$text\" is not on screen")
    }

    @Test
    fun `B-MEDIA-056 the offer names the picked model and its download`() =
        sheet("native-voice-setup-offer-dark", VoiceSetupStage.NeedsConsent(1, PackId("base"), SetupNetwork.Unmetered, 161_000_000, false)) {
            shows("Voice typing")
            shows(
                "Speak instead of typing. Speech turns into text on this phone, even offline, and audio never leaves it. " +
                    "It needs a one-time download."
            )
            shows("Balanced speech model, picked for this phone's memory")
            shows("Download 161 MB")
            shows("Not now")
            shows("Choose another speech model")
        }

    @Test
    fun `B-MEDIA-057 on mobile data the button says so and the cost is named`() =
        sheet("native-voice-setup-mobile-data-dark", VoiceSetupStage.NeedsConsent(1, PackId("base"), SetupNetwork.Metered, 161_000_000, false)) {
            shows("You're on mobile data. This download counts against your data plan.")
            shows("Download 161 MB on mobile data")
        }

    @Test
    fun `B-MEDIA-058 the download shows its progress, that listening follows, and Cancel download`() =
        sheet("native-voice-setup-downloading-dark", VoiceSetupStage.Downloading(64_000_000, 161_000_000)) {
            shows("64 MB of 161 MB")
            shows("Listening starts when it's done.")
            shows("Cancel download")
        }

    @Test
    fun `B-MEDIA-059 ready offers Start listening and offline offers Try again`() {
        sheet("native-voice-setup-ready-dark", VoiceSetupStage.Ready(PackId("base"))) {
            shows("The speech model is on this phone.")
            shows("Start listening")
        }
        sheet("native-voice-setup-offline-dark", VoiceSetupStage.Problem(MediaProblem.DownloadOffline)) {
            shows("No internet connection. Connect, then try again.")
            shows("Try again")
        }
    }
}
