package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.layout.wrapContentHeight
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.kitMore
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShow
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShowBody
import io.github.eslamasabry.oc.ui.words.res.kitViewerCopyContents
import io.github.eslamasabry.oc.ui.words.res.kitViewerEmpty
import io.github.eslamasabry.oc.ui.words.res.kitViewerFind
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindCount
import io.github.eslamasabry.oc.ui.words.res.kitViewerFindNone
import io.github.eslamasabry.oc.ui.words.res.kitViewerLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitViewerOpenAll
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import io.github.eslamasabry.oc.ui.words.res.kitViewerShowSource
import io.github.eslamasabry.oc.ui.words.res.kitViewerTruncated
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownSize
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownType
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines

/** A labelled action the host puts in the viewer's bar. [tag] only where a test needs a handle. */
@Immutable
public data class KitViewerAction(val label: String, val onClick: () -> Unit, val tag: String? = null)

/** Test tags of the frame. */
public object KitViewerTags {
    public const val NAME: String = "kit-viewer-name"
    public const val FOLDER: String = "kit-viewer-folder"
    public const val CLOSE: String = "kit-viewer-close"
    public const val BODY: String = "kit-viewer-body"
}

/** The viewer's widest frame (plan 06 viewer cap; prose inside keeps its own 720 dp rails). */
private const val VIEWER_MAX_WIDTH_DP = 960

/** One row of the viewer's "More actions" menu. [checked] non-null makes it a toggle drawn with its state. */
@Immutable
public data class KitViewerMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
)

/**
 * The file viewer as a page of the app (plan 10 §11.3, MEDIA-VOICE-37): the same page frame as every other page. The
 * top bar carries Back (it calls [onClose]), the file name as the title and the muted parent folder under it
 * ([ViewerPaths.folderOf], B-NAV-028); Find, when the body is a text document, is the bar's icon action; the bar's
 * "More actions" menu holds, in this order, the document's own tools (Copy contents, Show source, Wrap lines with its
 * state), then the host's [menu] rows, then [secondary]. The body fills the page; at the bottom, on the page margin, sits
 * the one filled [primary] button (full width up to the kit's button cap), and the host's [footer] (notices) above it.
 * Nothing shows for what the host does not pass: no menu without rows, no button without [primary]. [onClose] null is an
 * embedded pane: no Back.
 *
 * Paths and names are isolated left to right inside an Arabic interface (a path never reorders).
 *
 * States: content (the body decides loading, error, empty and its own states)
 * Text: frame (name, folder, button); the body is reading text
 * Cap: Viewer (960 dp)
 * Motion: none of its own
 * Hidden: none (every tool is in the bar or its labelled menu)
 * Replaces: KitViewer frame (lib/ui/kit/kit_viewer.dart)
 */
@Composable
public fun KitViewerFrame(
    name: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    primary: KitViewerAction? = null,
    secondary: KitViewerAction? = null,
    onClose: (() -> Unit)? = null,
    menu: List<KitViewerMenuItem> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) {
    val folder = path?.let { ViewerPaths.folderOf(it, name) }
    val chrome = remember { ViewerChrome() }
    var open by remember { mutableStateOf(false) }
    val moreLabel = stringResource(Res.string.kitTopBarMore)
    val backLabel = stringResource(Res.string.kitTopBarBack)
    val find = chrome.tools.firstOrNull { it.id == "find" }
    val items =
        buildList {
            chrome.tools.filter { it.id != "find" }.forEach { tool ->
                add(
                    KitMenuItem(
                        tool.label,
                        { chrome.run(tool.id) },
                        icon = tool.icon,
                        checked = tool.checked,
                    ),
                )
            }
            menu.forEach { row ->
                add(
                    KitMenuItem(
                        row.label,
                        row.onClick,
                        icon = row.icon,
                        checked = row.checked,
                        enabled = if (row.enabled) KitEnabled.Enabled else KitEnabled.Disabled(row.label),
                    ),
                )
            }
            if (secondary != null) add(KitMenuItem(secondary.label, secondary.onClick))
        }
    CompositionLocalProvider(LocalViewerChrome provides chrome) {
        KitScreen(
            modifier = modifier.widthIn(max = VIEWER_MAX_WIDTH_DP.dp),
            page = true,
            edges = KitScreenEdges.Bleed,
            topBar = {
                KitTopBar(
                    title = isolateLtr(name),
                    subtitle = folder?.let { isolateLtr(it) },
                    exit = if (onClose != null) KitTopBarExit.Back else KitTopBarExit.None,
                    exitLabel = backLabel,
                    onExit = onClose,
                    actions =
                        listOfNotNull(
                            find?.let { tool ->
                                KitLineAction(tool.label, { chrome.run("find") }, icon = tool.icon, testTag = KitViewerKeys.FIND)
                            },
                        ),
                    more = {
                        if (items.isNotEmpty()) {
                            KitMenu(
                                expanded = open,
                                items = items,
                                onDismissRequest = { open = false },
                                title = moreLabel,
                            ) {
                                KitIconButton(KitIcons.more, moreLabel, { open = true })
                            }
                        }
                    },
                )
            },
            bottom =
                if (primary != null || footer != null) {
                    {
                        Column {
                            footer?.invoke()
                            if (primary != null) {
                                // The screen's bottom block already sits on the page margin: no second gutter here.
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    KitButton(primary.label, primary.onClick, Modifier.tagged(primary.tag), fullWidth = true)
                                }
                            }
                        }
                    }
                } else null,
        ) {
            Box(Modifier.fillMaxSize().testTag(KitViewerTags.BODY), content = body)
        }
    }
}

