package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chats.presentation.RenameUi
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.p12Rename
import io.github.eslamasabry.oc.ui.words.res.p12RenameEmpty
import io.github.eslamasabry.oc.ui.words.res.p12RenameField
import io.github.eslamasabry.oc.ui.words.res.p12RenameTitle
import org.jetbrains.compose.resources.stringResource

/** Shared by home and All conversations, inside each screen's KitSheetHost. */
@Composable
internal fun ConversationRenameDialog(renaming: RenameUi?, onResult: (String?) -> Unit) {
    val emptyTitle = stringResource(Res.string.p12RenameEmpty)
    KitInputDialog(
        visible = renaming != null,
        title = stringResource(Res.string.p12RenameTitle),
        fieldLabel = stringResource(Res.string.p12RenameField),
        confirmLabel = stringResource(Res.string.p12Rename),
        initial = renaming?.initial.orEmpty(),
        validate = { if (it.isBlank()) emptyTitle else null },
        operationKey = renaming?.key,
        onResult = onResult,
    )
}
