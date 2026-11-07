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
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron

/**
 * The kind glyph of a step: the Phosphor glyph the Flutter step rows use for the same tool (terminal, file, pencil,
 * magnifier...), tinted [color]. Decorative (the title says what the step did), so it carries no semantics. None of
 * them points a reading direction, so none mirrors.
 */
@Composable
internal fun KitToolGlyph(kind: KitToolKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    KitToolGlyph(kind.icon(), color, size, modifier)
}

/** [icon] drawn at [size] (a node's glyph is 0.6 of its tile, between the kit's icon sizes). */
@Composable
internal fun KitToolGlyph(icon: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

/** Dart `_glyphFor` (lib/ui/kit/chat/kit_tool_row.dart:304-315): every edit is the note-with-pencil glyph. */
internal fun KitToolKind.icon(): ImageVector =
    when (this) {
        KitToolKind.Shell -> KitIcons.terminal
        KitToolKind.Read -> KitIcons.fileText
        KitToolKind.List -> KitIcons.folderOpen
        KitToolKind.Edit,
        KitToolKind.Write,
        KitToolKind.Patch -> KitIcons.editNote
        KitToolKind.Search -> KitIcons.search
        KitToolKind.WebFetch -> KitIcons.globe
        KitToolKind.SubAgent -> KitIcons.agent
        KitToolKind.Todo -> KitIcons.checklist
        KitToolKind.Question -> KitIcons.question
        KitToolKind.Card -> KitIcons.article
        KitToolKind.Other -> KitIcons.tools
    }

/**
 * The fold chevron (M-016): the kit's one disclosure chevron ([KitChevron], Flutter `KitSpin.chevron`), down when folded
 * and turning half a turn to up when open instead of flipping in one frame; Calm cross-fades, reduced motion is
 * instant. Vertical, so it never mirrors; `text2` at the small icon size.
 */
@Composable
internal fun KitFoldChevron(opened: Boolean, modifier: Modifier = Modifier) {
    KitChevron(opened, modifier)
}
