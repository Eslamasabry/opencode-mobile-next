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
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoicePill
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Conversation mode uses the same kit pill as dictation; every action is visible. */
@Composable
internal fun VoiceConversationControls(
    state: ComposerVoiceUi,
    presenter: ComposerVoicePresenter,
    modifier: Modifier,
) {
    if (!state.conversationAvailable || !state.available) return
    if (!state.conversation) {
        if (state.phase == MicPhase.Idle) {
            KitButton(
                label = stringResource(Res.string.voiceConversationTitle),
                onClick = presenter::startConversation,
                modifier = modifier.testTag("voice-conversation-start"),
                role = KitButtonRole.Tertiary,
            )
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
        if (state.phase == MicPhase.Idle) {
            val title =
                when {
                    state.speakingReply -> Res.string.kitVoiceSpeaking
                    state.waitingReply -> Res.string.kitVoiceWaitingReply
                    !state.canListen -> Res.string.voiceConversationPausedDetail
                    state.canReadReply -> Res.string.kitVoiceReplyReady
                    else -> Res.string.voiceConversationTitle
                }
            KitVoicePill(
                title = stringResource(title),
                words = state.replyNote?.let { conversationNote(it) },
                primary =
                    when {
                        state.speakingReply ->
                            KitLineAction(
                                stringResource(Res.string.kitVoiceStopReading),
                                presenter::stopReply,
                            )
                        state.canListen ->
                            KitLineAction(
                                stringResource(Res.string.kitVoiceListen),
                                presenter::listenConversation,
                            )
                        else -> null
                    },
                secondary =
                    KitLineAction(
                        stringResource(Res.string.kitVoiceLeave),
                        { presenter.exitConversation() },
                    ),
            )
            if (state.canReadReply) {
                KitButton(
                    label = stringResource(Res.string.kitVoiceReadReply),
                    onClick = presenter::readReply,
                    role = KitButtonRole.Tertiary,
                )
            }
        }
        KitSwitchRow(
            title = stringResource(Res.string.kitVoiceReadAloud),
            checked = state.speakReplies,
            supporting = stringResource(Res.string.voiceConversationNativeSends),
            onCheckedChange = presenter::speakReplies,
        )
        if (state.phase != MicPhase.Idle) {
            KitButton(
                label = stringResource(Res.string.kitVoiceLeave),
                onClick = { presenter.exitConversation() },
                role = KitButtonRole.Tertiary,
            )
        }
    }
    KitSheet(
        visible = state.pickingReplyVoice,
        onDismissRequest = presenter::closeReplyVoices,
        title = stringResource(Res.string.readAloudVoiceTitle),
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
                    confirmLabel = stringResource(Res.string.voiceConversationNativeAllowReplies),
                    cancelLabel = stringResource(Res.string.e7VoiceUiNotNow),
                ),
            onConfirm = presenter::consentToReplies,
            onCancel = presenter::declineReplyConsent,
        )
    }
}

@Composable
private fun conversationNote(note: ConversationReplyNote): String =
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
