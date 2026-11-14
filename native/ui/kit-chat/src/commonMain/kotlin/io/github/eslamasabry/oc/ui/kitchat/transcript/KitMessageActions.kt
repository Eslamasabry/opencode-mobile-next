package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
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
 */
@Composable
public fun KitMessageActions(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.kitMenuHold(label, onOpen, KitShapeId.Tile.shape())) {
        content()
    }
}
