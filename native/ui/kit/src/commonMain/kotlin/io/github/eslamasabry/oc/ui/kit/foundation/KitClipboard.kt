package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/** Preview protection is independent of redaction: deliberate copies keep their exact payload. */
public enum class KitClipboardMode { Sensitive, Public }

/** The kit's sole common text-write boundary. Every caller chooses a mode, without a default. */
@Suppress("DEPRECATION")
public class KitClipboard internal constructor(
    private val clipboard: ClipboardManager,
    private val platform: KitPlatform,
) {
    public fun setText(text: AnnotatedString, mode: KitClipboardMode) {
        if (!platform.writeClipboard(text.text, mode)) clipboard.setText(text)
    }

    public fun getText(): AnnotatedString? = clipboard.getText()
    public fun hasText(): Boolean = clipboard.hasText()
}

@Composable
@Suppress("DEPRECATION")
public fun rememberKitClipboard(): KitClipboard = KitClipboard(LocalClipboardManager.current, LocalKitPlatform.current)
