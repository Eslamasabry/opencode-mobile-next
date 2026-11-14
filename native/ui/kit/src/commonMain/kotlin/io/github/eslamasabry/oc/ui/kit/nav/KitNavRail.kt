package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
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
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouBadge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The rail's lens and slot geometry (Dart `_LensTrack`, `_itemExtent`, `_labelMetrics`), pure so it is tested alone. */
public object NavRailMetrics {
    /** The lens: the chip's visual height (kit_nav.dart:347). */
    public val lensHeight: Int get() = FlutterKitTokens.CHIP_HEIGHT

    /** The lens: a target and a step wide (kit_nav.dart:348). */
    public val lensWidth: Int get() = FlutterKitTokens.MIN_TARGET + Space.S2

    /**
     * The label's own scale cap: up to the nav cap, never beyond what fits one label per destination in the slot less
     * a step (kit_nav.dart:370), and never below 1. [widestLabel] is the widest label at scale 1, in dp.
     */
    public fun labelMaxScale(widestLabel: Float): Float {
        val room = Layout.RAIL_WIDTH - Space.S2 // F: lib/ui/kit/kit_nav.dart:370
        val fit = if (widestLabel > 0f) room / widestLabel else NAV_LABEL_CAP
        return minOf(NAV_LABEL_CAP, fit).coerceIn(1f, NAV_LABEL_CAP)
    }

    /** One label line at [scale] rounded up, a step more for large text so labels never touch (kit_nav.dart:394). */
    public fun labelHeight(lineHeight: Float, scale: Float): Float =
        kotlin.math.ceil(lineHeight * scale) + if (scale > 1.2f) Space.S2 else 0 // F: lib/ui/kit/kit_nav.dart:394

    /** One destination's height: lens, label and a space1 around both, at least a target (kit_nav.dart:402). */
    public fun itemExtent(labelHeight: Float): Float =
        maxOf(FlutterKitTokens.MIN_TARGET.toFloat(), lensHeight + labelHeight + 3 * Space.S1) // F: lib/ui/kit/kit_nav.dart:402

    /** The lens's top on destination [index] (Dart `_LensTrack.rest`: index × slot + lead). */
    public fun lensTop(index: Int, slot: Float): Float = index * slot + Space.S1
}

/**
 * The medium-window rail (Dart `KitNavRail`, kit_nav.dart): 80 dp on a floating `surface2` piece with the nav radius,
 * one slot per destination (glyph over its label, labels up to 2.0× while they fit), and the lens, a stadium of
 * `surface3` with a hairline rim, behind the chosen glyph. The lens moves to a new choice on the kit's spatial spring
 * (at once under reduced motion). Compact windows have no bar at all (owner 2026-10-20). [header] holds extra
 * controls above the destinations.
 *
 * States: default, selected, needs-you Text: reading (labels capped at 2.0×) Cap: Fill Motion: the lens glides
 * Hidden: none Replaces: KitNavRail (lib/ui/kit/kit_nav.dart)
 */