internal fun Modifier.tagged(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/** U+2066 LEFT-TO-RIGHT ISOLATE … U+2069 POP DIRECTIONAL ISOLATE: a path keeps its order in a right-to-left line. */
internal fun isolateLtr(text: String): String = "⁦$text⁩"

// --- KitViewer: the Flutter frame (lib/ui/kit/kit_viewer.dart, kit_viewer_build.dart) ------------------------------

/** The most of the viewer's height the frame's top (header and the truncation notice) takes before it scrolls. */
private const val TOP_SHARE = 0.5f // F: lib/ui/kit/kit_viewer.dart:334

/** A full-height viewer sheet's share of the window's height (Dart `KitLayout.sheetFullHeight`). */
private const val SHEET_FULL_HEIGHT = .95f // F: lib/ui/kit/kit_layout.dart:161

/** The kit's own menu group: Find, Copy contents, Wrap lines, Show source (Dart `#kitViewer`). */
private val KitViewerMenuGroup = KitMenuGroup("kitViewer")

/** Test handles of the Flutter-shaped viewer (the Dart `ValueKey`s). */
public object KitViewerKeys {
    public const val VIEWER: String = "kit-viewer"
    public const val MORE: String = "kit-viewer-more"
    public const val CLOSE: String = "kit-viewer-close"
    public const val PRIMARY: String = "kit-viewer-primary"
    public const val FIND: String = "kit-viewer-find"
    public const val FIND_NEXT: String = "kit-viewer-find-next"
    public const val FIND_PREVIOUS: String = "kit-viewer-find-previous"
    public const val FIND_CLOSE: String = "kit-viewer-find-close"
    public const val FIND_COUNT: String = "kit-viewer-find-count"
    public const val OPEN_ALL: String = "kit-viewer-open-all"
    public const val EMPTY: String = "kit-viewer-empty"
}

/**
 * Opens the viewer (Dart `showKitViewer`, KIT-11) while [visible]: a full-height bottom sheet (the handle, then the
 * viewer) on a compact window, the same sheet capped at 640 dp on a medium one, and a page (Close at the end) from
 * expanded up. [asPage] null picks by window. Close, the scrim and the sheet's dismiss all call [onDismissRequest].
 *
 * States: closed, sheet, page
 * Text: the viewer's
 * Cap: Sheet (640) / Fill
 * Motion: the sheet rises and the scrim fades on standard; reduced motion = instant
 * Hidden: none
 * Replaces: showKitViewer (lib/ui/kit/kit_viewer.dart)
 */
@Composable
public fun KitViewerModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    name: String,
    source: KitViewerSource,
    path: String? = null,
    primary: KitAction? = null,
    more: List<KitMenuItem> = emptyList(),
    asPage: Boolean? = null,
    interactive: Boolean = true,
    onOpenAll: (() -> Unit)? = null,
    wrap: Boolean? = null,
    onWrapChanged: ((Boolean) -> Unit)? = null,
    showSource: Boolean? = null,
    onShowSourceChanged: ((Boolean) -> Unit)? = null,
    reader: Boolean = false,
    markdown: (@Composable (String) -> Unit)? = null,
    load: (suspend () -> KitViewerContent)? = null,
) {
    val window = LocalKitWindow.current
    val page = asPage ?: window.cls.isWide
    val viewer: @Composable (Modifier) -> Unit = { m ->
        KitViewer(
            name = name,
            source = source,
            modifier = m,
            path = path,
            primary = primary,
            more = more,
            onClose = onDismissRequest,
            interactive = interactive,
            onOpenAll = onOpenAll,
            wrap = wrap,
            onWrapChanged = onWrapChanged,
            showSource = showSource,
            onShowSourceChanged = onShowSourceChanged,
            reader = reader,
            markdown = markdown,
            namesRoute = true,
            load = load,
        )
    }
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    val colors = LocalKitColors.current
    if (page) {
        if (visible) viewer(Modifier.fillMaxSize().background(colors.ground))
        return
    }
    AnimatedVisibility(
        visible,
        enter = if (reduced) fadeIn(snapIn()) else fadeIn(specs.enter()),
        exit = if (reduced) fadeOut(snapIn()) else fadeOut(specs.exit()),
    ) {
        Box(Modifier.fillMaxSize().background(colors.scrim).pointerInput(Unit) { detectTapGestures { onDismissRequest() } })
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val height = maxHeight * SHEET_FULL_HEIGHT
        AnimatedVisibility(
            visible,
            enter = if (reduced) fadeIn(snapIn()) else slideInVertically(specs.enter<IntOffset>()) { it },
            exit = if (reduced) fadeOut(snapIn()) else slideOutVertically(specs.exit<IntOffset>()) { it },
        ) {
            KitViewerSheetPanel(
                name = name,
                onDismissRequest = onDismissRequest,
                modifier = Modifier.widthIn(max = io.github.eslamasabry.oc.ui.design.layout.Layout.SHEET_MAX_WIDTH.dp).fillMaxWidth().height(height),
            ) { viewer(Modifier.fillMaxSize()) }
        }
    }
}

