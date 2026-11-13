package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Reader controls shared by text, code, Markdown, SVG and tables. The source stays exact for Copy;
 * Find is literal, case insensitive and moves the lazy listing to the active match. A formatted
 * body is supplied by the host's kit. All controls are visible, and a source switch keeps Find
 * available even for formatted documents.
 */
@Composable
public fun KitViewerDocument(
    source: String,
    modifier: Modifier = Modifier,
    code: Boolean = false,
    initialLine: Int? = null,
    formatted: (@Composable () -> Unit)? = null,
    lineContent: (@Composable (AnnotatedString, Modifier, Boolean) -> Unit)? = null,
) {
    val cut = remember(source) { ViewerText.cut(source) }
    val lines = remember(cut) { ViewerText.lines(cut.text) }
    var wrap by remember(source) { mutableStateOf(!code) }
    var showSource by remember(source) { mutableStateOf(false) }
    var finding by remember(source) { mutableStateOf(false) }
    var query by remember(source) { mutableStateOf("") }
    var active by remember(source, query) { mutableIntStateOf(0) }
    val matches =
        remember(lines, query) {
            if (query.isEmpty()) emptyList()
            else
                buildList {
                    lines.forEachIndexed { line, text ->
                        var from = 0
                        while (from <= text.length - query.length) {
                            val at = text.indexOf(query, from, ignoreCase = true)
                            if (at < 0) break
                            add(Triple(line, at, at + query.length))
                            from = at + query.length
                        }
                    }
                }
        }
    val list = rememberLazyListState()
    LaunchedEffect(source, initialLine) {
        val at = initialLine?.minus(1)?.takeIf { it in lines.indices }
        if (at != null) list.scrollToItem(maxOf(0, at - 3))
    }
    LaunchedEffect(matches, active, finding) {
        if (finding && matches.isNotEmpty())
            list.scrollToItem(maxOf(0, matches[active.coerceIn(matches.indices)].first - 3))
    }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val colors = LocalKitColors.current
    val style =
        LocalKitTypography.current
            .style(if (code || showSource) TypeRole.Mono else TypeRole.Body)
            .copy(color = colors.text1)
    val rendering = formatted != null && !showSource && !finding
    val chrome = LocalViewerChrome.current
    val copy = { clipboard.setText(AnnotatedString(source)) }
    val copyLabel = stringResource(Res.string.kitViewerCopyContents)
    val findLabel = stringResource(Res.string.kitViewerFind)
    val sourceLabel =
        stringResource(if (showSource) Res.string.kitDiffShowFormatted else Res.string.kitViewerShowSource)
    val wrapLabel = stringResource(Res.string.kitWrapLines)
    val tools =
        buildList {
            add(ViewerTool("copy", copyLabel, KitIcons.copy, null))
            add(ViewerTool("find", findLabel, KitIcons.search, null))
            if (formatted != null) add(ViewerTool("source", sourceLabel, KitIcons.code, null))
            if (!rendering) add(ViewerTool("wrap", wrapLabel, KitIcons.wrapText, wrap))
        }
    if (chrome != null) {
        // The page's bar and menu carry the tools; this body paints no row of its own.
        SideEffect {
            chrome.offer(
                tools,
                mapOf(
                    "copy" to copy,
                    "find" to { finding = !finding },
                    "source" to { showSource = !showSource },
                    "wrap" to { wrap = !wrap },
                ),
            )
        }
        DisposableEffect(chrome) { onDispose { chrome.clear() } }
    }
    val rail = kitPageGutter()
    Column(modifier.fillMaxSize()) {
        if (chrome == null) {
            // C05: the tools of a document with no page around it are text actions with their glyphs and a full step
            // between them, on the gutter.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = rail).testTag("kit-viewer-tools"),
                horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
            ) {
                tools.forEach { tool ->
                    KitButton(
                        tool.label,
                        {
                            when (tool.id) {
                                "copy" -> copy()
                                "find" -> finding = !finding
                                "source" -> showSource = !showSource
                                else -> wrap = !wrap
                            }
                        },
                        modifier = if (tool.id == "wrap") Modifier.semantics { selected = wrap } else Modifier,
                        role = KitButtonRole.Tertiary,
                        icon = tool.icon,
                        expand = false,
                    )
                }
            }
        }
        if (finding) {
            KitSearchField(
                stringResource(Res.string.kitViewerFind),
                { query = it },
                Modifier.padding(horizontal = rail),
                onSubmit = {
                    if (matches.isNotEmpty()) active = (active + 1) % matches.size
                },
            )
            FlowRow(
                Modifier.padding(horizontal = rail),
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                KitButton(
                    stringResource(Res.string.kitViewerFindPrevious),
                    {
                        if (matches.isNotEmpty())
                            active = (active + matches.size - 1) % matches.size
                    },
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.kitViewerFindNext),
                    { if (matches.isNotEmpty()) active = (active + 1) % matches.size },
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.kitViewerFindClose),
                    {
                        finding = false
                        query = ""
                    },
                    role = KitButtonRole.Tertiary,
                )
            }
            if (query.isNotEmpty())
                KitText(
                    if (matches.isEmpty()) stringResource(Res.string.kitViewerFindNone)
                    else
                        stringResource(
                            Res.string.kitViewerFindCount,
                            (active + 1).toString(),
                            matches.size.toString(),
                        ),
                    Modifier.padding(horizontal = rail).semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
        }
        if (cut.truncated)
            KitText(
                stringResource(Res.string.kitViewerPartial),
                Modifier.padding(horizontal = rail),
            )
        if (rendering) {
            Box(Modifier.weight(1f)) { formatted?.invoke() }
        } else if (lines.isEmpty()) {
            KitText(stringResource(Res.string.kitViewerEmpty), Modifier.padding(horizontal = rail, vertical = Space.S3.dp))
        } else
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize()
                        .then(
                            if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
                        ),
                    state = list,
                    contentPadding = PaddingValues(horizontal = rail, vertical = Space.S3.dp),
                ) {
                    itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                        val annotated = buildAnnotatedString {
                            append(line.ifEmpty { " " })
                            matches.forEachIndexed { n, match ->
                                if (match.first == i)
                                    addStyle(
                                        SpanStyle(
                                            background =
                                                colors.accent.copy(
                                                    alpha = if (n == active) .4f else .18f
                                                )
                                        ),
                                        match.second,
                                        match.third,
                                    )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (initialLine == i + 1 && !finding)
                                        Modifier.background(colors.accent.copy(alpha = .18f))
                                    else Modifier
                                )
                        ) {
                            if (code || showSource)
                                androidx.compose.foundation.text.selection.DisableSelection {
                                    KitText(
                                        (i + 1).toString().padStart(lines.size.toString().length),
                                        Modifier.padding(end = Space.S3.dp),
                                        role = TypeRole.Mono,
                                    )
                                }
                            val textModifier = if (wrap) Modifier.weight(1f) else Modifier
                            if (lineContent != null) lineContent(annotated, textModifier, wrap)
                            else BasicText(annotated, textModifier, style = style, softWrap = wrap)
                        }
                    }
                }
            }
    }
}