@Composable
public fun KitNavRail(
    destinations: List<KitNavDestination>,
    selectedId: String?,
    onSelect: (KitNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * The system's room on the rail's side of the window: the status bar above it, the navigation bar under it and a
     * cutout or bar at its start. The rail floats inside them, so the clock is never drawn over the chosen lens
     * (cut audit 2, finding 10: on a phone on its side the rail ran up under the status bar).
     */
    insets: WindowInsets = io.github.eslamasabry.oc.ui.kit.frame.kitSystemInsets().only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
) {
    val colors = LocalKitColors.current
    val radius = FlutterKitTokens.NAV_RADIUS.dp
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Panel, SurfaceLevel.Surface2, ownFill = colors.surface2, radius = radius),
        )
    val solid = LocalKitStyle.current.style == Style.Solid
    // The label clamp: measured once over every label (Dart `_labelMetrics`).
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = LocalKitTypography.current.style(TypeRole.Label)
    val widest =
        destinations.maxOfOrNull {
            measurer.measure(it.label, labelStyle.copy(fontSize = labelStyle.fontSize), maxLines = 1, softWrap = false,
                density = androidx.compose.ui.unit.Density(density.density, 1f)).size.width / density.density
        } ?: 0f
    val maxScale = NavRailMetrics.labelMaxScale(widest)
    val scale = minOf(kitTextScale(), maxScale)
    val labelHeight = NavRailMetrics.labelHeight(labelStyle.lineHeight.value, scale)
    val slot = NavRailMetrics.itemExtent(labelHeight)
    val chosen = destinations.indexOfFirst { it.id == selectedId }
    val specs = KitMotion.specs
    val lensTop by animateFloatAsState(NavRailMetrics.lensTop(maxOf(chosen, 0), slot), specs.spatial(), label = "kit-nav-lens")
    val lensWidth = NavRailMetrics.lensWidth.toFloat()
    val lensFill = colors.surface3
    val rim = colors.hairline
    val hairline = kitHairline()
    Column(
        modifier
            .windowInsetsPadding(insets)
            .width(Layout.RAIL_WIDTH.dp)
            .fillMaxHeight()
            .then(look.modifier)
            .then(if (solid) Modifier.border(hairline, rim, look.shape) else Modifier)
            .clip(look.shape)
            .padding(vertical = Space.S2.dp)
            .kitEdge(KitEdge.Chrome)
            .testTag("kit-nav-rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.invoke(this)
        Box(
            Modifier.fillMaxWidth().selectableGroup().drawBehind {
                if (chosen < 0) return@drawBehind
                // The lens (kit_nav.dart:939-955): a solid pill with a one physical pixel rim, centred across the rail.
                val w = lensWidth.dp.toPx()
                val h = NavRailMetrics.lensHeight.dp.toPx()
                val left = (size.width - w) / 2 // F: lib/ui/kit/kit_nav.dart:648
                val top = lensTop.dp.toPx()
                val r = h / 2 // F: lib/ui/kit/kit_nav.dart:943
                val stroke = hairline.toPx()
                val pill = RoundRect(left, top, left + w, top + h, CornerRadius(r))
                drawRoundRect(lensFill, Offset(left, top), Size(w, h), CornerRadius(r))
                // drawDRRect(pill, pill.deflate(width)): the rim is the pill less the pill deflated by the hairline.
                val inner = RoundRect(left + stroke, top + stroke, left + w - stroke, top + h - stroke, CornerRadius(r - stroke))
                val rimPath = Path.combine(PathOperation.Difference, Path().apply { addRoundRect(pill) }, Path().apply { addRoundRect(inner) })
                drawPath(rimPath, rim)
            },
        ) {
            Column {
                destinations.forEachIndexed { i, d -> RailItem(d, i == chosen, slot, maxScale) { onSelect(d) } }
            }
        }
    }
}

@Composable
private fun RailItem(d: KitNavDestination, chosen: Boolean, slot: Float, maxScale: Float, onClick: () -> Unit) {
    // Dart: the chosen (or hovered) destination in text1, the others in text2; never the accent.
    val tone = if (chosen) TextTone.Primary else TextTone.Secondary
    KitTappable(
        onClick = onClick,
        modifier =
            Modifier.width(Layout.RAIL_WIDTH.dp)
                .height(slot.dp)
                .semantics(mergeDescendants = true) {
                    selected = chosen
                    contentDescription = d.label
                    if (d.needsYou > 0) d.needsYouWords?.let { stateDescription = it }
                }
                .kitEdge(KitEdge.Bleed)
                .testTag("kit-nav-${d.id}"),
        role = KitTappableRole.Tab,
        shape = KitShapeId.Pill,
    ) {
        // Top-down in the slot (Dart Column in a SizedBox), not centred by the tappable's box.
        Column(Modifier.fillMaxWidth().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(Space.S1.dp))
            Box(Modifier.height(NavRailMetrics.lensHeight.dp), contentAlignment = Alignment.Center) {
                val glyph = (if (chosen) d.selectedIcon ?: d.icon else d.icon)
                KitNeedsYouBadge(d.needsYou) { glyph?.let { KitIcon(it, size = KitIconSize.Large, tone = tone) } }
            }
            NavLabel(d.label, TypeRole.Label, tone, Modifier.padding(horizontal = Space.S1.dp), center = true, maxLines = 1, cap = maxScale)
        }
    }
}