private fun <T> snapIn(): androidx.compose.animation.core.FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()

/** The full-height sheet body (Dart `_KitViewerSheet`): the handle, then the viewer, on the sheet's surface. */
@Composable
private fun KitViewerSheetPanel(
    name: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Sheet, KitShapeId.Sheet, SurfaceLevel.Surface2, ownFill = colors.surface2))
    val dismiss = stringResource(Res.string.kitSheetDismiss)
    Column(
        modifier
            .then(look.modifier)
            .clip(look.shape)
            .pointerInput(Unit) { detectTapGestures {} } // taps on the sheet never reach the scrim
            .semantics { paneTitle = name },
    ) {
        Box(
            Modifier.fillMaxWidth().height(tokens.handleBand.dp).semantics {
                contentDescription = dismiss
                dismiss {
                    onDismissRequest()
                    true
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(tokens.handleWidth.dp, tokens.handleThickness.dp)
                    .clip(KitShapeId.Pill.shape())
                    .background(colors.text3),
            )
        }
        content()
    }
}

/**
 * The one viewer for a file, a code block opened in full, or a document (Dart `KitViewer`): the same frame always (the
 * name, the muted parent folder, one labelled [primary], More, Close) and one of the kit's renderers as the body: text,
 * code, Markdown, image, PDF, SVG, a delimited table, or "Can't show this file". Parsing, sanitising, file reading and
 * PDF rendering stay with the caller ([source]; a source made with `KitViewerSource.load` passes the same function as
 * [load], since the source keeps it private). [markdown] draws a Markdown document as prose (the host passes the
 * chat kit's renderer); without it the document shows as its source.
 *
 * States: loading, error, empty, loaded, truncated, binary, page-failed, finding
 * Text: the name and folder (reading), the body's own
 * Cap: Fill (the code block inside holds the list cap)
 * Motion: the find bar reveals; none else of its own
 * Hidden: none (More is the labelled path to Find, Copy, Wrap and Show source)
 * Replaces: KitViewer (lib/ui/kit/kit_viewer.dart, kit_viewer_build.dart)
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
    interactive: Boolean = true,
    onOpenAll: (() -> Unit)? = null,
    wrap: Boolean? = null,
    onWrapChanged: ((Boolean) -> Unit)? = null,
    showSource: Boolean? = null,
    onShowSourceChanged: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = true,
    reader: Boolean = false,
    markdown: (@Composable (String) -> Unit)? = null,
    namesRoute: Boolean = false,
    load: (suspend () -> KitViewerContent)? = null,
) {
    val state = remember(source) { ViewerState(source, load) }
    LaunchedEffect(state, state.generation) { state.load() }
    var wrapOwn by remember(wrap) { mutableStateOf<Boolean?>(null) }
    var sourceOwn by remember(showSource) { mutableStateOf<Boolean?>(null) }
    var findOpen by remember(source) { mutableStateOf(false) }
    var query by remember(source) { mutableStateOf("") }
    var active by remember(source) { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val window = LocalKitWindow.current.cls
    val content = state.content
    val view = ViewerView(content, sourceOwn ?: showSource ?: false)
    val effectiveWrap =
        wrapOwn ?: wrap ?: if (reader && window == WindowClass.Compact) true else kitViewerDefaultWrap(window, content?.kind)
    val prepared = view.codeText?.let { remember(it) { ViewerPrepared(it) } }
    val marks = remember(prepared, query) { if (prepared == null || query.isEmpty()) emptyList() else ViewerFind.marks(prepared.redacted, query) }
    val findable = view.codeText != null && !view.empty
    val toggleWrap: () -> Unit = {
        val next = !effectiveWrap
        wrapOwn = next
        onWrapChanged?.invoke(next)
    }
    val closeFind = {
        findOpen = false
        query = ""
        active = 0
    }
    val step = { delta: Int -> if (marks.isNotEmpty()) active = ViewerFind.step(active, delta, marks.size) }
    val items = buildList {
        if (findable) add(KitMenuItem(stringResource(Res.string.kitViewerFind), { findOpen = true }, KitIcons.search, KitViewerMenuGroup))
        val copyText = content?.copyText
        if (copyText != null) {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            add(
                KitMenuItem(
                    stringResource(Res.string.kitViewerCopyContents),
                    { clipboard.setText(androidx.compose.ui.text.AnnotatedString(copyText)) }, // SEC-13: verbatim
                    KitIcons.copy,
                    KitViewerMenuGroup,
                    enabled = KitEnabled.of(!view.empty, stringResource(Res.string.kitViewerEmpty)),
                ),
            )
        }
        if (view.codeText != null && !view.empty && !reader)
            add(KitMenuItem(stringResource(Res.string.kitWrapLines), toggleWrap, KitIcons.wrapText, KitViewerMenuGroup, checked = effectiveWrap))
        if (view.sourceApplies)
            add(
                KitMenuItem(stringResource(Res.string.kitViewerShowSource), {
                    val next = !view.sourceView
                    if (findOpen) closeFind()
                    sourceOwn = next
                    onShowSourceChanged?.invoke(next)
                }, KitIcons.code, KitViewerMenuGroup, checked = view.sourceView),
            )
        addAll(more)
    }
    val notice = truncationNotice(content, view, prepared)
    Column(modifier.semantics { if (namesRoute) paneTitle = name }) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val topMax = maxHeight * TOP_SHARE
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().heightIn(max = topMax).verticalScroll(rememberScrollState())) {
                    if (showHeader) {
                        ViewerHeader(
                            name = name,
                            path = path,
                            primary = if (content?.kind == KitViewerKind.Binary || reader) null else primary,
                            compact = window == WindowClass.Compact,
                            onClose = onClose,
                            wrapToggle = if (reader && findable) effectiveWrap else null,
                            onToggleWrap = toggleWrap,
                            more = {
                                KitMenu(
                                    expanded = menuOpen,
                                    items = items,
                                    onDismissRequest = { menuOpen = false },
                                    title = name,
                                ) {
                                    KitIconButton(
                                        KitIcons.more,
                                        stringResource(Res.string.kitMore),
                                        { menuOpen = true },
                                        Modifier.testTag(KitViewerKeys.MORE),
                                    )
                                }
                            },
                        )
                        // A hairline is a third of a pixel: its box lays out at no height, so at the very end of the
                        // scrolling top the scroll's clip would hide it. With no notice under it, it is drawn just
                        // below the scrolling top instead (the same place).
                        if (notice != null) KitDivider()
                    }
                    if (notice != null) {
                        val tokens = LocalKitTokens.current
                        KitNotice(
                            message = notice,
                            modifier = Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                            liveRegion = false,
                            actions = listOfNotNull(
                                onOpenAll?.let { KitLineAction(stringResource(Res.string.kitViewerOpenAll), it, testTag = KitViewerKeys.OPEN_ALL) },
                            ),
                        )
                    }
                }
                if (showHeader && notice == null) {
                    // No height of its own, as Flutter's third-of-a-pixel line: it draws over the body's top edge.
                    Box(Modifier.fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Top, unbounded = true)) { KitDivider() }
                }
                // Find stays outside the scrolling top: it is short, and its count must stay in view while typing.
                KitReveal(findOpen) {
                    ViewerFindBar(
                        query = query,
                        count = if (marks.isEmpty()) stringResource(Res.string.kitViewerFindNone)
                        else stringResource(Res.string.kitViewerFindCount, (active + 1).toString(), marks.size.toString()),
                        hasMarks = marks.isNotEmpty(),
                        onQuery = {
                            query = it
                            active = 0
                        },
                        onStep = step,
                        onClose = closeFind,
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    ViewerBody(
                        name = name,
                        state = state,
                        view = view,
                        prepared = prepared,
                        primary = primary,
                        wrap = effectiveWrap,
                        marks = if (findOpen) marks else emptyList(),
                        active = if (findOpen && marks.isNotEmpty()) active else null,
                        reader = reader,
                        interactive = interactive,
                        markdown = markdown,
                    )
                }
            }
        }
    }
}

/** The header (Dart `_header`): the name, the parent folder, More and Close; the primary on its own row under them. */
@Composable
private fun ViewerHeader(
    name: String,
    path: String?,
    primary: KitAction?,
    compact: Boolean,
    onClose: (() -> Unit)?,
    wrapToggle: Boolean?,
    onToggleWrap: () -> Unit,
    more: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val folder = path?.let { ViewerPaths.folderOf(it, name) }
    // The primary's own row follows the name directly: no bottom inset.
    val topBottom = if (primary == null) Space.S2 else 0
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = tokens.gutter.dp,
                top = (if (onClose == null) Space.S3 else Space.S1).dp,
                end = Space.S2.dp,
                bottom = topBottom.dp,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = Space.S2.dp, end = Space.S2.dp)) {
                KitText(name, Modifier.testTag(KitViewerTags.NAME).semantics { heading() }, role = TypeRole.Headline)
                if (folder != null) {
                    Spacer(Modifier.height((Space.S1 / 2f).dp)) // F: lib/ui/kit/kit_viewer_build.dart:30
                    Box(Modifier.semantics(mergeDescendants = true) { contentDescription = path }) {
                        KitMonoText(folder, Modifier.testTag(KitViewerTags.FOLDER), tone = TextTone.Secondary, cut = KitMonoCut.Middle)
                    }
                }
            }
            if (wrapToggle != null) {
                KitIconButton(KitIcons.wrapText, stringResource(Res.string.kitWrapLines), onToggleWrap, selected = wrapToggle)
            }
            more()
            if (onClose != null) {
                KitIconButton(KitIcons.close, stringResource(Res.string.kitSheetClose), onClose, Modifier.testTag(KitViewerKeys.CLOSE))
            }
        }
        if (primary != null) {
            // The one labelled action has its own full-width row under the name, starting where the name starts.
            val reason = (primary.enabled as? KitEnabled.Disabled)?.reason
            Column(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)) {
                // The tertiary button's words line up with the name (Dart shifts it back by `KitButton.tertiaryInset`;
                // the native tertiary button already reaches its padding into the gutter, so its words sit there).
                KitButton(
                    primary,
                    Modifier.testTag(KitViewerKeys.PRIMARY),
                    role = if (compact) KitButtonRole.Tertiary else KitButtonRole.Secondary,
                )
                if (reason != null) KitText(reason, role = TypeRole.Secondary)
            }
        }
    }
}