// --- The Flutter viewer's state and text rules (lib/ui/kit/kit_viewer.dart) ------------------------------------------

/** Text sources show at most this many lines (Dart `KitViewer.maxLines`); beyond it the notice says how many. */
internal const val VIEWER_MAX_LINES: Int = 2000 // F: lib/ui/kit/kit_viewer.dart:302

/**
 * Text prepared once per content (Dart `_Prepared`): capped at [VIEWER_MAX_LINES], redacted for Find, with the counts
 * for the truncation notice and the longest line for the sideways width.
 */
internal class ViewerPrepared(text: String) {
    val totalLines: Int
    val capped: Boolean
    val shown: String
    val shownLines: Int
    val redacted: String
    val longest: String

    init {
        val lines = text.split('\n').toMutableList()
        if (text.endsWith('\n')) lines.removeAt(lines.size - 1)
        totalLines = lines.size
        capped = totalLines > VIEWER_MAX_LINES
        shown = if (capped) lines.take(VIEWER_MAX_LINES).joinToString("\n") else text
        shownLines = minOf(totalLines, VIEWER_MAX_LINES)
        redacted = KitRedact.text(shown)
        longest = shown.split('\n').maxByOrNull { it.length }.orEmpty()
    }
}

/** Find's pure rules (Dart `_onQuery`, `_step`) and the notice's number words. */
internal object ViewerFind {
    /** Every literal, case-insensitive match of [query] in [haystack]; empty matches are skipped. */
    fun marks(haystack: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        return pattern.findAll(haystack).filter { !it.range.isEmpty() }.map { it.range }.toList()
    }

    /** The next active mark, wrapping both ways. */
    fun step(active: Int, delta: Int, count: Int): Int {
        if (count == 0) return active
        val next = (active + delta) % count
        return if (next < 0) next + count else next
    }

