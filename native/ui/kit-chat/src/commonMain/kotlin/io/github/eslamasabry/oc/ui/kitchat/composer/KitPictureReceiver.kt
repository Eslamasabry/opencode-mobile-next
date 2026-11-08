package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * A picture a keyboard committed into a field under [KitPictureReceiver] (Gboard's GIFs and
 * stickers, Android's clipboard-image chip). Opaque to features: only the app's platform reader
 * opens [content], since its bytes sit behind a content URI.
 */
@OptIn(ExperimentalFoundationApi::class)
public class KitInsertedPicture(public val content: TransferableContent)

/**
 * S24-CHAT story 5 (Flutter `contentInsertionConfiguration`): the message box's fields take
 * pictures the keyboard inserts. Wraps [content] (the composer row); a text field inside it tells
 * the keyboard it accepts pictures, and each picture goes to [onPicture]. Text pastes as before.
 * [onPicture] null = no pictures are taken and the keyboard is told so.
 *
 * States: taking pictures, not taking them. Text: none. Cap: none (wraps). Motion: none. Hidden:
 * the keyboard's insert is the hidden path; "+" › Photos is the visible one. Replaces: Flutter's
 * `_handleInsertedContent` hook on the composer field.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun KitPictureReceiver(
    onPicture: ((KitInsertedPicture) -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val latest by rememberUpdatedState(onPicture)
    val listener = remember {
        ReceiveContentListener { received ->
            val take = latest
            if (take != null && received.hasMediaType(MediaType.Image)) {
                take(KitInsertedPicture(received))
                null
            } else received
        }
    }
    Box(modifier.then(if (onPicture != null) Modifier.contentReceiver(listener) else Modifier)) {
        content()
    }
}
