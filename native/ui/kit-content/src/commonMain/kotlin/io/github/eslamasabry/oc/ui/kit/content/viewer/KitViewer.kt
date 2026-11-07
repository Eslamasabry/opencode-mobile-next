package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.content.image.KitImage
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageSource
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomView
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.svg.SvgDecoder
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMore
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShow
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShowBody
import io.github.eslamasabry.oc.ui.words.res.kitViewerCopyContents
import io.github.eslamasabry.oc.ui.words.res.kitViewerEmpty
import io.github.eslamasabry.oc.ui.words.res.kitViewerFind
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindClose
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindCount
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindNext
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindNone
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindPrevious
import io.github.eslamasabry.oc.ui.words.res.kitViewerLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitViewerOpenAll
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import io.github.eslamasabry.oc.ui.words.res.kitViewerShowSource
import io.github.eslamasabry.oc.ui.words.res.kitViewerTruncated
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownSize
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownType
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/** Test tags of [KitViewer] (Dart's `ValueKey`s in kit_viewer_build.dart). */
public object KitViewerKeys {
    public const val VIEWER: String = "kit-viewer"
    public const val MORE: String = "kit-viewer-more"
    public const val CLOSE: String = "kit-viewer-close"
    public const val PRIMARY: String = "kit-viewer-primary"
    public const val OPEN_ALL: String = "kit-viewer-open-all"
    public const val RETRY: String = "kit-viewer-retry"
    public const val FIND: String = "kit-viewer-find"
    public const val FIND_NEXT: String = "kit-viewer-find-next"
    public const val FIND_PREVIOUS: String = "kit-viewer-find-previous"
    public const val FIND_CLOSE: String = "kit-viewer-find-close"
    public const val EMPTY: String = "kit-viewer-empty"
}

/** Text sources show at most this many lines (Dart `KitViewer.maxLines`). */
public const val KIT_VIEWER_MAX_LINES: Int = 2000 // F: lib/ui/kit/kit_viewer.dart:327

/** The most of the viewer's height the frame's top takes before it scrolls (Dart `_topShare`). */
private const val TOP_SHARE = 0.5f // F: lib/ui/kit/kit_viewer.dart:356

/** The notice's words column inside its gutter: the lead glyph slot, then the row gap (KitNotice's layout). */
private const val NOTICE_TEXT_INSET = 40 // NV: native KitNotice lead slot 28 + row gap 12

/** The sheet's share of the window height (Dart `KitLayout.sheetFullHeight`). */
private const val SHEET_FULL_HEIGHT = .95f // F: lib/ui/kit/kit_layout.dart:161

/** The widest the sheet gets (Dart `KitLayout.sheetMaxWidth`). */
private const val SHEET_MAX_WIDTH = 640 // F: lib/ui/kit/kit_layout.dart:142

/** The sheet's top corners (Dart `KitTokens.sheetRadius`). */
private const val SHEET_RADIUS = 30 // F: lib/ui/kit/kit_tokens.dart:795

/** The code block's widest (Dart `KitMaxWidth.list`, kit_code_block.dart:528). */
private const val CODE_MAX_WIDTH = 960 // F: lib/ui/kit/kit_layout.dart:40

/** Find's passive and active mark washes over the accent (plan 06 `KitFindMark`). */
private const val MARK_ALPHA = .18f // NV: KitFindMark passive wash, as KitViewerDocument
private const val MARK_ACTIVE_ALPHA = .4f // NV: KitFindMark active wash, as KitViewerDocument

/** Text prepared once per content (Dart `_Prepared`): capped at [KIT_VIEWER_MAX_LINES], with the counts. */
internal class ViewerPrepared(text: String) {
    val lines: List<String>
    val totalLines: Int
    val capped: Boolean
    val shownLines: Int

    init {
        val all = text.split('\n').toMutableList()
        if (text.endsWith('\n')) all.removeAt(all.lastIndex)
        totalLines = all.size
        capped = totalLines > KIT_VIEWER_MAX_LINES
        lines = if (capped) all.take(KIT_VIEWER_MAX_LINES) else all
        shownLines = minOf(totalLines, KIT_VIEWER_MAX_LINES)
    }

