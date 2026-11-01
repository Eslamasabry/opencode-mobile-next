package io.github.eslamasabry.oc.engine.media.preview

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.media.attach.DataUrl

/**
 * What the viewer shows for one file (port of Flutter `FilePreviewData`, plan 10 §11; B-MEDIA-001).
 *
 * Text is decoded **strictly**: invalid UTF-8 or a NUL gives `text == null` (no replacement characters), while
 * [exportBytes] still holds the original bytes for Save. [problem] is set only when nothing could be read at all.
 *
 * Deviation: plan 10 §3.1 files this type under `state/PreviewData.kt`; `state/` is outside S3-MEDIAATT's write set,
 * so it lives in `preview/` until the media lead moves it.
 */
public class PreviewData(
    public val name: String,
    mime: String?,
    public val bytes: ByteArray? = null,
    text: String? = null,
    public val truncated: Boolean = false,
    public val problem: MediaProblem? = null,
) {
    /** The declared type (normalized), else the type the name suggests. */
    public val mime: String? = MimeMap.normalize(mime) ?: MimeMap.fromName(name)

    public val text: String? = text ?: decodeText(bytes, this.mime, name)

    /** Bytes for Save: the original bytes when there are any, else the text as UTF-8. */
    public val exportBytes: ByteArray? get() = bytes ?: text?.encodeToByteArray()

    public val separator: Char? get() = DelimitedText.separator(name, mime)

    public val isRasterImage: Boolean get() = MimeMap.isRasterImage(mime)

    public val isPdf: Boolean get() = mime == "application/pdf" && bytes?.isNotEmpty() == true

    /** The table for CSV/TSV text, or null (the viewer then shows Source). */
    public fun table(): DelimitedText? {
        val sep = separator ?: return null
        val source = text ?: return null
        return DelimitedText.parse(source, sep).takeIf { it.failure == null }
    }

    /** A static SVG picture, or null (Source only). */
    public fun svg(): StaticSvg? = if (mime == "image/svg+xml") text?.let(StaticSvg::parse) else null

    public companion object {
        /** A chat attachment: `data:` URLs decode; anything else is [MediaProblem.RemoteAttachmentPreview]. */
        public fun fromDataUrl(name: String, mime: String?, url: String?): PreviewData {
            val normalized = MimeMap.normalize(mime)
            if (url.isNullOrBlank()) return PreviewData(name, normalized, problem = MediaProblem.AttachmentContentMissing)
            if (!url.startsWith("data:")) return PreviewData(name, normalized, problem = MediaProblem.RemoteAttachmentPreview)
            val decoded = DataUrl.decode(url) ?: return PreviewData(name, normalized, problem = MediaProblem.FileNotShowable)
            return PreviewData(name, normalized ?: MimeMap.normalize(decoded.mime), bytes = decoded.bytes)
        }

        private fun decodeText(bytes: ByteArray?, mime: String?, name: String): String? {
            if (bytes == null) return null
            if (!MimeMap.isText(mime) && DelimitedText.separator(name, mime) == null) return null
            val text = StrictUtf8.decode(bytes) ?: return null
            return if ('\u0000' in text) null else text
        }
    }
}

/** Plan 10 §4.6: what ViewModels call. Pure, so the default implementation needs no platform. */
public interface FilePreview {
    public suspend fun prepare(name: String, declaredMime: String?, bytes: ByteArray, wantedLine: Int?): PreviewData
}

public object DefaultFilePreview : FilePreview {
    override suspend fun prepare(name: String, declaredMime: String?, bytes: ByteArray, wantedLine: Int?): PreviewData =
        PreviewData(name, declaredMime, bytes = bytes)
}
