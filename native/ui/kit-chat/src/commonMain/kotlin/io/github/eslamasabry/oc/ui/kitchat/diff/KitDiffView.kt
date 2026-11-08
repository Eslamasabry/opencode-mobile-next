package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

/** `codeAddedSurface` / `codeRemovedSurface` (design Roles.kt:40-41): success and text2 at 10 %. */
private const val TINT_ALPHA = .10f // F: lib/ui/theme_roles.dart codeAddedSurface

/** Unwrapped rows are at most this many characters wide; longer lines are cut at the edge. */
private const val MAX_CHARS = 4000

/** The line list's height when the view sits in an unbounded column (a scrolling page). */
private val UNBOUNDED_LINES_MAX = 560.dp // NV: Flutter shrink-wraps; a lazy list needs a bound

private data class SelectedLines(val old: Boolean, val anchor: Int, val end: Int) {
    fun contains(oldSide: Boolean, n: Int?): Boolean =
        old == oldSide && n != null && n in minOf(anchor, end)..maxOf(anchor, end)
}

/**
 * The gutter and text geometry of one file (Dart `_Geometry`, kit_diff_chrome.dart:774-822): a number cell is the
 * widest number plus 1.5 × space2, the sign cell the mono size plus space1; [textWidth] is set when lines do not wrap.
 */
private class Geometry(val number: Dp, val glyph: Dp, val wrap: Boolean, val split: Boolean, val textWidth: Dp?) {
    /** Where a line's words start: the gutter (two number cells unified, one split) and the sign. */
    val indent: Dp get() = (if (split) number else number * 2) + glyph
}

/**
 * Where a [KitDiffView]'s file sits among the files it is shown with (Dart `KitDiffView.files` / `initialFile`): the
 * header names "1 of 2" and counts changes across every file ("Change 1 of 5"). [onPick] opens the host's file picker
 * (the header becomes the switcher); null keeps the header plain.
 */
public data class KitDiffPosition(
    val fileIndex: Int,
    val fileCount: Int,
    val changeOffset: Int = 0,
    val changeTotal: Int? = null,
    val onPick: (() -> Unit)? = null,
)

/**
 * A real diff listing in chat, Git and review (Dart `KitDiffView`, lib/ui/kit/kit_diff_view.dart and its parts
 * diff/kit_diff_chrome.dart, diff/kit_diff_rows.dart). Inline previews ([maxLines] ≤ 12) are the lines alone on the
 * page. A full view draws the file header on the ground with a hairline under it (the name in the row-title role, "· 1
 * of 2 ›" when [position] has more files, the folder and status under it, the +/− counts at the end), the change bar
 * (previous and next change, "Change 1 of 5", the wrap toggle), then the lines in the code frame (`detailsSurface`):
 * two number cells, the sign, the words; added rows in `codeAddedSurface`, removed in `codeRemovedSurface`; unchanged
 * runs fold into `surface2` bars that open 20 lines a tap. A file with one change and no switcher has the one-line
 * header with the wrap toggle in it. Lines wrap on a compact window and scroll sideways from medium ([wrap] overrides);
 * a wide window shows the two sides ([split] overrides). Selecting from the gutter shows the selection bar (count,
 * Comment, Add to prompt, Copy lines, Clear). [loading], [error] and an empty file are the view's own states. Copies
 * use redacted source lines, never signs or line numbers.
 */
