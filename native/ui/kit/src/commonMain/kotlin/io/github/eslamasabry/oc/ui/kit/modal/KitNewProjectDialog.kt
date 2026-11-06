package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.projectFolderCancel
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreate
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateAction
import io.github.eslamasabry.oc.ui.words.res.homeProjectSheetNewDetail
import io.github.eslamasabry.oc.ui.words.res.projectFolderNewProject
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
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
        // N-41: the same words on every server (the phone's projects, or beside a computer's other projects).
        helper = stringResource(Res.string.homeProjectSheetNewDetail),
        working = working,
        error = error,
        validate = validate,
        details = details,
        onTextChanged = onTextChanged,
        onSubmit = onSubmit,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * N-41: the one New project row (Projects and a new conversation's project sheet): the add-folder glyph in its tile,
 * the title in the accent, one quiet line saying what it makes. While a folder is being made the row waits.
 *
 * States: default, working (disabled with the reason)
 * Text: frame (the title), reading (the line)
 * Motion: the row's own press
 */
@Composable
public fun KitNewProjectRow(onClick: () -> Unit, working: Boolean = false, modifier: Modifier = Modifier) {
    val wait = stringResource(Res.string.serversWorking)
    KitRow(
        title = stringResource(Res.string.projectFolderNewProject),
        modifier = modifier.testTag("new-project-row"),
        supporting = if (working) wait else stringResource(Res.string.homeProjectSheetNewDetail),
        leading = KitRowLeading.Glyph(KitIcons.folderAdd, tile = true),
        trailing = KitRowTrailing.Chevron,
        titleAccent = true,
        enabled = !working,
        disabledReason = wait,
        onClick = onClick,
    )
}
