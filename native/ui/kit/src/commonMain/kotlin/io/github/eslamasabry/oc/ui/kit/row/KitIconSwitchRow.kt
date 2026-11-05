package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.kitSelectTap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Flutter Settings' KitSwitchRow with KitRow.icon: one toggle target, its icon on the shared lead
 * rail.
 */
@Composable
public fun KitIconSwitchRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = tokens.rowHeightOne.dp)
            .kitSelectTap(checked, Role.Switch, KitEnabled.Enabled, SwitchRules.haptic(!checked)) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Box(
            Modifier.size(tokens.iconTile.dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            val size = 24.dp // F: lib/ui/kit/kit_row.dart:1085
            KitGlyph(icon, colors.text2, size)
        }
        KitText(title, Modifier.weight(1f), role = TypeRole.RowTitle, tone = TextTone.Primary)
        KitSwitchTrack(checked, true)
    }
}
