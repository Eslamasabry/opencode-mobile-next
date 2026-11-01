package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The medium-window rail (Dart `KitNav` rail; plan 06 §7.3): 80 dp at the start, each destination a glyph with its
 * label always shown (labels up to 2.0×), the chosen one on a `surface3` tile with accent words. Compact windows have
 * no bar at all (owner 2026-10-20); the shell picks rail or sidebar by window class. [header] holds the shell's
 * controls above the destinations.
 *
 * States: default, selected, needs-you
 * Text: reading (labels capped at 2.0×)
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitNav rail (lib/ui/kit/kit_nav.dart)
 */
@Composable
public fun KitNavRail(
    destinations: List<KitNavDestination>,
    selectedId: String?,
    onSelect: (KitNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val hairline = colors.hairline
    Column(
        modifier
            .width(Layout.RAIL_WIDTH.dp)
            .fillMaxHeight()
            .background(colors.ground)
            .drawBehind {
                val x = if (layoutDirection == LayoutDirection.Rtl) 0.5f else size.width - 0.5f
                drawLine(hairline, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            .padding(vertical = Space.S2.dp)
            .selectableGroup()
            .testTag("kit-nav-rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        header?.invoke(this)
        destinations.forEach { d -> RailItem(d, d.id == selectedId) { onSelect(d) } }
    }
}

@Composable
private fun RailItem(d: KitNavDestination, chosen: Boolean, onClick: () -> Unit) {
    val colors = LocalKitColors.current
    val tone = if (chosen) TextTone.Accent else TextTone.Secondary
    KitTappable(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.S1.dp)
                .semantics(mergeDescendants = true) {
                    selected = chosen
                    contentDescription = d.label
                    if (d.needsYou > 0) d.needsYouWords?.let { stateDescription = it }
                }
                .testTag("kit-nav-${d.id}"),
        role = KitTappableRole.Tab,
        shape = KitShapeId.Panel,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (chosen) Modifier.background(colors.surface3, KitShapeId.Panel.toShape()) else Modifier)
                .padding(vertical = Space.S2.dp, horizontal = Space.S1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                d.icon?.let { NavGlyph(it, tone) }
                if (d.needsYou > 0) NavNeedsYouBadge(d.needsYou)
            }
            NavLabel(d.label, TypeRole.Label, tone, center = true)
        }
    }
}
