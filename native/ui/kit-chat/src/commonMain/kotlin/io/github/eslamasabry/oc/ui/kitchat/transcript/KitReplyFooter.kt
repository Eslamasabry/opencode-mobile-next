package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** The reply footer's visible Copy and More affordances (Flutter kit_turn.dart:667-737). */
@Composable
public fun KitReplyFooter(
    copyLabel: String,
    moreLabel: String,
    onCopy: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    metadata: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { metadata() }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            if (onCopy != null) KitIconButton(KitIcons.copy, copyLabel, onCopy, tone = TextTone.Secondary)
            if (onMore != null) KitIconButton(KitIcons.more, moreLabel, onMore, tone = TextTone.Secondary)
        }
    }
}