@Composable
public fun KitDiffView(
    file: DiffFile,
    maxLines: Int = 12,
    hunkLabel: (oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int) -> String,
    gapLabel: (hidden: Int) -> String,
    modifier: Modifier = Modifier,
    onComment: ((String) -> Unit)? = null,
    onAddToPrompt: ((String) -> Unit)? = null,
    position: KitDiffPosition? = null,
    wrap: Boolean? = null,
    split: Boolean? = null,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onOpenAll: (() -> Unit)? = null,
) {
    val full = maxLines > 12
    if (loading) return DiffLoading(modifier)
    if (error != null) {
        KitStateView(
            KitStateKind.Error,
            stringResource(Res.string.kitDiffLoadFailed),
            modifier,
            body = error,
            onRetry = onRetry,
            retryLabel = onRetry?.let { stringResource(Res.string.kitTryAgain) },
            size = KitStateSize.Inline,
        )
        return
    }
    if (full && !file.binary && file.segments.isEmpty()) {
        KitStateView(
            KitStateKind.Empty,
            stringResource(Res.string.kitDiffNoChanges),
            modifier,
            icon = KitIcons.checks,
            size = KitStateSize.Inline,
        )
        return
    }
    val displayRows = remember(file, maxLines) { DiffModel.displayRows(file, maxLines) }
    val base = displayRows.rows
    val window = LocalKitWindow.current
    var userWrap by remember(file) { mutableStateOf<Boolean?>(null) }
    val wrapOn = wrap ?: userWrap ?: (window.cls == WindowClass.Compact) // F: lib/ui/kit/kit_diff_view.dart:330-331
    val splitOn = full && (split ?: window.cls.isWide) // F: lib/ui/kit/kit_diff_view.dart:766-768
    var source by remember(file) { mutableStateOf(false) }
    var selection by remember(file) { mutableStateOf<SelectedLines?>(null) }
    var expanded by remember(file) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var change by remember(file) { mutableIntStateOf(0) }
    val markdown = file.path.substringAfterLast('.').lowercase() in setOf("md", "markdown", "mdx")
    val formatted = markdown && !source
    val rows =
        remember(base, expanded) {
            buildList {
                for (row in base) {
                    if (row is DiffRow.Gap && row.lines != null) {
                        val count = (expanded[row.segment] ?: 0).coerceAtMost(row.lines.size)
                        val above = row.position == DiffGapPosition.Above
                        val shown = if (above) row.lines.takeLast(count) else row.lines.take(count)
                        if (!above) shown.forEach { add(DiffRow.Line(it, row.segment)) }
                        add(row.copy(hidden = row.hidden - count))
                        if (above) shown.forEach { add(DiffRow.Line(it, row.segment)) }
                    } else add(row)
                }
            }
        }
    val fenceState = remember(file) { diffFenceStates(file) }
    val display = remember(rows, formatted, splitOn, fenceState) { diffDisplay(rows, formatted, splitOn, fenceState) }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val redactor = LocalKitRedactor.current
    val digits = file.maxLineNumber.toString().length
    val language = CodeLanguage.forPath(file.path)
    val highlight = file.totalLines <= 2000
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val lineWrap = wrapOn || formatted
    // F: lib/ui/kit/diff/kit_diff_chrome.dart:774-785 — the number cell, the sign cell
    val numberWidth =
        remember(mono, density, digits) {
            with(density) { ceil(measurer.measure("0".repeat(digits), mono).size.width.toDp().value + Space.S2 * 1.5f).dp }
        }
    val glyphWidth = with(density) { ceil(mono.fontSize.toDp().value + Space.S1).dp }
    val list = rememberLazyListState()
    val changeTotal = position?.changeTotal ?: file.changeCount
    val changeOffset = position?.changeOffset ?: 0
    val changeSegment = file.changeStarts.getOrNull(change)
    LaunchedEffect(change, file) {
        val target = display.indexOfFirst { it.segment >= (changeSegment ?: 0) }
        if (full && change > 0 && target >= 0) list.animateScrollToItem(target)
    }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val allLines =
        remember(file) {
            file.segments.flatMap {
                when (it) {
                    is DiffLine -> listOf(it)
                    is DiffGap -> it.lines.orEmpty()
                }
            }
        }
    val longest = remember(allLines) { allLines.maxByOrNull { it.text.length }?.text.orEmpty().take(MAX_CHARS) }
    val longestWidth =
        remember(longest, mono, density) {
            with(density) { measurer.measure(longest, mono, softWrap = false, maxLines = 1).size.width.toDp() } + Space.S4.dp
        }
    val selectedLines = allLines.filter { line ->
        line.kind != DiffLineKind.Hunk &&
            selection?.let { it.contains(it.old, if (it.old) line.oldNo else line.newNo) } == true
    }
    fun selectedText() = selectedLines.joinToString("\n") { redactor.redact(it.text) }
    fun select(old: Boolean, start: Int, end: Int = start) {
        selection = selection?.takeIf { it.old == old }?.copy(end = end) ?: SelectedLines(old, start, end)
    }
    val selectLabel = if (full) stringResource(Res.string.kitDiffSelectHunk) else ""
    val hideWords = stringResource(Res.string.kitDiffHideUnchanged)
    val cells = Cells(numberWidth, glyphWidth, digits, mono, colors, selection, selectLabel, redactor::redact, ::select, language, highlight)
    val rowContent: @Composable (Geometry, Int, DiffDisplay) -> Unit = { g, index, item ->
        when (item) {
            is DiffDisplay.Pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    val half = g.textWidth?.let { Modifier.width(g.number + g.glyph + it) } ?: Modifier.weight(1f)
                    cells.Half(item.old, true, g, half)
                    Box(Modifier.fillMaxHeight().width(kitHairline()).background(colors.hairline))
                    cells.Half(item.current, false, g, half)
                }
            is DiffDisplay.Markdown -> {
                val unit = item.unit
                val first = unit.lines.first()
                val old = first.kind == DiffLineKind.Removed
                val text = redactor.redact(unit.lines.joinToString("\n") { it.text })
                val blocks = remember(text) { KitMdSource.blocks("diff-${unit.segment}", text, false) }
                Row(Modifier.fillMaxWidth().background(tint(colors, first.kind))) {
                    cells.Number(
                        if (old) first.oldNo else first.newNo,
                        old,
                        { n -> select(old, n, (if (old) unit.lines.last().oldNo else unit.lines.last().newNo) ?: n) },
                    )
                    cells.Glyph(first.kind)
                    // A diff's Markdown is presentation only; source links are selectable text, never external actions.
                    KitMarkdown(blocks, KitMarkdownActions(onLink = { source = true }), Modifier.weight(1f).padding(end = Space.S2.dp))
                }
            }
            is DiffDisplay.Source ->
                when (val row = item.row) {
                    is DiffRow.Line -> cells.Unified(row.line, g)
                    is DiffRow.HunkHeader -> {
                        // F: lib/ui/kit/diff/kit_diff_rows.dart:60-64 — the fold bar above already says where it starts.
                        val afterGap = (display.getOrNull(index - 1) as? DiffDisplay.Source)?.row is DiffRow.Gap
                        if (!afterGap) {
                            val label = hunkLabel(row.oldStart, row.oldEnd, row.newStart, row.newEnd)
                            val hunk = file.hunks.firstOrNull { it.headerSegment == row.segment }
                            val words: @Composable () -> Unit = {
                                KitText(
                                    label,
                                    Modifier.padding(start = g.indent, top = Space.S1.dp, bottom = Space.S1.dp),
                                    role = TypeRole.Mono,
                                    tone = TextTone.Secondary,
                                )
                            }
                            if (full && hunk != null)
                                KitTappable(
                                    {
                                        val old = hunk.newStart == null
                                        val start = (if (old) hunk.oldStart else hunk.newStart) ?: 0
                                        val end = (if (old) hunk.oldEnd else hunk.newEnd) ?: start
                                        selection = SelectedLines(old, start, end)
                                    },
                                    onClickLabel = selectLabel,
                                ) { words() }
                            else words()
                        }
                    }
                    is DiffRow.Gap -> {
                        val shown = expanded[row.segment] ?: 0
                        val open = row.lines != null && full
                        if (open && shown > 0) {
                            GapBar(hideWords, KitIcons.unfoldLess, g.indent, flat = !full) { expanded = expanded - row.segment }
                        }
                        if (row.hidden > 0) {
                            GapBar(gapLabel(row.hidden), null, g.indent, flat = !full, onTap = if (open) ({ expanded = expanded + (row.segment to shown + 20) }) else null)
                        }
                    }
                }
        }
    }
    val lines: @Composable (Modifier, Dp, Boolean) -> Unit = { frame, width, bounded ->
        val gutter = (if (splitOn) numberWidth else numberWidth * 2) + glyphWidth
        val textWidth: Dp?
        val contentWidth: Dp
        if (lineWrap) {
            textWidth = null
            contentWidth = width
        } else if (splitOn) {
            val half = maxOf((width - kitHairline()) / 2, gutter + longestWidth)
            textWidth = half - gutter
            contentWidth = half * 2 + kitHairline()
        } else {
            textWidth = maxOf(width - gutter, longestWidth)
            contentWidth = gutter + textWidth
        }
        val g = Geometry(numberWidth, glyphWidth, lineWrap, splitOn, textWidth)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            KitMarkdownSelection {
                val sideways = if (lineWrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()).width(contentWidth)
                if (full) {
                    LazyColumn(
                        frame.then(if (bounded) Modifier else Modifier.heightIn(max = UNBOUNDED_LINES_MAX)).then(sideways),
                        state = list,
                        contentPadding = PaddingValues(bottom = Space.S4.dp), // F: lib/ui/kit/diff/kit_diff_chrome.dart:868
                    ) {
                        itemsIndexed(display) { i, item -> rowContent(g, i, item) }
                    }
                } else {
                    Column(frame.then(sideways)) { display.forEachIndexed { i, item -> rowContent(g, i, item) } }
                }
            }
        }
    }
    val codeFill = if (colors.dark) colors.surface1 else colors.ground // F: lib/ui/kit/kit_tokens.dart detailsSurface
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight && full
        val width = maxWidth
        Column(Modifier.fillMaxWidth()) {
            if (full) {
                DiffHeader(
                    file = file,
                    position = position,
                    change = changeOffset + change,
                    changeTotal = changeTotal,
                    wrap = wrapOn,
                    canWrap = !file.binary && !formatted,
                    onWrap = { userWrap = !wrapOn },
                    onPrevious = if (change > 0) ({ change-- }) else null,
                    onNext = if (change < file.changeCount - 1) ({ change++ }) else null,
                    sourceSwitch = if (markdown && !file.binary) ({ SourceSwitch(source) { source = !source } }) else null,
                )
            } else if (markdown) {
                SourceSwitch(source) { source = !source }
            }
            if (file.binary) {
                KitText(
                    stringResource(Res.string.kitDiffBinary),
                    Modifier.padding(tokens.gutter.dp).semantics(mergeDescendants = true) {},
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            } else {
                val frame = if (full) Modifier.background(codeFill) else Modifier
                // The listing runs edge to edge like a terminal (KitTerminalView): code in its own frame, not page words
                // on the gutter (kit_diff_chrome.dart `_codeFrame`).
                Box((if (bounded) Modifier.weight(1f) else Modifier).fillMaxWidth().then(frame).then(if (full) Modifier.kitEdge(KitEdge.Chrome) else Modifier)) {
                    lines(if (bounded) Modifier.fillMaxSize() else Modifier, width, bounded)
                }
                if (full && displayRows.cut) {
                    // F: lib/ui/kit/diff/kit_diff_chrome.dart:645-678 — "Showing 400 of 3,200 lines" and Open all.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        KitText(
                            stringResource(Res.string.kitDiffTooBig, grouped(displayRows.shownLines), grouped(displayRows.totalLines)),
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                        )
                        if (onOpenAll != null) KitButton(stringResource(Res.string.kitDiffOpenAll), onOpenAll, role = KitButtonRole.Tertiary)
                    }
                }
            }
            if (selection != null && full) {
                SelectionBar(
                    count = selectedLines.size,
                    onComment = onComment?.let { { it(selectedText()) } },
                    onAddToPrompt = onAddToPrompt?.let { { it(selectedText()) } },
                    onCopy = { clipboard.setText(AnnotatedString(selectedText())) },
                    onClear = { selection = null },
                )
            }
        }
    }
}

