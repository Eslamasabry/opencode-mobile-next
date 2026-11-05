package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitNotice

/**
 * A one-value dialog whose save can fail in place. Flutter's showKitInputDialog helper, alternative
 * action and validation after the first edit/submit are kept by the kit.
 */
@Composable
public fun KitInputFormDialog(
    state: TextFieldState,
    title: String,
    fieldLabel: String,
    confirmLabel: String,
    cancelLabel: String,
    onSave: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KitFieldKind = KitFieldKind.Text,
    helper: String? = null,
    validate: (String) -> String? = { null },
    onTextChanged: (String) -> Unit = {},
    alternative: KitAction? = null,
    working: Boolean = false,
    enabled: KitEnabled = KitEnabled.Enabled,
    failure: String? = null,
) {
    var touched by remember(state) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val error = if (touched) validate(state.text.toString()) else null
    val submit = {
        touched = true
        if (!working && enabled.isEnabled && validate(state.text.toString()) == null) onSave()
    }
    KitDialogFrame(true, title, onDismissRequest, sheetOnCompact = true, dismissible = !working) {
        KitText(title, role = TypeRole.Title, kind = KitTextKind.Frame)
        KitField(
            state,
            fieldLabel,
            modifier,
            kind = kind,
            helper = helper,
            error = error,
            checking = working,
            enabled = enabled,
            focusRequester = focus,
            imeAction = ImeAction.Done,
            onSubmit = submit,
            onTextChanged = {
                touched = true
                onTextChanged(it)
            },
        )
        failure?.let { KitNotice(it) }
        KitActionBlock(
            primary = KitAction(confirmLabel, submit, working = working, enabled = enabled),
            tertiary =
                listOfNotNull(
                    alternative?.copy(working = working),
                    KitAction(cancelLabel, onDismissRequest, working = working),
                ),
        )
        LaunchedEffect(state) {
            state.edit { selectAll() }
            try {
                focus.requestFocus()
            } catch (_: IllegalStateException) {}
        }
    }
}
