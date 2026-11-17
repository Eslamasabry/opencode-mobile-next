package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.foundation.kitMenuHold
import io.github.eslamasabry.oc.ui.kit.foundation.shape

/**
 * Reading content keeps its natural height and its own link/code interactions. Tap and hold open
 * the same message menu; the reply's visible More button remains the discoverable twin. No control
 * surface or minimum button height is added to every streamed paragraph. The press is recorded for
 * the menu (`kitMenuHold`), so the menu opens at this message, beside the finger, and the message
 * keeps a held face while its menu is open (cut audit 2, finding 13).
 *
 * The press area is the message's row: it runs the list's width (declared [KitEdge.Bleed], like every list row, so the
 * guard does not read it as a control touching the window edge and two stacked messages are not "tight"), and it is at
 * least the minimum target tall, so a one-line reply is as easy to tap as any button. The words keep their place at the
 * start of the row and the page gutter.
 */
@Composable
public fun KitMessageActions(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val minTarget = LocalKitTokens.current.minTarget.dp
    Box(
        modifier.heightIn(min = minTarget).kitEdge(KitEdge.Bleed).kitMenuHold(label, onOpen, KitShapeId.Tile.shape()),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}
