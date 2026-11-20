package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/**
 * A pane that is its own window for the parts inside it: the kit reads its window class (side-by-side diff, labelled
 * or glyph actions, short-window sizing) from [LocalKitWindow], one value for the whole window. Inside a 420 dp pane of
 * a 1280 dp window that value is wrong, so this measures the pane's own constraints and provides a window of that
 * width and height. The pointer kind (and any other non-size fact) is the parent's. It draws and pads nothing; a side
 * left unbounded keeps the parent's size for that side, and an unchanged size provides the same value again.
 */
@Composable
public fun KitPaneWindow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val parent = LocalKitWindow.current
    BoxWithConstraints(modifier, propagateMinConstraints = true) {
        val width = if (constraints.hasBoundedWidth) maxWidth.value else parent.widthDp
        val height = if (constraints.hasBoundedHeight) maxHeight.value else parent.heightDp
        val window = remember(width, height, parent.finePointer) { parent.copy(widthDp = width, heightDp = height) }
        CompositionLocalProvider(LocalKitWindow provides window, content = content)
    }
}