    /** Every case-insensitive literal match of [query], as (line, start, end). */
    fun find(query: String): List<Triple<Int, Int, Int>> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<Triple<Int, Int, Int>>()
        lines.forEachIndexed { line, text ->
            var from = 0
            while (from <= text.length - query.length) {
                val at = text.indexOf(query, from, ignoreCase = true)
                if (at < 0) break
                out += Triple(line, at, at + query.length)
                from = at + query.length
            }
        }
        return out
    }
}

/** What the truncation notice says (Dart `_truncationNotice`); null when nothing is cut. */
internal sealed interface ViewerNotice {
    data class Counted(val shown: Int, val total: Int) : ViewerNotice
    data object Partial : ViewerNotice
}

internal fun viewerNotice(content: KitViewerContent, prepared: ViewerPrepared?, sourceView: Boolean): ViewerNotice? {
    val truncated = when (content) {
        is KitViewerContent.Text -> content.truncated
        is KitViewerContent.Code -> content.truncated
        is KitViewerContent.Markdown -> content.truncated
        is KitViewerContent.Svg -> content.truncated
        is KitViewerContent.Delimited -> content.truncated
        else -> false
    }
    val known = when (content) {
        is KitViewerContent.Text -> content.totalLines
        is KitViewerContent.Code -> content.totalLines
        else -> null
    }
    val textual = content is KitViewerContent.Text || content is KitViewerContent.Code || sourceView
    if (prepared != null && textual) { // F: lib/ui/kit/kit_viewer_build.dart:323-337
        if (!truncated && !prepared.capped) return null
        val total = if (prepared.capped) maxOf(prepared.totalLines, known ?: 0) else known
        return if (total != null && total > prepared.shownLines) ViewerNotice.Counted(prepared.shownLines, total) else ViewerNotice.Partial
    }
    return if (truncated) ViewerNotice.Partial else null
}

/** "2.4 MB" (Dart `_sizeInWords`): 1024 steps, one decimal under 10 above bytes. */
internal fun viewerSizeInWords(bytes: Long?): String? {
    if (bytes == null || bytes < 0) return null
    val units = listOf("B", "KB", "MB", "GB", "TB") // F: lib/ui/kit/kit_viewer_bodies.dart:5
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) { // F: lib/ui/kit/kit_viewer_bodies.dart:8
        value /= 1024 // F: lib/ui/kit/kit_viewer_bodies.dart:9
        unit++
    }
    val whole = unit == 0 || value >= 10 // F: lib/ui/kit/kit_viewer_bodies.dart:14
    val shown = if (whole) kotlin.math.round(value).toLong().toString() else {
        val tenths = kotlin.math.round(value * 10).toLong() // NV: one decimal place
        if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}" // NV: one decimal place
    }
    return "$shown ${units[unit]}"
}

/**
 * The one viewer for a file, a code block opened in full, or a document (Dart `KitViewer`, kit_viewer.dart): the
 * frame is always the same — the name, the muted parent folder, one labelled [primary] on its own row, More (the
 * kit's Find, Copy contents, Wrap lines and Show source, then the caller's [more]) and Close — and the body is one of
 * the kit's renderers: text, code, Markdown, image, PDF, SVG, a delimited table, or "Can't show this file".
 *
 * A host that has a syntax colourer or a Markdown renderer (kit-chat's) passes it as [codeLine] / [prose]; without
 * them code is one colour and Markdown shows its source.
 *
 * States: loading, error, empty, loaded, truncated, binary, finding
 * Text: frame (name, folder, buttons); the body is reading text
 * Cap: Viewer (960 dp)
 * Motion: Find opens with [KitReveal]; Off = at once
 * Hidden: none (every action is in the visible More menu)
 * Replaces: KitViewer (lib/ui/kit/kit_viewer.dart, kit_viewer_build.dart, kit_viewer_bodies.dart)
 */
