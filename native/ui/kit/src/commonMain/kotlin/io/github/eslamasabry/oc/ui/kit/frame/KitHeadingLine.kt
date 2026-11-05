package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** The heading's visible Inbox link (Flutter kit_page_heading.dart), with the kit's minimum target. */
@Composable
public fun KitHeadingLine(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KitTappable(onClick, modifier, onClickLabel = text, ink = TextTone.Attention) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitText(text, role = TypeRole.Label, tone = TextTone.Attention, maxLines = 1)
            KitIcon(KitIcons.chevronRight, size = KitIconSize.Small, tone = TextTone.Attention)
        }
    }
}
