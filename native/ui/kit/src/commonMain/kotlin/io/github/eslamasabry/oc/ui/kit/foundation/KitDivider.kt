package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

public enum class KitDividerInset { None, Gutter, Text }

/**
 * Flutter's decorative physical-pixel separator; insets follow the reading direction.
 *
 * [prominent] is Flutter's `KitDivider.prominent` (kit_divider.dart): the stronger line for lists whose rows must read
 * apart (the conversations feed on OLED dark screens), at least 1 dp (`KitLayout.prominentRule`) in the theme's
 * tertiary ink at 30 %, unless the style draws its own rule (Brutal's ink keeps it; Soft still draws none).
 */
@Composable
public fun KitDivider(
    modifier: Modifier = Modifier,
    inset: KitDividerInset = KitDividerInset.None,
    endInset: Dp? = null,
    vertical: Boolean = false,
    section: Boolean = false,
    prominent: Boolean = false,
) {
    require(endInset == null || endInset >= 0.dp)
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val tokens = LocalKitTokens.current
    // One physical pixel at the kit's pixel ratio (Flutter `KitTokens.hairlineWidth`), not at the picture density.
    val hairline = kitHairline().value
    val request = SurfaceRequest(roles, SurfaceRole.Divider, style.knobs, withinGroup = !vertical && inset != KitDividerInset.None)
    val row = style.spec.resolve(request)
    val look = if (section) style.spec.resolve(request.copy(selected = true)) else row
    if (section && (look == null || look == row || look.fill == null || look.fill?.alpha == 0)) return
    val ruleColor = Color((look?.fill ?: roles.hairline).v)
    val ruled = if (section && look != null && look.edgeWidthDp > 0f) look.edgeWidthDp else if (ruleColor.alpha > 0f) style.spec.tokens.dividerWidthDp ?: hairline else hairline
    // A style that paints no divider (Soft: transparent) keeps drawing none, as Flutter's `rule == null` does.
    val strong = prominent && !vertical && !section && ruleColor.alpha > 0f
    val styled = (look?.fill ?: roles.hairline) != roles.hairline
    val color = if (strong && !styled) Color(roles.text3.v).copy(alpha = PROMINENT_ALPHA) else ruleColor
    val thickness = if (strong) maxOf(ruled, PROMINENT_RULE_DP) else ruled
    val end = endInset ?: if (inset == KitDividerInset.None) 0.dp else tokens.gutter.dp
    val start = when (inset) {
        KitDividerInset.None -> 0.dp
        KitDividerInset.Gutter -> Space.S4.dp
        KitDividerInset.Text -> (Space.S4 + tokens.leadColumn).dp
    }
    // The layout box rounds a sub-pixel line to whole pixels (to nothing at a third of a pixel), so the line is drawn
    // at its exact thickness from the box's start edge, the way Flutter paints a fractional hairline.
    Canvas(
        modifier.clearAndSetSemantics {}.then(
            if (vertical) Modifier.width(thickness.dp).fillMaxHeight()
            else Modifier.padding(start = start, end = end).fillMaxWidth().height(thickness.dp),
        ),
    ) {
        val t = thickness.dp.toPx()
        if (vertical) drawRect(color, size = Size(t, size.height)) else drawRect(color, size = Size(size.width, t))
    }
}

/** A prominent line between rows that a panel draws itself: its ink and thickness in dp. */
internal class KitProminentRule(val color: Color, val thicknessDp: Float)

/**
 * The prominent rule between a panel's rows, resolved the way [KitDivider] resolves it (B-173): at least 1 dp in the
 * theme's tertiary ink at 30 %, unless the style draws its own rule (Brutal keeps its ink) or none (Soft: null).
 */
@Composable
internal fun kitProminentRule(): KitProminentRule? {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val hairline = kitHairline().value
    val look = style.spec.resolve(SurfaceRequest(roles, SurfaceRole.Divider, style.knobs, withinGroup = true))
    val raw = look?.fill ?: roles.hairline
    val ruleColor = Color(raw.v)
    if (ruleColor.alpha <= 0f) return null
    val ruled = style.spec.tokens.dividerWidthDp ?: hairline
    val color = if (raw != roles.hairline) ruleColor else Color(roles.text3.v).copy(alpha = PROMINENT_ALPHA)
    return KitProminentRule(color, maxOf(ruled, PROMINENT_RULE_DP))
}

/** Flutter `KitLayout.prominentRule`. */
private const val PROMINENT_RULE_DP = 1f

/** The prominent line's share of the tertiary ink (Flutter `text3.withValues(alpha: 0.3)`). */
private const val PROMINENT_ALPHA = 0.3f