@Composable
public fun KitViewer(
    name: String,
    source: KitViewerSource,
    modifier: Modifier = Modifier,
    path: String? = null,
    primary: KitAction? = null,
    more: List<KitMenuItem> = emptyList(),
    onClose: (() -> Unit)? = null,
    onOpenAll: (() -> Unit)? = null,
    wrap: Boolean? = null,
    showSource: Boolean? = null,
    window: WindowClass? = null,
    codeLine: ((line: String, language: String?) -> AnnotatedString)? = null,
    prose: (@Composable (source: String) -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    var generation by remember(source) { mutableIntStateOf(0) }
    var content by remember(source) { mutableStateOf(source.content) }
    var failed by remember(source) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(source, generation) {
        val load = source.load ?: return@LaunchedEffect
        content = null
        failed = null
        try {
            content = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // a failed load is the error state with Try again, never a crash
            failed = e
        }
    }
    val loading = content == null && failed == null
    var wrapChoice by remember(source) { mutableStateOf<Boolean?>(null) }
    var sourceChoice by remember(source) { mutableStateOf<Boolean?>(null) }
    var findOpen by remember(source) { mutableStateOf(false) }
    var query by remember(source) { mutableStateOf("") }
    var active by remember(source) { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize().testTag(KitViewerKeys.VIEWER)) {
        val windowClass = window ?: WindowClass.of(maxWidth.value)
        val compact = windowClass == WindowClass.Compact
        val c = content
        val sourceOn = sourceChoice ?: showSource ?: false
        val sourceApplies = when (c) { // F: lib/ui/kit/kit_viewer.dart:436-443
            is KitViewerContent.Markdown -> true
            is KitViewerContent.Svg -> c.original != null
            is KitViewerContent.Delimited -> c.original != null
            else -> false
        }
        val sourceView = sourceApplies && sourceOn
        // The text the body draws as code, or null for prose, image, PDF, picture, table and binary.
        val codeText: String? = when (c) { // F: lib/ui/kit/kit_viewer.dart:447-457
            is KitViewerContent.Text -> c.text
            is KitViewerContent.Code -> c.text
            is KitViewerContent.Markdown -> if (sourceView || prose == null) c.source else null
            is KitViewerContent.Svg -> if (sourceView) c.original else null
            is KitViewerContent.Delimited -> if (sourceView) c.original else null
            else -> null
        }
        val empty = when (c) { // F: lib/ui/kit/kit_viewer.dart:459-470
            is KitViewerContent.Text -> c.text.isBlank()
            is KitViewerContent.Code -> c.text.isBlank()
            is KitViewerContent.Markdown -> c.source.isBlank()
            is KitViewerContent.Svg -> c.safeSource.isBlank()
            is KitViewerContent.Delimited -> c.rows.isEmpty()
            else -> false
        }
        val prepared = remember(codeText) { codeText?.let(::ViewerPrepared) }
        val effectiveWrap = wrapChoice ?: wrap ?: kitViewerDefaultWrap(windowClass, c?.kind)
        val findable = codeText != null && !empty
        val marks = remember(prepared, query, findOpen) { if (findOpen) prepared?.find(query).orEmpty() else emptyList() }
        @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
        val menu = buildList {
            if (findable) add(KitMenuItem(stringResource(Res.string.kitViewerFind), { findOpen = true }, KitIcons.search))
            val copyText = c?.copyText
            if (copyText != null && !empty) {
                add(KitMenuItem(stringResource(Res.string.kitViewerCopyContents), { clipboard.setText(AnnotatedString(copyText)) }, KitIcons.copy))
            }
            if (codeText != null && !empty) {
                add(KitMenuItem(stringResource(Res.string.kitWrapLines), { wrapChoice = !effectiveWrap }, KitIcons.wrapText, checked = effectiveWrap))
            }
            if (sourceApplies) {
                add(KitMenuItem(stringResource(Res.string.kitViewerShowSource), { findOpen = false; sourceChoice = !sourceView }, KitIcons.code, checked = sourceView))
            }
            addAll(more)
        }
        val topMax = maxHeight * TOP_SHARE
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.heightIn(max = topMax).verticalScroll(rememberScrollState())) {
                // The divider sits on the header's last hairline instead of adding its own height, so the blocks
                // under it keep whole spacing steps.
                Box {
                    ViewerHeader(name, path, if (c is KitViewerContent.Binary) null else primary, compact, menu, onClose)
                    // One physical pixel, as Dart's KitDivider (KitTokens.hairlineWidth), on the role's hairline.
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(kitHairline()).background(LocalKitColors.current.hairline))
                }
                val notice = c?.takeIf { !empty }?.let { viewerNotice(it, prepared, sourceView) }
                if (notice != null) {
                    KitNotice(
                        message = when (notice) {
                            is ViewerNotice.Counted ->
                                stringResource(Res.string.kitViewerTruncated, groupDigits(notice.shown), groupDigits(notice.total))
                            ViewerNotice.Partial -> stringResource(Res.string.kitViewerPartial)
                        },
                        modifier = Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp), // F: lib/ui/kit/kit_viewer.dart:678-683
                        liveRegion = false,
                    )
                    if (onOpenAll != null) {
                        // "Open all" under the notice's words (Dart passes it as the notice's action).
                        KitButton(
                            stringResource(Res.string.kitViewerOpenAll),
                            onOpenAll,
                            Modifier.padding(start = (tokens.gutter + NOTICE_TEXT_INSET).dp, top = Space.S1.dp, bottom = Space.S2.dp)
                                .testTag(KitViewerKeys.OPEN_ALL),
                            role = KitButtonRole.Secondary,
                            expand = false,
                        )
                    }
                }
            }
            KitReveal(findOpen) {
                ViewerFindBar(
                    count = marks.size,
                    active = active,
                    query = query,
                    onQuery = { query = it; active = 0 },
                    onStep = { delta -> if (marks.isNotEmpty()) active = ((active + delta) % marks.size + marks.size) % marks.size },
                    onClose = { findOpen = false; query = ""; active = 0 },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp), contentAlignment = Alignment.TopCenter) { // F: lib/ui/kit/kit_viewer_build.dart:362-366
                            // Dart's KitSkeletonRows holds the list cap itself (kit_progress.dart:60); the native part
                            // does not yet, so the viewer holds it here (a no-op once the kit part does).
                            Box(Modifier.kitMaxWidth(CapRole.List)) { KitSkeletonRows(count = 6) }
                        }
                    failed != null ->
                        ViewerCentred {
                            KitStateView(
                                kind = KitStateKind.Error,
                                title = stringResource(Res.string.kitViewerLoadFailed, name),
                                icon = KitIcons.error,
                                primary = KitAction(stringResource(Res.string.kitTryAgain), { generation++ }),
                            )
                        }
                    c == null -> Unit
                    empty ->
                        KitText(
                            stringResource(Res.string.kitViewerEmpty),
                            Modifier.padding(tokens.gutter.dp).testTag(KitViewerKeys.EMPTY),
                            tone = TextTone.Tertiary,
                        )
                    prepared != null ->
                        ViewerCode(
                            prepared,
                            language = when (c) {
                                is KitViewerContent.Code -> c.language
                                is KitViewerContent.Markdown -> "markdown"
                                is KitViewerContent.Svg -> "xml"
                                else -> null
                            },
                            lineNumbers = c !is KitViewerContent.Text,
                            wrap = effectiveWrap,
                            marks = marks,
                            active = active,
                            colour = if (c is KitViewerContent.Text) null else codeLine,
                            initialLine = (c as? KitViewerContent.Code)?.initialLine,
                        )
                    c is KitViewerContent.Markdown ->
                        Box(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = tokens.gutter.dp, vertical = Space.S4.dp), // F: lib/ui/kit/kit_viewer_bodies.dart:49-52
                        ) { prose?.invoke(c.source) }
                    c is KitViewerContent.Image -> ViewerImage(c.bytes, c.semanticsLabel ?: name, name)
                    c is KitViewerContent.Pdf -> ViewerPdf(c, name)
                    c is KitViewerContent.Svg -> ViewerSvg(c.safeSource, name)
                    c is KitViewerContent.Delimited -> KitViewerTableBody(c.rows, Modifier.fillMaxSize(), header = c.header)
                    c is KitViewerContent.Binary ->
                        ViewerCentred {
                            KitStateView(
                                kind = KitStateKind.Empty,
                                title = stringResource(Res.string.kitViewerCantShow),
                                body = stringResource(
                                    Res.string.kitViewerCantShowBody,
                                    c.mimeType ?: stringResource(Res.string.kitViewerUnknownType),
                                    viewerSizeInWords(c.byteLength) ?: stringResource(Res.string.kitViewerUnknownSize),
                                ),
                                icon = KitIcons.file,
                                primary = primary,
                            )
                        }
                }
            }
        }
    }
}

