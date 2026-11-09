package io.github.eslamasabry.oc.android.tiles

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** What the stash may keep: the composer's own limits, passed in so there is one copy of them. */
data class StashLimits(val maxItems: Int, val maxItemBytes: Long, val maxTotalBytes: Long)

/** One copied item. [file] lives in the app's private storage; [name] and [mime] are only hints. */
class StashedFile(val file: File, val name: String?, val mime: String?) {
    override fun toString(): String = "StashedFile(bytes=${file.length()})" // never the name
}

/** Why an item was not copied. */
enum class StashRefusal { OverCount, TooLarge, Unreadable }

sealed interface StashSlot {
    class Kept(val stashed: StashedFile) : StashSlot {
        override fun toString(): String = "Kept($stashed)"
    }

    data class Refused(val why: StashRefusal) : StashSlot
}

/**
 * Copies shared streams into the app's private storage the moment they arrive (the read grant is temporary), each
 * bounded by [StashLimits] while it is read, so a huge or endless stream never lands whole. Slots keep the order of
 * the share. No address, name or byte is logged; failures are values.
 */
class SharedStash(private val resolver: ContentResolver, private val dir: File) {
    /** Items beyond [StashLimits.maxItems] are not opened at all; they come back as [StashRefusal.OverCount]. */
    fun copyIn(uris: List<Uri>, limits: StashLimits): List<StashSlot> {
        dir.mkdirs()
        var total = 0L
        return uris.mapIndexed { index, uri ->
            if (index >= limits.maxItems) return@mapIndexed StashSlot.Refused(StashRefusal.OverCount)
            val room = minOf(limits.maxItemBytes, limits.maxTotalBytes - total)
            if (room <= 0) return@mapIndexed StashSlot.Refused(StashRefusal.TooLarge)
            val meta = describe(uri)
            if (meta.size != null && meta.size > room) return@mapIndexed StashSlot.Refused(StashRefusal.TooLarge)
            val target = File(dir, "share-${System.nanoTime()}-$index.bin")
            val written =
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { out -> copyBounded(input, out, room) }
                    } ?: -1L
                } catch (_: IOException) {
                    -1L
                } catch (_: SecurityException) {
                    -1L
                } catch (_: RuntimeException) {
                    -1L
                }
            when {
                written == TOO_BIG -> {
                    target.delete()
                    StashSlot.Refused(StashRefusal.TooLarge)
                }
                written < 0 -> {
                    target.delete()
                    StashSlot.Refused(StashRefusal.Unreadable)
                }
                else -> {
                    total += written
                    StashSlot.Kept(StashedFile(target, meta.name, meta.mime))
                }
            }
        }
    }

    /** Deletes every copy this stash made (after the draft saved them, on discard, and on start-up leftovers). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private class Meta(val name: String?, val size: Long?, val mime: String?)

    private fun describe(uri: Uri): Meta {
        var name: String? = null
        var size: Long? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s).takeIf { it >= 0 }
                }
            }
        } catch (_: RuntimeException) {
            // The bounded read decides whether the item is really readable.
        }
        val mime =
            try {
                resolver.getType(uri)
            } catch (_: RuntimeException) {
                null
            }
        return Meta(name, size, mime)
    }

    private fun copyBounded(input: java.io.InputStream, out: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return total
            total += n
            if (total > limit) return TOO_BIG
            out.write(buffer, 0, n)
        }
    }

    private companion object {
        const val BUFFER = 16 * 1024
        const val TOO_BIG = -2L
    }
}
