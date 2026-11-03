package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitMicButton
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoicePill
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitComposerVoice
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitVoiceDone
import io.github.eslamasabry.oc.ui.words.res.kitVoiceElapsed
import io.github.eslamasabry.oc.ui.words.res.kitVoiceListening
import io.github.eslamasabry.oc.ui.words.res.kitVoiceMicDenied
import io.github.eslamasabry.oc.ui.words.res.kitVoiceStarting
import io.github.eslamasabry.oc.ui.words.res.kitVoiceTranscribing
import io.github.eslamasabry.oc.ui.words.res.tipHoldMic
import io.github.eslamasabry.oc.ui.words.res.voiceAllowMicInSettings
import io.github.eslamasabry.oc.ui.words.res.voiceInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicAllow
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicAsk
import io.github.eslamasabry.oc.ui.words.res.voiceModeMicBlocked
import io.github.eslamasabry.oc.ui.words.res.voiceModeNothingHeard
import io.github.eslamasabry.oc.ui.words.res.voiceNativeSendNow
import io.github.eslamasabry.oc.ui.words.res.voiceNativeSettingsAction
import io.github.eslamasabry.oc.ui.words.res.voiceNativeStoppedAfterPause
import io.github.eslamasabry.oc.ui.words.res.vpttDontSend
import io.github.eslamasabry.oc.ui.words.res.vpttLetGoToCancel
import io.github.eslamasabry.oc.ui.words.res.vpttLetGoToFinish
import io.github.eslamasabry.oc.ui.words.res.vpttSendingIn
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiModelRequired
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNotNow
import org.jetbrains.compose.resources.stringResource

/** The composer's voice slot (kit-chat `KitComposerVoice`), one ViewModel per conversation composer. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<KitComposerVoice>())
public class ComposerVoiceSlot : KitComposerVoice {
    @Composable
    override fun Mic(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        VoiceMic(state, presenter, modifier)
    }

    @Composable
    override fun Panel(field: KitVoiceField, modifier: Modifier) {
        val presenter = presenterFor(field)
        val state by presenter.state.collectAsStateWithLifecycle()
        val level = presenter.level.collectAsStateWithLifecycle()
        // Anything typed during the countdown cancels the send (Flutter `_autoSendText`).
        LaunchedEffect(presenter, field.key) { snapshotFlow { field.text() }.collect { presenter.fieldChanged(it) } }
        // Leaving the app ends a recording (keeping what was written) and the countdown (plan 10 §6.5).
        LifecycleStartEffect(presenter) {
            presenter.hostChanged(inForeground = true)
            onStopOrDispose { presenter.hostChanged(inForeground = false) }
        }
        VoicePanel(state, { level.value }, presenter, modifier, fieldText = field.text)
    }

    @Composable
    private fun presenterFor(field: KitVoiceField): ComposerVoicePresenter {
        val vm =
            assistedMetroViewModel<ComposerVoiceViewModel, ComposerVoiceViewModel.Factory>(key = "voice:${field.key}") {
                create(field.key)
            }
        val port = remember(field) { FieldPort(field) }
        vm.presenter.attach(port)
        return vm.presenter
    }
}

private class FieldPort(private val field: KitVoiceField) : VoiceFieldPort {
    override fun text(): String = field.text()

    override fun selection(): IntRange? = field.selection()

    override fun replace(text: String) = field.replace(text)

    override fun send() = field.send()

    override fun canSend(): Boolean = field.canSend()
}

/** The mic beside Send: tap to talk, hold to talk, slide away to cancel. Absent when voice cannot work here. */
@Composable
internal fun VoiceMic(state: ComposerVoiceUi, presenter: ComposerVoicePresenter, modifier: Modifier = Modifier) {
    if (!state.available) return
    KitMicButton(
        label = stringResource(Res.string.kitComposerVoice),
        onTap = presenter::tapMic,
        onHoldStart = presenter::holdStart,
        onHoldEnd = presenter::holdEnd,
        onArmedChanged = presenter::holdArmed,
        active = state.active,
        holdLabel = stringResource(Res.string.tipHoldMic),
        modifier = modifier,
    )
}

