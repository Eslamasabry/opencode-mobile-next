package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.kitSelectTap
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge

/** Flutter KitSwitchRow(onOpen): the words open details; the switch changes only its value. */
@Composable
public fun KitOpenSwitchRow(
    title: String,
    checked: Boolean,
    openLabel: String,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    highlighted: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.background(LocalKitColors.current.surface3) else Modifier
            )
            .heightIn(min = tokens.rowHeightTwo.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitTappable(
            onOpen,
            Modifier.weight(1f).semantics { selected = highlighted },
            enabled = enabled.isEnabled,
            disabledReason = enabled.reason,
            role = KitTappableRole.Row,
            onClickLabel = openLabel,
            shape = KitShapeId.Square,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
            ) {
                KitText(title, role = TypeRole.RowTitle, maxLines = kitRowTitleLines(scale))
                (enabled.reason ?: supporting)?.let {
                    KitText(
                        it,
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        maxLines =
                            if (enabled.isEnabled) kitRowSupportingLines(scale) else Int.MAX_VALUE,
                    )
                }
            }
        }
        Box(
            Modifier.widthIn(min = tokens.minTarget.dp)
                .heightIn(min = tokens.minTarget.dp)
                .kitEdge(KitEdge.Bleed) // a row's highlight runs edge to edge (native/docs/kit/LAYOUT-RULES.md §2)
                .kitSelectTap(checked, Role.Switch, enabled, SwitchRules.haptic(!checked)) {
                    onCheckedChange(!checked)
                }
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            KitSwitchTrack(checked, enabled.isEnabled)
        }
        actions?.invoke()
        Spacer(Modifier.width(Space.S4.dp))
    }
}
