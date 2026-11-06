package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Reading content keeps its natural height and its own link/code interactions. Tap and hold open
 * the same message menu; the reply's visible More button remains the discoverable twin. No control
 * surface or minimum button height is added to every streamed paragraph.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun KitMessageActions(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = label,
            onLongClickLabel = label,
            onClick = onOpen,
            onLongClick = onOpen,
        )
    ) {
        content()
    }
}
