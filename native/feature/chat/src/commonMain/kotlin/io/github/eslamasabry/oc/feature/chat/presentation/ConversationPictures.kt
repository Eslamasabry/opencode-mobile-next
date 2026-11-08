package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow

/** One server picture of the conversation, as the viewer pages through them. */
public data class GalleryPicture(val path: String, val name: String)

/**
 * S24-CHAT story 3 (Flutter `chat_gallery.dart`): the pictures a conversation's replies show from
 * the server (Markdown `![label](path)`), oldest first, each once. Web pictures are links, and a
 * `data:` picture has no path, so neither is listed. [rows] are newest first, as the engine keeps
 * them. O(rows).
 */
public object ConversationPictures {
    private val image = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")

    public fun of(rows: List<TranscriptRow>): List<GalleryPicture> {
        val seen = LinkedHashMap<String, GalleryPicture>()
        for (i in rows.indices.reversed()) {
            val row = rows[i] as? BlockRow ?: continue
            if ("![" !in row.text) continue
            for (m in image.findAll(row.text)) {
                val path = m.groupValues[2]
                if (path.contains("://") || path.startsWith("data:")) continue
                val label = m.groupValues[1].trim()
                if (path !in seen)
                    seen[path] = GalleryPicture(path, label.ifEmpty { path.substringAfterLast('/') })
            }
        }
        return seen.values.toList()
    }

    /** The viewer's pages and the one to open on: [path] joins the list if no reply named it. */
    public fun around(rows: List<TranscriptRow>, path: String, name: String): Pair<List<GalleryPicture>, Int> {
        val all = of(rows)
        val at = all.indexOfFirst { it.path == path }
        return if (at >= 0) all to at else (all + GalleryPicture(path, name)) to all.size
    }
}