/** The pill while voice runs, or the one note after it. Nothing when idle. */
@Composable
internal fun VoicePanel(
    state: ComposerVoiceUi,
    level: () -> Float,
    presenter: ComposerVoicePresenter,
    modifier: Modifier = Modifier,
    fieldText: () -> String = { "" },
) {
    val cancel = KitLineAction(stringResource(Res.string.kitConfirmCancel), presenter::cancel, testTag = "voice-cancel")
    when (state.phase) {
        MicPhase.Starting ->
            KitVoicePill(
                title = stringResource(Res.string.kitVoiceStarting),
                modifier = modifier,
                secondary = cancel.takeUnless { state.holding },
            )
        MicPhase.Listening -> {
            val (minutes, seconds) = elapsedParts(state.elapsedSeconds)
            val title =
                when {
                    state.holding && state.cancelArmed -> Res.string.vpttLetGoToCancel
                    state.holding -> Res.string.vpttLetGoToFinish
                    else -> Res.string.kitVoiceListening
                }
            KitVoicePill(
                title = stringResource(title),
                modifier = modifier,
                words = state.words,
                elapsed = stringResource(Res.string.kitVoiceElapsed, minutes, seconds),
                level = level,
                attention = state.cancelArmed,
                // A held mic ends by the finger; a tap ends by Done (or a pause, when the person opted in).
                primary =
                    if (state.mode == VoiceMode.Tap)
                        KitLineAction(stringResource(Res.string.kitVoiceDone), presenter::done, testTag = "voice-done")
                    else null,
                secondary = cancel.takeIf { state.mode == VoiceMode.Tap },
            )
        }
        MicPhase.Writing ->
            KitVoicePill(title = stringResource(Res.string.kitVoiceTranscribing), modifier = modifier, words = state.words)
        MicPhase.Countdown ->
            KitVoicePill(
                title = stringResource(Res.string.vpttSendingIn, (state.secondsLeft ?: 0).toString()),
                modifier = modifier.testTag("voice-countdown"),
                words = fieldText(),
                attention = true,
                primary = KitLineAction(stringResource(Res.string.voiceNativeSendNow), presenter::sendNow, testTag = "voice-send-now"),
                secondary = KitLineAction(stringResource(Res.string.vpttDontSend), presenter::dontSend, testTag = "voice-dont-send"),
            )
        MicPhase.Idle -> VoiceNoteView(state.note, presenter, modifier)
    }
}

@Composable
private fun VoiceNoteView(note: VoiceNote?, presenter: ComposerVoicePresenter, modifier: Modifier) {
    note ?: return
    val dismiss = KitLineAction(stringResource(Res.string.e7VoiceUiNotNow), presenter::dismissNote)
    val settings = KitLineAction(stringResource(Res.string.voiceNativeSettingsAction), presenter::openSettings)
    when (note) {
        VoiceNote.NothingHeard ->
            KitNotice(stringResource(Res.string.voiceModeNothingHeard), modifier, dismiss = dismiss)
        VoiceNote.StoppedAfterPause ->
            KitNotice(stringResource(Res.string.voiceNativeStoppedAfterPause), modifier, dismiss = dismiss)
        is VoiceNote.MicNeeded ->
            KitNotice(
                message = stringResource(if (note.blocked) Res.string.voiceModeMicBlocked else Res.string.voiceModeMicAsk),
                modifier = modifier,
                title = stringResource(Res.string.kitVoiceMicDenied),
                tone = StatusTone.Attention,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(if (note.blocked) Res.string.voiceAllowMicInSettings else Res.string.voiceModeMicAllow),
                            presenter::allowMic,
                        ),
                    ),
                dismiss = dismiss,
            )
        VoiceNote.NeedsModel ->
            KitNotice(stringResource(Res.string.e7VoiceUiModelRequired), modifier, actions = listOf(settings), dismiss = dismiss)
        is VoiceNote.Failed ->
            KitNotice(
                message = problemWords(note.problem),
                modifier = modifier,
                tone = StatusTone.Failure,
                actions = listOf(settings),
                dismiss = dismiss,
            )
        VoiceNote.Unavailable ->
            KitNotice(stringResource(Res.string.voiceInputUnavailable), modifier, tone = StatusTone.Failure, dismiss = dismiss)
    }
}
