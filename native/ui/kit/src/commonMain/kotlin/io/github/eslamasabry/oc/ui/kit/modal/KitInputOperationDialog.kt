package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The controlled operation form of Flutter showKitInputDialog (kit_dialog.dart:426-688). The host
 * owns completion: an acknowledged success removes the dialog; a failure keeps its text. Unlike a
 * rename result, an unchanged valid initial value can be submitted (for example a suggested folder
 * name).
 */
@Composable
public fun KitInputOperationDialog(
    visible: Boolean,
    title: String,
    fieldLabel: String,
    confirmLabel: String,
    initial: String,
    onSubmit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    validate: (String) -> String? = { null },
    hint: String? = null,
    helper: String? = null,
    error: String? = null,
    working: Boolean = false,
    cancelLabel: String? = null,
    onTextChanged: (String) -> Unit = {},
    details: (@Composable () -> Unit)? = null,
) {
    if (!visible) return
    val field = rememberTextFieldState(initial)
    val original = remember { initial }
    val focus = remember { FocusRequester() }
    var selectedOnOpen by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    val invalid = validate(field.text.toString())
    val blocked = edited && invalid != null
    val busyReason = stringResource(Res.string.kitWorking)
    val cancel = cancelLabel ?: stringResource(Res.string.kitConfirmCancel)
    val held = if (working) KitEnabled.Disabled(busyReason) else KitEnabled.Enabled
    val submit = {
        if (!working) {
            edited = true
            if (validate(field.text.toString()) == null) onSubmit(field.text.toString())
        }
    }
    val close = { if (!working) onDismissRequest() }
    val primary =
        KitAction(
            confirmLabel,
            onClick = submit,
            working = working,
            enabled =
                if (working) held
                else if (blocked) KitEnabled.Disabled(invalid!!) else KitEnabled.Enabled,
        )
    val cancelAction = KitAction(cancel, close, enabled = held)
    val input: @Composable () -> Unit = {
        KitField(
            state = field,
            label = fieldLabel,
            placeholder = hint,
            helper = helper,
            error = error ?: invalid.takeIf { edited },
            enabled = held,
            focusRequester = focus,
            imeAction = ImeAction.Done,
            onSubmit = submit,
            onTextChanged = {
                edited = true
                onTextChanged(it)
            },
            modifier = Modifier.testTag("kit-operation-input"),
        )
        details?.invoke()
        // The modal body attaches after the caller registers its sheet. Request focus here,
        // and retain the person's selection when Keep editing returns from the discard question.
        LaunchedEffect(Unit) {
            if (!selectedOnOpen) {
                field.edit { selectAll() }
                selectedOnOpen = true
            }
            try {
                focus.requestFocus()
            } catch (_: IllegalStateException) {
                /* A detached field can be focused on the next body entry. */
            }
        }
    }
    KitSheet(
        visible = true,
        title = title,
        onDismissRequest = close,
        dismissible = !working,
        showClose = false,
        dirty = field.text.toString() != original,
        primary = primary,
        tertiary = listOf(cancelAction),
    ) {
        input()
    }
}
