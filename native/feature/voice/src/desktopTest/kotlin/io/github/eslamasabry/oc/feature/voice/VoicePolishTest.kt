package io.github.eslamasabry.oc.feature.voice

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Polish sweep #14 (B-557): under "Voice typing" the page asked for a speech model download, and the notice under it
 * said this version does not use those models while the phone's own recogniser types.
 */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoicePolishTest {
    private fun ComposeUiTest.show(state: VoiceSettingsUi, presenter: VoiceSettingsPresenter) {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                VoiceSettingsScreen(state, presenter)
            }
        }
        waitForIdle()
    }

    @Test
    fun `B-VOI-557 the phone recogniser page does not ask for a model download`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val phone = presenter.state.value
        runDesktopComposeUiTest(360, 2600) {
            show(phone, presenter)
            onNodeWithText(SUBTITLE).assertDoesNotExist()
        }
        runDesktopComposeUiTest(360, 2600) {
            show(phone.copy(engine = SpeechEngineKind.OfflineModel), presenter)
            onNodeWithText(SUBTITLE).assertExists()
        }
    }

    @Test
    fun `B-VOI-557 the voice notice uses calm body text`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val phone = presenter.state.value
        runDesktopComposeUiTest(360, 2600) {
            show(phone, presenter)
            val node = onNodeWithTag("kit-notice-message", useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val size = layouts.first().layoutInput.style.fontSize.value
            assertTrue(size <= 14f, "explanatory notice should use calm body size (<= 14sp), got $size")
        }
    }

    private companion object {
        const val SUBTITLE =
            "Download a speech model once. After that, voice input runs on this phone without the internet."
    }
}
