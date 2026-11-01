package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/**
 * The expanded-window sidebar (Dart `KitNav` sidebar): 296 dp at the start, growing with text up to 400 dp or a third
 * of the window (`Layout.sidebarWidth`). Each destination is a row with its glyph and label (up to 2.0×); the chosen
 * one sits on `surface3`. [header] holds the shell's controls (server pill, project switcher, search).
 *
 * States: default, selected, needs-you
 * Text: reading (labels capped at 2.0×)
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitNav sidebar (lib/ui/kit/kit_nav.dart)
 */
@Composable
public fun KitSidebar(
    destinations: List<KitNavDestination>,
    selectedId: String?,
    onSelect: (KitNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val hairline = colors.hairline
    val width = Layout.sidebarWidth(kitTextScale(), LocalKitWindow.current.widthDp)
    Column(
        modifier
            .width(width.dp)
            .fillMaxHeight()
            .background(colors.ground)
            .drawBehind {
                val x = if (layoutDirection == LayoutDirection.Rtl) 0.5f else size.width - 0.5f
                drawLine(hairline, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            .testTag("kit-sidebar")
            .padding(Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        header?.invoke(this)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            destinations.forEach { d -> SidebarItem(d, d.id == selectedId) { onSelect(d) } }
        }
    }
}

@Composable
private fun SidebarItem(d: KitNavDestination, chosen: Boolean, onClick: () -> Unit) {
    val colors = LocalKitColors.current
    val tone = if (chosen) TextTone.Accent else TextTone.Primary
    KitTappable(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    selected = chosen
                    if (d.needsYou > 0) d.needsYouWords?.let { stateDescription = it }
                }
                .testTag("kit-nav-${d.id}"),
        role = KitTappableRole.Tab,
        shape = KitShapeId.Button,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = LocalKitTokens.current.minTarget.dp)
                .then(if (chosen) Modifier.background(colors.surface3, KitShapeId.Button.toShape()) else Modifier)
                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            d.icon?.let { NavGlyph(it, tone) }
            NavLabel(d.label, TypeRole.RowTitle, tone, Modifier.weight(1f))
            if (d.needsYou > 0) NavNeedsYouBadge(d.needsYou)
        }
    }
}
