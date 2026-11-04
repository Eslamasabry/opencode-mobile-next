package io.github.eslamasabry.oc.feature.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.requests.forms.*
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.form.*
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ConnectionFormScreen(state: ConnectionFormUi, actions: ConnectionFormActions) {
    val title =
        state.form?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.monitor40FormTitle)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitSheetClose),
                onExit = actions::close,
            )
        },
        loading = state.phase == ConnectionFormPhase.Loading,
    ) {
        Column(Modifier.fillMaxSize()) {
            when (state.phase) {
                ConnectionFormPhase.Loading -> Unit
                ConnectionFormPhase.Changed,
                ConnectionFormPhase.Unavailable ->
                    KitNotice(stringResource(Res.string.monitor40FormGone))
                ConnectionFormPhase.Settled -> {
                    KitNotice(stringResource(Res.string.monitor40FormSent))
                    KitButton(stringResource(Res.string.kitSheetClose), actions::close)
                }
                ConnectionFormPhase.AnsweredElsewhere -> {
                    KitNotice(stringResource(Res.string.monitor40FormElsewhere))
                    KitButton(stringResource(Res.string.kitSheetClose), actions::close)
                }
                else ->
                    state.form?.let { form ->
                        if (state.phase == ConnectionFormPhase.Held)
                            KitNotice(
                                stringResource(Res.string.monitor40FormHeld),
                                actions =
                                    listOf(
                                        KitLineAction(
                                            stringResource(Res.string.monitor40Undo),
                                            actions::undo,
                                        )
                                    ),
                            )
                        state.failure?.let { failure ->
                            KitErrorNotice(
                                message = stringResource(Res.string.monitor40FormFailed),
                                failure = failure,
                                detailsLabel = stringResource(Res.string.kitDetails),
                                copyLabel = stringResource(Res.string.kitCopyDetails),
                            )
                        }
                        val waiting =
                            state.phase == ConnectionFormPhase.Waiting ||
                                state.phase == ConnectionFormPhase.Refused
                        val errors = state.evaluation?.problems.orEmpty()
                        val fields =
                            FormRules.visible(form)
                                .map { field ->
                                    KitFormField(
                                        field.key,
                                        field.label,
                                        KitFormFieldKind.valueOf(field.kind.name),
                                        description = field.description,
                                        placeholder = field.placeholder,
                                        options =
                                            field.options
                                                .map {
                                                    KitFormOption(
                                                        it.value,
                                                        it.label,
                                                        it.description,
                                                    )
                                                }
                                                .toImmutableList(),
                                        active = field.key in state.evaluation?.active.orEmpty(),
                                        sensitive = FormRules.isSensitive(field),
                                        error = errors[field.key]?.let { problemWords(it) },
                                        maxLength = field.maxLength,
                                        ownLabel =
                                            if (field.custom)
                                                stringResource(Res.string.monitor40Other)
                                            else null,
                                        linkLabel =
                                            if (field.kind == FieldKind.ExternalLink)
                                                stringResource(Res.string.monitor40OpenLink)
                                            else null,
                                    )
                                }
                                .toImmutableList()
                        val values =
                            state.draft
                                .mapValues { (_, value) ->
                                    when (value) {
                                        is FormValue.Text -> KitFormValue.Text(value.text)
                                        is FormValue.Number ->
                                            KitFormValue.Text(value.value.toString())
                                        is FormValue.Toggle -> KitFormValue.Toggle(value.on)
                                        is FormValue.Choices -> KitFormValue.Choices(value.values)
                                    }
                                }
                                .toImmutableMap()
                        KitFormSheetBody(
                            title = title,
                            description = form.description,
                            sendLabel =
                                form.submitLabel ?: stringResource(Res.string.monitor40Send),
                            declineLabel = stringResource(Res.string.monitor40Decline),
                            onSend = actions::send,
                            onDecline = actions::askDecline,
                            blockedReason =
                                if (errors.isNotEmpty())
                                    stringResource(Res.string.monitor40FixFields)
                                else null,
                            sending =
                                if (waiting) null
                                else
                                    stringResource(
                                        if (state.slow) Res.string.monitor40NotConfirmed
                                        else Res.string.monitor40Sending
                                    ),
                            modifier = Modifier.weight(1f),
                        ) {
                            KitForm(
                                fields,
                                values,
                                { key, value ->
                                    actions.edit(
                                        key,
                                        when (value) {
                                            is KitFormValue.Text -> FormValue.Text(value.text)
                                            is KitFormValue.Toggle -> FormValue.Toggle(value.on)
                                            is KitFormValue.Choices ->
                                                FormValue.Choices(value.values)
                                        },
                                    )
                                },
                                onOpenLink = actions::openLink,
                                enabled =
                                    if (waiting) KitEnabled.Enabled
                                    else
                                        KitEnabled.Disabled(
                                            stringResource(Res.string.monitor40Sending)
                                        ),
                            )
                        }
                    } ?: KitButton(stringResource(Res.string.kitTryAgain), actions::retry)
            }
        }
        KitConfirm(
            spec =
                when {
                    state.declineQuestion ->
                        KitConfirmSpec(
                            stringResource(Res.string.monitor40DeclineQuestion),
                            stringResource(Res.string.monitor40Decline),
                            kind = KitConfirmKind.Destructive,
                            body = stringResource(Res.string.monitor40DeclineBody),
                        )
                    state.linkHost != null ->
                        KitConfirmSpec(
                            stringResource(Res.string.monitor40LinkQuestion),
                            stringResource(Res.string.monitor40OpenLink),
                            body = state.linkHost,
                        )
                    else -> null
                },
            onConfirm = { if (state.declineQuestion) actions.decline() else actions.approveLink() },
            onCancel = actions::dismiss,
        )
    }
}

@Composable
private fun problemWords(problem: FieldProblem): String =
    when (problem) {
        FieldProblem.Required -> stringResource(Res.string.monitor40Required)
        FieldProblem.NotANumber -> stringResource(Res.string.monitor40Number)
        FieldProblem.NotAWholeNumber -> stringResource(Res.string.monitor40Integer)
        FieldProblem.NoMatch,
        FieldProblem.NotOffered -> stringResource(Res.string.monitor40NotOffered)
        is FieldProblem.TooShort -> stringResource(Res.string.monitor40MinLength, problem.minLength)
        is FieldProblem.TooLong -> stringResource(Res.string.monitor40MaxLength, problem.maxLength)
        is FieldProblem.OutOfRange ->
            stringResource(
                Res.string.monitor40Range,
                problem.min?.toString().orEmpty(),
                problem.max?.toString().orEmpty(),
            )
        is FieldProblem.TooFewItems ->
            stringResource(Res.string.monitor40MinItems, problem.minItems)
        is FieldProblem.TooManyItems ->
            stringResource(Res.string.monitor40MaxItems, problem.maxItems)
    }

/**
 * A targeted monitor form stays a card in its real conversation until the person chooses Answer.
 */
@Composable
public fun MonitoredFormCard(state: ConnectionFormUi, onAnswer: () -> Unit) {
    val form = state.form ?: return
    if (
        state.phase in
            setOf(
                ConnectionFormPhase.Changed,
                ConnectionFormPhase.Settled,
                ConnectionFormPhase.AnsweredElsewhere,
            )
    )
        return
    KitFormCard(
        title =
            form.title.takeIf { it.isNotBlank() } ?: stringResource(Res.string.monitor40FormTitle),
        summary = stringResource(Res.string.monitor40QuestionCount, form.fields.size),
        answerLabel = stringResource(Res.string.monitor40Answer),
        onAnswer = onAnswer,
    )
}
