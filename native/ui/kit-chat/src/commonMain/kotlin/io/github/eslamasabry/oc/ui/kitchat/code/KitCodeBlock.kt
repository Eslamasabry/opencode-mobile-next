package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCodeChanges
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCode
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCommand
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyOutput
import io.github.eslamasabry.oc.ui.words.res.kitCodeEmpty
import io.github.eslamasabry.oc.ui.words.res.kitCodeOpenFull
import io.github.eslamasabry.oc.ui.words.res.kitCodeShowAll
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What a [KitCodeBlock] holds (Dart `KitCodeKind`). */
public enum class KitCodeKind {
    /** Source code: syntax colour from the block's language. */
    Code,

    /**
     * A command the person runs elsewhere: a `$` before each command, outside the copied text
     * (B-CHAT-055).
     */
    Command,

    /** Output of a tool or process: no colour. */
    Output,
}

/**
 * C-019: the colour and weight of each code role in the current theme, as Flutter's `KitCodeHighlight._rawStyleFor`
 * paints them (`lib/ui/kit/kit_code_block.dart:222-246`): keywords in codeKeyword at w600, strings in codeString,
 * comments in text3 italic, the codeType family (numbers, types, function names at their declaration, attributes,
 * meta) in codeType, added lines in success, removed lines in codeRemoved (text2), and everything else (punctuation,
 * operators, calls, plain words) in the block's own text1. [fill] is `detailsSurface`; [text] is text1.
 */
@Immutable
public data class KitCodeColors(
    val roles: Map<CodeRole, Color>,
    val text: Color,
    val gutter: Color,
    val fill: Color,
) {
    public operator fun get(role: CodeRole): Color = roles[role] ?: text

    /** The style a token of [role] draws in: its colour, plus Flutter's weight for keywords and italic for comments. */
    public fun span(role: CodeRole): SpanStyle =
        SpanStyle(
            color = this[role],
            fontWeight = if (role == CodeRole.Keyword) FontWeight.W600 else null,
            fontStyle = if (role == CodeRole.Comment) FontStyle.Italic else null,
        )

    public companion object {
        public fun of(c: KitColors): KitCodeColors =
            KitCodeColors(
                roles =
                    mapOf(
                        CodeRole.Keyword to c.codeKeyword,
                        CodeRole.String to c.codeString,
                        CodeRole.Type to c.codeType,
                        CodeRole.Number to c.codeType,
                        CodeRole.Function to c.codeType,
                        CodeRole.Meta to c.codeType,
                        CodeRole.Comment to c.text3,
                        CodeRole.Punctuation to c.text1,
                        CodeRole.Addition to c.success,
                        CodeRole.Deletion to c.text2, // Roles.codeRemoved
                    ),
                text = c.text1,
                gutter = c.text3,
                fill = codeFill(c),
            )
    }
}

/** One displayed line: its text and its offset in the displayed (redacted) source. */
private class CodeLine(val text: String, val start: Int)

/**
 * A code, command or output block (plan 09 §9.2; Dart `KitCodeBlock`, `lib/ui/kit/kit_code_block.dart`).
 *
 * - **Displays** `Redactor.redact(source)` (R18, SEC-2); [onCopy] receives the redacted source,
 *   never the display decorations (prompts, the line cap, break chances).
 * - Code is coloured through [LocalKitCodeTokenizer] on [LocalKitChatWork], once per closed source
 *   (the cache keeps it); an [open] fence is never coloured (plan 09 §3.8), and the first frame of
 *   a closed block is plain until its tokens arrive.
 * - C-006 look: radius 14 on `detailsSurface` (a hairline edge in light), 16 dp insets; with Copy on the first line
 *   the top, end and bottom insets shrink to 4 (end 1, so the glyph's ink sits on the 16 dp rail) and the lines are
 *   centred on the 48 dp target. Copy is an icon ([copyLabel] is its name); Wrap sits under it while a line is wider
 *   than the block (never on a command, as in Flutter). Copy shows whenever [onCopy] is set; without [copyLabel] its
 *   name is the kind's ("Copy code", "Copy command", "Copy output").
 * - Order 08: a header appears only for words ([fileName] or [caption], the [added] / [removed] counts, or
 *   [copyWords] for a labelled Copy on a block of several lines); then Copy and Wrap sit in the header. [lineNumbers]
 *   adds a numbered gutter; [onWrapChanged] hands Wrap to the host. A blank, closed source says "Empty".
 *   [KitCodeFill] is the same block filling a reader (no frame, no cap, find marks).
 * - Capped at [maxLines] lines, then a hairline and a quiet button: with [onMore] it says [moreLabel] (default
 *   "Open full output") and hands over; without, it says "Show all N lines" and unfolds the rest in place.
 *   The transcript passes `maxLines = 12` with [onMore] (Flutter `_KitMdCode`, `kit_markdown.dart:958-969`).
 * - The lines are always left to right (R15), mono reading text at full scale (§9.7); the controls keep the
 *   reading direction's end side. Lines scroll sideways unless [wrap].
 *
 * Text: reading (mono); the copy and more labels are frame text.
 */
