package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/**
 * The kind glyph of a step: the Phosphor glyph the Flutter step rows use for the same tool (terminal, file, pencil,
 * magnifier...), tinted [color]. Decorative (the title says what the step did), so it carries no semantics. None of
 * them points a reading direction, so none mirrors.
 */
@Composable
internal fun KitToolGlyph(kind: KitToolKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(kind.icon(), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

internal fun KitToolKind.icon(): ImageVector =
    when (this) {
        KitToolKind.Shell -> KitIcons.terminal
        KitToolKind.Read -> KitIcons.fileText
        KitToolKind.Edit -> KitIcons.edit
        KitToolKind.Write -> KitIcons.editNote
        KitToolKind.Patch -> KitIcons.code
        KitToolKind.Search -> KitIcons.search
        KitToolKind.WebFetch -> KitIcons.globe
        KitToolKind.SubAgent -> KitIcons.agent
        KitToolKind.Todo -> KitIcons.checklist
        KitToolKind.Question -> KitIcons.question
        KitToolKind.Card -> KitIcons.article
        KitToolKind.Other -> KitIcons.more
    }

/** The fold chevron: Phosphor caret down when folded, up when open (vertical, so it never mirrors). */
@Composable
internal fun KitFoldChevron(opened: Boolean, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(if (opened) KitIcons.chevronUp else KitIcons.chevronDown, null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}
