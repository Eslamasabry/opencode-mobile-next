package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.engine.media.preview.StrictUtf8

/**
 * The provider-facing MIME of a prompt attachment, or null for an unsupported binary (plan 10 §10.6 step 4; port of
 * `lib/domain/prompt_attachment.dart`, B-CHAT-009). OpenCode expands exact `text/plain` data URLs into text instead of
 * sending them to a model as arbitrary file parts, so every textual type becomes exactly `text/plain`.
 */
public object PromptAttachmentMime {
    private val MEDIA =
        mapOf(
            "image/png" to "image/png",
            "image/jpeg" to "image/jpeg",
            "image/jpg" to "image/jpeg",
            "image/gif" to "image/gif",
            "image/webp" to "image/webp",
            "application/pdf" to "application/pdf",
        )
    private val BY_EXTENSION =
        mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp", "pdf" to "application/pdf")
    private val TEXT_EXTENSIONS =
        setOf(
            "md", "markdown", "txt", "log", "html", "htm", "css", "csv", "json", "xml", "yaml", "yml", "toml", "dart",
            "js", "ts", "tsx", "jsx", "py", "go", "rs", "java", "kt", "kts", "c", "cc", "cpp", "h", "hpp", "sh", "sql",
        )
    private val TEXT_APPLICATION =
        setOf(
            "application/json", "application/javascript", "application/x-javascript", "application/xml", "application/yaml",
            "application/x-yaml", "application/toml", "application/x-sh", "application/sql", "application/graphql",
            "application/x-ndjson",
        )

    public fun of(declaredMime: String?, name: String, bytes: ByteArray): String? {
        val declared = declaredMime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        MEDIA[declared]?.let { return it }
        if (isTextMime(declared)) {
            if (bytes.isNotEmpty() && !looksLikeText(bytes)) return null
            return TEXT_PLAIN
        }
        if (declared.startsWith("image/")) return null
        val extension = if ('.' in name) name.substringAfterLast('.').lowercase() else ""
        BY_EXTENSION[extension]?.let { return it }
        if (extension in TEXT_EXTENSIONS) {
            if (bytes.isNotEmpty() && !looksLikeText(bytes)) return null
            return TEXT_PLAIN
        }
        if ((declared.isEmpty() || declared == "application/octet-stream") && looksLikeText(bytes)) return TEXT_PLAIN
        return null
    }

    public const val TEXT_PLAIN: String = "text/plain"

    private fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") || mime in TEXT_APPLICATION || mime.endsWith("+json") || mime.endsWith("+xml")

    /** No NUL; valid UTF-8; no control characters except tab, LF, VT, FF and CR. */
    public fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.any { it.toInt() == 0 }) return false
        val text = StrictUtf8.decode(bytes) ?: return false
        return text.none { it.code < 0x09 || (it.code in 0x0E until 0x20) }
    }
}

/** File names for attachments (plan 10 §10.6 step 5): base name only; control characters and `"` → `_`; empty → `file`. */
public object AttachmentName {
    public fun clean(name: String?): String {
        val base = name.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.map { if (it.code < 0x20 || it.code == 0x7F || it == '"') '_' else it }.joinToString("").trim()
        return cleaned.ifEmpty { "file" }
    }
}
