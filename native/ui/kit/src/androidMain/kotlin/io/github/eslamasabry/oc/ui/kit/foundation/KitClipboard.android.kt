package io.github.eslamasabry.oc.ui.kit.foundation

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
        }
    }
}

/** Compose's native selection/cut menus also write the clipboard; protect those implicit text writes. */
@Composable
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal fun KitAndroidClipboardBoundary(context: Context, mode: KitClipboardMode, content: @Composable () -> Unit) {
    val current = LocalClipboard.current
    val legacy = LocalClipboardManager.current
    val clipboard = remember(current, mode) {
        object : Clipboard by current {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                clipEntry?.clipData?.let { KitAndroidClipboard.protect(it, mode) }
                current.setClipEntry(clipEntry)
            }
        }
    }
    val manager = remember(legacy, context, mode) {
        object : androidx.compose.ui.platform.ClipboardManager by legacy {
            override fun setText(annotatedString: AnnotatedString) {
                KitAndroidClipboard.write(context, annotatedString.text, mode)
            }
        }
    }
    CompositionLocalProvider(LocalClipboard provides clipboard, LocalClipboardManager provides manager, content = content)
}
