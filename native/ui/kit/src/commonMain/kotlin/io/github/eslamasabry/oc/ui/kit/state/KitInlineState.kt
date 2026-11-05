package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Whether the surrounding screen supplies spacing or the state owns its list insets. */
public enum class KitInlineStateLayout {
    Embedded,
    List,
}

/**
 * Flutter KitStateView(size: inline): a small tile, headline, optional body, progress and action.
 * [KitInlineStateLayout.Embedded] leaves outer padding to its host; [KitInlineStateLayout.List]
 * owns list insets and starts the state cap at the list edge.
 */
@Composable
public fun KitInlineState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    progress: KitProgress? = null,
    primary: KitAction? = null,
    layout: KitInlineStateLayout = KitInlineStateLayout.Embedded,
) {
    val tokens = LocalKitTokens.current
    val contentModifier =
        when (layout) {
            KitInlineStateLayout.Embedded -> modifier.kitMaxWidth(CapRole.State).fillMaxWidth()
            KitInlineStateLayout.List ->
                modifier
                    .kitMaxWidth(CapRole.State, CapAlign.Start)
                    .fillMaxWidth()
                    .padding(
                        start = tokens.gutter.dp,
                        top = tokens.gutter.dp,
                        end = tokens.gutter.dp,
                        bottom = Space.S2.dp,
                    )
        }
    Column(contentModifier, horizontalAlignment = Alignment.Start) {
        when (layout) {
            KitInlineStateLayout.Embedded ->
                KitSurface(
                    level = SurfaceLevel.Surface3,
                    shape = KitShapeId.Tile,
                    padding = KitSurfacePadding.None,
                    clip = false,
                ) {
                    Box(Modifier.size(tokens.iconTile.dp), contentAlignment = Alignment.Center) {
                        KitIcon(icon, size = KitIconSize.Small)
                    }
                }
            KitInlineStateLayout.List -> KitSurfaceTile(icon, tone = TextTone.Secondary)
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
