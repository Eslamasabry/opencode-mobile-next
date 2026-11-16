package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/** Explicit preview policy, independent of redaction: ordinary copies are Public, credentials/diagnostics Sensitive. */
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
public fun rememberKitClipboard(): KitClipboard {
    val clipboard = LocalClipboardManager.current
    val platform = LocalKitPlatform.current
    return remember(clipboard, platform) { KitClipboard(clipboard, platform) }
}

/** Clipboard policy for native selection/cut in a subtree. States/text/cap: none; draws no UI. */
@Composable
public fun KitClipboardScope(mode: KitClipboardMode, content: @Composable () -> Unit) {
    LocalKitPlatform.current.ClipboardBoundary(mode, content)
}

/** A selection region with an explicit clipboard mode. Layout and selection behavior are unchanged. */
@Composable
public fun KitClipboardSelection(
    mode: KitClipboardMode,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit,
) {
    KitClipboardScope(mode) {
        androidx.compose.foundation.text.selection.SelectionContainer(modifier, content = content)
    }
}