@Composable
public fun KitCodeBlock(
    source: String,
    kind: KitCodeKind,
    modifier: Modifier = Modifier,
    language: CodeLanguage = CodeLanguage.Plain,
    open: Boolean = false,
    maxLines: Int = TRANSCRIPT_MAX_LINES,
    wrap: Boolean = false,
    copyLabel: String? = null,
    onCopy: ((String) -> Unit)? = null,
    /** Shown on Copy for a moment after a tap ("Copied"); null keeps [copyLabel]. */
    copiedLabel: String? = null,
    moreLabel: ((hiddenLines: Int) -> String)? = null,
    onMore: (() -> Unit)? = null,
    /** False hides the Wrap toggle (a host that owns wrapping). */
    showWrapToggle: Boolean = true,
    /** A header's file name (middle-cut mono); gives the block a header with Copy and Wrap in it. */
    fileName: String? = null,
    /** A header's caption, when there is no [fileName]. */
    caption: String? = null,
    /** "+N" in the header, in the addition colour. */
    added: Int? = null,
    /** "−M" in the header, in the removed colour. */
    removed: Int? = null,
    /** Words for a labelled Copy button in the header ("Copy commands"), on a block of several lines or with words. */
    copyWords: String? = null,
    /** Line numbers in a gutter (a code block; a command always has its `$` gutter). */
    lineNumbers: Boolean = false,
    /** When set, the host owns wrapping: Wrap reports the next value here instead of flipping it in place. */
    onWrapChanged: ((Boolean) -> Unit)? = null,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    if (source.isBlank() && !open) {
        CodeEmpty(modifier)
        return
    }
    val lines =
        remember(display) {
            // A source ending in a newline draws no empty last line.
            val split = codeSplitLines(display)
            val starts = codeLineStarts(split)
            split.mapIndexed { i, text -> CodeLine(text, starts[i]) }
        }
    val colourable = kind == KitCodeKind.Code && !open && language != CodeLanguage.Plain
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val tokens by
        produceState<ImmutableList<CodeToken>>(
            persistentListOf(),
            display,
            colourable,
            language,
            tokenizer,
        ) {
            value =
                if (colourable) withContext(work) { tokenizer.tokenize(display, language) }
                else persistentListOf()
        }
    val prompts =
        remember(lines, kind) {
            if (kind == KitCodeKind.Command) commandLineStarts(lines.map { it.text }) else null
        }
    val colors = KitCodeColors.of(LocalKitColors.current)
    val look = codeFrameLook()
    // Copy shows whenever there is something to copy to; its name defaults to the kind's ("Copy code").
    val copyName =
        copyLabel
            ?: stringResource(
                when (kind) {
                    KitCodeKind.Code -> Res.string.kitCodeCopyCode
                    KitCodeKind.Command -> Res.string.kitCodeCopyCommand
                    KitCodeKind.Output -> Res.string.kitCodeCopyOutput
                },
            )
    val hasWords = fileName != null || caption != null || added != null || removed != null
    val labelledCopy = onCopy != null && copyWords != null && (hasWords || lines.size > 1)
    if (hasWords || labelledCopy || lineNumbers || onWrapChanged != null) {
        // Order 08 (kit_code_block.dart `_buildHeader`, `_plan`): words put Copy and Wrap in a header row.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            CodeWithHeader(
                display = display,
                lines = lines,
                tokens = tokens,
                prompts = prompts,
                kind = kind,
                modifier = modifier,
                look = look,
                colors = colors,
                maxLines = maxLines,
                wrapParam = wrap,
                copyName = copyName,
                onCopy = onCopy,
                copiedLabel = copiedLabel,
                moreLabel = moreLabel,
                onMore = onMore,
                header = CodeHeaderWords(fileName, caption, added, removed, copyWords?.takeIf { labelledCopy }),
                showWrapToggle = showWrapToggle,
                lineNumbers = lineNumbers,
                onWrapChanged = onWrapChanged,
            )
        }
        return
    }
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val lineStyle = mono.copy(color = colors.text, textDirection = TextDirection.Ltr)
    var expanded by remember(source) { mutableStateOf(false) }
    val hasCap = lines.size > maxLines
    val head = if (hasCap) lines.subList(0, maxLines) else lines
    val tail = if (hasCap) lines.subList(maxLines, lines.size) else emptyList()
    val capped = hasCap && (onMore != null || !expanded)
    var effectiveWrap by remember(source, wrap) { mutableStateOf(wrap) }
    val measurer = rememberTextMeasurer()
    val copying = onCopy != null
    val lineHeight = codeLineHeight(lineStyle)

    BoxWithConstraints(modifier.fillMaxWidth().widthIn(max = 960.dp).codeSurface(look)) {
        // Flutter measures the lines shown now (the head while capped) against the room the trailing controls leave.
        val visible = if (capped) head else lines
        val longest = remember(visible) { visible.maxByOrNull { it.text.length }?.text.orEmpty() }
        val natural = remember(longest, mono) { measurer.measure(longest, mono, maxLines = 1).size.width }
        val density = LocalDensity.current
        val room = with(density) { codeTextRoom(maxWidth.value).dp.toPx() }
        val overflow = showWrapToggle && kind != KitCodeKind.Command && codeOverflows(natural.toFloat(), room)
        val inline = copying || overflow
        val (insetTop, insetBottom) = codeLineInsets(lineHeight)
        val padding =
            if (inline) {
                PaddingValues(
                    start = Space.S4.dp,
                    top = Space.S1.dp,
                    end = (if (copying) CODE_COPY_END else Space.S1).dp,
                    bottom = Space.S1.dp,
                )
            } else {
                PaddingValues(Space.S4.dp)
            }
        val rows =
            @Composable { part: List<CodeLine>, from: Int ->
                Column {
                    part.forEachIndexed { i, line ->
                        Row {
                            if (prompts != null) {
                                DisableSelection {
                                    BasicText(
                                        if (prompts[from + i]) "$" else "",
                                        Modifier.width(Space.S4.dp),
                                        style = lineStyle.copy(color = colors.gutter, textAlign = TextAlign.Start),
                                    )
                                }
                            }
                            BasicText(
                                text = lineText(line, tokens, colors),
                                style = lineStyle,
                                softWrap = effectiveWrap,
                            )
                        }
                    }
                }
            }
        val body =
            @Composable {
                Column {
                    rows(head, 0)
                    if (hasCap && onMore == null) KitReveal(visible = expanded) { rows(tail, head.size) }
                }
            }
        Row(Modifier.padding(padding), verticalAlignment = Alignment.Top) {
            // The lines, then the cap, in one left-to-right column beside the controls (`_buildBounded`): the
            // hairline and the button end where the text does, before the Copy column.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.weight(1f)) {
                    Box(
                        // Without Copy or Wrap there is nothing to touch: the block is its lines plus the padding.
                        if (inline) Modifier.padding(top = insetTop.dp, bottom = insetBottom.dp)
                        else Modifier
                    ) {
                        if (effectiveWrap) Box(Modifier.fillMaxWidth()) { body() }
                        else CodeOverflow(colors.fill, colors.gutter) { body() }
                    }
                    if (capped) {
                        DisableSelection {
                            Box(Modifier.fillMaxWidth().height(Space.S3.dp), contentAlignment = Alignment.Center) {
                                KitDivider()
                            }
                            val label =
                                when {
                                    onMore != null && moreLabel != null -> moreLabel(tail.size)
                                    onMore != null -> stringResource(Res.string.kitCodeOpenFull)
                                    else -> pluralStringResource(Res.plurals.kitCodeShowAll, lines.size, lines.size)
                                }
                            KitButton(
                                label = label,
                                onClick = onMore ?: { expanded = true },
                                role = KitButtonRole.Tertiary,
                                // Flutter's KitButton.tertiary keeps its 8 dp side inset (kit_code_block.dart:1046).
                                modifier = Modifier.padding(start = Space.S2.dp).testTag(CODE_MORE_TAG),
                            )
                        }
                    }
                }
            }
            if (inline) {
                // A real gap before the controls, so a long line's faded end never meets the glyphs.
                Spacer(Modifier.width(Space.S3.dp))
                CodeControls(
                    copyLabel = copyName,
                    copiedLabel = copiedLabel,
                    onCopy = onCopy?.let { copy -> { copy(display) } },
                    wrapOn = if (overflow) effectiveWrap else null,
                    onWrap = if (overflow) ({ effectiveWrap = !effectiveWrap }) else null,
                )
            }
        }
    }
}

