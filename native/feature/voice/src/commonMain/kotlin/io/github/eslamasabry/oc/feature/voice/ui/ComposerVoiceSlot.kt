package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceFieldPort
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceNote
import io.github.eslamasabry.oc.feature.voice.screen.ComposerVoiceViewModel
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitMicButton
import io.github.eslamasabry.oc.ui.kitchat.composer.KitRecorderStage
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoicePill
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceRecorderRow
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceRecording
import io.github.eslamasabry.oc.ui.kit.text.kitElapsedSecondsMinutes
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiModelRequired
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNotNow
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitVoiceDiscard
import io.github.eslamasabry.oc.ui.words.res.kitVoiceElapsed
import io.github.eslamasabry.oc.ui.words.res.kitVoiceListening
import io.github.eslamasabry.oc.ui.words.res.kitVoiceListeningFor
import io.github.eslamasabry.oc.ui.words.res.kitVoiceSendSpoken
import io.github.eslamasabry.oc.ui.words.res.kitVoiceUseWords
import io.github.eslamasabry.oc.ui.words.res.kitVoiceMicDenied
import io.github.eslamasabry.oc.ui.words.res.kitVoiceSend
import io.github.eslamasabry.oc.ui.words.res.kitVoiceStarting
import io.github.eslamasabry.oc.ui.words.res.kitVoiceTranscribing
import io.github.eslamasabry.oc.ui.words.res.tipHoldMic
import io.github.eslamasabry.oc.ui.words.res.upgradeComposerHoldDictate
import io.github.eslamasabry.oc.ui.words.res.upgradeComposerVoiceHold
import io.github.eslamasabry.oc.ui.words.res.voiceAllowMicInSettings
import io.github.eslamasabry.oc.ui.words.res.voiceInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicAllow
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicAsk
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicBlocked
import io.github.eslamasabry.oc.ui.words.res.voiceModeNothingHeard
import io.github.eslamasabry.oc.ui.words.res.voiceNativeSettingsAction
import io.github.eslamasabry.oc.ui.words.res.voiceNativeStoppedAfterPause
import io.github.eslamasabry.oc.ui.words.res.vpttLetGoToCancel
import io.github.eslamasabry.oc.ui.words.res.vpttLetGoToFinish
import org.jetbrains.compose.resources.stringResource

/**
 * The composer's voice slot (kit-chat `KitComposerVoice`), one ViewModel per conversation composer.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<KitComposerVoice>())
public class ComposerVoiceSlot : KitComposerVoice {
    @Composable
    override fun Mic(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        LaunchedEffect(presenter, state.available, state.conversationAvailable, field.text()) {
            presenter.offerMicTip()
        }
        DisposableEffect(presenter) { onDispose { presenter.dismissMicTip() } }
        VoiceMic(state, presenter, modifier, prominent = true)
    }

    @Composable
    override fun InlineMic(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        LaunchedEffect(presenter, state.available, state.conversationAvailable, field.text()) {
            presenter.offerMicTip()
        }
        DisposableEffect(presenter) { onDispose { presenter.dismissMicTip() } }
        VoiceMic(state, presenter, modifier, prominent = false)
    }

    @Composable
    override fun isActive(field: KitVoiceField): Boolean {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        return state.active
    }

    @Composable
    override fun StopWhenLeft(field: KitVoiceField) {
        val presenter = presenterFor(field)
        DisposableEffect(presenter) { onDispose { presenter.cancel() } }
    }

    @Composable
    override fun Actions(field: KitVoiceField, modifier: Modifier) {
        Options(field, modifier) {}
    }

    @Composable
    override fun Panel(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        val level = presenter.level.collectAsStateWithLifecycle()
        // Anything typed during the countdown cancels the send (Flutter `_autoSendText`).
        LaunchedEffect(presenter, field.key) {
            snapshotFlow { field.text() }.collect { presenter.fieldChanged(it) }
        }
        // Leaving the app ends a recording (keeping what was written) and the countdown (plan 10
        // §6.5).
        LifecycleStartEffect(presenter) {
            presenter.hostChanged(inForeground = true)
            onStopOrDispose { presenter.hostChanged(inForeground = false) }
        }
        VoicePanel(state, { level.value }, presenter, modifier)
        VoiceConversationControls(state, presenter, modifier)
        // The mic found no speech model: Flutter's first-mic "Voice typing" sheet offers it right here.
        VoiceSetupSheet(state.setup, presenter)
    }

    @Composable
    override fun recording(field: KitVoiceField): KitVoiceRecording? {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        return voiceRecording(state, presenter, dictationOnly = field.dictationOnly, sendLabel = stringResource(Res.string.kitVoiceSendSpoken))
    }

    @Composable
    override fun Recorder(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        val level = presenter.level.collectAsStateWithLifecycle()
        VoiceRecorder(state, { level.value }, presenter, modifier)
    }

    @Composable
    override fun Note(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        if (state.phase == MicPhase.Idle) VoiceNoteView(state.note, presenter, modifier)
    }

    @Composable
    override fun Options(field: KitVoiceField, modifier: Modifier, onSelected: () -> Unit) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        VoiceConversationEntry(state, presenter, modifier, onSelected)
    }

    @Composable
    private fun presenterFor(field: KitVoiceField): ComposerVoicePresenter {
        val vm =
            assistedMetroViewModel<ComposerVoiceViewModel, ComposerVoiceViewModel.Factory>(
                key = "voice:${field.key}"
            ) {
                create(field.key)
            }
        val port = remember(field) { FieldPort(field) }
        vm.presenter.attach(port)
        vm.attachConversation(field.conversationRef)
        return vm.presenter
    }
}

private class FieldPort(private val voiceField: KitVoiceField) : VoiceFieldPort {
    override fun text(): String = voiceField.text()

    override fun selection(): IntRange? = voiceField.selection()

    override fun replace(text: String) = voiceField.replace(text)

    override fun send() = voiceField.send()

    override fun canSend(): Boolean = voiceField.canSend()

    override val dictationOnly: Boolean
        get() = voiceField.dictationOnly
}

/**
 * The mic beside Send: tap to talk, hold to talk, slide away to cancel. Absent when voice cannot
 * work here.
 */
