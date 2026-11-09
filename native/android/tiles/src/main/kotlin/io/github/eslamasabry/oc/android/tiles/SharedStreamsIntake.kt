package io.github.eslamasabry.oc.android.tiles

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Pictures and files shared into the app (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `EXTRA_STREAM`). [uris] are
 * `content://` addresses with a temporary read grant: copy them at once ([SharedStash]). [text] is any text that came
 * with them (a caption). The class never prints an address or a name.
 */
class SharedStreams(val uris: List<Uri>, val refused: Int, val text: String?) {
    /** Everything the share named, kept or not. */
    val arrived: Int get() = uris.size + refused

    override fun toString(): String = "SharedStreams(items=${uris.size}, refused=$refused, text=${text?.length ?: 0} chars)"
}

object SharedStreamsIntake {
    /** More than this is a runaway share; the rest are not even looked at. */
    const val MAX_ITEMS: Int = 20

    /**
     * For a SEND / SEND_MULTIPLE that carries at least one acceptable `content://` stream: returns them and consumes
     * the share (action MAIN, streams, clip and text removed, grant flags kept off) so a recreated activity does not
     * deliver it again. Anything else returns null and leaves the intent untouched (text-only shares stay with
     * [SharedTextIntake]).
     *
     * [ownAuthorityPrefix] is the app's own package: a share naming our own provider is refused, so another app cannot
     * make us read our private files. `file://` is refused too.
     */
    fun consume(intent: Intent?, ownAuthorityPrefix: String): SharedStreams? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
        val found = streams(intent)
        if (found.isEmpty()) return null
        val distinct = found.distinct().take(MAX_ITEMS)
        val uris = distinct.filter { acceptable(it, ownAuthorityPrefix) }
        val caption = captionOf(intent)
        // Every stream may be refused (a file:// address, or ours): the share is spent either way.
        consumeIntent(intent)
        return SharedStreams(uris, distinct.size - uris.size, caption)
    }

    /** Pure rule: only `content://` from another app. */
    fun acceptable(uri: Uri, ownAuthorityPrefix: String): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        val authority = uri.authority ?: return false
        return authority != ownAuthorityPrefix && !authority.startsWith("$ownAuthorityPrefix.")
    }

    private fun streams(intent: Intent): List<Uri> {
        val out = ArrayList<Uri>()
        try {
            if (intent.action == Intent.ACTION_SEND) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::add)
            } else {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::addAll)
            }
            // Some apps only fill the clip.
            if (out.isEmpty()) {
                val clip = intent.clipData
                if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(out::add)
            }
        } catch (_: RuntimeException) {
            // A foreign Bundle that will not unparcel reads as no streams.
            return emptyList()
        }
        return out
    }

    private fun captionOf(intent: Intent): String? {
        val body = runCatchingRuntime { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }?.trim().orEmpty()
        val subject = runCatchingRuntime { intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString() }?.trim().orEmpty()
        return SharedTextIntake.combine(body, subject).ifEmpty { null }
    }

    private inline fun runCatchingRuntime(block: () -> String?): String? =
        try {
            block()
        } catch (_: RuntimeException) {
            null
        }

    private fun consumeIntent(intent: Intent) {
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_SUBJECT)
        intent.clipData = null
        intent.data = null // also clears the type
    }
}
