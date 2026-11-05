package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** Back (a direction glyph: it mirrors in RTL, B-KIT-001) or Close (never mirrors). */
internal enum class ExitGlyph { Back, Close, Chevron }

/**
 * The frame's exit and switcher glyphs: Phosphor back, close and caret from [KitIcons], at the control size (24 dp)
 * capped at the frame scale. Decorative: the control around it carries the label. Back and the caret mirror in RTL
 * through the glyphs' own auto-mirror (plan 06 §12.1 rule 2).
 */
@Composable
internal fun KitExitGlyph(glyph: ExitGlyph, color: Color, modifier: Modifier = Modifier) {
    val size = 24f * minOf(kitTextScale(), TextScalePolicy.FRAME_CAP)
    val icon =
        when (glyph) {
            ExitGlyph.Back -> KitIcons.back
            ExitGlyph.Close -> KitIcons.close
            ExitGlyph.Chevron -> KitIcons.chevronDown
        }
    KitGlyph(icon, color, if (glyph == ExitGlyph.Chevron) (size * 0.75f).dp else size.dp, modifier)
}
