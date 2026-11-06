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
import kotlin.test.assertTrue
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import kotlinx.coroutines.flow.update
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
    }

    @Test
    fun `B-MEDIA-019 a connection the phone cannot vouch for never says mobile data`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state =
            presenter.state.value.copy(
                confirm = VoiceConfirm.Download(PackId("kokoro-en"), 148_000_000, false, SetupNetwork.Unknown)
            )
        runComposeUiTest {
            show(state, presenter)
            onNodeWithText("Download the natural voice?").assertExists()
            onNodeWithText("This phone can't tell whether this connection costs data.").assertExists()
            onNodeWithText("Download 148 MB").assertExists()
            onAllNodesWithText("mobile data", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun `B-MEDIA-019 only a metered connection says mobile data`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val state =
            presenter.state.value.copy(
                confirm = VoiceConfirm.Download(PackId("base"), 161_000_000, false, SetupNetwork.Metered)
            )
        runComposeUiTest {
            show(state, presenter)
            onNodeWithText("Download Balanced?").assertExists()
            onNodeWithText("Download 161 MB on mobile data").assertExists()
        }
    }

    @Test
    fun `B-MEDIA-020 nothing received yet shows a moving start, then megabytes and percent`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(), backgroundScope) {}
        runCurrent()
        val starting = presenter.state.value.copy(job = PackJobUi(PackId("base"), 0, 161_000_000, false))
        runComposeUiTest {
            show(starting, presenter)
            onNodeWithText("Starting the download").assertExists()
            onAllNodesWithText("0 MB", substring = true).assertCountEquals(0)
        }
        val moving = presenter.state.value.copy(job = PackJobUi(PackId("base"), 40_000_000, 161_000_000, false))
        runComposeUiTest {
            show(moving, presenter)
            onNodeWithText("40 MB of 161 MB · 24%").assertExists()
        }
    }

    @Test
    fun `B-MEDIA-013 a stopped download says why with Try again and keeps the code under Details`() = runTest {
        val hub = FakeHub()
        hub.models.state.update {
            it.copy(lastProblem = io.github.eslamasabry.oc.core.common.MediaProblem.DownloadTimedOut, lastProblemPack = PackId("base"))
        }
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        val state = presenter.state.value
        runComposeUiTest {
            show(state, presenter)
            onNodeWithText("The download stopped").assertExists()
            onNodeWithText("The model download timed out. Check the connection and try again.").assertExists()
            onNodeWithText("DownloadTimedOut", substring = true).assertDoesNotExist()
            onNodeWithText("Details").assertExists()
            onNodeWithTag("voice-download-retry").performClick()
        }
        runCurrent()
        assertTrue(presenter.state.value.confirm is VoiceConfirm.Download)
    }
}
