package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusGlyph
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The tone glyph of a status (plan 06 §4.10): the Phosphor glyph of the Dart status map (`KitTokens.glyphFor`),
 * decorative (the words carry the meaning), and grown with text up to 1.5×. None of them has a direction.
 */
@Composable
internal fun KitStatusGlyph(glyph: StatusGlyph, color: Color, modifier: Modifier = Modifier) {
    val size = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    Image(
        statusGlyphIcon(glyph),
        null,
        modifier.size(size.dp).clearAndSetSemantics {},
        colorFilter = ColorFilter.tint(color),
    )
}

/**
 * The picture of a notice's or status line's tone. Neutral is the info mark (P-014: the empty ring read as an
 * unticked radio button and meant nothing beside a message); a row's own state ring stays in `KitIcon.status`.
 */
internal fun statusGlyphIcon(glyph: StatusGlyph): ImageVector =
    when (glyph) {
        StatusGlyph.EmptyRing -> KitIcons.info
        StatusGlyph.Sync -> KitIcons.sync
        StatusGlyph.Check -> KitIcons.checkCircle
        StatusGlyph.Warning -> KitIcons.warning
        StatusGlyph.Error -> KitIcons.error
    }
