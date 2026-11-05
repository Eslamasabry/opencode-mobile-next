package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
    val icon =
        when (glyph) {
            StatusGlyph.EmptyRing -> KitIcons.radioEmpty
            StatusGlyph.Sync -> KitIcons.sync
            StatusGlyph.Check -> KitIcons.checkCircle
            StatusGlyph.Warning -> KitIcons.warning
            StatusGlyph.Error -> KitIcons.error
        }
    Image(icon, null, modifier.size(size.dp).clearAndSetSemantics {}, colorFilter = ColorFilter.tint(color))
}
