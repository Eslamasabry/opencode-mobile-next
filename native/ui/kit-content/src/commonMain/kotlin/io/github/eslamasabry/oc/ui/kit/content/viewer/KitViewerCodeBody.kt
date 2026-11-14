package io.github.eslamasabry.oc.ui.kit.content.viewer

import io.github.eslamasabry.oc.ui.kit.frame.KitReportPageScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitViewerEmpty
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.ceil

/** Test tag prefix of one code line: `kit-viewer-line-12` (1-based). */
public const val KIT_VIEWER_LINE_TAG: String = "kit-viewer-line-"

/** Rows kept above the opened line, so it is not glued to the top edge. */
private const val LINE_CONTEXT = 3

/** The find mark's passive wash (plan 06 `KitFindMark`: .18 passive, never a text colour). */
private const val LINE_MARK_ALPHA = 0.18f

/**
 * The viewer's Code body (plan 10 §11.3): monospace lines with line numbers, always left to right (code never mirrors,
 * LAY-8), scrolling sideways by default. [initialLine] (1-based) is scrolled to and marked with the find wash; a line
 * out of range is the host's notice ("Line N is outside this preview"), not a mark. Lazy like the Text body.
 *
 * Native: highlighting arrives with `CodeTokenizer` (SyntaxMP, after its benchmark); until then lines are one colour.
 *
 * States: content, empty, truncated, opened at a line
 * Text: reading (mono)
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitViewer code body (lib/ui/kit/kit_viewer_build.dart `_codeBody`)
 */