/** Plan 09 §9.2: code and output in the transcript stop at 12 lines. */
public const val TRANSCRIPT_MAX_LINES: Int = 12

/** The "Open full output" / "Show all N lines" button under a capped block, for tests. */
public const val CODE_MORE_TAG: String = "kit-code-more"

/**
 * [line] with the styles of the [tokens] that cross it (tokens are offsets into the whole
 * displayed source).
 */
internal fun lineText(
    line: String,
    lineStart: Int,
    tokens: List<CodeToken>,
    colors: KitCodeColors,
): AnnotatedString {
    // Compose's native line breaking works on the source. No synthetic zero-width spaces reach
    // selection/clipboard.
    if (tokens.isEmpty()) return AnnotatedString(line)
    val end = lineStart + line.length
    return buildAnnotatedString {
        var cursor = lineStart
        for (t in tokens) {
            if (t.end <= lineStart) continue
            if (t.start >= end) break
            val from = maxOf(t.start, cursor)
            val to = minOf(t.end, end)
            if (to <= from) continue
            if (from > cursor) append(line.substring(cursor - lineStart, from - lineStart))
            withStyle(colors.span(t.role)) { append(line.substring(from - lineStart, to - lineStart)) }
            cursor = to
        }
        if (cursor < end) append(line.substring(cursor - lineStart))
    }
}

