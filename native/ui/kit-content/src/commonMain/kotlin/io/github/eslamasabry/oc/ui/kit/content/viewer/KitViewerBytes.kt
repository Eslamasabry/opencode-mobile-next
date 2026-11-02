package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageView
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

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
