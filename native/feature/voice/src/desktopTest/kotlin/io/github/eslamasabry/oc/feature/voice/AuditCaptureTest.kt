@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.voice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.settings.teaching.TipId
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupStage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.ConversationReplyNote
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.feature.voice.presentation.NaturalVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.PackJobUi
import io.github.eslamasabry.oc.feature.voice.presentation.PackRowUi
import io.github.eslamasabry.oc.feature.voice.presentation.ReadNote
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderUi
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingActions
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingUi
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConfirm
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceNote
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.feature.voice.ui.TeachingScreen
import io.github.eslamasabry.oc.feature.voice.ui.VoiceConversationControls
import io.github.eslamasabry.oc.feature.voice.ui.VoiceMic
import io.github.eslamasabry.oc.feature.voice.ui.VoicePanel
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSetupSheet
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.feature.voice.ui.ReplyReaderView
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test

/** Audit capture only: voice typing settings, the first-mic setup sheet, the composer's voice parts and the tips page. */
class AuditCaptureTest {
    private val scope = TestScope()
    private val hub = FakeHub()
    private val settings = VoiceSettingsPresenter(hub, scope) {}
    private val composer = ComposerVoicePresenter(hub, SchedulerClock(scope), scope) {}
    private val reader = ReplyReaderPresenter(hub, scope)

    private object TeachingA : TeachingActions {
        override fun back() {}

        override fun replay(id: TipId) {}

        override fun next() {}

        override fun dismiss() {}

        override fun reset() {}

        override fun retry() {}
    }

    // ---- Settings

    private val packs =
        listOf(
            PackRowUi(PackId("base"), 161_000_000, PackStatus.Missing, 1_500, 8_000),
            PackRowUi(PackId("small"), 488_000_000, PackStatus.Missing, 3_000, 8_000),
            PackRowUi(PackId("tiny"), 75_000_000, PackStatus.Missing, 700, 8_000),
        )

    private val loaded =
        VoiceSettingsUi(
            loading = false,
            engine = SpeechEngineKind.OfflineModel,
            packs = packs,
            natural = NaturalVoiceUi(bytes = 148_000_000, installed = false, chosen = false, downloading = false, available = true),
        )

    private fun installed(ui: VoiceSettingsUi) =
        ui.copy(
            packs = ui.packs.map { if (it.id == PackId("base")) it.copy(status = PackStatus.Installed) else it },
            natural = NaturalVoiceUi(bytes = 148_000_000, installed = true, chosen = true, downloading = false, available = true),
        )

    private fun voiceSettings(state: String, ui: VoiceSettingsUi) {
        Audit.shoot("voice", "voice-settings", state) { VoiceSettingsScreen(ui, settings) }
    }

    @Test
    fun settingsStates() = Audit.guard("voice__voice-settings__build") {
        voiceSettings("loading", VoiceSettingsUi(loading = true))
        voiceSettings("model-missing", loaded)
        voiceSettings("model-installed", installed(loaded))
        voiceSettings("model-installed-auto-send", installed(loaded).copy(autoSend = true, language = VoiceLanguage.Arabic))
        voiceSettings("downloading", loaded.copy(job = PackJobUi(PackId("base"), 64_000_000, 161_000_000, false)))
        voiceSettings("starting-download", loaded.copy(job = PackJobUi(PackId("base"), 0, 161_000_000, false)))
        voiceSettings("verifying", loaded.copy(job = PackJobUi(PackId("base"), 161_000_000, 161_000_000, true)))
        voiceSettings("problem-timed-out", loaded.copy(problem = MediaProblem.DownloadTimedOut, retryPack = PackId("base")))
        voiceSettings("problem-offline", loaded.copy(problem = MediaProblem.DownloadOffline, retryPack = PackId("base")))
        voiceSettings("problem-no-space", loaded.copy(problem = MediaProblem.DownloadNoSpace, retryPack = PackId("base")))
        voiceSettings("model-unsupported", loaded.copy(packs = packs.map { if (it.id == PackId("small")) it.copy(status = PackStatus.Unsupported(MediaProblem.PackMemoryTooSmall)) else it }, selected = PackId("small")))
        voiceSettings("model-partial", loaded.copy(packs = packs.map { if (it.id == PackId("base")) it.copy(status = PackStatus.Partial(40_000_000, 161_000_000)) else it }))
        voiceSettings("phone-recognizer", loaded.copy(engine = SpeechEngineKind.PhoneRecognizer))
        voiceSettings("no-microphone", loaded.copy(hasMicrophone = false))
        voiceSettings("unavailable", VoiceSettingsUi(loading = false, available = false))
        voiceSettings("language-picking", loaded.copy(languagePicking = true))
        voiceSettings("confirm-download-metered", loaded.copy(confirm = VoiceConfirm.Download(PackId("base"), 161_000_000, false, SetupNetwork.Metered)))
        voiceSettings("confirm-download-natural", loaded.copy(confirm = VoiceConfirm.Download(PackId("kokoro-en"), 148_000_000, false, SetupNetwork.Unknown)))
        voiceSettings("confirm-delete-pack", installed(loaded).copy(confirm = VoiceConfirm.DeletePack(PackId("base"), 161_000_000)))
        voiceSettings("confirm-remove-natural", installed(loaded).copy(confirm = VoiceConfirm.RemoveNatural))
        voiceSettings("natural-downloading", loaded.copy(natural = NaturalVoiceUi(bytes = 148_000_000, installed = false, chosen = true, downloading = true, available = true)))
    }

    // ---- First-mic setup sheet

