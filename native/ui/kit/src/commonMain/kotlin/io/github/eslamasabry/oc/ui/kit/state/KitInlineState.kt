package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** KitStateView.inline at zero outer padding (kit_state_view.dart:689-792). */
@Composable
public fun KitInlineState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    progress: KitProgress? = null,
    primary: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    Column(modifier.kitMaxWidth(CapRole.State).fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        KitSurface(level = SurfaceLevel.Surface3, shape = KitShapeId.Tile, padding = KitSurfacePadding.None, clip = false) {
            Box(Modifier.size(tokens.iconTile.dp), contentAlignment = Alignment.Center) { KitIcon(icon, size = KitIconSize.Small) }
        }
        Spacer(Modifier.height(Space.S3.dp))
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline)
        body?.let {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        progress?.let {
            Spacer(Modifier.height(Space.S3.dp))
            KitProgressView(it)
        }
        primary?.let {
            Spacer(Modifier.height(Space.S3.dp))
            KitActionBlock(primary = it)
        }
    }
}
