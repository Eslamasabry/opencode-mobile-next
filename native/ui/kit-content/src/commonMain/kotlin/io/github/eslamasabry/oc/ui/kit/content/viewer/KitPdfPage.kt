package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.content.foundation.ContentTextAction
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitViewerPage
import io.github.eslamasabry.oc.ui.words.res.kitViewerPageFailed
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.image.KitImage
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageSource
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomView
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One page's state, owned by the host's page queue (S5-VIEWER `PdfPages`). */
@Immutable
public sealed interface KitPdfPageState {
    /** Waiting for its turn or rendering. [aspect] (width / height) reserves the page's space when known. */
    public data class Loading(val aspect: Float? = null) : KitPdfPageState

    public data class Ready(val image: ImageBitmap) : KitPdfPageState

    /** The host's own reason, already in words (for example `filePdfEncrypted`); `null` says only "Couldn't show page N". */
    public data class Failed(val reason: String? = null) : KitPdfPageState
}

/** Test tag prefix of a page: `kit-pdf-page-3` (1-based). */
public const val KIT_PDF_PAGE_TAG: String = "kit-pdf-page-"

/** Plan 10 §11.6: a page is never wider than the reading rail. */
private const val PAGE_MAX_WIDTH_DP = 720

/** A4 portrait, used to reserve space before the first page reports its size. */
private const val DEFAULT_ASPECT = 0.707f

/**
 * One PDF page in the viewer's stacked list (plan 10 §11.6, `KitPdfPage`): the page image on white whatever the theme,
 * `min(available − 2 × space3, 720 dp)` wide, its caption "Page 3 of 12" under it, and per-page loading and error with
 * **Try again**. Pages come from the isolated renderer as decoded bitmaps; this part never touches a document.
 *
 * States: loading (space reserved), ready, failed (+ Try again)
 * Text: frame (caption, Try again), reading (the failure words)
 * Cap: Reading (720 dp)
 * Motion: none
 * Hidden: none
 * Replaces: KitPdfPage (lib/ui/kit/kit_viewer_content.dart, lib/ui/widgets/pdf_file_preview.dart)
 */
@Composable
public fun KitPdfPage(
    page: Int,
    pageCount: Int,
    state: KitPdfPageState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val caption = stringResource(Res.string.kitViewerPage, page.toString(), pageCount.toString())
    val shape = KitShapeId.Square.shape()
    BoxWithConstraints(modifier.fillMaxWidth().testTag("$KIT_PDF_PAGE_TAG$page"), contentAlignment = Alignment.TopCenter) {
        val width: Dp = minOf(maxWidth - (Space.S3 * 2).dp, PAGE_MAX_WIDTH_DP.dp).coerceAtLeast(0.dp)
        Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) { // F: lib/ui/kit/kit_viewer_bodies.dart:236
            when (state) {
                is KitPdfPageState.Ready ->
                    Image(
                        state.image,
                        contentDescription = caption,
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(state.image.width.toFloat() / state.image.height.coerceAtLeast(1))
                            .clip(shape)
                            .border(Dp.Hairline, colors.hairline, shape),
                        contentScale = ContentScale.FillWidth,
                    )
                is KitPdfPageState.Loading ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(state.aspect ?: DEFAULT_ASPECT).background(colors.surface2)
                            .semantics { contentDescription = caption },
                    )
                is KitPdfPageState.Failed ->
                    Column(
                        Modifier.fillMaxWidth().background(colors.surface1).border(Dp.Hairline, colors.hairline, shape)
                            .padding(Space.S4.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        KitText(
                            stringResource(Res.string.kitViewerPageFailed, page.toString()),
                            role = TypeRole.RowTitle,
                            kind = KitTextKind.Frame,
                        )
                        if (state.reason != null) KitText(state.reason, role = TypeRole.Secondary, tone = TextTone.Secondary)
                        if (onRetry != null) ContentTextAction(stringResource(Res.string.kitTryAgain), onRetry)
                    }
            }
            KitText(
                caption,
                Modifier.fillMaxWidth().padding(horizontal = Space.S1.dp), // inside the gutter while the page spans 12..400
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
                textAlign = TextAlign.Center, // the caption centres under its page (kit_viewer_bodies.dart:233-240)
            )
        }
    }
}

// --- The Flutter viewer's PDF body (lib/ui/kit/kit_viewer_bodies.dart `_KitPdfBody`, `_KitPdfPageView`) -----------

/**
 * Every page of [content] stacked in one zoomable list, each `min(available − 2 × space3, reading width)` wide (floored)
 * and centred, space3 above and below the list; a page renders only while it is composed (PERF-4). Rounded as a panel
 * on a wide window, square on a phone.
 */
@Composable
internal fun ViewerPdfBody(content: KitViewerContent.Pdf, name: String) {
    val wide = LocalKitWindow.current.cls.isWide
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = floor(minOf(maxWidth.value - Space.S3 * 2f, Layout.READING_WIDTH.toFloat())).dp // F: lib/ui/kit/kit_viewer_bodies.dart:106
        KitZoomView(name, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Space.S3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(content.pageCount, key = { it }) { index ->
                    ViewerPdfPageView(index, content.pageCount, pageWidth, wide, content.renderPage, content.cancelPage)
                }
            }
        }
    }
}

@Composable
private fun ViewerPdfPageView(
    index: Int,
    count: Int,
    width: Dp,
    rounded: Boolean,
    render: suspend (index: Int, widthPx: Int) -> ViewerPdfPage,
    cancel: ((Int) -> Unit)?,
) {
    val colors = LocalKitColors.current
    val label = stringResource(Res.string.kitViewerPage, (index + 1).toString(), count.toString())
    val widthPx = maxOf(1, (width.value * kitPixelRatio()).roundToInt())
    var attempt by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<ViewerPdfPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(index, widthPx, attempt) {
        failed = false
        var done = false
        try {
            page = render(index, widthPx)
            done = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (!done) cancel?.invoke(index)
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }
    val shown = page
    val height =
        if (shown != null && shown.width > 0) (width.value * shown.height / shown.width).roundToInt().dp
        else (width.value * sqrt(2f)).roundToInt().dp // NV: √2 of Dart math.sqrt2 (kit_viewer_bodies.dart:216), an A-series page
    val shape = if (rounded) KitShapeId.Panel else KitShapeId.Square
    Column(
        Modifier.padding(bottom = Space.S3.dp).testTag("$KIT_PDF_PAGE_TAG${index + 1}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            failed ->
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    KitStateView(
                        KitStateKind.Error,
                        stringResource(Res.string.kitViewerPageFailed, (index + 1).toString()),
                        icon = KitIcons.error,
                        primary = KitAction(stringResource(Res.string.kitTryAgain), onClick = { attempt++ }),
                    )
                }
            shown != null -> KitImage(KitImageSource.Memory(shown.bytes), label, width = width, height = height, shape = shape)
            else -> {
                // A page not drawn yet: the document's opaque ground, edged as the style edges code.
                val fill = if (colors.dark) colors.surface1 else colors.ground
                val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Code, shape, ownFill = fill))
                Box(Modifier.size(width, height).then(look.modifier).clip(look.shape).semantics { contentDescription = label })
            }
        }
        Spacer(Modifier.height(Space.S1.dp))
        KitText(label, Modifier.clearAndSetSemantics {}, role = TypeRole.Caption, tabular = true)
    }
}