@Composable
internal fun VoiceMic(
    state: ComposerVoiceUi,
    presenter: ComposerVoicePresenter,
    modifier: Modifier = Modifier,
    prominent: Boolean = true,
) {
    if (!state.available || (state.conversation && !state.active && !state.canListen)) return
    // Holding is the way to talk; a tap also works and ends with Done. The hands-free conversation
    // lives in the plus sheet, never on a long press.
    val mic: @Composable () -> Unit = {
        KitMicButton(
            label = stringResource(Res.string.upgradeComposerHoldDictate),
            onTap = presenter::tapMic,
            onHoldStart = presenter::holdStart,
            onHoldEnd = presenter::holdEnd,
            onArmedChanged = presenter::holdArmed,
            active = state.active,
            holdLabel = stringResource(Res.string.tipHoldMic),
            modifier = modifier.testTag("composer-voice-button"),
            prominent = prominent,
        )
    }
    if (state.conversationAvailable) {
        KitTip(
            message = stringResource(Res.string.upgradeComposerVoiceHold),
            visible = state.micTip,
            onDismissed = presenter::dismissMicTip,
        ) {
            mic()
        }
    } else mic()
}

/**
 * The hands-free voice conversation's pill (its own words heard, Send, Cancel). Dictation never draws here: the message
 * box itself is the recorder ([VoiceRecorder]) and its note sits inside the box ([VoiceNoteView]). Nothing when idle.
 */
@Composable
internal fun VoicePanel(
    state: ComposerVoiceUi,
    level: () -> Float,
    presenter: ComposerVoicePresenter,
    modifier: Modifier = Modifier,
) {
    if (state.mode != VoiceMode.Conversation) return
    val cancel =
        KitLineAction(
            stringResource(Res.string.kitConfirmCancel),
            presenter::cancel,
            testTag = "voice-cancel",
        )
    when (state.phase) {
        MicPhase.Starting ->
            KitVoicePill(
                title = stringResource(Res.string.kitVoiceStarting),
                modifier = modifier,
                secondary = cancel,
            )
        MicPhase.Listening -> {
            val (minutes, seconds) = elapsedParts(state.elapsedSeconds)
            KitVoicePill(
                title = stringResource(Res.string.kitVoiceListening),
                modifier = modifier,
                words = state.words,
                elapsed = stringResource(Res.string.kitVoiceElapsed, minutes, seconds),
                level = level,
                primary = KitLineAction(stringResource(Res.string.kitVoiceSend), presenter::done, testTag = "voice-done"),
                secondary = cancel,
            )
        }
        MicPhase.Writing ->
            KitVoicePill(
                title = stringResource(Res.string.kitVoiceTranscribing),
                modifier = modifier,
                words = state.words,
            )
        MicPhase.Idle -> Unit
    }
}

/**
 * How the message box hosts a dictation (kit-chat [KitVoiceRecording]); null while idle and in a voice conversation.
 * Send means "write down and send" only for a tapped dictation that is still listening, and never on a draft-only box.
 */
internal fun voiceRecording(
    state: ComposerVoiceUi,
    presenter: ComposerVoicePresenter,
    dictationOnly: Boolean,
    sendLabel: String,
): KitVoiceRecording? {
    if (!state.active || state.mode == VoiceMode.Conversation) return null
    val held = state.mode == VoiceMode.Hold
    val sends = !held && !dictationOnly && state.phase != MicPhase.Writing
    return KitVoiceRecording(held = held, sendNow = if (sends) presenter::doneAndSend else null, sendLabel = sendLabel)
}

