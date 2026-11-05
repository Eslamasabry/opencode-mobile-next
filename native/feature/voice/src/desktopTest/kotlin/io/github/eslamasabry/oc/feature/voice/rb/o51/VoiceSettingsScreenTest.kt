package io.github.eslamasabry.oc.feature.voice.rb.o51

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.FakeHub
import io.github.eslamasabry.oc.feature.voice.presentation.*
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceSettingsScreenTest {
    @Test
    fun `B-MEDIA-012 missing model has one pinned Download and source language choices`() =
        runTest {
            val hub = FakeHub()
            val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
            runCurrent()
            val state = presenter.state.value
            runComposeUiTest {
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
                onAllNodesWithTag("voice-model-primary-action").assertCountEquals(1)
                onNodeWithText("Auto detect").assertExists()
                onNodeWithText("English").assertExists()
                onNodeWithText("Arabic").assertExists()
                onNodeWithText("More languages").assertExists()
                onNodeWithText("Recommended").assertDoesNotExist()
                onNodeWithText("Auto detect").performScrollTo().performClick()
            }
            runCurrent()
            assertEquals(VoiceLanguage.Detect, hub.models.state.value.language)
        }

    @Test
    fun `B-MEDIA-012 installed model has maintenance actions and no Download footer`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val initial = presenter.state.value
        val state =
            initial.copy(
                packs =
                    initial.packs.map {
                        if (it.id == PackId("base")) it.copy(status = PackStatus.Installed) else it
                    }
            )
        runComposeUiTest {
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
            onNodeWithTag("voice-model-primary-action").assertDoesNotExist()
            onNodeWithTag("voice-redownload-base").assertExists()
            onNodeWithTag("voice-delete-base").assertExists()
        }
    }

    @Test
    fun `B-MEDIA-012 download announces coarse progress and keeps Cancel separately usable`() =
        runTest {
            val hub = FakeHub()
            val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
            runCurrent()
            val state = presenter.state.value.copy(job = PackJobUi(PackId("base"), 49, 100, false))
            runComposeUiTest {
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
                onNodeWithContentDescription("Downloading voice model 40 percent").assertExists()
                onNodeWithText("Cancel download").performClick()
            }
            runCurrent()
            assertEquals(1, hub.models.calls.count { it == "cancel" })
        }

    @Test
    fun `B-MEDIA-012 verification announces its phase and removes download cancellation`() =
        runTest {
            val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
            runCurrent()
            val state = presenter.state.value.copy(job = PackJobUi(PackId("base"), 100, 100, true))
            runComposeUiTest {
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
                onNodeWithContentDescription("Verifying downloaded model").assertExists()
                onNodeWithText("Cancel download").assertDoesNotExist()
                onNodeWithContentDescription("Downloading voice model 100 percent")
                    .assertDoesNotExist()
            }
        }
}