    /** "5,210": the count with its thousands grouped, as the Dart message's number format writes it. */
    fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed() // NV: thousands grouping of intl NumberFormat
}

/** What the body draws for the current content and Show source (Dart `_sourceView`, `_codeText`, `_isEmpty`). */
internal class ViewerView(content: KitViewerContent?, sourceOn: Boolean) {
    val sourceView: Boolean =
        when (content) {
            is KitViewerContent.Markdown -> sourceOn
            is KitViewerContent.Svg -> sourceOn && content.original != null
            is KitViewerContent.Delimited -> sourceOn && content.original != null
            else -> false
        }

    val sourceApplies: Boolean =
        when (content) {
            is KitViewerContent.Markdown -> true
            is KitViewerContent.Svg -> content.original != null
            is KitViewerContent.Delimited -> content.original != null
            else -> false
        }

    /** The text the body draws in a code block, or null when the body is not a code block. */
    val codeText: String? =
        when (content) {
            is KitViewerContent.Text -> content.text
            is KitViewerContent.Code -> content.text
            is KitViewerContent.Markdown -> if (sourceView) content.source else null
            is KitViewerContent.Svg -> if (sourceView) content.original else null
            is KitViewerContent.Delimited -> if (sourceView) content.original else null
            else -> null
        }

    val empty: Boolean =
        when (content) {
            is KitViewerContent.Text -> content.text.isBlank()
            is KitViewerContent.Code -> content.text.isBlank()
            is KitViewerContent.Markdown -> content.source.isBlank()
            is KitViewerContent.Svg -> content.safeSource.isBlank()
            is KitViewerContent.Delimited -> content.rows.isEmpty()
            else -> false
        }
}

/** Dart `content._truncated`. */
internal fun KitViewerContent.truncatedFlag(): Boolean =
    when (this) {
        is KitViewerContent.Text -> truncated
        is KitViewerContent.Code -> truncated
        is KitViewerContent.Markdown -> truncated
        is KitViewerContent.Svg -> truncated
        is KitViewerContent.Delimited -> truncated
        else -> false
    }

/** Dart `content._totalLines`. */
internal fun KitViewerContent.knownTotal(): Int? =
    when (this) {
        is KitViewerContent.Text -> totalLines
        is KitViewerContent.Code -> totalLines
        else -> null
    }

/** Loading a [KitViewerSource] (Dart `_resolve`, `_retry`): content in hand at once, else loading then content or error. */
internal class ViewerState(private val source: KitViewerSource, private val loader: (suspend () -> KitViewerContent)?) {
    var content: KitViewerContent? by mutableStateOf(source.content)
        private set
    var error: Throwable? by mutableStateOf(null)
        private set
    var loading: Boolean by mutableStateOf(source.content == null)
        private set
    var generation: Int by mutableIntStateOf(0)
        private set

    fun retry() {
        if (source.content != null) return
        error = null
        content = null
        loading = true
        generation++
    }

    suspend fun load() {
        if (source.content != null) return
        val run = loader ?: return
        try {
            content = run()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { // the caller's failure is shown as "Couldn't open", never its text
            error = e
        }
        loading = false
    }
}

/** The image body (Dart `KitZoom` over a centred `KitImage`). */
@Composable
internal fun ViewerImage(content: KitViewerContent.Image, name: String) {
    io.github.eslamasabry.oc.ui.kit.content.image.KitZoomView(name, Modifier.fillMaxSize()) {
        // Centre loosens the window: the picture keeps its own size (1 px = 1 dp, as Flutter's Image), never larger
        // than the room, and its aspect ratio.
        val size = remember(content.bytes) { pictureSizeOf(content.bytes) }
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            io.github.eslamasabry.oc.ui.kit.content.image.KitImage(
                io.github.eslamasabry.oc.ui.kit.content.image.KitImageSource.Memory(content.bytes),
                content.semanticsLabel ?: name,
                if (size == null) Modifier
                else Modifier.sizeIn(maxWidth = size.first.dp, maxHeight = size.second.dp).aspectRatio(size.first.toFloat() / size.second),
            )
        }
    }
}

/** The pixel size of an encoded picture, or null when it does not decode. */
internal fun pictureSizeOf(bytes: ByteArray): Pair<Int, Int>? =
    try {
        io.github.eslamasabry.oc.ui.kit.image.KitImages.sizeOf(bytes)
    } catch (_: RuntimeException) { // an undecodable picture keeps the image part's own failure state
        null
    }
