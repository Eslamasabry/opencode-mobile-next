package io.github.eslamasabry.oc.feature.setupreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.setupreview.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GuidedSetupContent(state: SetupGuidedUi, actions: SetupReviewActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitGroupNote(stringResource(Res.string.setupNativeGuidedBody))
        KitRowGroup(label = stringResource(Res.string.setupNativeChooseAction)) {
            for (intent in SetupGuidedIntent.entries) KitChoiceRow(
                title =
                    stringResource(
                        when (intent) {
                            SetupGuidedIntent.ChooseModel -> Res.string.setupNativeChooseModel
                            SetupGuidedIntent.DefaultAgent -> Res.string.setupNativeDefaultAgent
                            SetupGuidedIntent.Permission -> Res.string.setupNativePermission
                            SetupGuidedIntent.RemoteMcp -> Res.string.setupNativeRemoteMcp
                            SetupGuidedIntent.LocalMcp -> Res.string.setupNativeLocalMcp
                            SetupGuidedIntent.RemoveMcp -> Res.string.setupNativeRemoveMcp
                        }
                    ),
                selected = state.intent == intent,
                onClick = { actions.chooseIntent(intent) },
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            for (field in state.intent.fields()) key(state.intent, field) {
                if (field == SetupGuidedField.Effect) {
                    for ((value, label) in
                        listOf(
                            "ask" to Res.string.setupNativeAsk,
                            "allow" to Res.string.setupNativeAllow,
                            "deny" to Res.string.setupNativeDeny,
                        )) {
                        KitChoiceRow(
                            stringResource(label),
                            selected = state.inputs[field] == value,
                            onClick = { actions.editInput(field, value) },
                        )
                    }
                } else {
                    val text = remember { TextFieldState(state.inputs[field].orEmpty()) }
                    KitField(
                        state = text,
                        label =
                            stringResource(
                                when (field) {
                                    SetupGuidedField.Model -> Res.string.setupNativeModelField
                                    SetupGuidedField.Agent -> Res.string.setupNativeAgentField
                                    SetupGuidedField.Tool -> Res.string.setupNativeToolField
                                    SetupGuidedField.Name -> Res.string.setupNativeNameField
                                    SetupGuidedField.Url -> Res.string.setupNativeUrlField
                                    SetupGuidedField.Command -> Res.string.setupNativeCommandField
                                    SetupGuidedField.Effect -> Res.string.setupNativeEffectField
                                }
                            ),
                        kind =
                            if (field == SetupGuidedField.Command) KitFieldKind.Multiline
                            else if (field == SetupGuidedField.Url) KitFieldKind.Url
                            else KitFieldKind.Text,
                        helper =
                            when (field) {
                                SetupGuidedField.Tool ->
                                    stringResource(Res.string.setupNativeToolHelp)
                                SetupGuidedField.Command ->
                                    stringResource(Res.string.setupNativeCommandHelp)
                                else -> null
                            },
                        maxLength = 4096,
                        onTextChanged = { actions.editInput(field, it) },
                    )
                }
            }
            KitButton(
                stringResource(Res.string.setupNativePrepare),
                actions::prepareProposal,
                role = KitButtonRole.Secondary,
            )
        }
        state.reply?.let { reply ->
            when (reply.phase) {
                SetupGuidedPhase.NeedsInput ->
                    KitErrorNotice(stringResource(Res.string.setupNativeInvalidInputs))
                SetupGuidedPhase.Unavailable ->
                    KitErrorNotice(stringResource(Res.string.setupNativeProposalUnavailable))
                SetupGuidedPhase.Proposed -> {
                    KitGroupNote(stringResource(Res.string.setupNativeProposalBody))
                    KitRowGroup(label = stringResource(Res.string.setupNativeProposal)) {
                        for (change in reply.changes) KitRow(
                            title = change.name,
                            supporting =
                                if (state.intent == SetupGuidedIntent.RemoveMcp)
                                    stringResource(Res.string.setupNativeRemoveDefinition)
                                else change.value,
                            supportingMaxLines = 8,
                        )
                    }
                }
            }
        }
    }
}
