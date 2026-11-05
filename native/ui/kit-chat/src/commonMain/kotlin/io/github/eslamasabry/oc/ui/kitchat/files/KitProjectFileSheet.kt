package io.github.eslamasabry.oc.ui.kitchat.files

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight

/**
 * Contract of the "+" tool "File from the project" sheet (Dart `showKitProjectFileSheet`,
 * lib/ui/kit/chat/kit_project_file_sheet.dart): a search field and the files it finds; a tap returns the file's
 * project-relative path, which the composer writes as an `@path` mention.
 */
public object KitProjectFileSheetSpec {
    /** The sheet's height: the whole window (`KitSheetHeight.full`). */
    public val height: KitSheetHeight = KitSheetHeight.Full // F: lib/ui/kit/chat/kit_project_file_sheet.dart:36

    /** The sheet's default key, for tests and semantics. */
    public const val SHEET_KEY: String = "project-file-sheet" // F: lib/ui/kit/chat/kit_project_file_sheet.dart:35

    /** The l10n key of the title (`l10n.mentionSheetTitle`, "Project files"). */
    public const val TITLE_KEY: String = "mentionSheetTitle" // F: lib/ui/kit/chat/kit_project_file_sheet.dart:34

    /** A folder in the initial listing is a path ending in this character. */
    public const val FOLDER_SUFFIX: Char = '/' // F: lib/ui/kit/chat/kit_project_file_sheet.dart:5

    /** Whether [entry] is a folder of an initial listing; search results are files only. */
    public fun isFolder(entry: String): Boolean = entry.endsWith(FOLDER_SUFFIX)
}

/**
 * The project-file sheet: full height, [title] ("Project files"), the [body] (search field and results) inside it.
 * Completes by [onDismissRequest]; the body calls it after returning the picked path.
 */
@Composable
public fun KitProjectFileSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    closeLabel: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title,
        closeLabel = closeLabel,
        height = KitProjectFileSheetSpec.height,
        body = body,
    )
}
