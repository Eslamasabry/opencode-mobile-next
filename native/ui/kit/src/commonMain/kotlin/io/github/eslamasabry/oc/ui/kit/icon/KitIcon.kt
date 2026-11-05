package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.color.StatusGlyph
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The three designed sizes (Dart `KitIconSize`, VL §7): 20 inline and in rows, 22 for a card mark, 24 for controls. */
public enum class KitIconSize(public val dp: Int) {
    Small(20),
    Medium(22),
    Large(24),
}

/**
 * The one way a screen draws a glyph (Dart `KitIcon`): a [KitIcons] glyph at a designed size, in a text tone, so an
 * icon always matches the words beside it. Decorative unless [label] is given. It keeps its size under the person's
 * text size unless [growsWithText] (then it grows up to [io.github.eslamasabry.oc.ui.design.tokens.Tokens.maxIconScale]
 * and lands on whole pixels).
 *
 * States: none (a disabled icon is the host passing [TextTone.Tertiary])
 * Motion: none
 * Replaces: KitIcon (lib/ui/kit/kit_icon.dart)
 */
@Composable
public fun KitIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: KitIconSize = KitIconSize.Large,
    tone: TextTone = TextTone.Primary,
    label: String? = null,
    growsWithText: Boolean = false,
) {
    KitGlyphBox(icon, LocalKitColors.current.tone(tone), size, modifier, label, growsWithText)
}

@Composable
private fun KitGlyphBox(
    icon: ImageVector,
    color: Color,
    size: KitIconSize,
    modifier: Modifier,
    label: String?,
    growsWithText: Boolean,
) {
    val density = LocalDensity.current.density
    val logical = size.dp.toFloat()
    val dimension =
        if (!growsWithText) logical
        else (logical * minOf(kitTextScale(), LocalKitTokens.current.maxIconScale) * density).roundToInt() / density
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(dimension.dp).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}

/**
 * A status in its own glyph and tone (Dart `KitIcon.status`, from `KitTokens.glyphFor` and `toneFor`): an empty ring
 * idle, turning arrows under way, a check done, a warning triangle needs-you, the neutral error glyph failed. Attention
 * and failure read in primary text and differ by shape. Grows with text by default.
 */
@Composable
public fun KitStatusIcon(
    status: StatusTone,
    modifier: Modifier = Modifier,
    size: KitIconSize = KitIconSize.Small,
    label: String? = null,
    growsWithText: Boolean = true,
) {
    val colors = LocalKitColors.current
    val glyph =
        when (status.glyph) {
            StatusGlyph.EmptyRing -> KitIcons.radioEmpty
            StatusGlyph.Sync -> KitIcons.sync
            StatusGlyph.Check -> KitIcons.check
            StatusGlyph.Warning -> KitIcons.warning
            StatusGlyph.Error -> KitIcons.error
        }
    val tone =
        when (status) {
            StatusTone.Neutral -> TextTone.Secondary
            StatusTone.Progress -> TextTone.Accent
            StatusTone.Ok -> TextTone.Success
            StatusTone.Attention, StatusTone.Failure -> TextTone.Primary
        }
    KitGlyphBox(glyph, colors.tone(tone), size, modifier, label, growsWithText)
}

/** The two sizes of the brand mark: the row tile and the standalone mark (Dart `KitBrandMarkSize`). */
public enum class KitBrandMarkSize { Tile, Mark }

private val brandMark: ImageVector by lazy {
    ImageVector.Builder("brandMark", Dp(60f), Dp(60f), 60f, 60f) // NV: the 60 x 60 viewBox of assets/branding/open-portal/mark.svg
        .apply {
            for (d in listOf("M37 7H19A12 12 0 0 0 7 19V40", "M53 20V41A12 12 0 0 1 41 53H20")) {
                addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 10f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) // NV: mark.svg stroke width 10
            }
        }
        .build()
}

/**
 * The Open Portal mark in the accent (Dart `KitBrandMark`): the icon-tile size as a row tile, [KitBrandMarkSize.Mark]
 * at the standalone size. Decorative unless [label] is given.
 */
@Composable
public fun KitBrandMark(
    modifier: Modifier = Modifier,
    size: KitBrandMarkSize = KitBrandMarkSize.Tile,
    label: String? = null,
) {
    val tokens = LocalKitTokens.current
    val dimension = if (size == KitBrandMarkSize.Tile) tokens.iconTile else FlutterKitTokens.MARK_SIZE
    Image(
        imageVector = brandMark,
        contentDescription = null,
        colorFilter = ColorFilter.tint(LocalKitColors.current.accent),
        modifier = modifier.size(dimension.dp).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}
