package io.github.eslamasabry.oc.feature.chat.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.engine.requests.forms.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.forms.FormRules
import io.github.eslamasabry.oc.engine.requests.forms.FormValue
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.form.KitForm
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormCard
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormField
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormFieldKind
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormOption
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormSheetBody
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormValue
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource

/**
 * Visible Answer opens the kit's sheet; requests never open a modal or move focus by themselves.
 */
@Composable
public fun ChatFormSlot(
    state: ChatFormUi,
    model: ChatFormsModel,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    if (form == null) {
        if (state.notice != null)
            Row(
                modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitReceipt(KitReceiptState.AnsweredElsewhere())
                KitButton(
                    stringResource(Res.string.stateFormDismiss),
                    onClick = model::acknowledgeNotice,
                )
            }
        return
    }
    val title = form.spec.title.ifBlank { stringResource(Res.string.stateFormTitle) }
    val phase = state.phase
    key(form.id, form.spec.revision) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            when (phase) {
                is AnswerPhase.Held ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                        KitText(
                            stringResource(Res.string.stateFormHeld),
                            Modifier.weight(1f),
                            role = TypeRole.Body,
                        )
                        KitButton(
                            stringResource(Res.string.stateFormUndo),
                            onClick = { model.undo(form.id, form.spec.revision) },
                        )
                    }
                is AnswerPhase.Sending ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                        KitText(title, Modifier.weight(1f), role = TypeRole.Body)
                        if (phase.slow)
                            KitText(
                                stringResource(Res.string.stateFormUncertain),
                                role = TypeRole.Secondary,
                            )
                        else KitReceipt(KitReceiptState.Sending)
                    }
                else ->
                    KitFormCard(
                        title = title,
                        summary = form.spec.description,
                        answerLabel = stringResource(Res.string.stateFormAnswer),
                        onAnswer = { model.open(form.id, form.spec.revision) },
                        error = (phase as? AnswerPhase.Refused)?.failure?.let { formFailure(it) },
                    )
            }
        }
        KitSheet(
            visible = state.open,
            onDismissRequest = model::dismiss,
            title = title,
            height =
                if (FormRules.prefersFullHeight(form.spec)) KitSheetHeight.Full
                else KitSheetHeight.Content,
            draft = true,
        ) {
            val evaluation = FormRules.evaluate(form.spec, state.draft)
            val invalid = stringResource(Res.string.stateFormInvalid)
            val fields =
                FormRules.visible(form.spec)
                    .map { field ->
                        KitFormField(
                            key = field.key,
                            label = field.label,
                            kind = KitFormFieldKind.valueOf(field.kind.name),
                            description = field.description,
                            placeholder = field.placeholder,
                            options =
                                field.options
                                    .map { KitFormOption(it.value, it.label, it.description) }
                                    .toImmutableList(),
                            active = field.key in evaluation.active,
                            sensitive = FormRules.isSensitive(field),
                            error = if (field.key in evaluation.problems) invalid else null,
                            maxLength = field.maxLength,
                            ownLabel =
                                if (field.custom) stringResource(Res.string.stateFormOther)
                                else null,
                            linkLabel =
                                if (field.url != null) stringResource(Res.string.stateFormOpenLink)
                                else null,
                        )
                    }
                    .toImmutableList()
            val values =
                state.draft
                    .mapValues { (_, value) ->
                        when (value) {
                            is FormValue.Text -> KitFormValue.Text(value.text)
                            is FormValue.Number -> KitFormValue.Text(value.value.toString())
                            is FormValue.Toggle -> KitFormValue.Toggle(value.on)
                            is FormValue.Choices -> KitFormValue.Choices(value.values)
                        }
                    }
                    .toImmutableMap()
            val notReady = stringResource(Res.string.stateFormRestoring)
            KitFormSheetBody(
                title = title,
                description = form.spec.description,
                sendLabel =
                    form.spec.submitLabel?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.stateFormSend),
                declineLabel = stringResource(Res.string.stateFormDecline),
                onSend = model::send,
                onDecline = model::askDecline,
                blockedReason =
                    if (!state.ready) notReady else if (!evaluation.canSend) invalid else null,
                error =
                    state.failure?.let { stringResource(Res.string.stateFormStorageFailed) }
                        ?: (phase as? AnswerPhase.Refused)?.failure?.let { formFailure(it) },
            ) {
                KitForm(
                    fields,
                    values,
                    onChange = { field, value ->
                        model.edit(
                            field,
                            when (value) {
                                is KitFormValue.Text -> FormValue.Text(value.text)
                                is KitFormValue.Toggle -> FormValue.Toggle(value.on)
                                is KitFormValue.Choices -> FormValue.Choices(value.values)
                            },
                        )
                    },
                    onOpenLink = { field ->
                        form.spec.fields.firstOrNull { it.key == field }?.url?.let(onOpenLink)
                    },
                    enabled =
                        if (state.ready) KitEnabled.Enabled else KitEnabled.Disabled(notReady),
                )
            }
        }
        KitConfirm(
            spec =
                if (state.confirmDecline)
                    KitConfirmSpec(
                        title = stringResource(Res.string.stateFormDeclineTitle),
                        body = stringResource(Res.string.stateFormDeclineBody),
                        confirmLabel = stringResource(Res.string.stateFormDecline),
                        cancelLabel = stringResource(Res.string.stateFormKeepEditing),
                    )
                else null,
            onConfirm = model::confirmDecline,
            onCancel = model::cancelDecline,
        )
    }
}

@Composable
private fun formFailure(failure: Failure): String =
    stringResource(
        when {
            failure is Failure.Refused && failure.reason == RefusalReason.DispatchUncertain ->
                Res.string.stateFormUncertain
            failure is Failure.Refused && failure.reason == RefusalReason.StaleScope ->
                Res.string.stateFormChanged
            else -> Res.string.stateFormNotAccepted
        }
    )