private fun lineText(line: CodeLine, tokens: List<CodeToken>, colors: KitCodeColors): AnnotatedString =
    lineText(line.text, line.start, tokens, colors)

/** One trailing newline trimmed, then the lines (Dart `_splitLines`): a source ending in a newline draws no empty last line. */
internal fun codeSplitLines(text: String): List<String> = text.removeSuffix("\n").split('\n')

/** Each line's start offset into the joined text (Dart `_lineStarts`). */
internal fun codeLineStarts(lines: List<String>): List<Int> {
    var cursor = 0
    return lines.map { line -> cursor.also { cursor += line.length + 1 } }
}

/** The glyph size of Copy and Wrap (`AppIconography.actionSize`). */
private const val ACTION_GLYPH = 24f // F: lib/ui/app_iconography.dart:20

/** How far the copy glyph's ink stands in from the end of its square (`KitOptical._ink` copy). */
private const val COPY_INK_END = .125f // F: lib/ui/kit/kit_optical.dart:32

/**
 * The end inset of a block whose Copy trails its first line, worked out as Flutter does (`KitOptical.endPad`,
 * kit_code_block.dart:608-613): the Copy glyph's ink ends `space4` inside the block. Equals [CODE_COPY_END] (1 dp).
 */
internal fun codeCopyEnd(): Float =
    maxOf(0f, Space.S4 - (FlutterKitTokens.MIN_TARGET - ACTION_GLYPH) / 2f - ACTION_GLYPH * COPY_INK_END)

/** Where a bounded block puts Copy and whether it offers Wrap (Dart `_CodePlan`). */
internal data class CodePlan(val inline: Boolean, val wrapToggle: Boolean)

/**
 * Dart `_plan` (R3): with no words for a header ([hasWords], [labelledCopy]) the controls trail the first line, and
 * Wrap is offered only while the longest visible line ([longest], in dp) is wider than the text's room in a block of
 * [maxWidth] dp, less a gutter of [gutter] dp when the block has one.
 */
internal fun codePlan(
    maxWidth: Float,
    hasWords: Boolean,
    labelledCopy: Boolean,
    copyable: Boolean,
    offersWrap: Boolean,
    longest: Float,
    gutter: Float?,
): CodePlan {
    if (!maxWidth.isFinite()) return CodePlan(inline = false, wrapToggle = false)
    val trailing = !hasWords && !labelledCopy
    var overflows = false
    if (offersWrap) {
        var room = if (trailing) codeTextRoom(maxWidth) else maxWidth - Space.S4 * 2 // F: kit_code_block.dart:717-723
        if (gutter != null) room -= gutter + Space.S2
        overflows = codeOverflows(longest, room) // F: lib/ui/kit/kit_code_block.dart:727
    }
    return CodePlan(inline = trailing && (copyable || overflows), wrapToggle = overflows)
}

/** The empty block (Dart `_empty`): "Empty" in tertiary mono, `space4` inside the frame, as wide as its words. */
@Composable
private fun CodeEmpty(modifier: Modifier) {
    Box(modifier.widthIn(max = 960.dp).codeSurface(codeFrameLook()).padding(Space.S4.dp)) {
        KitText(stringResource(Res.string.kitCodeEmpty), role = TypeRole.Mono, tone = TextTone.Tertiary)
    }
}

