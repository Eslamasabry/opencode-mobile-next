package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageView
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Which renderer a [KitViewerContent] uses (Dart `KitViewerKind`, lib/ui/kit/kit_viewer_content.dart): the enum order is
 * the wire contract, so the values are never reordered.
 */
@Immutable
public enum class KitViewerKind { Text, Code, Markdown, Image, Pdf, Svg, Delimited, Binary }

/**
 * The wrap a viewer uses before the person picks one (Dart `kitViewerDefaultWrap`): source code scrolls sideways on
 * every window, so a long line is never broken mid-identifier; text, markdown source and the rest read like output,
 * wrapped on a compact window. `KitCodeBlock.defaultWrap(context, kind == code ? code : output)`: only the output kind
 * wraps, and only on a compact window.
 */
public fun kitViewerDefaultWrap(window: WindowClass, kind: KitViewerKind?): Boolean =
    kind != KitViewerKind.Code && window == WindowClass.Compact // F: lib/ui/kit/kit_viewer_content.dart:10-14 lib/ui/kit/kit_code_block.dart:334-337

/**
 * One page of a PDF, already rendered by the caller's renderer (Dart `KitPdfPage`; the name `KitPdfPage` is the page
 * composable in KitPdfPage.kt). In pixels.
 */
@Immutable
public data class ViewerPdfPage(
    /** An encoded bitmap (PNG) at the requested pixel width. */
    public val bytes: ByteArray,
    public val width: Int,
    public val height: Int,
) {
    override fun equals(other: Any?): Boolean = other is ViewerPdfPage && other.bytes contentEquals bytes && other.width == width && other.height == height

    override fun hashCode(): Int = listOf(bytes.contentHashCode(), width, height).hashCode() // NV: data-array hash, rule is == ⇒ equal hash
}

/**
 * What the viewer shows (Dart `KitViewerContent`, lib/ui/kit/kit_viewer_content.dart). Every constructor is data only:
 * parsing, sanitising and file reading stay with the caller. [copyText] is the whole text for Copy and Find (text,
 * code, markdown source, svg and delimited originals); null for image, pdf and binary.
 */
@Immutable
public sealed interface KitViewerContent {
    public val kind: KitViewerKind

    /** The whole text for Copy and Find; null for image, pdf and binary. */
    public val copyText: String?

    /** Plain text: no line numbers, no syntax colour. */
    @Immutable
    public data class Text(
        public val text: String,
        public val truncated: Boolean = false, // F: lib/ui/kit/kit_viewer_content.dart:40
        public val totalLines: Int? = null, // F: lib/ui/kit/kit_viewer_content.dart:41
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Text // F: lib/ui/kit/kit_viewer_content.dart:42
        override val copyText: String? get() = text // F: lib/ui/kit/kit_viewer_content.dart:225
    }

    /**
     * Source code: line numbers, syntax colour from [language]. [initialLine] (1-based) is scrolled to and marked.
     */
    @Immutable
    public data class Code(
        public val text: String,
        public val language: String? = null, // F: lib/ui/kit/kit_viewer_content.dart:63
        public val truncated: Boolean = false, // F: lib/ui/kit/kit_viewer_content.dart:64
        public val totalLines: Int? = null, // F: lib/ui/kit/kit_viewer_content.dart:65
        public val initialLine: Int? = null, // F: lib/ui/kit/kit_viewer_content.dart:66
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Code // F: lib/ui/kit/kit_viewer_content.dart:67
        override val copyText: String? get() = text // F: lib/ui/kit/kit_viewer_content.dart:225
    }

    /** A Markdown document, reflowed as prose (Show source swaps to its text). */
    @Immutable
    public data class Markdown(
        public val source: String,
        public val truncated: Boolean = false, // F: lib/ui/kit/kit_viewer_content.dart:85
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Markdown // F: lib/ui/kit/kit_viewer_content.dart:86
        override val copyText: String? get() = source // F: lib/ui/kit/kit_viewer_content.dart:225
    }

    /** An encoded image; [semanticsLabel] defaults to the viewer's name. */
    @Immutable
    public data class Image(
        public val bytes: ByteArray,
        public val semanticsLabel: String? = null, // F: lib/ui/kit/kit_viewer_content.dart:104
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Image // F: lib/ui/kit/kit_viewer_content.dart:105
        override val copyText: String? get() = null // F: lib/ui/kit/kit_viewer_content.dart:228

        override fun equals(other: Any?): Boolean = other is Image && other.bytes contentEquals bytes && other.semanticsLabel == semanticsLabel

        override fun hashCode(): Int = listOf(bytes.contentHashCode(), semanticsLabel).hashCode() // NV: data-array hash, rule is == ⇒ equal hash
    }

    /**
     * A PDF of [pageCount] pages. [renderPage] is called only for visible pages; [cancelPage] runs for a page scrolled
     * away before it rendered.
     */
    @Immutable
    public data class Pdf(
        public val pageCount: Int,
        public val renderPage: suspend (index: Int, widthPx: Int) -> ViewerPdfPage,
        public val cancelPage: ((index: Int) -> Unit)? = null, // F: lib/ui/kit/kit_viewer_content.dart:127
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Pdf // F: lib/ui/kit/kit_viewer_content.dart:128
        override val copyText: String? get() = null // F: lib/ui/kit/kit_viewer_content.dart:228
    }

