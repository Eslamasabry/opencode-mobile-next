package io.github.eslamasabry.oc.ui.kit.foundation

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
 * The marks the part-B controls use: the Phosphor glyphs of [KitIcons] (the Dart `AppIconography` set). Direction
 * glyphs mirror themselves in RTL through the vector's auto-mirror, so [KitGlyph] never flips them.
 */
internal object KitPartGlyphs {
    val Check: ImageVector get() = KitIcons.check
    val Close: ImageVector get() = KitIcons.close
    val ChevronDown: ImageVector get() = KitIcons.chevronDown
    val ChevronUp: ImageVector get() = KitIcons.chevronUp

    /** Points to the end; the glyph mirrors itself in RTL. */
    val ChevronEnd: ImageVector get() = KitIcons.chevronRight
    val More: ImageVector get() = KitIcons.more
    val Eye: ImageVector get() = KitIcons.visible
    val EyeOff: ImageVector get() = KitIcons.hidden
    val Warning: ImageVector get() = KitIcons.warning
    val Ring: ImageVector get() = KitIcons.radioEmpty
    val Undo: ImageVector get() = KitIcons.undo
    val Copy: ImageVector get() = KitIcons.copy
    val Paste: ImageVector get() = KitIcons.paste
}

/** Draws [glyph] decoratively (no content description: the owning control carries the words), tinted [tint]. */
@Composable
internal fun KitGlyph(glyph: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(
        imageVector = glyph,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
