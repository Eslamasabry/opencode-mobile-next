package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Flutter KitSwitchRow's locked form: a reason and lock replace an unavailable switch. */
@Composable
public fun KitLockedRow(title: String, lockedLabel: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Row(modifier.fillMaxWidth().semantics { stateDescription = lockedLabel }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { KitRow(title, supporting = supporting) }
        Row(Modifier.padding(end = LocalKitTokens.current.gutter.dp), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp), verticalAlignment = Alignment.CenterVertically) {
            KitIcon(KitIcons.locked)
            KitText(lockedLabel, role = TypeRole.Secondary)
        }
    }
}
