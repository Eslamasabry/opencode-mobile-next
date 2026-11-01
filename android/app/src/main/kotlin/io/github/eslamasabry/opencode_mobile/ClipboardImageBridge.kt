package io.github.eslamasabry.opencode_mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException

/**
 * Native half of `oc/clipboard`. Reads the clipboard only when the person
 * taps "Paste image": `hasImage` looks at the clip's declared types (no
 * content access, no system toast); `clipboardImage` reads the bytes and is
 * the call Android 12+ announces with its own clipboard toast.
 */
internal class ClipboardImageBridge(context: Context, messenger: BinaryMessenger) {
    private val app = context.applicationContext
    private val channel = MethodChannel(messenger, CHANNEL_NAME)

    init {
        channel.setMethodCallHandler { call, result -> handle(call, result) }
    }

    fun dispose() = channel.setMethodCallHandler(null)

    private fun manager() = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "hasImage" -> result.success(hasImage())
            "clipboardImage" -> readImage(result)
            else -> result.notImplemented()
        }
    }

    private fun hasImage(): Boolean {
        val description = manager()?.primaryClipDescription ?: return false
        return (0 until description.mimeTypeCount).any {
            ClipboardImagePolicy.isImageMime(description.getMimeType(it))
        }
    }

    private fun readImage(result: MethodChannel.Result) {
        try {
            val clip = manager()?.primaryClip
            val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            val uri = item?.uri
            if (clip == null || uri == null) {
                result.success(null)
                return
            }
            val declared = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
            val mime = ClipboardImagePolicy.firstImageMime(declared + app.contentResolver.getType(uri))
            if (mime == null) {
                result.success(null)
                return
            }
            val bytes = app.contentResolver.openInputStream(uri)?.use { ClipboardImagePolicy.readLimited(it) }
            if (bytes == null || bytes.isEmpty()) {
                result.success(null)
                return
            }
            result.success(mapOf("mime" to mime, "bytes" to bytes))
        } catch (_: ClipboardImagePolicy.TooLargeException) {
            result.error("too_large", "clipboard image is too large", null)
        } catch (_: IOException) {
            result.success(null)
        } catch (_: SecurityException) {
            result.success(null)
        }
    }

    companion object {
        const val CHANNEL_NAME = "oc/clipboard"
    }
}
