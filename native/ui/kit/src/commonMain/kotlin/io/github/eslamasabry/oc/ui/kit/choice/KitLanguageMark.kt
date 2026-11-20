package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** What a language row's round mark shows. */
@Immutable
public sealed interface KitLanguageMark {
    /** The round flag of a country by its two letters (`US`, `EG`); one with no shape draws as [fallback] letters. */
    public data class Flag(val country: String, val fallback: String) : KitLanguageMark

    /** A kit glyph on the round mark (the phone, for "System default"). */
    public data class Glyph(val icon: ImageVector) : KitLanguageMark
}

/**
 * The round mark at the start of a language row: a flag, two letters or a glyph, 40 dp, round. The chosen row's mark
 * wears an accent ring (the row itself takes the kit's chosen band). Plan 06 §7.5 neighbours: [KitChoiceRow] takes it
 * as its `leading`.
 */
@Composable
public fun KitLanguageMark(mark: KitLanguageMark, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val ring = if (selected) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier
    when (mark) {
        is KitLanguageMark.Flag ->
            if (KitFlagShapes.flags.containsKey(mark.country)) KitCircleFlag(mark.country, modifier.then(ring))
            else Letters(mark.fallback, selected, modifier)
        is KitLanguageMark.Glyph ->
            Box(
                modifier.size(MARK_SIZE).clip(CircleShape).background(colors.surface3).then(ring),
                contentAlignment = Alignment.Center,
            ) {
                KitIcon(
                    mark.icon,
                    size = KitIconSize.Large,
                    tone = if (selected) TextTone.Accent else TextTone.Secondary,
                )
            }
    }
}

@Composable
private fun Letters(text: String, selected: Boolean, modifier: Modifier) {
    val colors = LocalKitColors.current
    Box(
        modifier
            .size(MARK_SIZE)
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.accent.copy(alpha = LETTERS_FILL_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        KitText(
            text,
            role = TypeRole.Label,
            tone = if (selected) TextTone.OnAccent else TextTone.Accent,
            kind = KitTextKind.Frame,
            maxLines = 1,
        )
    }
}

/** A round flag drawn from [KitFlagShapes] (Circle Flags, MIT): vector fills on a circle, sharp at any size. */
@Composable
public fun KitCircleFlag(country: String, modifier: Modifier = Modifier, size: Dp = MARK_SIZE) {
    val fills =
        remember(country) {
            KitFlagShapes.flags[country].orEmpty().map { (rgb, data) ->
                Color((OPAQUE + rgb).toLong(HEX)) to PathParser().parsePathString(data).toPath()
            }
        }
    Canvas(modifier.size(size).clip(CircleShape)) {
        scale(this.size.width / FLAG_CANVAS, Offset.Zero) {
            fills.forEach { (color, path) -> drawPath(path as Path, color) }
        }
    }
}

private val MARK_SIZE = 40.dp
private const val OPAQUE = "FF"
private const val HEX = 16
private const val FLAG_CANVAS = 512f
private const val LETTERS_FILL_ALPHA = 0.16f
