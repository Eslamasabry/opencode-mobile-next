package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffFailureKind
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffPhase
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HandoffBody(sheet: HeaderSheet.Handoff, actions: ChatHeaderActions) {
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("agent-handoff-review")
    ) {
        KitNotice(message = stringResource(Res.string.nativeHandoffIntro))
        val state = sheet.state
        if (state == null) {
            if (sheet.loading)
                KitNotice(message = stringResource(Res.string.nativeHandoffPreparing))
            sheet.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.nativeHandoffFailed),
                    failure = it,
                )
            }
            KitRowGroup {
                sheet.choices.forEach { choice ->
                    KitRow(
                        choice.label,
                        supporting = choice.providerId,
                        onClick = { actions.handoffSelect(choice) },
                    )
                }
            }
            if (!sheet.loading && sheet.choices.isEmpty())
                KitNotice(message = stringResource(Res.string.continueWithNothingReady))
        } else {
            sheet.label?.let { KitNotice(message = it) }
            when (state.phase) {
                HandoffPhase.Idle,
                HandoffPhase.Loading ->
                    KitNotice(message = stringResource(Res.string.nativeHandoffPreparing))
                HandoffPhase.Ready ->
                    state.draft?.let { draft ->
                        KitNotice(message = stringResource(Res.string.nativeHandoffReview))
                        KitCodeBlock(
                            draft.record.markdown,
                            KitCodeKind.Output,
                            maxLines = Int.MAX_VALUE,
                            wrap = true,
                        )
                        val prompt = remember(draft.recordId) { TextFieldState(draft.prompt) }
                        KitField(
                            prompt,
                            stringResource(Res.string.nativeHandoffPrompt),
                            kind = KitFieldKind.Multiline,
                            maxLength = 16384,
                        )
                        KitButton(
                            stringResource(Res.string.nativeHandoffSend),
                            { actions.handoffSend(prompt.text.toString()) },
                        )
                    }
                HandoffPhase.WritingFile,
                HandoffPhase.Creating,
                HandoffPhase.Sending,
                HandoffPhase.Linking ->
                    KitNotice(message = stringResource(Res.string.nativeHandoffSending))
                HandoffPhase.Complete -> {
                    KitNotice(message = stringResource(Res.string.nativeHandoffSucceeded))
                    KitButton(stringResource(Res.string.importOpen), actions::handoffOpen)
                }
                HandoffPhase.Uncertain ->
                    KitNotice(message = stringResource(Res.string.nativeHandoffUnconfirmed))
                HandoffPhase.Failed -> {
                    KitNotice(
                        message =
                            stringResource(
                                if (state.failure == HandoffFailureKind.LinkFailed)
                                    Res.string.nativeHandoffLinkFailed
                                else Res.string.nativeHandoffFailed
                            )
                    )
                    if (state.failure == HandoffFailureKind.LinkFailed) {
                        KitButton(
                            stringResource(Res.string.nativeHandoffSaveLink),
                            actions::handoffRetryLink,
                        )
                    }
                }
            }
        }
    }
}
