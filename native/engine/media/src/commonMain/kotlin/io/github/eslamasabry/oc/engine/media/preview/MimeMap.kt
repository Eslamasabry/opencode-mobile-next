package io.github.eslamasabry.oc.engine.media.preview

/** MIME rules for previews (port of `FilePreviewData._normalizedMime/_mimeFromName/_isTextMime`). */
public object MimeMap {
    /** Lowercased type without parameters, or null for a blank value. */
    public fun normalize(value: String?): String? = value?.substringBefore(';')?.trim()?.lowercase()?.ifEmpty { null }

    public fun fromName(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return null
        return when (name.substring(dot + 1).lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "xml" -> "application/xml"
            "md", "txt", "log", "dart", "js", "ts", "tsx", "jsx", "py", "go", "rs", "yaml", "yml" -> "text/plain"
            else -> null
        }
    }

    public fun isText(mime: String?): Boolean =
        mime != null &&
            (
                mime.startsWith("text/") || mime == "application/json" || mime == "application/javascript" ||
                    mime == "application/xml" || mime == "image/svg+xml"
                )

    public fun isRasterImage(mime: String?): Boolean =
        mime == "image/png" || mime == "image/jpeg" || mime == "image/gif" || mime == "image/webp" || mime == "image/bmp"
}