/** "3,200": the count with its thousands marked, as Flutter's `NumberFormat` words it in English. */
private fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The Markdown file's Formatted / Source switch. */
@Composable
private fun SourceSwitch(source: Boolean, onToggle: () -> Unit) {
    KitButton(
        stringResource(if (source) Res.string.kitDiffShowFormatted else Res.string.kitDiffShowSource),
        onToggle,
        role = KitButtonRole.Tertiary,
    )
}

/** The loading look (kit_diff_chrome.dart:12-35): the header band at row height, then up to six skeleton rows. */
@Composable
private fun DiffLoading(modifier: Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    BoxWithConstraints(modifier) {
        val room = maxHeight - tokens.rowHeightOne.dp
        val rows = if (constraints.hasBoundedHeight) (room / tokens.skeletonRow.dp).toInt().coerceIn(0, 6) else 6
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(tokens.rowHeightOne.dp).background(colors.surface1).bottomRule(colors.hairline))
            if (rows > 0) KitSkeletonRows(count = rows)
        }
    }
}

/** A hairline along the bottom edge (the diff bands' `_KitDiffRule.bottom`). */
@Composable
private fun Modifier.bottomRule(color: Color): Modifier {
    val w = kitHairline()
    return drawBehind {
        val px = w.toPx()
        drawRect(color, topLeft = Offset(0f, size.height - px), size = androidx.compose.ui.geometry.Size(size.width, px))
    }
}

