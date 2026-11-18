package io.github.eslamasabry.oc.ui.kit.foundation

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** All Android text writes, including sign-in and terminal OSC 52, end here. No payload is logged or altered. */
public object KitAndroidClipboard {
    public fun write(context: Context, text: String, mode: KitClipboardMode, label: CharSequence? = "") {
        val clip = ClipData.newPlainText(label, text)
        protect(clip, mode)
        requireNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
    }

    internal fun protect(clip: ClipData, mode: KitClipboardMode) {
        // The key is inlined on older Android versions; the extras are harmless there (minSdk 26).
        clip.description.extras = (clip.description.extras ?: PersistableBundle()).apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, mode == KitClipboardMode.Sensitive)
        }
    }
}

// Nested policies always delegate to the original platform clipboard, not a parent's policy wrapper.
// Otherwise a Public parent would overwrite a secret field's Sensitive metadata (or vice versa).
@Suppress("DEPRECATION")
private class KitClipboardSource(val clipboard: Clipboard, val legacy: androidx.compose.ui.platform.ClipboardManager)
private val LocalKitClipboardSource = staticCompositionLocalOf<KitClipboardSource?> { null }

/** Compose's native selection/cut menus also write the clipboard; protect those implicit text writes. */
@Composable
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal fun KitAndroidClipboardBoundary(context: Context, mode: KitClipboardMode, content: @Composable () -> Unit) {
    val current = LocalClipboard.current
    val legacy = LocalClipboardManager.current
    val source = LocalKitClipboardSource.current ?: remember(current, legacy) { KitClipboardSource(current, legacy) }
    val original = source.clipboard
    val clipboard = remember(original, mode) { policyClipboard(original, mode) }
    val manager = remember(source, context, mode) {
        object : androidx.compose.ui.platform.ClipboardManager by source.legacy {
            override fun setText(annotatedString: AnnotatedString) {
                KitAndroidClipboard.write(context, annotatedString.text, mode)
            }
        }
    }
    CompositionLocalProvider(LocalKitClipboardSource provides source, LocalClipboard provides clipboard, LocalClipboardManager provides manager, content = content)
}

/**
 * The policy wrapper must stay an [AndroidClipboard]: Compose's text fields read the platform manager through
 * `getNativeClipboardManager`, which throws for any other [Clipboard]: a plain wrapper crashed every text field's selection toolbar.
 */
private fun policyClipboard(original: Clipboard, mode: KitClipboardMode): Clipboard {
    val platform = original as? AndroidClipboard ?: return object : Clipboard by original {
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            clipEntry?.clipData?.let { KitAndroidClipboard.protect(it, mode) }
            original.setClipEntry(clipEntry)
        }
    }
    return object : AndroidClipboard {
        override val clipboardManager: ClipboardManager get() = platform.clipboardManager

        override suspend fun getClipEntry(): ClipEntry? = platform.getClipEntry()

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            clipEntry?.clipData?.let { KitAndroidClipboard.protect(it, mode) }
            platform.setClipEntry(clipEntry)
        }
    }
}