@Composable
public fun KitViewerCodeBody(
    text: String,
    modifier: Modifier = Modifier,
    initialLine: Int? = null,
    wrap: Boolean = false,
) {
    val cut = remember(text) { ViewerText.cut(text) }
    val lines = remember(cut) { ViewerText.lines(cut.text) }
    val gutter = LocalKitTokens.current.gutter.dp
    if (lines.isEmpty()) {
        KitText(stringResource(Res.string.kitViewerEmpty), modifier.padding(gutter), tone = TextTone.Secondary)
        return
    }
    val mark = initialLine?.takeIf { it in 1..lines.size }
    val offset = if (cut.truncated) 1 else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = mark?.let { maxOf(0, it - 1 - LINE_CONTEXT + offset) } ?: 0)
    val numberWidth = remember(lines.size) { lines.size.toString().length }
    val wash = LocalKitColors.current.accent.copy(alpha = LINE_MARK_ALPHA)
    val partial = if (cut.truncated) stringResource(Res.string.kitViewerPartial) else null
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionContainer(modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().testTag(KIT_VIEWER_LINES_TAG)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                state = listState.also { KitReportPageScroll(it) },
                contentPadding = PaddingValues(horizontal = gutter, vertical = gutter / 2),
            ) {
                if (partial != null) {
                    item(key = "partial", contentType = "notice") {
                        KitText(partial, Modifier.padding(bottom = gutter / 2), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
                itemsIndexed(lines, key = { i, _ -> i }, contentType = { _, _ -> "line" }) { i, line ->
                    val number = i + 1
                    val marked = number == mark
                    Row(
                        Modifier
                            .testTag("$KIT_VIEWER_LINE_TAG$number")
                            .then(if (marked) Modifier.background(wash).semantics { selected = true } else Modifier),
                        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                    ) {
                        DisableSelection { // copying lines never copies their numbers
                            KitText(
                                number.toString().padStart(numberWidth),
                                Modifier.widthIn(min = (numberWidth * 8).dp),
                                role = TypeRole.Mono,
                                tone = TextTone.Tertiary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                        KitText(line.ifEmpty { " " }, role = TypeRole.Mono, maxLines = if (wrap) Int.MAX_VALUE else 1)
                    }
                }
            }
        }
    }
}

// --- The Flutter viewer's code body (kit_viewer_build.dart `_codeBody` over `KitCodeBlock.fill`) -------------------

/** A find mark's wash on the code: the active one stronger (Dart `_paintMarks`). */
private const val MARK_ALPHA = .18f // F: lib/ui/kit/kit_code_block.dart:1366
private const val MARK_ACTIVE_ALPHA = .38f // F: lib/ui/kit/kit_code_block.dart:1366

/**
 * The file as one code block filling the viewer edge to edge on the document's opaque fill (`detailsSurface`): numbered
 * lines, always left to right, on the same gutter as the header's name. Unwrapped, one sideways scroller holds every
 * line at the longest line's width; the block itself keeps the list cap (960 dp), centred.
 *
 * Native gap: syntax colour (Dart `KitCodeHighlight`) lives with `KitCodeBlock` in `:ui:kit-chat`, which this module
 * does not depend on, so lines are drawn in the block's one text colour; [language] and [highlight] are kept for it.
 */
@Composable
internal fun ViewerCodeBody(
    prepared: ViewerPrepared,
    language: String?,
    lineNumbers: Boolean,
    highlight: Boolean,
    initialLine: Int?,
    wrap: Boolean,
    marks: List<IntRange>,
    activeMark: Int?,
    hangingIndent: Boolean,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val mono =
        LocalKitTypography.current.style(TypeRole.Mono).copy(
            color = colors.text1,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
            ),
        )
    val measurer = rememberTextMeasurer()
    // The file fills the viewer sheet edge to edge on an opaque fill in every style (contract F7).
    val fill = if (colors.dark) colors.surface1 else colors.ground
    val lines = remember(prepared) { KitRedact.text(prepared.shown).removeSuffix("\n").split('\n') } // Dart `_splitLines`
    val starts = remember(lines) { lines.runningFold(0) { at, line -> at + line.length + 1 } }
    val digits = if (lineNumbers) prepared.shownLines.toString().length else 0
    val gutterWidth = if (lineNumbers) with(density) { measurer.measure("9".repeat(digits), mono).size.width.toDp() } else 0.dp
    val extent = with(density) { mono.lineHeight.toDp() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialLine?.minus(1)?.coerceIn(0, maxOf(0, lines.size - 1)) ?: 0)
    val block: @Composable (Modifier) -> Unit = { m ->
        LazyColumn(m.fillMaxWidth().kitMaxWidth(CapRole.List).fillMaxWidth().testTag(KIT_VIEWER_LINES_TAG), state = list.also { KitReportPageScroll(it) }) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val annotated = buildAnnotatedString {
                    append(line)
                    val start = starts[i]
                    val end = start + line.length
                    marks.forEachIndexed { n, mark ->
                        val from = maxOf(mark.first, start)
                        val to = minOf(mark.last + 1, end)
                        if (from < to) {
                            addStyle(
                                SpanStyle(background = colors.accent.copy(alpha = if (n == activeMark) MARK_ACTIVE_ALPHA else MARK_ALPHA)),
                                from - start,
                                to - start,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().then(if (wrap) Modifier else Modifier.height(extent)).testTag("$KIT_VIEWER_LINE_TAG${i + 1}"),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (lineNumbers) {
                        BasicText(
                            (i + 1).toString(),
                            Modifier.width(gutterWidth),
                            style = mono.copy(color = colors.text3, textAlign = TextAlign.End),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                        Spacer(Modifier.width(Space.S2.dp))
                        if (hangingIndent) {
                            KitDivider(vertical = true)
                            Spacer(Modifier.width(Space.S3.dp))
                        }
                    }
                    BasicText(
                        annotated,
                        if (wrap) Modifier.weight(1f) else Modifier,
                        style = mono,
                        softWrap = wrap,
                        maxLines = if (wrap) Int.MAX_VALUE else 1,
                        overflow = if (wrap) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize().background(fill)) {
            val inset = Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
            if (wrap) {
                block(inset.fillMaxSize())
            } else {
                // One horizontal scroller for the whole file: the block is laid out as wide as its longest line.
                val sample = "9".repeat(digits) + " " + prepared.longest
                val painter = with(density) { measurer.measure(sample, mono, softWrap = false, maxLines = 1).size.width.toDp() }
                val natural = ceil((painter + Space.S2.dp + tokens.gutter.dp * 2 + Space.S4.dp).value).dp // F: lib/ui/kit/kit_viewer_build.dart:507
                val width = maxOf(maxWidth, natural)
                val height = maxHeight
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    Box(Modifier.width(width).height(height)) { block(inset.fillMaxSize()) }
                }
            }
        }
    }
}
