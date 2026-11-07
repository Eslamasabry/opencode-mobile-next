package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.kitSelectTap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge

/** Flutter KitSwitchRow's leading slot, including provider marks, with one semantic toggle. */
@Composable
public fun KitLeadingSwitchRow(
    title: String,
    leading: KitRowLeading,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingMaxLines: Int = 1,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .heightIn(
                min = if (supporting == null) tokens.rowHeightOne.dp else tokens.rowHeightTwo.dp
            )
            .kitEdge(KitEdge.Bleed) // a row's highlight runs edge to edge (native/docs/kit/LAYOUT-RULES.md §2)
            .kitSelectTap(checked, Role.Switch, enabled, SwitchRules.haptic(!checked)) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        RowLeadingTile(leading, dim = !enabled.isEnabled)
        Column(Modifier.weight(1f)) {
            KitText(title, role = TypeRole.RowTitle, maxLines = kitRowTitleLines(scale))
            (enabled.reason ?: supporting)?.let {
                KitText(
                    it,
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines =
                        if (enabled.isEnabled) kitRowSupportingLines(scale, supportingMaxLines)
                        else Int.MAX_VALUE,
                )
            }
        }
        KitSwitchTrack(checked, enabled.isEnabled)
    }
}
