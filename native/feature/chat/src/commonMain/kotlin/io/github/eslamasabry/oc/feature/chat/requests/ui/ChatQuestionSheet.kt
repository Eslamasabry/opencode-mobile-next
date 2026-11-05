package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceGroup
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.activityAnswerEveryQuestion
import io.github.eslamasabry.oc.ui.words.res.activityOwnAnswer
import io.github.eslamasabry.oc.ui.words.res.activityQuestionOptional
import io.github.eslamasabry.oc.ui.words.res.activityQuestionProgress
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceNeedsInput
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceSendAnswers
import org.jetbrains.compose.resources.stringResource

/** The supported question form from Flutter `chat/question_sheet.dart`, on the existing request lane. */
@Composable
internal fun ChatQuestionSheet(state: QuestionSheetUi, actions: ChatRequestActions) {
    val question = state.question
    val id = question.id
    val tokens = LocalKitTokens.current
    KitSheet(
        visible = true,
        onDismissRequest = { actions.closeSheet(id) },
        title = stringResource(Res.string.e7WorkspaceNeedsInput),
        icon = KitIcons.question,
        primary = KitAction(
            stringResource(Res.string.e7WorkspaceSendAnswers),
            onClick = { actions.sendSheet(id) },
            enabled = if (state.canSend) KitEnabled.Enabled
                else KitEnabled.Disabled(stringResource(Res.string.activityAnswerEveryQuestion)),
        ),
    ) {
        Column(Modifier.fillMaxWidth().testTag("question-sheet")) {
            question.prompts.forEachIndexed { index, prompt ->
                key(question, index) {
                    val draft = state.drafts[index]
                    if (index > 0) Spacer(Modifier.height(tokens.sectionGap.dp))
                    if (question.prompts.size > 1) KitText(
                        stringResource(Res.string.activityQuestionProgress, index + 1, question.prompts.size),
                        role = TypeRole.Caption,
                        tone = TextTone.Secondary,
                    )
                    KitText(prompt.title, role = TypeRole.Headline)
                    Spacer(Modifier.height(Space.S1.dp))
                    KitText(prompt.question, role = TypeRole.Body, kind = KitTextKind.Reading)
                    if (prompt.optional) KitText(
                        stringResource(Res.string.activityQuestionOptional),
                        role = TypeRole.Caption,
                        tone = TextTone.Secondary,
                    )
                    if (prompt.choices.isNotEmpty()) {
                        Spacer(Modifier.height(Space.S3.dp))
                        KitChoiceGroup(
                            choices = prompt.choices.map { KitChoice(it.label, it.label, it.description.takeIf(String::isNotBlank)) },
                            selected = draft.chosen.toSet(),
                            semanticsLabel = prompt.title,
                            multiple = prompt.multiple,
                            onSelect = { actions.chooseInSheet(id, index, it) },
                        )
                    }
                    if (prompt.custom) {
                        Spacer(Modifier.height(Space.S3.dp))
                        val words = rememberTextFieldState(draft.own)
                        // Selecting a radio answer clears the model's own answer. Reflect that in the
                        // retained platform field without replaying the programmatic edit as a user edit.
                        LaunchedEffect(draft.own) {
                            if (words.text.toString() != draft.own) words.edit {
                                replace(0, length, draft.own)
                            }
                        }
                        KitField(
                            words,
                            label = stringResource(Res.string.activityOwnAnswer),
                            modifier = Modifier.testTag("question-own-answer-$index"),
                            imeAction = ImeAction.Done,
                            onTextChanged = { actions.editSheetOther(id, index, it) },
                        )
                    }
                }
            }
        }
    }
}