    /** An SVG already sanitised by the caller (`StaticSvg`); [original] is the file's own text for Show source and Copy. */
    @Immutable
    public data class Svg(
        public val safeSource: String,
        public val original: String? = null, // F: lib/ui/kit/kit_viewer_content.dart:149
        public val truncated: Boolean = false, // F: lib/ui/kit/kit_viewer_content.dart:150
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Svg // F: lib/ui/kit/kit_viewer_content.dart:151
        override val copyText: String? get() = original ?: safeSource // F: lib/ui/kit/kit_viewer_content.dart:226
    }

    /**
     * A parsed CSV or TSV: [rows] of cells, the first a header row when [header]. [original] is the raw text for Show
     * source and Copy.
     */
    @Immutable
    public data class Delimited(
        public val rows: List<List<String>>,
        public val original: String? = null, // F: lib/ui/kit/kit_viewer_content.dart:172
        public val header: Boolean = true, // F: lib/ui/kit/kit_viewer_content.dart:173
        public val truncated: Boolean = false, // F: lib/ui/kit/kit_viewer_content.dart:174
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Delimited // F: lib/ui/kit/kit_viewer_content.dart:175
        override val copyText: String? get() = original // F: lib/ui/kit/kit_viewer_content.dart:227
    }

    /** A file the viewer cannot show: its type and size, and the caller's action. */
    @Immutable
    public data class Binary(
        public val mimeType: String? = null, // F: lib/ui/kit/kit_viewer_content.dart:194
        public val byteLength: Long? = null, // F: lib/ui/kit/kit_viewer_content.dart:194
    ) : KitViewerContent {
        override val kind: KitViewerKind get() = KitViewerKind.Binary // F: lib/ui/kit/kit_viewer_content.dart:195
        override val copyText: String? get() = null // F: lib/ui/kit/kit_viewer_content.dart:228
    }
}

/**
 * Where the content comes from (Dart `KitViewerSource`): in hand, or loaded on open — the viewer shows loading and a
 * failure with Try again (which runs [load] again). Equality is Dart's: the same content instance and the same load
 * function; two equal values are different sources.
 */
@Immutable
public class KitViewerSource private constructor(
    /** Content already in hand; null when loaded on open. */
    public val content: KitViewerContent?,
    private val load: (suspend () -> KitViewerContent)?,
) {
    override fun equals(other: Any?): Boolean =
        other is KitViewerSource && other.content === content && other.load === load // F: lib/ui/kit/kit_viewer_content.dart:250-253

    override fun hashCode(): Int = listOf(System.identityHashCode(content), load?.hashCode()).hashCode() // F: lib/ui/kit/kit_viewer_content.dart:255-256

    public companion object {
        /** Content already in hand (Dart `KitViewerSource(content)`). */
        public operator fun invoke(content: KitViewerContent): KitViewerSource = KitViewerSource(content, null) // F: lib/ui/kit/kit_viewer_content.dart:236-238

        /** Content loaded on open (Dart `KitViewerSource.load(load)`). */
        public fun load(load: suspend () -> KitViewerContent): KitViewerSource = KitViewerSource(null, load) // F: lib/ui/kit/kit_viewer_content.dart:242-244
    }
}

/**
 * Encoded picture bytes (PNG, JPEG, GIF, WebP, BMP) in the zoomable image view (P12-FILES; plan 10 §11.4 image body).
 * Hosts hold bytes, never bitmaps, so features stay free of graphics types. Bytes that do not decode show [unreadable]
 * (the host passes the binary body) instead of an empty frame.
 *
 * States: image, unreadable
 * Text: none of its own
 * Cap: the viewer's
 * Motion: none of its own
 * Hidden: none (zoom has visible controls)
 * Replaces: KitViewerContent.image (lib/ui/kit/kit_viewer_content.dart)
 */
@Composable
public fun KitViewerImageBody(
    bytes: ByteArray,
    contentDescription: String,
    modifier: Modifier = Modifier,
    unreadable: @Composable () -> Unit,
) {
    val bitmap = remember(bytes) { decodeOrNull(bytes) }
    if (bitmap == null) {
        unreadable()
        return
    }
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    KitImageView(painter, contentDescription, modifier)
}

/**
 * One PDF page from rendered PNG bytes ([png] null while it renders; [page] is 1-based) in [KitPdfPage]'s stacked list.
 *
 * States: loading, ready, failed (+ Try again)
 * Text: the page's (caption, Try again, [failedReason])
 * Cap: Reading (720 dp)
 * Motion: none
 * Hidden: none
 * Replaces: KitPdfPage with a host-side decode (lib/ui/widgets/pdf_file_preview.dart)
 */
@Composable
public fun KitPdfPngPage(
    page: Int,
    pageCount: Int,
    png: ByteArray?,
    failed: Boolean,
    modifier: Modifier = Modifier,
    failedReason: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val bitmap = remember(png) { png?.let(::decodeOrNull) }
    val state =
        when {
            failed || (png != null && bitmap == null) -> KitPdfPageState.Failed(failedReason)
            bitmap != null -> KitPdfPageState.Ready(bitmap)
            else -> KitPdfPageState.Loading()
        }
    KitPdfPage(page, pageCount, state, modifier, onRetry)
}

@OptIn(ExperimentalResourceApi::class)
private fun decodeOrNull(bytes: ByteArray): ImageBitmap? =
    if (bytes.isEmpty()) null
    else
        try {
            bytes.decodeToImageBitmap()
        } catch (_: RuntimeException) { // an undecodable picture is a plain "can't show", never a crash
            null
        }