/** "5,210": digits grouped by three, as Dart's `intl` formats the counts in the notice. */
internal fun groupDigits(n: Int): String {
    val s = n.toString()
    val out = StringBuilder()
    s.forEachIndexed { i, ch ->
        if (i > 0 && (s.length - i) % 3 == 0) out.append(',') // NV: thousands grouping
        out.append(ch)
    }
    return out.toString()
}

@Composable
private fun ViewerHeader(
    name: String,
    path: String?,
    primary: KitAction?,
    compact: Boolean,
    menu: List<KitMenuItem>,
    onClose: (() -> Unit)?,
) {
    val tokens = LocalKitTokens.current
    val folder = path?.let { ViewerPaths.folderOf(it, name) }
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = tokens.gutter.dp,
                top = (if (onClose == null) Space.S3 else Space.S1).dp, // F: lib/ui/kit/kit_viewer_build.dart:51-56
                end = Space.S2.dp,
                bottom = (if (primary == null) Space.S2 else 0).dp, // F: lib/ui/kit/kit_viewer_build.dart:48
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = Space.S2.dp, end = Space.S2.dp)) { // F: lib/ui/kit/kit_viewer_build.dart:62-65
                KitText(
                    isolateLtr(name),
                    Modifier.testTag(KitViewerTags.NAME).semantics { heading() },
                    role = TypeRole.Headline,
                    kind = KitTextKind.Frame,
                )
                if (folder != null) {
                    Spacer(Modifier.height((Space.S1 / 2f).dp)) // F: lib/ui/kit/kit_viewer_build.dart:31
                    Box(Modifier.testTag(KitViewerTags.FOLDER).semantics { contentDescription = path }) {
                        KitMonoText(isolateLtr(folder), tone = TextTone.Secondary, cut = KitMonoCut.Middle)
                    }
                }
            }
            KitMenu(menuOpen, menu, { menuOpen = false }, title = name) {
                KitIconButton(KitIcons.more, stringResource(Res.string.kitMore), { menuOpen = true }, Modifier.testTag(KitViewerKeys.MORE))
            }
            if (onClose != null) {
                KitIconButton(KitIcons.close, stringResource(Res.string.kitSheetClose), onClose, Modifier.testTag(KitViewerKeys.CLOSE))
            }
        }
        if (primary != null) {
            // The one labelled action has its own row under the name, starting where the name starts.
            Box(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)) { // F: lib/ui/kit/kit_viewer_build.dart:124-129
                KitButton(
                    primary,
                    // Dart shifts its tertiary button back by its 8 dp inset so the words line up with the name; the
                    // native tertiary button draws its words at its own edge, so it needs no shift.
                    Modifier.testTag(KitViewerKeys.PRIMARY),
                    role = if (compact) KitButtonRole.Tertiary else KitButtonRole.Secondary, // F: lib/ui/kit/kit_viewer_build.dart:118
                    expand = false,
                )
            }
        }
    }
}