/** A hairline along the top edge (`_KitDiffRule.top`). */
@Composable
private fun Modifier.topRule(color: Color): Modifier {
    val w = kitHairline()
    return drawBehind { drawRect(color, size = androidx.compose.ui.geometry.Size(size.width, w.toPx())) }
}

/** "+4 −3" in mono, added in `success`, removed in `codeRemoved` (kit_diff_rows.dart `_Counts`). */
@Composable
private fun Counts(added: Int, removed: Int) {
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val spoken = stringResource(Res.string.kitDiffCounts, added, removed)
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.success)) { append("+$added") }
            append(" ")
            withStyle(SpanStyle(color = colors.text2)) { append("−$removed") }
        },
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        style = mono,
    )
}

/**
 * The file header (kit_diff_chrome.dart:250-507): the page's ground with a hairline under it. With more than one change
 * (or a switcher) the name row (name, "· 1 of 2 ›", the folder and status under it, the counts at the end) and the
 * change bar under it; with one change, one line holding the name, the folder and status, the counts and the wrap
 * toggle.
 */
@Composable
private fun DiffHeader(
    file: DiffFile,
    position: KitDiffPosition?,
    change: Int,
    changeTotal: Int,
    wrap: Boolean,
    canWrap: Boolean,
    onWrap: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    sourceSwitch: (@Composable () -> Unit)?,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val status =
        when (file.status) {
            DiffFileStatus.Renamed -> file.oldPath?.let { stringResource(Res.string.kitDiffRenamed, it) }.orEmpty()
            DiffFileStatus.Added -> stringResource(Res.string.kitDiffAddedFile)
            DiffFileStatus.Deleted -> stringResource(Res.string.kitDiffDeletedFile)
            DiffFileStatus.Modified -> ""
        }
    val folder = file.path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val supporting = listOf(folder, status).filter { it.isNotEmpty() }.joinToString(" · ")
    val multi = (position?.fileCount ?: 1) > 1
    val switcher = multi // F: lib/ui/kit/diff/kit_diff_chrome.dart:276 (no file list beside a single-file view)
    val single = changeTotal <= 1
    val compact = single && !switcher
    val positionWords = position?.let { stringResource(Res.string.kitDiffFilePosition, it.fileIndex + 1, it.fileCount) }
    val name = buildAnnotatedString {
        withStyle(typography.style(TypeRole.RowTitle).copy(color = colors.text1).toSpanStyle()) {
            append(file.path.substringAfterLast('/'))
        }
        if (switcher && positionWords != null) {
            withStyle(typography.style(TypeRole.Secondary).copy(color = colors.text2).toSpanStyle()) { append(" · $positionWords") }
        }
    }
    val wrapButton: @Composable () -> Unit = {
        if (canWrap) {
            KitIconButton(KitIcons.wrapText, stringResource(Res.string.kitWrapLines), onWrap, selected = wrap)
        }
    }
    Column(Modifier.fillMaxWidth().background(colors.ground).bottomRule(colors.hairline)) {
        if (compact) {
            Row(
                Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, top = Space.S1.dp, bottom = Space.S1.dp, end = if (file.binary) tokens.gutter.dp else Space.S3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (supporting.isNotEmpty()) KitText(supporting, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
                    Counts(file.added, file.removed)
                }
                sourceSwitch?.invoke()
                wrapButton()
            }
        } else {
            val row: @Composable (Modifier) -> Unit = { m ->
                Row(
                    m.fillMaxWidth().padding(start = tokens.gutter.dp, top = Space.S2.dp, end = tokens.gutter.dp, bottom = if (switcher) Space.S2.dp else 0.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (switcher) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(name, Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(Space.S1.dp))
                                KitIcon(KitIcons.chevronRight, size = KitIconSize.Small, tone = TextTone.Secondary)
                            }
                        } else {
                            BasicText(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (supporting.isNotEmpty()) KitText(supporting, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2)
                    }
                    Spacer(Modifier.width(Space.S3.dp))
                    Counts(file.added, file.removed)
                }
            }
            val pick = position?.onPick
            if (switcher && pick != null) KitTappable(pick) { row(Modifier) } else row(Modifier)
            if (!compact) {
                // The change bar is the header's toolbar: its controls own their placement (kit_diff_chrome.dart:437-441).
                Row(
                    Modifier.fillMaxWidth().kitEdge(KitEdge.Chrome).padding(start = Space.S1.dp, end = Space.S3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (changeTotal > 1) {
                        val previous = stringResource(Res.string.kitDiffPreviousChange)
                        val next = stringResource(Res.string.kitDiffNextChange)
                        KitIconButton(KitIcons.chevronUp, previous, { onPrevious?.invoke() }, shortcut = "P")
                        KitIconButton(KitIcons.chevronDown, next, { onNext?.invoke() }, shortcut = "N")
                        Spacer(Modifier.width(Space.S1.dp))
                        KitText(
                            stringResource(Res.string.kitDiffChangeOf, (change + 1).toString(), changeTotal.toString()),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            role = TypeRole.Caption,
                            tone = TextTone.Secondary,
                        )
                        Spacer(Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    sourceSwitch?.invoke()
                    wrapButton()
                }
            }
        }
    }
}

/**
 * A fold bar (kit_diff_rows.dart `_GapBar`): a `surface2` band at least the touch target tall, its words in the
 * secondary role starting on the line words' edge, an optional glyph in the slot before them. [flat] (an inline
 * preview) draws no band.
 */
@Composable
private fun GapBar(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, indent: Dp, flat: Boolean, onTap: (() -> Unit)?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val start = maxOf(0.dp, indent - tokens.smallIcon.dp - Space.S2.dp)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(start = start, end = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(tokens.smallIcon.dp + Space.S2.dp)) {
                if (icon != null) KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary)
            }
            KitText(label, Modifier.padding(vertical = Space.S2.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
    Box(Modifier.fillMaxWidth().then(if (flat) Modifier else Modifier.background(colors.surface2))) {
        if (onTap != null) KitTappable(onTap, Modifier.fillMaxWidth()) { content() } else content()
    }
}

/** The selection's bar (kit_diff_chrome.dart:680-753): `surface1` with a hairline on top, the count, the actions and Clear. */
@Composable
private fun SelectionBar(
    count: Int,
    onComment: (() -> Unit)?,
    onAddToPrompt: (() -> Unit)?,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val compact = LocalKitWindow.current.cls == WindowClass.Compact
    val counted: @Composable (Modifier) -> Unit = { m ->
        KitText(
            pluralStringResource(Res.plurals.kitDiffSelected, count, count),
            m.semantics { liveRegion = LiveRegionMode.Polite },
            role = TypeRole.Label,
        )
    }
    val clear: @Composable () -> Unit = {
        KitIconButton(KitIcons.close, stringResource(Res.string.kitDiffClearSelection), onClear, shortcut = "Esc")
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        KitActionBlock(
            m,
            primary = onComment?.let { KitAction(stringResource(Res.string.kitDiffComment), it) },
            tertiary = listOfNotNull(
                onAddToPrompt?.let { KitAction(stringResource(Res.string.kitDiffAddToPrompt), it) },
                KitAction(stringResource(Res.string.kitDiffCopyLines), onCopy, icon = KitIcons.copy),
            ),
        )
    }
    Column(
        // A bar pinned under the lines (kit_diff_chrome.dart:742-752): it places its own count, actions and Clear.
        Modifier.fillMaxWidth().kitEdge(KitEdge.Chrome).background(colors.surface1).topRule(colors.hairline)
            .padding(start = tokens.gutter.dp, top = Space.S1.dp, end = Space.S1.dp, bottom = Space.S2.dp),
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                counted(Modifier.weight(1f))
                clear()
            }
            Spacer(Modifier.height(Space.S1.dp))
            actions(Modifier.padding(end = Space.S3.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                counted(Modifier.weight(1f))
                Spacer(Modifier.width(Space.S3.dp))
                actions(Modifier.weight(3f, fill = false))
                Spacer(Modifier.width(Space.S2.dp))
                clear()
            }
        }
    }
}

private fun sign(kind: DiffLineKind): String =
    when (kind) {
        DiffLineKind.Added -> "+"
        DiffLineKind.Removed -> "−"
        else -> " "
    }

/** `codeAddedSurface` / `codeRemovedSurface` (kit_diff_rows.dart `_tint`). */
private fun tint(colors: KitColors, kind: DiffLineKind): Color =
    when (kind) {
        DiffLineKind.Added -> colors.success.copy(alpha = TINT_ALPHA)
        DiffLineKind.Removed -> colors.text2.copy(alpha = TINT_ALPHA)
        else -> Color.Transparent
    }

/** The cells of one row: number, sign and words (kit_diff_rows.dart `_numberCell`, `_glyphCell`, `_textCell`). */
private class Cells(
    val number: Dp,
    val glyph: Dp,
    val digits: Int,
    val mono: TextStyle,
    val colors: KitColors,
    val selected: SelectedLines?,
    val label: String,
    val redact: (String) -> String,
    val select: (Boolean, Int, Int) -> Unit,
    val language: CodeLanguage,
    val highlight: Boolean,
) {
    /** A selected row: the accent's mark tint over the row's own tint (kit_diff_rows.dart `_rowFrame`). */
    fun rowColor(kind: DiffLineKind?, isSelected: Boolean): Color {
        val own = tint(colors, kind ?: DiffLineKind.Context)
        if (!isSelected) return own
        val under = if (own == Color.Transparent) (if (colors.dark) colors.surface1 else colors.ground) else own.compositeOver(colors.ground)
        return colors.accent.copy(alpha = FlutterKitTokens.MARK_TINT_ALPHA).compositeOver(under)
    }

    /** The number, end-aligned with space1 after it, in text3 (accent when selected) and an accent start edge then. */
    @Composable
    fun Number(n: Int?, old: Boolean, onSelect: (Int) -> Unit = { select(old, it, it) }) {
        val isSelected = selected?.contains(old, n) == true
        val ring = kitFocusRingWidth()
        val accent = colors.accent
        DisableSelection {
            val text = n?.toString().orEmpty().padStart(digits)
            val style = mono.copy(color = if (isSelected) colors.accent else colors.text3, textAlign = TextAlign.End)
            val cell =
                Modifier.width(number)
                    .then(if (isSelected) Modifier.drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(ring.toPx(), size.height)) } else Modifier)
            if (n == null || label.isEmpty()) {
                BasicText(text, cell.padding(end = Space.S1.dp), style = style)
            } else {
                // Flutter selects from the line's gutter (kit_diff_rows.dart `_gutterGestures`): the rows stay at the
                // text's height and touch the gutter column edge to edge, so a tap selects the line it lands on; the
                // row's accessibility action names what it does (A11Y-2), not a 19 dp button.
                BasicText(
                    text,
                    cell.pointerInput(n, old) { detectTapGestures { onSelect(n) } }
                        .semantics { customActions = listOf(CustomAccessibilityAction(label) { onSelect(n); true }) }
                        .padding(end = Space.S1.dp),
                    style = style,
                )
            }
        }
    }

    /** The sign, centred in its cell: `+` in success, `−` in codeRemoved (text2). */
    @Composable
    fun Glyph(kind: DiffLineKind?) {
        DisableSelection {
            Box(Modifier.width(glyph), contentAlignment = Alignment.TopCenter) {
                BasicText(
                    kind?.let(::sign).orEmpty(),
                    style = mono.copy(
                        color = when (kind) {
                            DiffLineKind.Added -> colors.success
                            DiffLineKind.Removed -> colors.text2
                            else -> colors.text3
                        },
                    ),
                )
            }
        }
    }

    @Composable
    private fun Words(line: DiffLine?, g: Geometry, modifier: Modifier) {
        DiffCodeText(
            line?.text?.let(redact).orEmpty(),
            language,
            highlight && line != null,
            modifier.padding(end = Space.S2.dp),
            mono.copy(color = colors.text1),
            g.wrap,
        )
    }

    @Composable
    fun Unified(line: DiffLine, g: Geometry) {
        val isSelected = selected?.contains(true, line.oldNo) == true || selected?.contains(false, line.newNo) == true
        Row(Modifier.fillMaxWidth().background(rowColor(line.kind, isSelected))) {
            Number(line.oldNo, true)
            Number(line.newNo, false)
            Glyph(line.kind)
            Words(line, g, g.textWidth?.let { Modifier.width(it) } ?: Modifier.weight(1f))
        }
    }

    @Composable
    fun Half(line: DiffLine?, old: Boolean, g: Geometry, modifier: Modifier) {
        val n = if (old) line?.oldNo else line?.newNo
        val isSelected = selected?.contains(old, n) == true
        Row(modifier.fillMaxHeight().background(if (line == null) Color.Transparent else rowColor(line.kind, isSelected))) {
            Number(n, old)
            Glyph(line?.kind)
            Words(line, g, Modifier.weight(1f))
        }
    }
}
