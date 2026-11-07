package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitDividerInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The lazy group's rules, apart from the drawing (Dart `KitSliverRowGroup`). */
public object KitSliverRowGroupRules {
    /**
     * Whether the hairlines start where the words do (after a lead tile): [leadingIcons] when given; otherwise a list
     * with built-later rows assumes icons, and a list of [children] only reads them (Dart `leads`).
     */
    public fun leads(leadingIcons: Boolean?, itemCount: Int, childrenLead: Boolean, track: Boolean = false): Boolean =
        track || (leadingIcons ?: (itemCount > 0 || childrenLead))

    /** How far a bare panel's rows reach back into the side margin: space4, at most the margin itself. */
    public fun reach(bare: Boolean, sideMargin: Float): Float = if (bare) minOf(Space.S4.toFloat(), sideMargin) else 0f
}

/**
 * Rows on one panel in a lazy list (Dart `KitSliverRowGroup`; visual language §4, §5): the lazy twin of [KitRowGroup],
 * for a list too long to build at once. `surface1` with the panel's 18 dp corners (Solid sets the rows flat on the
 * page), a hairline of one physical pixel between rows inset to where their words start, and an optional [label] above
 * on the same inset as a [KitRowGroup]'s. The panel holds [children] first (a list's head: the few rows that are always
 * there), then [itemCount] rows from [itemContent], built only when they scroll into view. [margin] is the gutter at the
 * sides by default; [gapBefore] is the space above the label. It does not move destructive rows last: an act that ends
 * one lives on the row's menu. An empty group (no children, no items) draws nothing.
 *
 * States: none (each row carries its own states)
 * Text: none (its rows and label decide)
 * Cap: List
 * Motion: none of its own
 * Hidden: none
 * Replaces: KitSliverRowGroup (lib/ui/kit/kit_sliver_row_group.dart)
 */
public fun LazyListScope.kitSliverRowGroup(
    children: List<@Composable () -> Unit> = emptyList(),
    itemCount: Int = 0,
    itemKey: ((index: Int) -> Any)? = null,
    label: String? = null,
    labelTrailing: (@Composable () -> Unit)? = null,
    labelTerm: String? = null,
    leadingIcons: Boolean? = null,
    margin: Dp? = null,
    gapBefore: Dp? = null,
    itemContent: (@Composable (index: Int) -> Unit)? = null,
) {
    require(itemCount >= 0)
    require(itemCount == 0 || itemContent != null) { "KitSliverRowGroup: itemCount rows need an itemContent" }
    val head = children.size
    val total = head + itemCount
    if (total == 0) return
    if (label != null) {
        item(contentType = "kit-sliver-label") {
            // The label keeps the rows' inset: on a bare (flat) panel the rows reach back into the margin by their own
            // gutter, so the label takes that reach as its margin and its words start flush with theirs.
            Edge(margin, bare = LocalKitStyle.current.spec.tokens.flatLists) { reach ->
                KitSectionLabel(label, trailing = labelTrailing, explanation = labelTerm, margin = reach, gapBefore = gapBefore)
            }
        }
    } else if (gapBefore != null && gapBefore > 0.dp) {
        item(contentType = "kit-sliver-gap") { Spacer(Modifier.height(gapBefore)) }
    }
    val leads = KitSliverRowGroupRules.leads(leadingIcons, itemCount, childrenLead = false)
    for (index in 0 until head) {
        item(contentType = "kit-sliver-row") { Cell(index, total, leads, margin) { children[index]() } }
    }
    if (itemCount > 0) {
        items(itemCount, key = itemKey?.let { k -> { i: Int -> k(i) } }, contentType = { "kit-sliver-row" }) { i ->
            Cell(head + i, total, leads, margin) { itemContent!!(i) }
        }
    }
}

/** The list's cap and the side margin; [content] gets how far a bare panel's rows reach back into it. */
@Composable
private fun Edge(margin: Dp?, bare: Boolean, content: @Composable (reach: Dp) -> Unit) {
    val side = margin ?: LocalKitTokens.current.gutter.dp
    val reach = KitSliverRowGroupRules.reach(bare, side.value).dp
    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.List).padding(horizontal = side - reach)) { content(reach) }
}

/** One row of the panel: the rule above it (not on the first), the panel's fill, and its corners at the two ends. */
@Composable
private fun Cell(index: Int, total: Int, leads: Boolean, margin: Dp?, row: @Composable () -> Unit) {
    // Flat lists (Solid, Signal): the rows sit on the page itself, with square ends.
    val flat = LocalKitStyle.current.spec.tokens.flatLists
    val colors = LocalKitColors.current
    Edge(margin, bare = flat) { reach ->
        val first = index == 0
        val last = index == total - 1
        val radius = FlutterKitTokens.PANEL_CORNER_RADIUS.dp
        val shape = RoundedCornerShape(
            topStart = if (first) radius else 0.dp,
            topEnd = if (first) radius else 0.dp,
            bottomStart = if (last) radius else 0.dp,
            bottomEnd = if (last) radius else 0.dp,
        )
        val panel = if (flat) Modifier else Modifier.background(colors.surface1, shape).clip(shape)
        Column(Modifier.fillMaxWidth().then(panel)) {
            if (!first) KitDivider(inset = if (leads) KitDividerInset.Text else KitDividerInset.Gutter, endInset = reach)
            row()
        }
    }
}