/** The words a header row carries (order 08): a file name or caption, the counts, and a labelled Copy's words. */
private class CodeHeaderWords(
    val fileName: String?,
    val caption: String?,
    val added: Int?,
    val removed: Int?,
    val copyWords: String?,
) {
    val hasWords: Boolean get() = fileName != null || caption != null || added != null || removed != null
}

/**
 * Order 08's bounded block (Dart `_buildBounded` with `_buildHeader`), used when the block has words for a header, a
 * labelled Copy, line numbers or a host that owns wrapping. It shares the chat block's frame, colours and cap; Copy
 * and Wrap sit in the header row (or trail the first line when there are only line numbers).
 */
@Composable
private fun CodeWithHeader(
    display: String,
    lines: List<CodeLine>,
    tokens: List<CodeToken>,
    prompts: List<Boolean>?,
    kind: KitCodeKind,
    modifier: Modifier,
    look: CodeFrameLook,
    colors: KitCodeColors,
    maxLines: Int,
    wrapParam: Boolean,
    copyName: String,
    onCopy: ((String) -> Unit)?,
    copiedLabel: String?,
    moreLabel: ((Int) -> String)?,
    onMore: (() -> Unit)?,
    header: CodeHeaderWords,
    showWrapToggle: Boolean,
    lineNumbers: Boolean,
    onWrapChanged: ((Boolean) -> Unit)?,
) {
    val mono = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text, textDirection = TextDirection.Ltr)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var expanded by remember(display) { mutableStateOf(false) }
    var wrapOwn by remember(display, wrapParam) { mutableStateOf(wrapParam) }
    val wrap = if (kind == KitCodeKind.Command) wrapParam else wrapOwn
    val hasCap = lines.size > maxLines
    val stillCapped = hasCap && (onMore != null || !expanded)
    val visible = if (stillCapped) lines.subList(0, maxLines) else lines
    val copyable = onCopy != null
    val labelledCopy = header.copyWords != null
    val hasGutter = kind == KitCodeKind.Command || lineNumbers
    val gutterWidth =
        if (!hasGutter) 0f
        else
            remember(mono, lines.size, kind) {
                val sample = if (kind == KitCodeKind.Command) "$" else "9".repeat(lines.size.toString().length)
                with(density) { measurer.measure(sample, mono).size.width.toDp().value }
            }
    val longest =
        remember(visible, mono) {
            val text = visible.maxByOrNull { it.text.length }?.text.orEmpty()
            if (text.isEmpty()) 0f else with(density) { measurer.measure(text, mono, maxLines = 1).size.width.toDp().value }
        }
    val copy = { onCopy?.invoke(display) ?: Unit }
    val toggleWrap = {
        val next = !wrap
        if (onWrapChanged != null) onWrapChanged(next) else wrapOwn = next
    }

    // The list cap first, then the room under it (fill first would let the block outgrow the cap on a tablet).
    BoxWithConstraints(modifier.widthIn(max = 960.dp).fillMaxWidth()) {
        val plan =
            codePlan(
                maxWidth = maxWidth.value,
                hasWords = header.hasWords,
                labelledCopy = labelledCopy,
                copyable = copyable,
                offersWrap = showWrapToggle && kind != KitCodeKind.Command,
                longest = longest,
                gutter = if (hasGutter) gutterWidth else null,
            )
        val showCopy = copyable && !plan.inline
        val headerWrap = plan.wrapToggle && !plan.inline
        val hasHeader = header.hasWords || headerWrap || showCopy
        val padding =
            when {
                plan.inline ->
                    Modifier.padding(
                        start = Space.S4.dp,
                        top = Space.S1.dp,
                        end = if (copyable) CODE_COPY_END.dp else Space.S1.dp,
                        bottom = Space.S1.dp,
                    ) // F: lib/ui/kit/kit_code_block.dart:614-620
                hasHeader && (plan.wrapToggle || (copyable && !labelledCopy)) ->
                    Modifier.padding(start = Space.S4.dp, top = Space.S1.dp, end = Space.S1.dp, bottom = Space.S4.dp)
                else -> Modifier.padding(Space.S4.dp)
            }
        Column(Modifier.fillMaxWidth().codeSurface(look).then(padding)) {
            if (hasHeader) {
                CodeHeader(
                    header, mono, colors,
                    wrapToggle = if (headerWrap) ({ CodeWrapToggle(wrap, toggleWrap) }) else null,
                    copy =
                        if (!showCopy) null
                        else if (labelledCopy) ({ m: Modifier -> CodeCopyButton(header.copyWords!!, copy, m) })
                        else ({ m: Modifier -> CodeCopyIcon(copyName, copiedLabel, copy, m) }),
                    labelledCopy = showCopy && labelledCopy,
                )
                Spacer(Modifier.height(Space.S2.dp))
            }
            val lineHeight = codeLineHeight(mono)
            val body =
                @Composable {
                    CodeHeaderBody(
                        lines = lines,
                        visibleCount = if (hasCap) maxLines else lines.size,
                        expanded = expanded && onMore == null,
                        tokens = tokens,
                        prompts = prompts,
                        lineNumbers = hasGutter && kind != KitCodeKind.Command,
                        gutterWidth = gutterWidth.dp,
                        wrap = wrap,
                        mono = mono,
                        colors = colors,
                        inline = plan.inline,
                        lineHeight = lineHeight,
                        cap =
                            when {
                                !stillCapped -> null
                                onMore != null ->
                                    (moreLabel?.invoke(lines.size - maxLines) ?: stringResource(Res.string.kitCodeOpenFull)) to onMore
                                else ->
                                    pluralStringResource(Res.plurals.kitCodeShowAll, lines.size, lines.size) to { expanded = true }
                            },
                    )
                }
            if (plan.inline) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { body() }
                    Spacer(Modifier.width(Space.S3.dp)) // F: lib/ui/kit/kit_code_block.dart:576
                    DisableSelection {
                        Column {
                            if (copyable) CodeCopyIcon(copyName, copiedLabel, copy)
                            if (plan.wrapToggle) CodeWrapToggle(wrap, toggleWrap)
                        }
                    }
                }
            } else {
                body()
            }
        }
    }
}

