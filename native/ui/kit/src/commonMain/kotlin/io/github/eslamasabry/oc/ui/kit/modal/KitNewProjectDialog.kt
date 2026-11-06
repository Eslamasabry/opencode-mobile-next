package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.projectFolderCancel
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreate
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateAction
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateHelper
import io.github.eslamasabry.oc.ui.words.res.projectFolderNameHint
import io.github.eslamasabry.oc.ui.words.res.projectFolderNameLabel
import org.jetbrains.compose.resources.stringResource

/** The shared name form for creating a project from Projects or a conversation's project sheet. */
@Composable
public fun KitNewProjectDialog(
    initial: String,
    validate: (String) -> String?,
    onSubmit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    working: Boolean = false,
    error: String? = null,
    onTextChanged: (String) -> Unit = {},
    details: (@Composable () -> Unit)? = null,
) {
    KitInputOperationDialog(
        visible = true,
        title = stringResource(Res.string.projectFolderCreate),
        fieldLabel = stringResource(Res.string.projectFolderNameLabel),
        confirmLabel = stringResource(Res.string.projectFolderCreateAction),
        cancelLabel = stringResource(Res.string.projectFolderCancel),
        initial = initial,
        hint = stringResource(Res.string.projectFolderNameHint),
        helper = stringResource(Res.string.projectFolderCreateHelper, "/root/projects"),
        working = working,
        error = error,
        validate = validate,
        details = details,
        onTextChanged = onTextChanged,
        onSubmit = onSubmit,
        onDismissRequest = onDismissRequest,
    )
}
