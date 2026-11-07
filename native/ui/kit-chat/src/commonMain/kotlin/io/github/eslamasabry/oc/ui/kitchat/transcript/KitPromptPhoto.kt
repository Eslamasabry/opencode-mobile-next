package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A sent photo shown in the prompt bubble (Flutter `_PromptPhoto`, kit_message.dart:1257-1300): [label] is its file
 * name; [image] is its decoded thumbnail, null while it loads (a quiet tile of the same size holds its place);
 * [onOpen] opens the host's full view, null for a picture that only shows.
 */
@Immutable
public class KitPromptPhoto(
    public val label: String,
    public val image: ImageBitmap?,
    public val onOpen: (() -> Unit)? = null,
)