    private fun sheet(state: String, stage: VoiceSetupStage) {
        Audit.shoot("voice", "voice-setup-sheet", state) { KitSheetHost { VoiceSetupSheet(stage, composer) } }
    }

    @Test
    fun setupSheetStates() = Audit.guard("voice__voice-setup-sheet__build") {
        sheet("offer", VoiceSetupStage.NeedsConsent(1, PackId("base"), SetupNetwork.Unmetered, 161_000_000, false))
        sheet("offer-mobile-data", VoiceSetupStage.NeedsConsent(1, PackId("base"), SetupNetwork.Metered, 161_000_000, false))
        sheet("offer-unknown-network", VoiceSetupStage.NeedsConsent(1, PackId("base"), SetupNetwork.Unknown, 161_000_000, true))
        sheet("checking", VoiceSetupStage.Checking)
        sheet("downloading", VoiceSetupStage.Downloading(64_000_000, 161_000_000))
        sheet("verifying", VoiceSetupStage.Verifying)
        sheet("ready", VoiceSetupStage.Ready(PackId("base")))
        sheet("problem-offline", VoiceSetupStage.Problem(MediaProblem.DownloadOffline))
        sheet("problem-no-space", VoiceSetupStage.Problem(MediaProblem.DownloadNoSpace, requiredBytes = 400_000_000))
        sheet("problem-no-microphone", VoiceSetupStage.Problem(MediaProblem.NoMicrophone))
    }

    // ---- Composer voice parts

    private fun composerPanel(state: String, ui: ComposerVoiceUi) {
        Audit.shoot("voice", "composer-voice", state) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    VoiceConversationControls(ui, composer, Modifier)
                    VoicePanel(ui, { 0.5f }, composer, Modifier, { "Please add a test for the parser" })
                    VoiceMic(ui, composer)
                }
            }
        }
    }

    @Test
    fun composerVoiceStates() = Audit.guard("voice__composer-voice__build") {
        val base = ComposerVoiceUi(available = true)
        composerPanel("idle", base)
        composerPanel("starting", base.copy(phase = MicPhase.Starting, mode = VoiceMode.Tap))
        composerPanel("listening-tap", base.copy(phase = MicPhase.Listening, mode = VoiceMode.Tap, elapsedSeconds = 7))
        composerPanel("listening-hold", base.copy(phase = MicPhase.Listening, mode = VoiceMode.Hold, holding = true, elapsedSeconds = 3))
        composerPanel("listening-hold-cancel-armed", base.copy(phase = MicPhase.Listening, mode = VoiceMode.Hold, holding = true, cancelArmed = true, elapsedSeconds = 3))
        composerPanel(
            "listening-conversation",
            base.copy(phase = MicPhase.Listening, mode = VoiceMode.Conversation, conversationAvailable = true, conversation = true, canListen = true, words = "Can you add a test for the parser and run it", elapsedSeconds = 12),
        )
        composerPanel("writing", base.copy(phase = MicPhase.Writing, mode = VoiceMode.Tap))
        composerPanel("countdown", base.copy(phase = MicPhase.Countdown, mode = VoiceMode.Tap, secondsLeft = 2))
        composerPanel("note-nothing-heard", base.copy(note = VoiceNote.NothingHeard))
        composerPanel("note-stopped-after-pause", base.copy(note = VoiceNote.StoppedAfterPause))
        composerPanel("note-mic-needed", base.copy(note = VoiceNote.MicNeeded(blocked = false)))
        composerPanel("note-mic-blocked", base.copy(note = VoiceNote.MicNeeded(blocked = true)))
        composerPanel("note-needs-model", base.copy(note = VoiceNote.NeedsModel))
        composerPanel("note-failed", base.copy(note = VoiceNote.Failed(MediaProblem.MicBusy)))
        composerPanel("note-unavailable", base.copy(note = VoiceNote.Unavailable))
        composerPanel("conversation-paused", base.copy(conversationAvailable = true, conversation = true, canListen = true, replyNote = ConversationReplyNote.Paused))
        composerPanel("conversation-review", base.copy(conversationAvailable = true, replyNote = ConversationReplyNote.Review))
        composerPanel("conversation-waiting-reply", base.copy(conversationAvailable = true, conversation = true, waitingReply = true))
        composerPanel("conversation-speaking-reply", base.copy(conversationAvailable = true, conversation = true, speakingReply = true, canReadReply = true))
    }

    private fun readAloud(state: String, ui: ReplyReaderUi) {
        Audit.shoot("voice", "read-aloud", state) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { ReplyReaderView(ui, reader) }
            }
        }
    }

    @Test
    fun readAloudStates() = Audit.guard("voice__read-aloud__build") {
        readAloud("speaking", ReplyReaderUi(available = true, reading = "m1"))
        readAloud("consent", ReplyReaderUi(available = true, asking = true))
        readAloud("note-no-prose", ReplyReaderUi(available = true, note = ReadNote.NoProse))
        readAloud("note-natural-fallback", ReplyReaderUi(available = true, note = ReadNote.NaturalFallback))
        readAloud("note-failed", ReplyReaderUi(available = true, note = ReadNote.Failed(MediaProblem.NoOfflineVoice)))
    }

    // ---- Tips

    private fun tips(state: String, ui: TeachingUi) {
        Audit.shoot("voice", "tips", state) { TeachingScreen(ui, TeachingA) }
    }

    @Test
    fun tipsStates() = Audit.guard("voice__tips__build") {
        tips("loading", TeachingUi())
        tips("ready", TeachingUi(ready = true))
        tips("failed", TeachingUi(failed = true))
        tips("reset-failed", TeachingUi(ready = true, resetFailed = true))
    }
}
