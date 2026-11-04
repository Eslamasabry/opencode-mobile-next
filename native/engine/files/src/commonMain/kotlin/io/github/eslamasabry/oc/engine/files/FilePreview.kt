package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.model.FileContent
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.ImmutableList

/**
 * The original bytes of an opened file: what Share on Wi-Fi and Open with hand over, never the
 * preview text.
 */
public class FileData(
    public val path: String,
    public val mime: String?,
    public val bytes: ByteArray,
) {
    public val name: String
        get() = FilePaths.name(path)

    override fun toString(): String =
        "FileData(${bytes.size} bytes, mime=$mime)" // never the path or content
}

/** What the viewer shows for one file; drawing is the kit's (KitViewer*). */
public sealed interface FileBody {
    public data class Text(val text: String, val truncated: Boolean) : FileBody

    public data class Code(
        val text: String,
        val language: String?,
        val truncated: Boolean,
        val line: Int?,
    ) : FileBody

    public data class Markdown(val text: String, val truncated: Boolean) : FileBody

    public data class Delimited(
        val rows: ImmutableList<ImmutableList<String>>,
        val original: String,
        val truncated: Boolean,
    ) : FileBody

    public data class Svg(val source: String, val original: String) : FileBody

    public class Image(public val bytes: ByteArray) : FileBody

    public class Pdf(public val bytes: ByteArray) : FileBody

    /**
     * Not text, or text that is not valid UTF-8 (B-MEDIA-001): no mangled preview; the bytes stay
     * exact.
     */
    public data class Binary(val mime: String?, val sizeBytes: Long) : FileBody
}

/**
 * Ports of `_FilesViewer._viewerContent` / `_exportData` (files_viewer.dart) and
 * `common_file_preview.dart`.
 */
public object FilePreview {
    /** files_screen.dart `_maxViewerChars`. */
    public const val MAX_VIEWER_CHARS: Int = 200_000

    private const val SNIFF_BYTES = 8192

    private val images = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    private val markdown = setOf("md", "markdown")

    private val code =
        setOf(
            "kt",
            "kts",
            "java",
            "dart",
            "js",
            "jsx",
            "ts",
            "tsx",
            "mjs",
            "cjs",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "h",
            "cc",
            "cpp",
            "hpp",
            "cs",
            "swift",
            "m",
            "mm",
            "php",
            "sh",
            "bash",
            "zsh",
            "fish",
            "ps1",
            "lua",
            "pl",
            "r",
            "scala",
            "sql",
            "html",
            "htm",
            "css",
            "scss",
            "sass",
            "less",
            "vue",
            "svelte",
            "json",
            "jsonc",
            "yaml",
            "yml",
            "toml",
            "xml",
            "gradle",
            "ini",
            "cfg",
            "conf",
            "properties",
            "proto",
            "graphql",
            "dockerfile",
            "makefile",
            "cmake",
            "svg",
            "tf",
            "nix",
            "zig",
            "ex",
            "exs",
            "erl",
            "hs",
            "clj",
            "elm",
            "groovy",
        )

    private val mimes =
        mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "bmp" to "image/bmp",
            "svg" to "image/svg+xml",
            "pdf" to "application/pdf",
            "md" to "text/markdown",
            "markdown" to "text/markdown",
            "txt" to "text/plain",
            "json" to "application/json",
            "html" to "text/html",
            "htm" to "text/html",
            "css" to "text/css",
            "js" to "text/javascript",
            "csv" to "text/csv",
            "xml" to "application/xml",
            "zip" to "application/zip",
            "mp4" to "video/mp4",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "apk" to "application/vnd.android.package-archive",
        )

    public fun mimeFor(path: String): String? = mimes[FilePaths.extension(path)]

    /** The exact bytes the server sent: base64 content decoded, text content as UTF-8. */
    public fun data(path: String, content: FileContent): FileData? {
        val bytes =
            if (content.encoding == "base64") decodeBase64(content.content) ?: return null
            else content.content.encodeToByteArray()
        val mime = content.mimeType?.takeIf { it.isNotBlank() } ?: mimeFor(path)
        return FileData(path, mime, bytes)
    }

    /**
     * What to draw for [data]. [line] opens the code view at that line (a path chip with `:42`).
     */
    public fun body(data: FileData, line: Int? = null): FileBody {
        val ext = FilePaths.extension(data.path)
        val bytes = data.bytes
        if (
            (ext in images ||
                data.mime?.startsWith("image/") == true && data.mime != "image/svg+xml") &&
                bytes.isNotEmpty() &&
                line == null
        ) {
            return FileBody.Image(bytes)
        }
        if ((ext == "pdf" || data.mime == "application/pdf") && bytes.isNotEmpty())
            return FileBody.Pdf(bytes)
        val text = strictText(bytes) ?: return FileBody.Binary(data.mime, bytes.size.toLong())
        val (shown, truncated) = cut(text)
        val separator = DelimitedText.separator(data.name, data.mime)
        if (line == null && separator != null && !truncated) {
            return FileBody.Delimited(text, DelimitedText.parse(text, separator))
        }
        return when {
            line != null -> FileBody.Code(shown, ext.ifEmpty { null }, truncated, line)
            ext in markdown -> FileBody.Markdown(shown, truncated)
            ext in code ||
                FilePaths.name(data.path).lowercase() in setOf("dockerfile", "makefile") ->
                FileBody.Code(shown, ext.ifEmpty { null }, truncated, null)
            else -> FileBody.Text(shown, truncated)
        }
    }

    /**
     * Strict UTF-8 (B-MEDIA-001): a NUL byte or an invalid sequence means no text preview at all.
     */
    public fun strictText(bytes: ByteArray): String? {
        val sniff = minOf(bytes.size, SNIFF_BYTES)
        for (i in 0 until sniff) if (bytes[i] == 0.toByte()) return null
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** At most [MAX_VIEWER_CHARS], never splitting a surrogate pair. */
    public fun cut(text: String): Pair<String, Boolean> {
        if (text.length <= MAX_VIEWER_CHARS) return text to false
        var end = MAX_VIEWER_CHARS
        if (text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end) to true
    }

    private fun decodeBase64(text: String): ByteArray? =
        try {
            Base64.decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
}
