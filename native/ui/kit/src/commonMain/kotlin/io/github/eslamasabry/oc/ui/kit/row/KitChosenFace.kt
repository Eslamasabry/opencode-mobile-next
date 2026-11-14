package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.drawGlassFace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/**
 * The container a row lies in, as the chosen face needs it: [reachDp], how far the container's inner edge lies past the
 * row's own box on each side (a sheet's rail, a Glass panel's pane inset; 0 where the row's box already reaches it), and
 * [radiusDp], the container's corner radius.
 */
@Immutable
public data class KitRowContainer(val reachDp: Float, val radiusDp: Float)

/** Set by a part that frames rows (a row group's panel); unset, a row works it out from [LocalKitOnRail]. */
public val LocalKitRowContainer: ProvidableCompositionLocal<KitRowContainer?> = staticCompositionLocalOf { null }

/**
 * The one chosen look for a row in a list or a sheet (cut audit 2, finding 3). Every picker draws it through
 * [kitChosenFace], so the chosen row looks the same in every sheet:
 * - the face spans the row's container from edge to edge, inset [SIDE_INSET_DP] (space2) from the container's inner
 *   edge, so the row's lead icon and its end check lie inside it with the row's own padding around them;
 * - it keeps [TOP_INSET_DP] (space1) from the row's top and bottom, and the container's inner radius (its corner less
 *   the side inset), so it is concentric with the panel or sheet it lies in;
 * - a list's hairline before and after a chosen row is not drawn ([seamTouches]), so no line touches the face;
 * - it is drawn behind the row's content, never over it.
 * The face itself is the style's: Glass's accent-lit glass, every other style an accent tint ([CHOSEN_ROW_TINT]).
 */
public object ChosenFaceRules {
    /** From the container's inner edge to the face, each side. */
    public const val SIDE_INSET_DP: Int = Space.S2

    /** From the row's top and bottom to the face. */
    public const val TOP_INSET_DP: Int = Space.S1

    /** The face's box in the row's own px; [reachPx] is how far the container's inner edge lies past the row's box. */
    public fun box(width: Float, height: Float, reachPx: Float, sidePx: Float, topPx: Float): Rect =
        Rect(sidePx - reachPx, topPx, width + reachPx - sidePx, height - topPx)

    /** The face's corner: the container's inner radius. */
    public fun radius(containerRadiusDp: Float): Float = (containerRadiusDp - SIDE_INSET_DP).coerceAtLeast(0f)

    /** Whether a hairline at [y] touches a chosen row spanning [top] to [bottom] (px, the list's own space). */
    public fun seamTouches(y: Int, top: Float, bottom: Float): Boolean = y + SEAM_SLACK >= top && y - SEAM_SLACK <= bottom

    private const val SEAM_SLACK = 1.5f
}

/** The chosen rows of one hairline list, so it leaves out the seams beside them. */
internal class ChosenRows {
    val rows = mutableStateListOf<ChosenRowBox>()
}

/** Where a chosen row lies, read by its list when it draws its seams. */
internal class ChosenRowBox {
    var coordinates: LayoutCoordinates? = null
}

internal val LocalKitChosenRows: ProvidableCompositionLocal<ChosenRows?> = staticCompositionLocalOf { null }

/** The container rows lie in here: a framing part's, else a sheet's rail, else the page (the row's box reaches it). */
@Composable
internal fun kitRowContainer(): KitRowContainer {
    LocalKitRowContainer.current?.let { return it }
    val style = LocalKitStyle.current
    val corners = style.knobs.corners
    val tokens = style.spec.tokens
    return if (LocalKitOnRail.current)
        KitRowContainer(FlutterKitTokens.RAIL.toFloat(), StyleResolver.paneCorner(FlutterKitTokens.SHEET_RADIUS.toFloat(), corners, tokens))
    else KitRowContainer(0f, StyleResolver.paneCorner(FlutterKitTokens.PANEL_CORNER_RADIUS.toFloat(), corners, tokens))
}

/**
 * [ChosenFaceRules]' face behind a [chosen] row. [ownReach]: how far the row's own box already reaches past its parent
 * (a choice row bleeds to a sheet's edges). [tint] replaces the flat styles' accent tint (a choice row's wash animates
 * in). Put it before the row's tap target in the chain: the tap target clips to the row's box, and the face may reach
 * past it to the container's edge.
 */
@Composable
internal fun Modifier.kitChosenFace(chosen: Boolean, ownReach: Dp = 0.dp, tint: Color? = null): Modifier {
    val container = kitRowContainer()
    val reach = (container.reachDp.dp - ownReach).coerceAtLeast(0.dp)
    val radius = ChosenFaceRules.radius(container.radiusDp)
    val glass = if (chosen) io.github.eslamasabry.oc.ui.kit.effects.kitGlassChosenFace(radius) else null
    val fill = tint ?: LocalKitColors.current.accent.copy(alpha = if (chosen) CHOSEN_ROW_TINT else 0f)
    val list = LocalKitChosenRows.current
    val box = remember { ChosenRowBox() }
    if (chosen && list != null) {
        DisposableEffect(list, box) {
            list.rows.add(box)
            onDispose { list.rows.remove(box) }
        }
    }
    // One chain whether chosen or not, so the row's tap target after it keeps its node when the choice moves.
    val shows = chosen || (tint != null && tint.alpha > 0f)
    return this.onPlaced { box.coordinates = it }.drawBehind {
        if (!shows) return@drawBehind
        val r = ChosenFaceRules.box(
            size.width, size.height, reach.toPx(), ChosenFaceRules.SIDE_INSET_DP.dp.toPx(), ChosenFaceRules.TOP_INSET_DP.dp.toPx(),
        )
        if (r.width <= 0f || r.height <= 0f) return@drawBehind
        if (glass != null) inset(r.left, r.top, size.width - r.right, size.height - r.bottom) { drawGlassFace(glass) }
        else {
            val c = radius.dp.toPx()
            drawRoundRect(fill, Offset(r.left, r.top), r.size, CornerRadius(c, c))
        }
    }
}