/**
 * The header (Dart `_buildHeader`): the file name (middle-cut mono) or caption, then the counts, taking every width
 * the controls leave; then Wrap and Copy at the end, `space2` apart. A labelled Copy beside a name is capped at 60 %
 * of the row so its words can wrap without pushing the name off the edge.
 */
@Composable
private fun CodeHeader(
    words: CodeHeaderWords,
    mono: TextStyle,
    colors: KitCodeColors,
    wrapToggle: (@Composable () -> Unit)?,
    copy: (@Composable (Modifier) -> Unit)?,
    labelledCopy: Boolean,
) {
    val leading = words.hasWords
    val trailing = listOfNotNull<@Composable (Modifier) -> Unit>(wrapToggle?.let { w -> { _: Modifier -> w() } }, copy)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rowWidth = maxWidth
        DisableSelection {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (words.fileName != null) {
                            KitMonoText(words.fileName, Modifier.weight(1f, fill = false), cut = KitMonoCut.Middle, maxLines = 1)
                        } else if (words.caption != null) {
                            Box(Modifier.weight(1f, fill = false)) { KitText(words.caption, role = TypeRole.Secondary) }
                        }
                        if (words.added != null || words.removed != null) {
                            CodeCounts(
                                words.added, words.removed, mono, colors,
                                if (words.fileName != null || words.caption != null) Modifier.padding(start = Space.S2.dp) else Modifier,
                            )
                        }
                    }
                }
                trailing.forEachIndexed { i, item ->
                    if (i > 0 || leading) Spacer(Modifier.width(Space.S2.dp))
                    if (labelledCopy && i == trailing.lastIndex) {
                        if (leading) item(Modifier.widthIn(max = rowWidth * .6f)) // F: lib/ui/kit/kit_code_block.dart:908
                        else Box(Modifier.weight(1f, fill = false)) { item(Modifier) }
                    } else {
                        item(Modifier)
                    }
                }
            }
        }
    }
}

