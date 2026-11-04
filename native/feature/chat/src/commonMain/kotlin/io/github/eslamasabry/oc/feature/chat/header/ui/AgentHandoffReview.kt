package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffFailureKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatAgentHandoffActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The actual masked record is reviewable before any file is written or conversation created. */
@Composable
internal fun AgentHandoffReview(
    sheet: HeaderSheet.ContinueWith,
    working: Boolean,
    actions: ChatAgentHandoffActions,
) {
    val draft = sheet.draft ?: return
    key(draft.recordId) {
        val prompt = rememberTextFieldState(draft.prompt)
        val invalid = stringResource(Res.string.continueWithMessageInvalid)
        val busy = stringResource(Res.string.continueWithStarting)
        Column(Modifier.fillMaxWidth().testTag("agent-handoff-review")) {
            if (working) KitNotice(message = busy)
            KitNotice(message = stringResource(Res.string.continueWithNewNote))
            KitNotice(message = stringResource(Res.string.continueWithSourceStays))
            KitNotice(message = stringResource(Res.string.continueWithReadPreview))
            if (draft.record.redacted)
                KitNotice(message = stringResource(Res.string.continueWithRedacted))
            if (draft.record.omittedMessages > 0)
                KitNotice(message = stringResource(Res.string.continueWithOlderLeftOut))
            KitSectionLabel(stringResource(Res.string.continueWithRecordTitle))
            KitCodeBlock(
                source = draft.record.markdown,
                kind = KitCodeKind.Output,
                wrap = true,
                maxLines = Int.MAX_VALUE,
            )
            KitNotice(
                message = stringResource(Res.string.continueWithRecordFile, draft.relativePath)
            )
            KitField(
                state = prompt,
                label = stringResource(Res.string.continueWithFirstMessage),
                helper = stringResource(Res.string.continueWithFirstMessageHelp),
                kind = KitFieldKind.Multiline,
                enabled = if (working) KitEnabled.Disabled(busy) else KitEnabled.Enabled,
                maxLength = 16_384,
            )
            val failure = sheet.handoffFailure
            if (failure != null) AgentHandoffFailure(failure)
            if (sheet.failure != null) {
                io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                    message = stringResource(Res.string.continueWithErrGeneric),
                    failure = sheet.failure,
                )
            }
            if (failure == HandoffFailureKind.LinkFailed) {
                KitButton(
                    stringResource(Res.string.continueWithSaveLinkAgain),
                    onClick = actions::retryHandoffLink,
                    enabled = if (working) KitEnabled.Disabled(busy) else KitEnabled.Enabled,
                )
            } else if (failure == null && sheet.failure == null) {
                val text = prompt.text.toString()
                KitButton(
                    stringResource(Res.string.continueWithStart),
                    onClick = { actions.sendHandoff(text) },
                    enabled =
                        when {
                            working -> KitEnabled.Disabled(busy)
                            text.isBlank() || !text.contains(draft.relativePath) ->
                                KitEnabled.Disabled(invalid)
                            else -> KitEnabled.Enabled
                        },
                )
            }
        }
    }
}

@Composable
internal fun AgentHandoffFailure(failure: HandoffFailureKind) {
    KitNotice(
        message =
            stringResource(
                when (failure) {
                    HandoffFailureKind.Unavailable -> Res.string.continueWithErrNoWriter
                    HandoffFailureKind.ProjectMismatch -> Res.string.continueWithErrProject
                    HandoffFailureKind.StaleScope -> Res.string.continueWithErrScope
                    HandoffFailureKind.TargetUnavailable -> Res.string.continueWithErrTarget
                    HandoffFailureKind.SourceChanged -> Res.string.continueWithErrSource
                    HandoffFailureKind.PreparationFailed -> Res.string.continueWithErrRead
                    HandoffFailureKind.InvalidDraft -> Res.string.continueWithMessageInvalid
                    HandoffFailureKind.FileWriteFailed -> Res.string.continueWithErrFile
                    HandoffFailureKind.CreationUnknown,
                    HandoffFailureKind.DeliveryUnknown -> Res.string.continueWithErrUnknown
                    HandoffFailureKind.LinkFailed -> Res.string.continueWithErrLink
                }
            )
    )
}
