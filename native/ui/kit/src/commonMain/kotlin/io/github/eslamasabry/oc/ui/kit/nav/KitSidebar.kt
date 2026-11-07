package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouBadge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/**
 * The expanded-window sidebar (Dart `KitNavRail(extended: true)`, kit_nav.dart): 296 dp at the start, growing with
 * text up to 400 dp or a third of the window (`Layout.sidebarWidth`), on the page's ground parted from the canvas by a
 * hairline. Top to bottom: [header] (the shell's controls), the destinations as rows (glyph and label; the chosen one a
 * chosen control on `surface3`), the chosen destination's own pane under a section gap, and the pinned [primary].
 *
 * States: default, selected, needs-you Text: reading (labels capped at 2.0×) Cap: Fill Motion: none Hidden: none
 * Replaces: KitNavRail extended (lib/ui/kit/kit_nav.dart)
 */
@Composable
public fun KitSidebar(
    destinations: List<KitNavDestination>,
    selectedId: String?,
    onSelect: (KitNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    primary: KitAction? = null,
) {
    val colors = LocalKitColors.current
    val hairline = colors.hairline
    val ground = colors.ground
    val rule = kitHairline()
    val width = Layout.sidebarWidth(kitTextScale(), LocalKitWindow.current.widthDp)
    val pane = destinations.firstOrNull { it.id == selectedId }?.pane
    Column(
        modifier
            .width(width.dp)
            .fillMaxHeight()
            .drawBehind {
                drawRect(ground)
                // The end border (Dart BorderDirectional end): a hairline inside the end edge. A sub-pixel hairline covers
                // that share of the last pixel column, so it is drawn as that column at the hairline's coverage.
                val stroke = rule.toPx().coerceAtMost(1f)
                val x = if (layoutDirection == LayoutDirection.Rtl) 0f else kotlin.math.ceil(size.width) - 1f
                drawRect(hairline, Offset(x, 0f), Size(1f, size.height), alpha = stroke)
            }
            .kitEdge(KitEdge.Chrome)
            .testTag("kit-sidebar")
            .padding(Space.S3.dp),
    ) {
        if (header != null) {
            header()
            Box(Modifier.height(Space.S3.dp))
        }
        Column(Modifier.selectableGroup()) {
            destinations.forEach { d -> SidebarItem(d, d.id == selectedId) { onSelect(d) } }
        }
        if (pane != null) {
            Box(Modifier.height(FlutterKitTokens.SECTION_GAP.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) { pane() }
        } else {
            Box(Modifier.weight(1f))
        }
        if (primary != null) {
            Box(Modifier.height(Space.S3.dp))
            KitButton(primary, Modifier.fillMaxWidth(), role = KitButtonRole.Primary, fullWidth = true)
        }
    }
}

@Composable
private fun SidebarItem(d: KitNavDestination, chosen: Boolean, onClick: () -> Unit) {
    val colors = LocalKitColors.current
    // Dart: the chosen (or hovered) row in text1, the others in text2.
    val tone = if (chosen) TextTone.Primary else TextTone.Secondary
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Button, SurfaceLevel.Surface3, ownFill = colors.surface3, selected = true))
    KitTappable(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
                .then(if (chosen) look.modifier else Modifier)
                .semantics(mergeDescendants = true) {
                    selected = chosen
                    if (d.needsYou > 0) d.needsYouWords?.let { stateDescription = it }
                }
                .kitEdge(KitEdge.Bleed)
                .testTag("kit-nav-${d.id}"),
        role = KitTappableRole.Tab,
        shape = KitShapeId.Button,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = LocalKitTokens.current.minTarget.dp)
                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitNeedsYouBadge(d.needsYou) { d.icon?.let { KitIcon(it, size = KitIconSize.Medium, tone = tone) } }
            Box(Modifier.width(Space.S3.dp))
            NavLabel(d.label, TypeRole.RowTitle, tone, Modifier.weight(1f))
        }
    }
}