/** "+4 −1" in mono, the added count in `success` and the removed in `codeRemoved`; spoken as "4 added, 1 removed". */
@Composable
private fun CodeCounts(added: Int?, removed: Int?, mono: TextStyle, colors: KitCodeColors, modifier: Modifier) {
    val spoken = stringResource(Res.string.kitCodeChanges, added ?: 0, removed ?: 0)
    val body = LocalKitTypography.current.style(TypeRole.Body).toSpanStyle()
    val text =
        buildAnnotatedString {
            if (added != null) withStyle(SpanStyle(color = colors[CodeRole.Addition])) { append("+$added") }
            // Flutter's gap is an unstyled span: the page's own words, not the mono face.
            if (added != null && removed != null) withStyle(body) { append(" ") }
            if (removed != null) withStyle(SpanStyle(color = colors[CodeRole.Deletion])) { append("−$removed") }
        }
    BasicText(
        text,
        modifier.clearAndSetSemantics { contentDescription = spoken },
        style = mono,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The header block's body: the head lines; the tail that unfolds in place after "Show all"; one sideways scroller for
 * every line unless they wrap; at least one 48 dp target tall, or, with Copy on the first line, padded so that line
 * centres on the Copy target. A capped block ends in a hairline and its [cap] button.
 */
@Composable
private fun CodeHeaderBody(
    lines: List<CodeLine>,
    visibleCount: Int,
    expanded: Boolean,
    tokens: List<CodeToken>,
    prompts: List<Boolean>?,
    lineNumbers: Boolean,
    gutterWidth: Dp,
    wrap: Boolean,
    mono: TextStyle,
    colors: KitCodeColors,
    inline: Boolean,
    lineHeight: Float,
    cap: Pair<String, () -> Unit>?,
) {
    val head = 0 until minOf(visibleCount, lines.size)
    val tail = visibleCount until lines.size
    val linesColumn =
        @Composable {
            Column {
                CodeGutterRows(lines, head, tokens, prompts, lineNumbers, gutterWidth, wrap, mono, colors)
                if (!tail.isEmpty()) {
                    KitReveal(visible = expanded) {
                        CodeGutterRows(lines, tail, tokens, prompts, lineNumbers, gutterWidth, wrap, mono, colors)
                    }
                }
            }
        }
    val scrolled = @Composable { if (wrap) linesColumn() else CodeOverflow(colors.fill, colors.gutter) { linesColumn() } }
    Column {
        if (!inline) {
            Box(Modifier.heightIn(min = FlutterKitTokens.MIN_TARGET.dp)) { scrolled() }
        } else {
            val (top, bottom) = codeLineInsets(lineHeight)
            Box(Modifier.padding(top = top.dp, bottom = bottom.dp)) { scrolled() }
        }
        if (cap != null) {
            DisableSelection {
                Box(Modifier.fillMaxWidth().height(Space.S3.dp), contentAlignment = Alignment.Center) { KitDivider() }
                KitButton(
                    label = cap.first,
                    onClick = cap.second,
                    role = KitButtonRole.Tertiary,
                    // Flutter's KitButton.tertiary keeps its 8 dp side inset (kit_code_block.dart:1046).
                    modifier = Modifier.padding(start = Space.S2.dp).testTag(CODE_MORE_TAG),
                )
            }
        }
    }
}

/**
 * The lines in [range]. Without a gutter every line joins one paragraph; with one, each line is a row: the `$` prompt
 * (only where a command starts) or the line number in `text3`, end-aligned in the gutter, `space2`, then the line.
 */
@Composable
private fun CodeGutterRows(
    lines: List<CodeLine>,
    range: IntRange,
    tokens: List<CodeToken>,
    prompts: List<Boolean>?,
    lineNumbers: Boolean,
    gutterWidth: Dp,
    wrap: Boolean,
    mono: TextStyle,
    colors: KitCodeColors,
) {
    if (prompts == null && !lineNumbers) {
        val text =
            remember(lines, range, tokens, colors) {
                buildAnnotatedString {
                    for (i in range) {
                        if (i > range.first) append('\n')
                        append(lineText(lines[i], tokens, colors))
                    }
                }
            }
        BasicText(text, style = mono, softWrap = wrap, overflow = TextOverflow.Clip)
        return
    }
    Column {
        for (i in range) {
            val label = if (prompts != null) (if (prompts[i]) "$" else "") else (i + 1).toString()
            Row(verticalAlignment = Alignment.Top) {
                DisableSelection {
                    BasicText(
                        label,
                        Modifier.width(gutterWidth),
                        style = mono.copy(color = colors.gutter, textAlign = TextAlign.End),
                    )
                }
                Spacer(Modifier.width(Space.S2.dp))
                BasicText(
                    lineText(lines[i], tokens, colors),
                    if (wrap) Modifier.weight(1f) else Modifier,
                    style = mono,
                    softWrap = wrap,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * The same block filling its host (Dart `KitCodeBlock.fill`): no frame, no cap, line numbers by default, one lazy row
 * per line at the mono line height, for readers. [marks] (ranges into the redacted text, end exclusive) are find
 * matches painted in `accent` at 18 %, the [activeMark] at 38 % and scrolled into view. Lines wider than the host
 * scroll sideways together.
 *
 * Text: reading (mono). Cap: none (fills its host).
 */
@Composable
public fun KitCodeFill(
    text: String,
    modifier: Modifier = Modifier,
    kind: KitCodeKind = KitCodeKind.Code,
    language: CodeLanguage = CodeLanguage.Plain,
    wrap: Boolean = false,
    highlight: Boolean = true,
    lineNumbers: Boolean = true,
    marks: List<IntRange> = emptyList(),
    activeMark: Int? = null,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(text, redactor) { redactor.redact(text) }
    val colourable = highlight && kind == KitCodeKind.Code && language != CodeLanguage.Plain
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val tokens by
        produceState<ImmutableList<CodeToken>>(persistentListOf(), display, colourable, language, tokenizer) {
            value =
                if (colourable) withContext(work) { tokenizer.tokenize(display, language) }
                else persistentListOf()
        }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (text.isBlank()) {
            Box(modifier.padding(Space.S3.dp)) {
                KitText(stringResource(Res.string.kitCodeEmpty), role = TypeRole.Mono, tone = TextTone.Tertiary)
            }
            return@CompositionLocalProvider
        }
        val lines = remember(display) { codeSplitLines(display) }
        val starts = remember(lines) { codeLineStarts(lines) }
        val colors = KitCodeColors.of(LocalKitColors.current)
        val accent = LocalKitColors.current.accent
        val mono = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text, textDirection = TextDirection.Ltr)
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val prompts = remember(lines, kind) { if (kind == KitCodeKind.Command) commandLineStarts(lines) else null }
        val hasGutter = kind == KitCodeKind.Command || lineNumbers
        val gutter =
            if (!hasGutter) 0f
            else
                remember(mono, lines.size, kind) {
                    val sample = if (kind == KitCodeKind.Command) "$" else "9".repeat(lines.size.toString().length)
                    with(density) { measurer.measure(sample, mono).size.width.toDp().value }
                }
        val extent = codeLineHeight(mono).dp
        val state = rememberLazyListState()
        LaunchedEffect(activeMark) {
            val at = activeMark?.let { marks.getOrNull(it) }?.first ?: return@LaunchedEffect
            val line = starts.indexOfLast { it <= at }.coerceAtLeast(0)
            state.animateScrollToItem(line)
        }
        val natural =
            remember(lines, mono, gutter) {
                val longest = lines.maxByOrNull { it.length }.orEmpty()
                val w = with(density) { measurer.measure(longest, mono, maxLines = 1).size.width.toDp().value }
                kotlin.math.ceil(w + if (hasGutter) gutter + Space.S2 else 0f) + 1f // F: lib/ui/kit/kit_code_block.dart:1116-1119
            }
        val list =
            @Composable { m: Modifier ->
                SelectionContainer(m) {
                    LazyColumn(Modifier.fillMaxHeight(), state = state) {
                        itemsIndexed(lines) { i, line ->
                            val shown =
                                remember(line, i, tokens, marks, activeMark, colors) {
                                    markLine(lineText(line, starts[i], tokens, colors), starts[i], marks, activeMark, accent)
                                }
                            Row(
                                (if (wrap) Modifier else Modifier.height(extent)).testTag("kit-code-line-${i + 1}"),
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (hasGutter) {
                                    DisableSelection {
                                        BasicText(
                                            if (prompts != null) (if (prompts[i]) "$" else "") else (i + 1).toString(),
                                            Modifier.width(gutter.dp),
                                            style = mono.copy(color = colors.gutter, textAlign = TextAlign.End),
                                        )
                                    }
                                    Spacer(Modifier.width(Space.S2.dp))
                                }
                                BasicText(
                                    shown,
                                    if (wrap) Modifier.weight(1f) else Modifier,
                                    style = mono,
                                    softWrap = wrap,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        if (wrap) {
            list(modifier)
            return@CompositionLocalProvider
        }
        BoxWithConstraints(modifier) {
            if (natural <= maxWidth.value) {
                list(Modifier.fillMaxWidth())
            } else {
                Box(Modifier.clipToBounds().horizontalScroll(rememberScrollState())) {
                    list(Modifier.width(natural.dp).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * [line] (starting at [lineStart] in the joined text) with the [marks] that cross it painted on top: `accent` at 18 %,
 * the [active] one at 38 % (Dart `_paintMarks`).
 */
internal fun markLine(line: AnnotatedString, lineStart: Int, marks: List<IntRange>, active: Int?, accent: Color): AnnotatedString {
    if (marks.isEmpty()) return line
    val lineEnd = lineStart + line.length
    val b = AnnotatedString.Builder(line)
    marks.forEachIndexed { i, mark ->
        val start = maxOf(mark.first, lineStart)
        val end = minOf(mark.last + 1, lineEnd)
        if (start >= end) return@forEachIndexed
        val alpha = if (i == active) .38f else .18f // F: lib/ui/kit/kit_code_block.dart:1366
        b.addStyle(SpanStyle(background = accent.copy(alpha = alpha)), start - lineStart, end - lineStart)
    }
    return b.toAnnotatedString()
}
