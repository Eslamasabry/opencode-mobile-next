package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitProminentRule
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitList
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Rows that belong together (Dart `KitRowGroup`). In Solid, lists are flat (`StyleTokens.flatLists`): full width on the
 * ground, rows separated by one-pixel hairlines inset to the text rail (16 dp, or past the lead tile when the rows lead
 * with one: [leadingTiles]). [label] heads it (`KitSectionLabel`), [note] follows it (`KitGroupNote`). Rows that delete
 * or end something go in [destructive]: always last, after a gutter-inset hairline (§4.2). Other styles' panels (18 dp
 * corners) join in S4. A labelled group that follows other parts in one column (a sheet's lists) sets [section]: its
 * label takes the section gap above it, as a standalone `KitSectionLabel` does, so no row touches the next heading.
 *
 * Seam (Dart `_KitGroupSeam`, kit_row_group_seam.dart): an unlabelled group whose style frames its panel (Soft,
 * Brutal, …) set right under another group ([afterGroup]: nothing between them) keeps a space3 gap, so two framed
 * panels never touch or share an edge; anywhere else, and in flat styles, it keeps its place. Compose cannot see a
 * sibling, so the caller that stacks two groups says so.
 *
 * States: default, with label, with note, destructive, seam under another group
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
    section: Boolean = false,
    afterGroup: Boolean = false,
    rows: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val hairline = LocalKitColors.current.hairline
    // The rows inside a panel keep their gutter (the panel is a surface of its own), so read it inside the panel.
    val rowGutter = kitRowGutterDp(LocalKitOnRail.current && LocalKitStyle.current.spec.tokens.flatLists, tokens.gutter)
    val inset = (rowGutter + if (leadingTiles) tokens.iconTile + Space.S3 else 0).dp
    val colors = LocalKitColors.current
    // Solid and Signal run lists flat on the page; Soft and Brutal set a group on a panel with 18 dp corners.
    val panel = !LocalKitStyle.current.spec.tokens.flatLists
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    // A sheet's body already sits on the sheet's rail: the panel lines up with the sheet's title instead of stepping in
    // a second gutter (the owner's project sheet, 2026-11-06).
    val onRail = LocalKitOnRail.current
    // Glass keeps its pane inset inside the panel (owner 2026-11-09): the rows sit clear of its rim and sheen.
    val paneInset = io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset()
    val panelModifier =
        if (panel) Modifier.padding(horizontal = if (onRail) 0.dp else tokens.gutter.dp).then(look.modifier).clip(KitShapeId.Panel.shape()).padding(paneInset) else Modifier
    Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List).testTag("kit-row-group").kitList("KitRowGroup")) {
        label?.let { KitSectionLabel(it, gapBefore = if (section) tokens.sectionGap.dp else null) }
        val seam = kitRowGroupSeam(labelled = label != null, gapBefore = 0, frames = panel, afterGroup = afterGroup, gap = Space.S3)
        if (seam > 0) androidx.compose.foundation.layout.Spacer(Modifier.height(seam.dp).testTag("kit-row-group-seam"))
        Column(panelModifier) {
            // A panel is a surface of its own: its rows keep their gutter inside it, off the sheet's rail (P-008).
            androidx.compose.runtime.CompositionLocalProvider(LocalKitOnRail provides (onRail && !panel)) {
                HairlineColumn(inset, rowGutter.dp, hairline, rows)
                destructive?.let { HairlineColumn(rowGutter.dp, rowGutter.dp, hairline, it, ruleAbove = true) }
            }
        }
        note?.let { KitGroupNote(it) }
    }
}

/**
 * The space above a row group (Dart `_KitGroupSeam` and its use in `KitRowGroup.build`, kit_row.dart:1309,1339): [gap]
 * when the group has no label, no gap of its own before it, a framed panel, and another group right above it; else 0.
 */
public fun kitRowGroupSeam(labelled: Boolean, gapBefore: Int, frames: Boolean, afterGroup: Boolean, gap: Int): Int =
    if (!labelled && gapBefore == 0 && frames && afterGroup) gap else 0

/** True inside a surface whose content already sits on a rail (a KitSheet's body): panels add no gutter of their own. */
public val LocalKitOnRail: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The space a row, a section label or a list hairline keeps from its box's edge: the page gutter, or none on a sheet's
 * rail, where the rail already is the margin (P-008: rows on a sheet started 16 dp past its title, a third edge).
 */
public fun kitRowGutterDp(onRail: Boolean, gutter: Int): Int = if (onRail) 0 else gutter

/** [kitRowGutterDp] here. */
@Composable
public fun kitRowGutter(): Dp = kitRowGutterDp(LocalKitOnRail.current, LocalKitTokens.current.gutter).dp

/** Where the separators go, written by the measure pass and read by the draw pass of the same node. */
private class Seams {
    var ys: IntArray = IntArray(0)
}

/** Stacks [content] and draws one physical-pixel hairline between consecutive children, inset on both reading edges. */
@Composable
internal fun KitHairlineColumn(
    inset: Dp,
    endInset: Dp,
    hairline: Color,
    prominent: KitProminentRule? = null,
    content: @Composable () -> Unit,
): Unit = HairlineColumn(inset, endInset, hairline, content, prominent = prominent)

@Composable
private fun HairlineColumn(
    inset: Dp,
    endInset: Dp,
    hairline: Color,
    content: @Composable () -> Unit,
    ruleAbove: Boolean = false,
    prominent: KitProminentRule? = null,
) {
    val seams = remember { Seams() }
    Layout(
        content = content,
        modifier =
            Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val start = inset.toPx()
                val end = endInset.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                val (x0, x1) = if (rtl) end to size.width - start else start to size.width - end
                val ys = if (ruleAbove) intArrayOf(0) + seams.ys else seams.ys
                ys.forEach { y ->
                    if (x1 <= x0) return@forEach
                    if (prominent == null) drawLine(hairline, Offset(x0, y + 0.5f), Offset(x1, y + 0.5f), 1f)
                    else {
                        // B-173: the line starts on the seam's pixel row and is at least 1 dp thick, in the theme's tertiary ink at 30 %.
                        val t = prominent.thicknessDp.dp.toPx()
                        drawRect(prominent.color, Offset(x0, y.toFloat()), androidx.compose.ui.geometry.Size(x1 - x0, t))
                    }
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