/** The notice above a partly shown file (Dart `_truncationNotice`), or null. */
@Composable
private fun truncationNotice(content: KitViewerContent?, view: ViewerView, prepared: ViewerPrepared?): String? {
    if (content == null || view.empty) return null
    val code = view.codeText
    if (code != null && prepared != null && (content.kind == KitViewerKind.Text || content.kind == KitViewerKind.Code || view.sourceView)) {
        if (!content.truncatedFlag() && !prepared.capped) return null
        val known = content.knownTotal()
        val total = if (prepared.capped) maxOf(prepared.totalLines, known ?: 0) else known
        return if (total != null && total > prepared.shownLines)
            stringResource(Res.string.kitViewerTruncated, ViewerFind.grouped(prepared.shownLines), ViewerFind.grouped(total))
        else stringResource(Res.string.kitViewerPartial)
    }
    return if (content.truncatedFlag()) stringResource(Res.string.kitViewerPartial) else null
}

/** The body (Dart `_body`): loading, error, empty, the code block, or the kind's own renderer. */
@Composable
private fun ViewerBody(
    name: String,
    state: ViewerState,
    view: ViewerView,
    prepared: ViewerPrepared?,
    primary: KitAction?,
    wrap: Boolean,
    marks: List<IntRange>,
    active: Int?,
    reader: Boolean,
    interactive: Boolean,
    markdown: (@Composable (String) -> Unit)?,
) {
    val tokens = LocalKitTokens.current
    if (state.loading) {
        Box(Modifier.fillMaxSize().padding(horizontal = tokens.gutter.dp)) { KitSkeletonRows(Modifier.fillMaxWidth().kitMaxWidth(io.github.eslamasabry.oc.ui.design.layout.CapRole.List), count = 6) } // F: lib/ui/kit/kit_viewer_build.dart:343
        return
    }
    if (state.error != null) {
        ViewerCentred {
            KitStateView(
                KitStateKind.Error,
                stringResource(Res.string.kitViewerLoadFailed, name),
                icon = KitIcons.error,
                primary = KitAction(stringResource(Res.string.kitTryAgain), onClick = state::retry),
                size = KitStateSize.Inline, // F: lib/ui/kit/kit_viewer_build.dart:354
                // Folded Details (Dart passes the redacted error): the failure's shape only, never exception text.
                details = Redacted.shape(state.error), // F: lib/ui/kit/kit_viewer_build.dart:355
            )
        }
        return
    }
    val content = state.content ?: return
    if (view.empty) {
        KitText(
            stringResource(Res.string.kitViewerEmpty),
            Modifier.padding(tokens.gutter.dp).testTag(KitViewerKeys.EMPTY),
            tone = TextTone.Tertiary,
        )
        return
    }
    if (prepared != null) {
        ViewerCodeBody(
            prepared = prepared,
            language = when (content) {
                is KitViewerContent.Code -> content.language
                is KitViewerContent.Markdown -> "markdown"
                is KitViewerContent.Svg -> "xml"
                else -> null
            },
            lineNumbers = content.kind != KitViewerKind.Text,
            highlight = content.kind != KitViewerKind.Text,
            initialLine = (content as? KitViewerContent.Code)?.initialLine,
            wrap = wrap,
            marks = marks,
            activeMark = active,
            hangingIndent = reader,
        )
        return
    }
    when (content) {
        is KitViewerContent.Markdown -> ViewerProse(content.source, interactive, markdown)
        is KitViewerContent.Image -> ViewerImage(content, name)
        is KitViewerContent.Pdf -> ViewerPdfBody(content, name)
        is KitViewerContent.Svg -> ViewerSvg(content.safeSource, name)
        is KitViewerContent.Delimited -> ViewerTable(content.rows, content.header)
        is KitViewerContent.Binary ->
            ViewerCentred {
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.kitViewerCantShow),
                    icon = KitIcons.file,
                    body = stringResource(
                        Res.string.kitViewerCantShowBody,
                        content.mimeType ?: stringResource(Res.string.kitViewerUnknownType),
                        ViewerText.sizeInWords(content.byteLength) ?: stringResource(Res.string.kitViewerUnknownSize),
                    ),
                    primary = primary,
                    size = KitStateSize.Inline, // F: lib/ui/kit/kit_viewer_build.dart:444
                )
            }
        else -> Unit
    }
}

/** The inline state view's place (Dart `_centred`): start-aligned, scrolling, a gutter above and below. */
@Composable
private fun ViewerCentred(content: @Composable () -> Unit) {
    val tokens = LocalKitTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = tokens.gutter.dp)) { content() }
    }
}
