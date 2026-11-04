package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val TINT_ALPHA = .12f

/** Gutter padding and sign column of one unwrapped cell, beyond its characters. */
private val CELL_SLACK = 40.dp

/** Unwrapped rows are at most this many characters wide; longer lines are cut at the edge. */
private const val MAX_CHARS = 4000

private data class SelectedLines(val old: Boolean, val anchor: Int, val end: Int) {
    fun contains(oldSide: Boolean, n: Int?): Boolean =
        old == oldSide && n != null && n in minOf(anchor, end)..maxOf(anchor, end)
}

/**
 * A real diff listing in chat, Git and review. Inline previews retain the twelve-line cap. Full
 * views have visible wrap/layout/source controls, change navigation, expandable unchanged context
 * and gutter range selection. Copies use redacted source lines, never signs or line numbers.
 * Optional host actions receive the selected source.
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
) {
    val base = remember(file, maxLines) { DiffModel.displayRows(file, maxLines).rows }
    val full = maxLines > 12
    var wrap by remember(file) { mutableStateOf(false) }
    var split by remember(file) { mutableStateOf(false) }
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
    val display =
        remember(rows, formatted, split, fenceState) {
            diffDisplay(rows, formatted, split, fenceState)
        }
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val redactor = LocalKitRedactor.current
    val digits = file.maxLineNumber.toString().length
    val language = CodeLanguage.forPath(file.path)
    val highlight = file.totalLines <= 2000
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val charPx = remember(mono, density) { measurer.measure("0", style = mono).size.width.coerceAtLeast(1) }
    val list = rememberLazyListState()
    val changeSegment = file.changeStarts.getOrNull(change)
    LaunchedEffect(change, file) {
        val target = display.indexOfFirst { it.segment >= (changeSegment ?: 0) }
        if (full && target >= 0) list.animateScrollToItem(target)
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
    val longest =
        remember(allLines) { (allLines.maxOfOrNull { it.text.length } ?: 0).coerceAtMost(MAX_CHARS) }
    val selectedLines = allLines.filter { line ->
        line.kind != DiffLineKind.Hunk &&
            selection?.let { it.contains(it.old, if (it.old) line.oldNo else line.newNo) } == true
    }
    fun selectedText() = selectedLines.joinToString("\n") { redactor.redact(it.text) }
    fun select(old: Boolean, start: Int, end: Int = start) {
        selection =
            selection?.takeIf { it.old == old }?.copy(end = end) ?: SelectedLines(old, start, end)
    }
    val selectLabel = if (full) stringResource(Res.string.kitDiffSelectHunk) else ""
    val rowContent: @Composable (DiffDisplay) -> Unit = { item ->
        when (item) {
            is DiffDisplay.Pair ->
                Row(Modifier.fillMaxWidth()) {
                    LineCell(
                        item.old,
                        true,
                        digits,
                        mono,
                        colors,
                        wrap,
                        selection,
                        selectLabel,
                        Modifier.weight(1f),
                        redactor::redact,
                        ::select,
                        language,
                        highlight,
                    )
                    LineCell(
                        item.current,
                        false,
                        digits,
                        mono,
                        colors,
                        wrap,
                        selection,
                        selectLabel,
                        Modifier.weight(1f),
                        redactor::redact,
                        ::select,
                        language,
                        highlight,
                    )
                }
            is DiffDisplay.Markdown -> {
                val unit = item.unit
                val first = unit.lines.first()
                val old = first.kind == DiffLineKind.Removed
                val text = redactor.redact(unit.lines.joinToString("\n") { it.text })
                val blocks =
                    remember(text) { KitMdSource.blocks("diff-${unit.segment}", text, false) }
                Row(Modifier.fillMaxWidth().background(tint(colors, first.kind))) {
                    NumberCell(
                        if (old) first.oldNo else first.newNo,
                        digits,
                        mono,
                        selectLabel,
                        { n ->
                            select(
                                old,
                                n,
                                (if (old) unit.lines.last().oldNo else unit.lines.last().newNo)
                                    ?: n,
                            )
                        },
                    )
                    BasicText(
                        sign(first.kind),
                        Modifier.padding(end = Space.S2.dp),
                        style = mono.copy(color = colors.text2),
                    )
                    // A diff's Markdown is presentation only; source links are selectable text,
                    // never external actions.
                    KitMarkdown(
                        blocks,
                        KitMarkdownActions(onLink = { source = true }),
                        Modifier.weight(1f).padding(end = Space.S2.dp),
                    )
                }
            }
            is DiffDisplay.Source ->
                when (val row = item.row) {
                    is DiffRow.Line ->
                        UnifiedLine(
                            row.line,
                            digits,
                            mono,
                            colors,
                            wrap || formatted,
                            selection,
                            selectLabel,
                            redactor::redact,
                            ::select,
                            language,
                            highlight,
                        )
                    is DiffRow.HunkHeader -> {
                        val label = hunkLabel(row.oldStart, row.oldEnd, row.newStart, row.newEnd)
                        val hunk = file.hunks.firstOrNull { it.headerSegment == row.segment }
                        if (full && hunk != null)
                            KitTappable(
                                {
                                    val old = hunk.newStart == null
                                    val start = (if (old) hunk.oldStart else hunk.newStart) ?: 0
                                    val end = (if (old) hunk.oldEnd else hunk.newEnd) ?: start
                                    selection = SelectedLines(old, start, end)
                                },
                                onClickLabel = selectLabel,
                            ) {
                                KitText(label, role = TypeRole.Mono)
                            }
                        else KitText(label, role = TypeRole.Mono)
                    }
                    is DiffRow.Gap -> {
                        val shown = expanded[row.segment] ?: 0
                        val label =
                            if (row.hidden == 0) stringResource(Res.string.kitDiffHideUnchanged)
                            else gapLabel(row.hidden)
                        if (row.lines != null && full) {
                            KitButton(
                                label,
                                {
                                    expanded =
                                        expanded +
                                            (row.segment to if (row.hidden == 0) 0 else shown + 20)
                                },
                                role = KitButtonRole.Tertiary,
                            )
                            if (shown > 0 && row.hidden > 0)
                                KitButton(
                                    stringResource(Res.string.kitDiffHideUnchanged),
                                    { expanded = expanded - row.segment },
                                    role = KitButtonRole.Tertiary,
                                )
                        } else
                            KitText(
                                label,
                                Modifier.padding(vertical = Space.S1.dp),
                                role = TypeRole.Mono,
                            )
                    }
                }
        }
    }
    Column(modifier) {
        if (full)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                if (!formatted) {
                    KitButton(
                        stringResource(Res.string.kitWrapLines),
                        { wrap = !wrap },
                        Modifier.semantics { selected = wrap },
                        role = KitButtonRole.Tertiary,
                    )
                    KitButton(
                        stringResource(
                            if (split) Res.string.kitDiffUnified else Res.string.kitDiffSideBySide
                        ),
                        { split = !split },
                        role = KitButtonRole.Tertiary,
                    )
                }
                if (file.changeCount > 1) {
                    KitButton(
                        stringResource(Res.string.kitDiffPreviousChange),
                        { change = (change - 1).coerceAtLeast(0) },
                        role = KitButtonRole.Tertiary,
                    )
                    KitButton(
                        stringResource(Res.string.kitDiffNextChange),
                        { change = (change + 1).coerceAtMost(file.changeCount - 1) },
                        role = KitButtonRole.Tertiary,
                    )
                    KitText(
                        stringResource(
                            Res.string.kitDiffChangeOf,
                            (change + 1).toString(),
                            file.changeCount.toString(),
                        )
                    )
                }
            }
        if (markdown)
            KitButton(
                stringResource(
                    if (source) Res.string.kitDiffShowFormatted else Res.string.kitDiffShowSource
                ),
                { source = !source },
                role = KitButtonRole.Tertiary,
            )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            KitMarkdownSelection {
                if (full) {
                    // Unwrapped lines scroll sideways together. A lazy list cannot size itself to
                    // its widest row, so it gets a definite width from the longest source line
                    // (with weights, an unbounded width would measure the side-by-side cells at 0).
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val horizontal =
                            if (wrap || formatted) Modifier.fillMaxWidth()
                            else {
                                val cellChars = longest + digits + 2
                                val chars = if (split) 2 * cellChars else cellChars + digits
                                val cells = if (split) 2 else 1
                                val needed =
                                    with(density) { (charPx * chars).toDp() } + CELL_SLACK * cells
                                Modifier.horizontalScroll(rememberScrollState())
                                    .width(
                                        if (constraints.hasBoundedWidth) maxOf(maxWidth, needed)
                                        else needed
                                    )
                            }
                        LazyColumn(Modifier.heightIn(max = 560.dp).then(horizontal), state = list) {
                            itemsIndexed(display) { _, item -> rowContent(item) }
                        }
                    }
                } else {
                    Column(
                        if (formatted) Modifier.fillMaxWidth()
                        else
                            Modifier.horizontalScroll(rememberScrollState())
                                .width(IntrinsicSize.Max)
                    ) {
                        display.forEach { rowContent(it) }
                    }
                }
            }
        }
        if (selection != null && full)
            FlowRow(Modifier.fillMaxWidth()) {
                KitText(
                    pluralStringResource(
                        Res.plurals.kitDiffSelected,
                        selectedLines.size,
                        selectedLines.size,
                    )
                )
                KitButton(
                    stringResource(Res.string.kitDiffCopyLines),
                    { clipboard.setText(AnnotatedString(selectedText())) },
                    role = KitButtonRole.Tertiary,
                )
                if (onComment != null)
                    KitButton(
                        stringResource(Res.string.kitDiffComment),
                        { onComment(selectedText()) },
                        role = KitButtonRole.Tertiary,
                    )
                if (onAddToPrompt != null)
                    KitButton(
                        stringResource(Res.string.kitDiffAddToPrompt),
                        { onAddToPrompt(selectedText()) },
                        role = KitButtonRole.Tertiary,
                    )
                KitButton(
                    stringResource(Res.string.kitDiffClearSelection),
                    { selection = null },
                    role = KitButtonRole.Tertiary,
                )
            }
    }
}

private fun sign(kind: DiffLineKind): String =
    when (kind) {
        DiffLineKind.Added -> "+"
        DiffLineKind.Removed -> "−"
        else -> " "
    }

private fun tint(colors: KitColors, kind: DiffLineKind): Color =
    when (kind) {
        DiffLineKind.Added -> colors.success.copy(alpha = TINT_ALPHA)
        DiffLineKind.Removed -> colors.attention.copy(alpha = TINT_ALPHA)
        else -> Color.Transparent
    }

@Composable
private fun NumberCell(
    number: Int?,
    digits: Int,
    mono: TextStyle,
    label: String,
    onSelect: (Int) -> Unit,
) {
    DisableSelection {
        if (number == null || label.isEmpty())
            BasicText(
                number?.toString().orEmpty().padStart(digits),
                Modifier.padding(start = Space.S2.dp),
                style = mono,
            )
        else
            // Flutter selects from the line's gutter without a 48 dp box (a tap target that size
            // would triple every diff row); the accessibility action names what it does.
            BasicText(
                number.toString().padStart(digits),
                Modifier.clickable(onClickLabel = label, role = Role.Button) { onSelect(number) }
                    .padding(start = Space.S2.dp),
                style = mono,
            )
    }
}

@Composable
private fun UnifiedLine(
    line: DiffLine,
    digits: Int,
    mono: TextStyle,
    colors: KitColors,
    wrap: Boolean,
    selected: SelectedLines?,
    label: String,
    redact: (String) -> String,
    select: (Boolean, Int, Int) -> Unit,
    language: CodeLanguage,
    highlight: Boolean,
) {
    val old = line.kind == DiffLineKind.Removed
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (selected?.contains(old, if (old) line.oldNo else line.newNo) == true)
                    colors.accent.copy(alpha = .2f)
                else tint(colors, line.kind)
            )
    ) {
        NumberCell(line.oldNo, digits, mono.copy(color = colors.text3), label) {
            select(true, it, it)
        }
        NumberCell(line.newNo, digits, mono.copy(color = colors.text3), label) {
            select(false, it, it)
        }
        BasicText(
            sign(line.kind),
            Modifier.padding(horizontal = Space.S2.dp),
            style = mono.copy(color = colors.text2),
        )
        DiffCodeText(
            redact(line.text),
            language,
            highlight,
            if (wrap) Modifier.weight(1f) else Modifier,
            mono.copy(color = colors.text1),
            wrap,
        )
    }
}

@Composable
private fun LineCell(
    line: DiffLine?,
    old: Boolean,
    digits: Int,
    mono: TextStyle,
    colors: KitColors,
    wrap: Boolean,
    selected: SelectedLines?,
    label: String,
    modifier: Modifier,
    redact: (String) -> String,
    select: (Boolean, Int, Int) -> Unit,
    language: CodeLanguage,
    highlight: Boolean,
) {
    val number = if (old) line?.oldNo else line?.newNo
    Row(
        modifier.background(
            if (selected?.contains(old, number) == true) colors.accent.copy(alpha = .2f)
            else tint(colors, line?.kind ?: DiffLineKind.Context)
        )
    ) {
        NumberCell(number, digits, mono.copy(color = colors.text3), label) { select(old, it, it) }
        BasicText(
            sign(line?.kind ?: DiffLineKind.Context),
            Modifier.padding(horizontal = Space.S2.dp),
            style = mono.copy(color = colors.text2),
        )
        DiffCodeText(
            line?.text?.let(redact).orEmpty(),
            language,
            highlight,
            Modifier.weight(1f),
            mono.copy(color = colors.text1),
            wrap,
        )
    }
}
