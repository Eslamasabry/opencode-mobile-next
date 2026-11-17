package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Issue 148: what Enter does in a message box. The on-screen keyboard's Enter always makes a new line
 * (only the Send button sends); a real keyboard's Enter sends, Shift+Enter makes a new line, and
 * Ctrl or Meta with Enter sends. Who pressed it is decided by the key event's source, never by the
 * pointer kind: a stylus or mouse on a phone does not turn the on-screen keyboard into a real one.
 */
public fun kitEnterSends(fromSoftKeyboard: Boolean, shift: Boolean, ctrlOrMeta: Boolean): Boolean =
    ctrlOrMeta || (!fromSoftKeyboard && !shift)

/** True when the key came from an on-screen (virtual) keyboard, not a physical one. */
public expect fun KeyEvent.fromSoftKeyboard(): Boolean

/** The one Enter rule of every message box (the live chat box and the full composer both use it). A message box's Enter rule ([kitEnterSends]); [onSend] runs for a send, any other Enter is left to the field. */
internal fun Modifier.kitComposerEnter(onSend: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    val sends =
        kitEnterSends(
            fromSoftKeyboard = event.fromSoftKeyboard(),
            shift = event.isShiftPressed,
            ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed,
        )
    if (!sends) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) onSend()
    true
}
