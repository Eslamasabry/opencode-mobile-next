package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

private const val HALF_TURN = .5f // F: lib/ui/kit/motion/kit_motion_parts.dart:295
private const val DEGREES_PER_TURN = 360f // NV: unit conversion, turns to degrees

/** Where a row's trailing chevron puts its ink (Dart `KitOptical` + `KitRow.trailingRailOf`). */
public object KitRowOptical {
    /** The caret-down glyph's ink stands this fraction of its size in from its box's end. */
    public const val CHEVRON_DOWN_INK: Float = .15625f // F: lib/ui/kit/kit_optical.dart:27

    /** The caret-right glyph's ink end inset, as a fraction of its size. */
    public const val CHEVRON_RIGHT_INK: Float = .28125f // F: lib/ui/kit/kit_optical.dart:26

    /** A row's trailing rail: the ink ends space4 from the row's end, of which the row's own end padding is space1. */
    public val RAIL: Int = Space.S4 - Space.S1

    /** Dart `KitOptical.endPad`: the room after a glyph's box so its ink ends [rail] from the edge, never negative. */
    public fun endPad(ink: Float, size: Float, rail: Float): Float = maxOf(0f, rail - ink * size)
}

/**
 * A row that unfolds what it stands for in place (Dart `KitExpandRow`, kit_row_parts.dart:856-999): the header is a
 * row (title, one supporting line, the lead glyph) whose chevron points down when folded and turns up when open, and
 * [content] unfolds under it (`KitReveal`); reduced motion makes both instant. The screen holds [expanded]; a screen
 * reader hears expand / collapse actions. [bare] reveals [content] as Flutter does, with no padding of its own (the
 * children are rows); otherwise the content keeps the gutter and space2 gaps (forms inside). Use [inset] = false inside
 * a form that already owns its horizontal gutter, so the title and revealed fields share one left edge.
 *
 * States: folded, open, with a two-line supporting line
 * Text: reading
 * Cap: List
 * Motion: reveal (enter / exit specs), chevron half turn on STANDARD; Off = instant
 * Replaces: KitExpandRow (lib/ui/kit/kit_row_parts.dart)
 */
@Composable
public fun KitExpandRow(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onExpandedChange: (Boolean) -> Unit,
    leading: ImageVector? = null,
    inset: Boolean = true,
    /** One line (kit_row_parts.dart:879); from 1.3× text two. A preview title ends with "…" where it is cut. */
    titleMaxLines: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    KitExpandRow(title, expanded, modifier, supporting, onExpandedChange, leading, inset, titleMaxLines, 1, false, content)
}

/** [KitExpandRow] with the supporting line's lines (1 by default, 2 where the fold's line explains itself) and [bare]. */
@Composable
public fun KitExpandRow(
    title: String,
    expanded: Boolean,
    modifier: Modifier,
    supporting: String?,
    onExpandedChange: (Boolean) -> Unit,
    leading: ImageVector?,
    inset: Boolean,
    titleMaxLines: Int,
    supportingMaxLines: Int,
    bare: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val gutter = if (inset) kitRowGutter() else 0.dp
    val specs = KitMotion.specs
    val reduced = specs.state.reduced
    val scale = kitTextScale()
    val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f, specs.enter(), label = "expand-chevron")
    Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List)) {
        Row(
            Modifier.fillMaxWidth()
                .testTag("kit-expand-row-header")
                .kitTappable(onClick = { onExpandedChange(!expanded) }, role = KitTappableRole.Row, shape = KitShapeId.Square)
                .semantics(mergeDescendants = true) {
                    if (expanded) collapse { onExpandedChange(false); true } else expand { onExpandedChange(true); true }
                }
                // 56 dp with one line, 64 with two (kit_row.dart:497-507); the 48 dp chevron reaches into the words' room.
                .heightIn(min = (if (supporting != null) tokens.rowHeightTwo else tokens.rowHeightOne).dp)
                .padding(start = gutter, end = if (inset) Space.S1.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let { icon ->
                val glyph = (24f * minOf(scale, TextScalePolicy.ICON_CAP)).dp // F: lib/ui/kit/kit_row.dart:1085
                Box(
                    Modifier.padding(vertical = Space.S2.dp).size((tokens.iconTile * minOf(scale, TextScalePolicy.ICON_CAP)).dp).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Image(icon, null, Modifier.size(glyph), colorFilter = ColorFilter.tint(colors.text2))
                }
                Spacer(Modifier.width(Space.S3.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = Space.S2.dp)) {
                KitText(title, Modifier.testTag("kit-row-title"), role = TypeRole.RowTitle, maxLines = kitRowTitleLines(scale, titleMaxLines))
                supporting?.let {
                    Spacer(Modifier.size(FlutterKitTokens.ROW_LINE_GAP.dp))
                    KitText(it, Modifier.testTag("kit-row-supporting"), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = kitRowSupportingLines(scale, supportingMaxLines))
                }
            }
            Spacer(Modifier.width(Space.S2.dp))
            // The caret's ink on the row's end rail, like KitChevron's (kit_row_parts.dart:969-988).
            val rail = if (inset) KitRowOptical.RAIL.toFloat() else Space.S3.toFloat()
            val pad = KitRowOptical.endPad(KitRowOptical.CHEVRON_DOWN_INK, tokens.smallIcon.toFloat(), rail)
            Box(Modifier.size(tokens.minTarget.dp).padding(end = pad.dp), contentAlignment = Alignment.CenterEnd) {
                KitGlyph(KitPartGlyphs.ChevronDown, colors.text2, tokens.smallIcon.dp, Modifier.graphicsLayer { rotationZ = turn * DEGREES_PER_TURN })
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(specs.exit()) + fadeOut(specs.exit()),
        ) {
            if (bare) {
                Column(Modifier.fillMaxWidth(), content = content)
            } else {
                // The content sits between the gutters this row just gave it, so it is on a rail: a group of rows or a
                // card inside adds no second gutter (audit 2: an opened card stood 85 px in, the page's cards 42).
                androidx.compose.runtime.CompositionLocalProvider(LocalKitOnRail provides true) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
