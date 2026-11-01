package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The slice-0 row shape (title, supporting, tap, grow key), kept so `:feature:chats` compiles unchanged. It is now the
 * row part, `row.KitRow`, with its chevron (a row that opens a page ends in one). With [growKey], the page opened from
 * it grows out of this row and shrinks back into it (plan 08 §5.4). The old Select haptic on tap is gone: plain taps
 * are silent (MOT-11).
 */
@Composable
public fun KitRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    growKey: Any? = null,
) {
    io.github.eslamasabry.oc.ui.kit.row.KitRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        trailing = io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing.Chevron,
        growKey = growKey,
        onClick = onClick,
    )
}