/** The inline state views bring their own side gutter; the body only centres them vertically. */
@Composable
private fun ViewerCentred(content: @Composable () -> Unit) {
    val tokens = LocalKitTokens.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = tokens.gutter.dp), contentAlignment = Alignment.CenterStart) {
        content()
    }
}

@Composable
private fun ViewerFindBar(
    count: Int,
    active: Int,
    query: String,
    onQuery: (String) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val tokens = LocalKitTokens.current
    Column(Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, top = Space.S2.dp, end = Space.S2.dp, bottom = Space.S2.dp)) { // F: lib/ui/kit/kit_viewer_build.dart:248-253
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitSearchField(stringResource(Res.string.kitViewerFind), onQuery, Modifier.weight(1f).testTag(KitViewerKeys.FIND), onSubmit = { onStep(1) })
            Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/kit_viewer_build.dart:272
            // Up and down: never mirrored.
            KitIconButton(KitIcons.chevronUp, stringResource(Res.string.kitViewerFindPrevious), { onStep(-1) }, Modifier.testTag(KitViewerKeys.FIND_PREVIOUS))
            KitIconButton(KitIcons.chevronDown, stringResource(Res.string.kitViewerFindNext), { onStep(1) }, Modifier.testTag(KitViewerKeys.FIND_NEXT))
            KitIconButton(KitIcons.close, stringResource(Res.string.kitViewerFindClose), onClose, Modifier.testTag(KitViewerKeys.FIND_CLOSE))
        }
        if (query.isNotEmpty()) {
            KitText(
                if (count == 0) stringResource(Res.string.kitViewerFindNone)
                else stringResource(Res.string.kitViewerFindCount, (active + 1).toString(), count.toString()),
                Modifier.padding(top = Space.S2.dp), // F: lib/ui/kit/kit_viewer_build.dart:301
                role = TypeRole.Secondary,
                tabular = true,
            )
        }
    }
}

