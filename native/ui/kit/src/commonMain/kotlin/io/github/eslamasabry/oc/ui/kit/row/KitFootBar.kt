package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * A screen's foot bar (its `bottom` block): the way to the whole list ([label] and [count] on a panel) and the round
 * floating action ([fabLabel], [fabIcon]) side by side on one line, margin to margin. It sits under the list, so rows
 * end above it and never pass under the button. While the action cannot run yet ([fabVisible] false) the link takes the
 * whole line. The host's block already keeps the page gutter.
 *
 * States: with action, without action
 * Text: frame (label), tabular count
 * Cap: the host's block
 * Motion: the button fades in and out; the link eases to its width
 * Hidden: none
 * Replaces: none
 */
@Composable
public fun KitFootBar(
    label: String,
    count: String?,
    onLink: () -> Unit,
    fabLabel: String,
    fabIcon: ImageVector,
    onFab: () -> Unit,
    modifier: Modifier = Modifier,
    fabVisible: Boolean = true,
) {
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).then(look.modifier).clip(look.shape)) {
            CompositionLocalProvider(LocalKitOnRail provides false) { KitPanelLinkRow(label, count, onLink) }
        }
        if (fabVisible) Spacer(Modifier.width(Space.S3.dp))
        KitFab(fabLabel, fabIcon, onFab, visible = fabVisible)
    }
}
