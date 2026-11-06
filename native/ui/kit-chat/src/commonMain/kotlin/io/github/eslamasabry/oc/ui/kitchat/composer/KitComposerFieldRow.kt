package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The composer's text with an optional small control beside it (the quiet mic). The text takes the
 * room the control leaves, so words never sit under it at any text size; the control keeps the
 * line the text ends on (bottom edge) and its own 48 dp target.
 */
@Composable
public fun KitComposerFieldRow(
    modifier: Modifier = Modifier,
    tail: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    if (tail == null) {
        Box(modifier.fillMaxWidth()) { field() }
        return
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = LocalKitTokens.current.minTarget.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f)) { field() }
        tail()
    }
}
