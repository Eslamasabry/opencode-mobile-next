package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** showKitInputDialog's checked-submit form: the caller keeps input while the asynchronous check answers in place. */
@Composable
public fun KitCheckedInputDialog(
    visible: Boolean,
    title: String,
    state: TextFieldState,
    fieldLabel: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    helper: String? = null,
    placeholder: String? = null,
    error: String? = null,
    working: Boolean = false,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    if (!visible) return
    val focus = remember { FocusRequester() }
    val canSubmit = enabled.isEnabled && !working
    val submit = { if (canSubmit) onConfirm() }
    KitDialogFrame(true, title, onDismissRequest = onDismiss, dismissible = !working) {
        KitText(title, role = TypeRole.Title, kind = KitTextKind.Frame)
        KitField(state, fieldLabel, helper = helper, placeholder = placeholder, error = error,
            checking = working, enabled = if (working) KitEnabled.Disabled(confirmLabel) else enabled, imeAction = ImeAction.Done, focusRequester = focus,
            onSubmit = submit)
        KitActionBlock(primary = KitAction(confirmLabel, submit, enabled = enabled, working = working),
            tertiary = listOf(KitAction(stringResource(Res.string.kitConfirmCancel), onDismiss,
                enabled = if (working) KitEnabled.Disabled(confirmLabel) else KitEnabled.Enabled)))
    }
    LaunchedEffect(state) {
        try { focus.requestFocus() } catch (_: IllegalStateException) { /* First frame not attached yet. */ }
    }
}