/** The recorder row inside the message box while words are dictated: ✕, the live waveform and the time, ✓. */
@Composable
internal fun VoiceRecorder(
    state: ComposerVoiceUi,
    level: () -> Float,
    presenter: ComposerVoicePresenter,
    modifier: Modifier = Modifier,
) {
    if (!state.active || state.mode == VoiceMode.Conversation) return
    val held = state.mode == VoiceMode.Hold
    // Tap dictation ends by a button: ✕ throws the words away, ✓ puts them in the box. A held mic ends by the finger.
    val cancel =
        KitLineAction(stringResource(Res.string.kitVoiceDiscard), presenter::cancel, testTag = "voice-cancel")
            .takeUnless { held && state.phase != MicPhase.Writing }
    val accept =
        KitLineAction(stringResource(Res.string.kitVoiceUseWords), presenter::done, testTag = "voice-done")
            .takeUnless { held || state.phase == MicPhase.Writing }
    val (minutes, seconds) = elapsedParts(state.elapsedSeconds)
    val elapsed = stringResource(Res.string.kitVoiceElapsed, minutes, seconds)
    val listening = stringResource(Res.string.kitVoiceListening)
    // Spoken: "Listening…", then the time every quarter minute, never a tick every second.
    val step = state.elapsedSeconds / ANNOUNCE_EVERY_S * ANNOUNCE_EVERY_S
    val announcement =
        if (step == 0) listening
        else stringResource(Res.string.kitVoiceListeningFor, kitElapsedSecondsMinutes(step.toLong()))
    when (state.phase) {
        MicPhase.Starting ->
            KitVoiceRecorderRow(
                stage = KitRecorderStage.Starting,
                status = stringResource(Res.string.kitVoiceStarting),
                modifier = modifier,
                cancel = cancel,
                accept = accept,
            )
        MicPhase.Listening ->
            KitVoiceRecorderRow(
                stage = if (held) KitRecorderStage.Held else KitRecorderStage.Listening,
                status =
                    stringResource(
                        when {
                            held && state.cancelArmed -> Res.string.vpttLetGoToCancel
                            held -> Res.string.vpttLetGoToFinish
                            else -> Res.string.kitVoiceListening
                        }
                    ),
                modifier = modifier,
                elapsed = elapsed,
                announcement = announcement,
                level = level,
                attention = state.cancelArmed,
                cancel = cancel,
                accept = accept,
            )
        MicPhase.Writing ->
            KitVoiceRecorderRow(
                stage = KitRecorderStage.Transcribing,
                status = stringResource(Res.string.kitVoiceTranscribing),
                modifier = modifier,
                cancel = cancel,
            )
        MicPhase.Idle -> Unit
    }
}

/** The recorder says the time aloud every this many seconds. */
private const val ANNOUNCE_EVERY_S = 15

@Composable
private fun VoiceNoteView(note: VoiceNote?, presenter: ComposerVoicePresenter, modifier: Modifier) {
    note ?: return
    val dismiss = KitLineAction(stringResource(Res.string.e7VoiceUiNotNow), presenter::dismissNote)
    val settings =
        KitLineAction(stringResource(Res.string.voiceNativeSettingsAction), presenter::openSettings)
    when (note) {
        VoiceNote.NothingHeard ->
            KitNotice(stringResource(Res.string.voiceModeNothingHeard), modifier, dismiss = dismiss)
        VoiceNote.StoppedAfterPause ->
            KitNotice(
                stringResource(Res.string.voiceNativeStoppedAfterPause),
                modifier,
                dismiss = dismiss,
            )
        is VoiceNote.MicNeeded ->
            KitNotice(
                message =
                    stringResource(
                        if (note.blocked) Res.string.voiceModeMicBlocked
                        else Res.string.voiceModeMicAsk
                    ),
                modifier = modifier,
                title = stringResource(Res.string.kitVoiceMicDenied),
                tone = StatusTone.Attention,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(
                                if (note.blocked) Res.string.voiceAllowMicInSettings
                                else Res.string.voiceModeMicAllow
                            ),
                            presenter::allowMic,
                        )
                    ),
                dismiss = dismiss,
            )
        VoiceNote.NeedsModel ->
            KitNotice(
                stringResource(Res.string.e7VoiceUiModelRequired),
                modifier,
                actions = listOf(settings),
                dismiss = dismiss,
            )
        is VoiceNote.Failed ->
            KitNotice(
                message = problemWords(note.problem),
                modifier = modifier,
                tone = StatusTone.Failure,
                actions = listOf(settings),
                dismiss = dismiss,
            )
        VoiceNote.Unavailable ->
            KitNotice(
                stringResource(Res.string.voiceInputUnavailable),
                modifier,
                tone = StatusTone.Failure,
                dismiss = dismiss,
            )
    }
}
