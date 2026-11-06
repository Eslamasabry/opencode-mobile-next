package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** One actual agent choice, or the visible first card that opens agent setup. */
@Immutable
public data class KitAgentGridItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val isAdd: Boolean = false,
)

/**
 * Flutter's KitAgentGrid: equal pairs, an odd last card across the row, one column above 1.3× text.
 * It belongs to the page's scroll; no nested scrolling or animated noise layer is introduced.
 */
@Composable
public fun KitAgentGrid(
    items: List<KitAgentGridItem>,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    val columns = if (kitTextScale() > 1.3f) 1 else 2
    Column(
        modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        items.chunked(columns).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                pair.forEach { item ->
                    KitAgentChoice(
                        item,
                        Modifier.weight(1f).fillMaxHeight(),
                        enabled,
                        wide = pair.size == 1,
                    )
                }
            }
        }
    }
}

/**
 * A matte agent card, a large monochrome mark, its name, and a shape marking the selected choice.
 */
@Composable
public fun KitAgentChoice(
    item: KitAgentGridItem,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    wide: Boolean = false,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.shape()
    KitTappable(
        item.onClick,
        modifier =
            modifier.heightIn(min = if (wide) 96.dp else 168.dp).semantics {
                contentDescription = item.title
                role = if (item.isAdd) Role.Button else Role.RadioButton
                if (!item.isAdd) selected = item.selected
            },
        enabled = enabled.isEnabled,
        disabledReason = enabled.reason,
        haptic = if (item.isAdd) null else KitHaptic.Select,
        shape = KitShapeId.Card,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .background(colors.surface1, shape)
                .border(1.dp, colors.hairline, shape)
                .padding(Space.S5.dp)
        ) {
            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitGlyph(
                        item.icon,
                        if (enabled.isEnabled) colors.text1 else colors.text3,
                        44.dp,
                    )
                    KitText(item.title, Modifier.weight(1f), role = TypeRole.Title, maxLines = 2)
                    if (item.selected) AgentCheck()
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    KitGlyph(
                        item.icon,
                        if (enabled.isEnabled) colors.text1 else colors.text3,
                        52.dp,
                    )
                    Spacer(Modifier.height(Space.S5.dp))
                    KitText(item.title, role = TypeRole.Title, maxLines = 2)
                }
                if (item.selected) Box(Modifier.align(Alignment.TopEnd)) { AgentCheck() }
            }
        }
    }
}

@Composable
private fun AgentCheck() {
    val colors = LocalKitColors.current
    Box(
        Modifier.size(24.dp).background(colors.accent, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(KitIcons.check, colors.onAccent, 16.dp)
    }
}
