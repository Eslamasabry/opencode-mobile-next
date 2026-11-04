package io.github.eslamasabry.oc.feature.chat.handoff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * A visible menu path; choosing a favorite destination never dispatches before the record and first
 * prompt review.
 */
@Composable
public fun StateHandoffSheet(state: StateHandoffUi, actions: StateHandoffActions) {
    KitSheet(
        visible = state.visible,
        onDismissRequest = actions::close,
        title = stringResource(Res.string.stateHandoffTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.loading) KitNotice(stringResource(Res.string.stateHandoffWorking))
            state.failure?.let {
                KitErrorNotice(stringResource(Res.string.stateHandoffFailed), failure = it)
            }
            if (state.transferFailed)
                KitNotice(stringResource(Res.string.stateHandoffTransferFailed))
            val draft = state.draft
            if (draft != null) {
                KitNotice(stringResource(Res.string.stateHandoffReviewNote))
                KitSectionLabel(stringResource(Res.string.stateHandoffRecord))
                KitText(draft.record.markdown)
                val prompt = remember(draft.recordId) { TextFieldState(draft.prompt) }
                KitField(
                    prompt,
                    stringResource(Res.string.stateHandoffPrompt),
                    kind = KitFieldKind.Multiline,
                )
                if (state.retryLink)
                    KitButton(
                        stringResource(Res.string.stateHandoffRetryLink),
                        actions::retryLink,
                        role = KitButtonRole.Secondary,
                    )
                else if (!state.transferFailed && !state.loading)
                    KitButton(
                        stringResource(Res.string.stateHandoffContinue),
                        { actions.send(prompt.text.toString()) },
                    )
            } else {
                KitSectionLabel(stringResource(Res.string.stateHandoffLinked))
                if (state.links.isEmpty() && !state.loading)
                    KitNotice(stringResource(Res.string.stateHandoffNoLinks))
                state.links.forEach { link ->
                    KitButton(
                        link.sourceTitle.ifBlank { link.targetAgent },
                        { actions.openCounterpart(link) },
                        role = KitButtonRole.Tertiary,
                    )
                }
                if (state.destinations.isNotEmpty()) {
                    KitSectionLabel(stringResource(Res.string.stateHandoffNew))
                    KitNotice(stringResource(Res.string.stateHandoffNewNote))
                    state.destinations.forEach { destination ->
                        KitButton(
                            destination.label,
                            { actions.prepare(destination) },
                            role = KitButtonRole.Tertiary,
                        )
                    }
                }
            }
        }
    }
}
