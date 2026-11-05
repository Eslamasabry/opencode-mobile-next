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
import androidx.compose.ui.platform.LocalDensity
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

/** Flutter's decorative physical-pixel separator; insets follow the reading direction. */
@Composable
public fun KitDivider(
    modifier: Modifier = Modifier,
    inset: KitDividerInset = KitDividerInset.None,
    endInset: Dp = 0.dp,
    vertical: Boolean = false,
    section: Boolean = false,
) {
    require(endInset >= 0.dp)
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val tokens = LocalKitTokens.current
    val hairline = 1f / LocalDensity.current.density
    val request = SurfaceRequest(roles, SurfaceRole.Divider, style.knobs, withinGroup = !vertical && inset != KitDividerInset.None)
    val row = style.spec.resolve(request)
    val look = if (section) style.spec.resolve(request.copy(selected = true)) else row
    if (section && (look == null || look == row || look.fill == null || look.fill?.alpha == 0)) return
    val color = Color((look?.fill ?: roles.hairline).v)
    val thickness = if (section && look != null && look.edgeWidthDp > 0f) look.edgeWidthDp else if (color.alpha > 0f) style.spec.tokens.dividerWidthDp ?: hairline else hairline
    val start = when (inset) {
        KitDividerInset.None -> 0.dp
        KitDividerInset.Gutter -> Space.S4.dp
        KitDividerInset.Text -> (Space.S4 + tokens.leadColumn).dp
    }
    Canvas(
        modifier.clearAndSetSemantics {}.then(
            if (vertical) Modifier.width(thickness.dp).fillMaxHeight()
            else Modifier.padding(start = start, end = endInset).fillMaxWidth().height(thickness.dp),
        ),
    ) { drawRect(color) }
}
