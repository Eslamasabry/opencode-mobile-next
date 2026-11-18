package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.ConversationReplyNote
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Voice mode is a visible choice in the composer's + tray, rather than permanent chrome. */
@Composable
internal fun VoiceConversationEntry(
    state: ComposerVoiceUi,
    presenter: ComposerVoicePresenter,
    modifier: Modifier,
    onSelected: () -> Unit,
) {
    if (
        state.conversationAvailable &&
            state.available &&
            !state.conversation &&
            state.phase == MicPhase.Idle
    ) {
        KitRow(
            title = stringResource(Res.string.voiceConversationTitle),
            leading = KitRowLeading.Glyph(KitIcons.mic),
            onClick = {
                onSelected()
                presenter.startConversation()
            },
            // With words or files on the box the row stays and says why (B-461); a missing speech model or a refused
            // microphone is met by the tap: the download offer with its size, and the microphone note.
            enabled = !state.draft,
            disabledReason = stringResource(Res.string.voiceConversationDoorDraft),
            supporting = if (state.draft) stringResource(Res.string.voiceConversationDoorDraft) else null,
            modifier = modifier.testTag("voice-conversation-start"),
        )
    }
}

/** The hands-free voice mode's extra controls; its states are the box's recorder row ([VoiceRecorder]). */
@Composable
internal fun VoiceConversationControls(
    state: ComposerVoiceUi,
    presenter: ComposerVoicePresenter,
    modifier: Modifier,
) {
    if (!state.conversationAvailable || !state.available) return
    if (!state.conversation) {
        if (state.phase == MicPhase.Idle) {
            state.replyNote?.let { KitNotice(conversationNote(it), modifier) }
        }
        return
    }
    Column(modifier.testTag("voice-conversation")) {
        state.tutorialStep?.let { step ->
            KitNotice(
                message =
                    stringResource(
                        when (step) {
                            0 -> Res.string.voiceConversationNativeTourSpeak
                            1 -> Res.string.voiceConversationNativeTourReplies
                            else -> Res.string.voiceConversationNativeTourLeave
                        }
                    ),
                title = stringResource(Res.string.voiceConversationTitle),
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(
                                if (step >= 2) Res.string.voiceConversationNativeTourDone
                                else Res.string.voiceConversationNativeTourNext
                            ),
                            presenter::nextTutorial,
                        )
                    ),
                dismiss =
                    KitLineAction(
                        stringResource(Res.string.e7VoiceUiNotNow),
                        presenter::dismissTutorial,
                    ),
            )
        }
        // Listening, the agent working, the reply read aloud and resting are the box's own recorder row (one look for
        // one thing, coordinator 2026-11-17); its ✕ leaves the mode. Reading the reply again stays a visible action.
        if (state.phase == MicPhase.Idle && state.canReadReply && !state.speakingReply) {
            KitButton(
                label = stringResource(Res.string.kitVoiceReadReply),
                onClick = presenter::readReply,
                role = KitButtonRole.Tertiary,
            )
        }
        KitSwitchRow(
            title = stringResource(Res.string.kitVoiceReadAloud),
            checked = state.speakReplies,
            supporting = stringResource(Res.string.voiceConversationNativeSends),
            onCheckedChange = presenter::speakReplies,
        )
    }
    KitSheet(
        visible = state.pickingReplyVoice,
        onDismissRequest = presenter::closeReplyVoices,
        title = stringResource(Res.string.readAloudVoiceTitle),
        // Closing without a voice leaves reading aloud off: the corner declines it for now.
        closeLabel = stringResource(Res.string.e7VoiceUiNotNow),
    ) {
        KitChoiceList(
            choices =
                state.replyVoices.map {
                    KitChoice(value = it.id, title = it.label, supporting = it.localeTag)
                },
            selected = null,
            semanticsLabel = stringResource(Res.string.readAloudVoiceTitle),
            onSelect = presenter::pickReplyVoice,
        )
    }
    if (state.askingReplyConsent) {
        KitConfirm(
            spec =
                KitConfirmSpec(
                    title = stringResource(Res.string.readAloudConsentTitle),
                    body = stringResource(Res.string.readAloudConsentDetail),
                    consequences =
                        listOf(
                            stringResource(Res.string.readAloudConsentHeard),
                            stringResource(Res.string.readAloudConsentEngine),
                        ),
                    confirmLabel = stringResource(Res.string.readAloudContinue),
                    kind = KitConfirmKind.Neutral,
                    cancelLabel = stringResource(Res.string.e7VoiceUiNotNow),
                ),
            onConfirm = presenter::consentToReplies,
            onCancel = presenter::declineReplyConsent,
        )
    }
}

@Composable
internal fun conversationNote(note: ConversationReplyNote): String =
    stringResource(
        when (note) {
            ConversationReplyNote.Review -> Res.string.voiceConversationReplyReviewNeeded
            ConversationReplyNote.Interrupted -> Res.string.voiceConversationReplyInterrupted
            ConversationReplyNote.NoProse -> Res.string.voiceConversationReplyNoProse
            ConversationReplyNote.Failed -> Res.string.voiceConversationReplyFailed
            ConversationReplyNote.DraftFirst -> Res.string.voiceConversationDraftFirst
            ConversationReplyNote.Paused -> Res.string.voiceConversationPausedDetail
            ConversationReplyNote.CommandsOnly -> Res.string.voiceConversationCommandsOnly
        }
    )
