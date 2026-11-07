package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
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
 *   than the block (never on a command, as in Flutter).
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
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    val lines =
        remember(display) {
            var at = 0
            val raw = display.split('\n')
            // A source ending in a newline draws no empty last line.
            val kept = if (raw.size > 1 && raw.last().isEmpty()) raw.dropLast(1) else raw
            kept.map { CodeLine(it, at).also { _ -> at += it.length + 1 } }
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
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val lineStyle = mono.copy(color = colors.text, textDirection = TextDirection.Ltr)
    var expanded by remember(source) { mutableStateOf(false) }
    val hasCap = lines.size > maxLines
    val head = if (hasCap) lines.subList(0, maxLines) else lines
    val tail = if (hasCap) lines.subList(maxLines, lines.size) else emptyList()
    val capped = hasCap && (onMore != null || !expanded)
    var effectiveWrap by remember(source, wrap) { mutableStateOf(wrap) }
    val measurer = rememberTextMeasurer()
    val copying = copyLabel != null && onCopy != null
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
                        if (inline) Modifier.padding(top = insetTop.dp, bottom = insetBottom.dp)
                        else Modifier.heightIn(min = FlutterKitTokens.MIN_TARGET.dp)
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
                                modifier = Modifier.testTag(CODE_MORE_TAG),
                            )
                        }
                    }
                }
            }
            if (inline) {
                // A real gap before the controls, so a long line's faded end never meets the glyphs.
                Spacer(Modifier.width(Space.S3.dp))
                CodeControls(
                    copyLabel = copyLabel,
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
