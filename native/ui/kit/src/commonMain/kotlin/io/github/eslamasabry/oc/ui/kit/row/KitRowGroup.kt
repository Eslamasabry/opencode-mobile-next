package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitTrack
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/**
 * Rows that belong together (Dart `KitRowGroup`). In Solid, lists are flat (`StyleTokens.flatLists`): full width on the
 * ground, rows separated by one-pixel hairlines inset to the text rail (16 dp, or past the lead tile when the rows lead
 * with one: [leadingTiles]). [label] heads it (`KitSectionLabel`), [note] follows it (`KitGroupNote`). Rows that delete
 * or end something go in [destructive]: always last, after a full-width hairline (§4.2). Other styles' panels (18 dp
 * corners) join in S4.
 *
 * States: default, with label, with note, destructive
 * Text: none (its rows bring their own)
 * Cap: List
 * Motion: none
 * Hidden: none
 * Replaces: KitRowGroup (lib/ui/kit/kit_row_group_seam.dart)
 */
@Composable
public fun KitRowGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    note: String? = null,
    leadingTiles: Boolean = false,
    destructive: (@Composable () -> Unit)? = null,
    rows: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val hairline = LocalKitColors.current.hairline
    val inset = (tokens.gutter + if (leadingTiles) tokens.iconTile + Space.S3 else 0).dp
    val colors = LocalKitColors.current
    // Solid and Signal run lists flat on the page; Soft and Brutal set a group on a panel with 18 dp corners.
    val panel = !LocalKitStyle.current.spec.tokens.flatLists
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    val panelModifier =
        if (panel) Modifier.padding(horizontal = tokens.gutter.dp).then(look.modifier).clip(KitShapeId.Panel.shape()) else Modifier
    Column(modifier.kitMaxWidth(CapRole.List).fillMaxWidth().testTag("kit-row-group")) {
        label?.let { KitSectionLabel(it) }
        val style = LocalKitStyle.current
        Column(panelModifier.then(if (style.track) Modifier.kitTrack((tokens.gutter + tokens.iconTile / 2f).dp, live = false) else Modifier)) {
            KitSurfaceScope {
                HairlineColumn(inset, hairline, rows)
                destructive?.let { HairlineColumn(0.dp, hairline, it, ruleAbove = true) }
            }
        }
        note?.let { KitGroupNote(it) }
    }
}

/** Where the separators go, written by the measure pass and read by the draw pass of the same node. */
private class Seams {
    var ys: IntArray = IntArray(0)
}

/** Stacks [content] and draws one physical-pixel hairline between consecutive children, inset at the start. */
@Composable
private fun HairlineColumn(inset: Dp, hairline: Color, content: @Composable () -> Unit, ruleAbove: Boolean = false) {
    val seams = remember { Seams() }
    val style = LocalKitStyle.current
    val divider = kitSurfaceDivider(style, hairline, ruleAbove)
    Layout(
        content = content,
        modifier =
            Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val start = inset.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                val (x0, x1) = if (rtl) 0f to size.width - start else start to size.width
                val ys = if (ruleAbove) intArrayOf(0) + seams.ys else seams.ys
                ys.forEachIndexed { i, y ->
                    val full = ruleAbove && i == 0
                    val a = if (full) 0f else x0
                    val b = if (full) size.width else x1
                    if (divider.alpha > 0f) drawLine(divider, Offset(a, y + 0.5f), Offset(b, y + 0.5f), 1f)
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.sumOf { it.height }
        val ys = IntArray(maxOf(0, placeables.size - 1))
        var y = 0
        placeables.forEachIndexed { i, p ->
            y += p.height
            if (i < ys.size) ys[i] = y
        }
        seams.ys = ys
        layout(constraints.maxWidth, height) {
            var top = 0
            placeables.forEach {
                it.placeRelative(0, top)
                top += it.height
            }
        }
    }
}

@Composable
private fun kitSurfaceDivider(style: io.github.eslamasabry.oc.ui.kit.theme.KitStyleState, original: Color, full: Boolean): Color =
    Color(style.resolve(io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest(io.github.eslamasabry.oc.ui.design.effects.SurfaceRole.Divider, withinGroup = !full),
        io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current,
        io.github.eslamasabry.oc.ui.design.effects.SurfaceDecoration(io.github.eslamasabry.oc.ui.design.color.Argb(original.toArgb()))).fill.v)