/**
 * The file as a code block filling the viewer edge to edge (Dart `_codeBody` + `KitCodeBlock.fill`): the details
 * fill, the gutter inset, end-aligned line numbers in text3, one space2 gap, then the line; one sideways scroller for
 * the whole file when it does not wrap. Always left to right.
 */
@Composable
private fun ViewerCode(
    prepared: ViewerPrepared,
    language: String?,
    lineNumbers: Boolean,
    wrap: Boolean,
    marks: List<Triple<Int, Int, Int>>,
    active: Int,
    colour: ((String, String?) -> AnnotatedString)?,
    initialLine: Int?,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val measurer = rememberTextMeasurer()
    val digits = prepared.shownLines.toString().length
    val gutterWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        measurer.measure("9".repeat(digits), mono).size.width.toDp() // F: lib/ui/kit/kit_code_block.dart:1159-1172
    }
    val fill = if (colors.dark) colors.surface1 else colors.ground // Dart tokens.detailsSurface (FlutterKitTokenRoles)
    val list = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialLine?.let { maxOf(0, it - 1) }?.coerceAtMost(maxOf(0, prepared.lines.size - 1)) ?: 0,
    )
    LaunchedEffect(marks, active) {
        if (marks.isNotEmpty()) list.scrollToItem(maxOf(0, marks[active.coerceIn(marks.indices)].first - 3)) // NV: lines kept above the match
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // The block holds the list cap and centres in a wider viewer (KitCodeBlock is `KitMaxWidth.list`).
        BoxWithConstraints(Modifier.fillMaxSize().background(fill), contentAlignment = Alignment.TopCenter) {
        val column = minOf(maxWidth, (CODE_MAX_WIDTH + tokens.gutter * 2).dp)
        SelectionContainer(Modifier.width(column).fillMaxHeight()) {
            LazyColumn(
                Modifier.fillMaxSize().then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                state = list,
                contentPadding = PaddingValues(horizontal = tokens.gutter.dp, vertical = Space.S2.dp), // F: lib/ui/kit/kit_viewer_build.dart:470-473
            ) {
                itemsIndexed(prepared.lines, key = { i, _ -> i }, contentType = { _, _ -> "line" }) { i, line ->
                    // A blank line keeps one space, so it stays a line of its own height.
                    val base = if (line.isEmpty()) AnnotatedString(" ") else colour?.invoke(line, language) ?: AnnotatedString(line)
                    val text = buildAnnotatedString {
                        append(base)
                        marks.forEachIndexed { n, m ->
                            if (m.first == i) {
                                addStyle(SpanStyle(background = colors.accent.copy(alpha = if (n == active) MARK_ACTIVE_ALPHA else MARK_ALPHA)), m.second, m.third)
                            }
                        }
                    }
                    Row(Modifier.then(if (wrap) Modifier.fillMaxWidth() else Modifier)) {
                        if (lineNumbers) {
                            DisableSelection { // copying lines never copies their numbers
                                BasicText(
                                    (i + 1).toString(),
                                    Modifier.width(gutterWidth),
                                    style = mono.copy(color = colors.text3, textAlign = TextAlign.End), // F: lib/ui/kit/kit_code_block.dart:1238-1248
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/kit_code_block.dart:1252
                        }
                        BasicText(
                            text,
                            if (wrap) Modifier.weight(1f) else Modifier,
                            style = mono.copy(color = colors.text1),
                            softWrap = wrap,
                            maxLines = if (wrap) Int.MAX_VALUE else 1,
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * The picture at its own size, scaled down to fit and never up, centred, with the kit's zoom (Dart: KitZoom > Center >
 * KitImage with no size, which an Image lays out at its intrinsic size under loose constraints).
 */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun ViewerImage(bytes: ByteArray, label: String, name: String) {
    val intrinsic = remember(bytes) {
        try {
            bytes.decodeToImageBitmap().let { it.width to it.height }
        } catch (_: RuntimeException) { // undecodable bytes: KitImage shows its own failure inside the frame
            null
        }
    }
    KitZoomView(name, Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fit = intrinsic?.let { (w, h) ->
                val scale = minOf(1f, maxWidth.value / w, maxHeight.value / h)
                Modifier.size((w * scale).dp, (h * scale).dp)
            } ?: Modifier.fillMaxSize()
            KitImage(KitImageSource.Memory(bytes), label, fit)
        }
    }
}

/** The width and height of an SVG's `viewBox`, the size flutter_svg draws a picture at when nothing sizes it. */
internal fun svgViewBox(source: String): Pair<Float, Float>? {
    val box = Regex("""viewBox\s*=\s*["']([^"']+)["']""").find(source)?.groupValues?.get(1) ?: return null
    val parts = box.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
    return if (parts.size == 4 && parts[2] > 0 && parts[3] > 0) parts[2] to parts[3] else null // NV: viewBox is x y w h
}

/** The picture centred at its own size with the kit's zoom (Dart: KitZoom > Center > Padding(space4) > SvgPicture). */
@Composable
private fun ViewerSvg(source: String, name: String) {
    val context = LocalPlatformContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val bytes = remember(source) { source.encodeToByteArray() }
    val size = remember(source) { svgViewBox(source) }
    var failed by remember(source) { mutableStateOf(false) }
    if (failed) {
        KitText(stringResource(Res.string.kitViewerCantShow), Modifier.padding(Space.S4.dp), tone = TextTone.Tertiary)
        return
    }
    KitZoomView(name, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(Space.S4.dp), contentAlignment = Alignment.Center) { // F: lib/ui/kit/kit_viewer_build.dart:419-421
            AsyncImage(
                model = bytes,
                imageLoader = loader,
                contentDescription = name,
                modifier = if (size != null) Modifier.size(size.first.dp, size.second.dp) else Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        }
    }
}

/** The PDF pages stacked down the reading column (Dart `_KitPdfBody`). */
@Composable
private fun ViewerPdf(content: KitViewerContent.Pdf, name: String) {
    KitZoomView(name, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Space.S3.dp), // F: lib/ui/kit/kit_viewer_bodies.dart:110
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(content.pageCount) { index ->
                var png by remember(index) { mutableStateOf<ByteArray?>(null) }
                var failed by remember(index) { mutableStateOf(false) }
                var attempt by remember(index) { mutableIntStateOf(0) }
                LaunchedEffect(index, attempt) {
                    failed = false
                    try {
                        png = content.renderPage(index, 0).bytes
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { // one page that fails shows its own Try again
                        failed = true
                    }
                }
                KitPdfPngPage(
                    page = index + 1,
                    pageCount = content.pageCount,
                    png = png,
                    failed = failed,
                    modifier = Modifier.padding(bottom = Space.S3.dp), // F: lib/ui/kit/kit_viewer_bodies.dart:233
                    onRetry = { attempt++ },
                )
            }
        }
    }
}

/**
 * The full-height bottom sheet that holds a [KitViewer] on a compact or medium window (Dart `_KitViewerSheet` inside
 * `showKitViewer`'s modal): the scrim, then the sheet at [SHEET_FULL_HEIGHT] of the window, capped at
 * [SHEET_MAX_WIDTH], with its handle, the top corners rounded. The host shows it over the page; it does not animate
 * on its own (the host's modal motion does, through KitMotion).
 */
@Composable
public fun KitViewerSheet(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val dismissLabel = stringResource(Res.string.kitSheetDismiss)
    BoxWithConstraints(modifier.fillMaxSize().background(colors.scrim), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)
        Column(
            Modifier.width(minOf(maxWidth, SHEET_MAX_WIDTH.dp))
                .height(maxHeight * SHEET_FULL_HEIGHT)
                .clip(shape)
                .background(colors.surface2),
        ) {
            Box(
                Modifier.fillMaxWidth().height(tokens.handleBand.dp).semantics {
                    contentDescription = dismissLabel
                    if (onDismiss != null) dismiss { onDismiss(); true }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(tokens.handleWidth.dp, tokens.handleThickness.dp)
                        .clip(RoundedCornerShape((tokens.handleThickness / 2f).dp)) // F: lib/ui/kit/kit_viewer.dart:178-180
                        .background(colors.text3),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}
