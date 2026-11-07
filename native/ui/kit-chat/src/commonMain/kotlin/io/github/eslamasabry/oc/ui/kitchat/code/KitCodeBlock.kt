package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
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
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokenRoles
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCodeChanges
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCode
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCommand
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyOutput
import io.github.eslamasabry.oc.ui.words.res.kitCodeEmpty
import io.github.eslamasabry.oc.ui.words.res.kitCodeShowAll
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
 * The colour of each code role in the current theme (opaque, B-KIT-041), Flutter's `KitCodeHighlight._rawStyleFor`:
 * keywords, built-ins, literals and tags in `codeKeyword`; strings in `codeString`; numbers, names, titles, functions
 * and meta in `codeType`; comments in `text3`; additions in `success`; deletions in `codeRemoved`; everything else
 * (punctuation, plain names) in the block's own text colour.
 */
@Immutable
public data class KitCodeColors(
    val roles: Map<CodeRole, Color>,
    val text: Color,
    val gutter: Color,
    val fill: Color,
) {
    public operator fun get(role: CodeRole): Color = roles[role] ?: text

    /** The span style of [role]: its colour, keywords in semibold and comments in italic (kit_code_block.dart:208-236). */
    public fun span(role: CodeRole): SpanStyle =
        when (role) {
            CodeRole.Keyword -> SpanStyle(color = get(role), fontWeight = FontWeight.W600) // F: lib/ui/kit/kit_code_block.dart:212
            CodeRole.Comment -> SpanStyle(color = get(role), fontStyle = FontStyle.Italic)
            else -> SpanStyle(color = get(role))
        }

    public companion object {
        public fun of(c: KitColors): KitCodeColors =
            KitCodeColors(
                roles =
                    mapOf(
                        CodeRole.Keyword to c.codeKeyword,
                        CodeRole.String to c.codeString,
                        CodeRole.Type to c.codeType,
                        CodeRole.Number to c.codeType,
                        // SyntaxMP marks every call a function; highlight.js marks none in a call, so calls
                        // stay in the text colour ("connect(", "notifyListeners()" in the Flutter goldens).
                        CodeRole.Function to c.text1,
                        CodeRole.Comment to c.text3,
                        CodeRole.Meta to c.codeType,
                        CodeRole.Addition to c.success,
                        // `codeRemoved` is `text2` in every pack (theme_roles.dart).
                        CodeRole.Deletion to c.text2,
                    ),
                text = c.text1,
                gutter = c.text3,
                fill = c.surface2,
            )
    }
}

/** Plan 09 §9.2: code and output in the transcript stop at 12 lines (Dart `maxLines = 12`). */
public const val TRANSCRIPT_MAX_LINES: Int = 12 // F: lib/ui/kit/kit_code_block.dart:260

/**
 * P-001: whether a block's lines need sideways room (and so its Wrap toggle): the [longest] line is wider than the
 * [room], or the [first] line is wider than what the Copy corner leaves of it ([copyReserve]). All in pixels.
 */
internal fun codeOverflows(longest: Int, first: Int, room: Int, copyReserve: Int): Boolean =
    longest > room || first > room - copyReserve

/** The glyph size of Copy and Wrap (`AppIconography.actionSize`). */
private const val ACTION_GLYPH = 24f // F: lib/ui/app_iconography.dart:20

/** How far the copy glyph's ink stands in from the end of its square (`KitOptical._ink` copy). */
private const val COPY_INK_END = .125f // F: lib/ui/kit/kit_optical.dart:32

/**
 * The end inset of a block whose Copy trails its first line: the Copy glyph's ink ends `space4` inside the block, the
 * inset its text starts at (`KitOptical.endPad`, kit_code_block.dart:608-613): 1 dp.
 */
internal fun codeCopyEnd(): Float =
    maxOf(0f, Space.S4 - (FlutterKitTokens.MIN_TARGET - ACTION_GLYPH) / 2f - ACTION_GLYPH * COPY_INK_END) // F: lib/ui/kit/kit_code_block.dart:612

/** One line of [text] with one trailing newline trimmed first (Dart `_splitLines`). */
internal fun codeSplitLines(text: String): List<String> = text.removeSuffix("\n").split('\n')

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
        var room =
            if (trailing) maxWidth - Space.S4 - Space.S1 - FlutterKitTokens.MIN_TARGET - Space.S3
            else maxWidth - Space.S4 * 2 // F: lib/ui/kit/kit_code_block.dart:717-723
        if (gutter != null) room -= gutter + Space.S2
        overflows = longest > room + .5f // F: lib/ui/kit/kit_code_block.dart:727
    }
    return CodePlan(inline = trailing && (copyable || overflows), wrapToggle = overflows)
}

/** The block's ground: `detailsSurface`, the page's ground in light and `surface1` in dark. */
@Composable
private fun codeDetailsSurface(): Color = Color(FlutterKitTokenRoles.detailsSurface(LocalKitRoles.current).v)

/**
 * The block's frame (Dart `kitCodeSurface` with `_blockShape`): the `code` role on `detailsSurface` with the code
 * radius. In light, Solid adds a hairline of `text1` at 16 %: the light block is the page's own ground and would
 * otherwise have no edge; every other style draws its own edge.
 */
@Composable
private fun Modifier.codeSurface(fill: Color): Modifier {
    val colors = LocalKitColors.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Code,
                KitShapeId.Code,
                if (colors.dark) SurfaceLevel.Surface1 else SurfaceLevel.Ground,
                ownFill = fill,
            ),
        )
    val edge =
        if (!colors.dark && LocalKitStyle.current.style == Style.Solid) {
            // A hairline thinner than a pixel stays that thin (Compose's border rounds it up to a whole pixel).
            val width = kitHairline()
            val ink = colors.text1.copy(alpha = .16f) // F: lib/ui/kit/kit_code_block.dart:362
            val shape = look.shape
            // Flutter strokes a shape's side inside its edge: the line's centre lies half a hairline in.
            Modifier.drawWithContent {
                drawContent()
                val w = width.toPx()
                translate(w / 2f, w / 2f) { // NV: half the stroke, so it lies inside the edge as Flutter strokes a side
                    val inner = Size(size.width - w, size.height - w)
                    drawOutline(shape.createOutline(inner, layoutDirection, this), ink, style = Stroke(w))
                }
            }
        } else {
            Modifier
        }
    return this.then(look.modifier).then(edge)
}

/** [line] with the colours of its [tokens] (offsets into the line), in the block's mono style. */
private fun lineText(line: String, tokens: List<CodeToken>, colors: KitCodeColors): AnnotatedString {
    // The drawn text is the source: no synthetic break marks reach selection or the clipboard.
    fun piece(s: String) = s
    if (tokens.isEmpty()) return AnnotatedString(piece(line))
    return buildAnnotatedString {
        var cursor = 0
        for (t in tokens) {
            if (t.end <= cursor) continue
            if (t.start >= line.length) break
            val from = maxOf(t.start, cursor)
            val to = minOf(t.end, line.length)
            if (from > cursor) append(line.substring(cursor, from))
            withStyle(colors.span(t.role)) { append(line.substring(from, to)) }
            cursor = to
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

/**
 * Each line's tokens: Flutter colours a block one line at a time (`KitCodeHighlight.spans(line, …)`,
 * kit_code_block.dart `_markedLineSpan`), so a line's colours never depend on the lines around it. Through the
 * cached [LocalKitCodeTokenizer]; empty when [colourable] is false.
 */
@Composable
private fun lineTokens(lines: List<String>, language: CodeLanguage, colourable: Boolean): List<List<CodeToken>> {
    val tokenizer = LocalKitCodeTokenizer.current
    return remember(lines, language, colourable, tokenizer) {
        if (!colourable) lines.map { emptyList() } else lines.map { highlightJsRoles(it, tokenizer.tokenize(it, language)) }
    }
}

/** The words after which highlight.js names a type as a class title (`title.class`, coloured `codeType`). */
private val TITLE_KEYWORDS = setOf("class", "extends", "implements", "with", "interface", "enum", "mixin", "struct", "trait", "object")

/**
 * SyntaxMP's roles brought to what Flutter's grammars (highlight.js) colour: a type is coloured only as a class title
 * (after `class`, `extends`, …; a type in an annotation such as "Future" or "Profile" stays plain), and `void` is a
 * keyword. kit_code_block.dart:208-236 colours `title`/`class` in `codeType` and keywords and built-ins in
 * `codeKeyword`; highlight.js marks no bare type reference.
 */
internal fun highlightJsRoles(line: String, tokens: List<CodeToken>): List<CodeToken> {
    var lastWord: String? = null
    return tokens.mapNotNull { t ->
        val word = line.substring(t.start, t.end)
        val out =
            when {
                t.role == CodeRole.Type && word == "void" -> t.copy(role = CodeRole.Keyword)
                t.role == CodeRole.Type && lastWord !in TITLE_KEYWORDS -> null
                else -> t
            }
        if (t.role != CodeRole.Punctuation) lastWord = word
        out
    }
}

/**
 * A code, command or output block (plan 09 §9.2; Dart `KitCodeBlock`, kit_code_block.dart).
 *
 * - **Displays** `Redactor.redact(source)` (R18, SEC-2); [onCopy] receives the redacted source, never the display
 *   decorations (prompts, the line cap).
 * - A header appears only for words: [fileName] or [caption], the [added] / [removed] counts, or a caller's
 *   [copyWords] on a block of several lines ("Copy commands", a labelled button). Without words Copy sits at the end
 *   of the first line, centred on it, with Wrap under it (R3). Wrap is offered only while a line is wider than the
 *   block, and shows its on state as an accent glyph.
 * - Code is coloured one line at a time through [LocalKitCodeTokenizer]; an [open] fence is never coloured.
 * - Capped at [maxLines]: "Show all N lines" unfolds the rest in place, or, with [moreLabel] and [onMore], one tap
 *   opens the full output elsewhere.
 * - Always left to right (R15), mono reading text at full scale (§9.7). Lines scroll sideways together unless they
 *   wrap.
 *
 * [copyLabel] names the Copy icon (its default is "Copy code", "Copy command" or "Copy output"); Copy shows only with
 * [onCopy].
 *
 * Text: reading (mono); the copy and more labels are frame text. Cap: List.
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
    fileName: String? = null,
    caption: String? = null,
    added: Int? = null,
    removed: Int? = null,
    copyWords: String? = null,
    showWrapToggle: Boolean = true,
    lineNumbers: Boolean = false,
    onWrapChanged: ((Boolean) -> Unit)? = null,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    val fill = codeDetailsSurface()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (source.isBlank()) {
            CodeEmpty(modifier, fill)
            return@CompositionLocalProvider
        }
        CodeBounded(
            display, kind, modifier, language, open, maxLines, wrap, copyLabel, onCopy, copiedLabel, moreLabel, onMore,
            fileName, caption, added, removed, copyWords, showWrapToggle, lineNumbers, onWrapChanged, fill,
        )
    }
}

/** The empty block (Dart `_empty`): "Empty" in tertiary mono, `space4` inside the frame, as wide as its words. */
@Composable
private fun CodeEmpty(modifier: Modifier, fill: Color) {
    Box(modifier.kitMaxWidth(CapRole.List).codeSurface(fill).padding(Space.S4.dp)) {
        KitText(stringResource(Res.string.kitCodeEmpty), role = TypeRole.Mono, tone = TextTone.Tertiary)
    }
}

@Composable
private fun CodeBounded(
    display: String,
    kind: KitCodeKind,
    modifier: Modifier,
    language: CodeLanguage,
    open: Boolean,
    maxLines: Int,
    wrapParam: Boolean,
    copyLabel: String?,
    onCopy: ((String) -> Unit)?,
    copiedLabel: String?,
    moreLabel: ((Int) -> String)?,
    onMore: (() -> Unit)?,
    fileName: String?,
    caption: String?,
    added: Int?,
    removed: Int?,
    copyWords: String?,
    showWrapToggle: Boolean,
    lineNumbers: Boolean,
    onWrapChanged: ((Boolean) -> Unit)?,
    fill: Color,
) {
    val lines = remember(display) { codeSplitLines(display) }
    val colors = KitCodeColors.of(LocalKitColors.current)
    val mono = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text, textDirection = TextDirection.Ltr)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val colourable = kind == KitCodeKind.Code && !open && language != CodeLanguage.Plain
    val tokens = lineTokens(lines, language, colourable)
    val prompts = remember(lines, kind) { if (kind == KitCodeKind.Command) commandLineStarts(lines) else null }
    var expanded by remember(display) { mutableStateOf(false) }
    var wrapOwn by remember(display, wrapParam) { mutableStateOf(wrapParam) }
    val wrap = if (kind == KitCodeKind.Command) wrapParam else wrapOwn
    val openFull = moreLabel != null && onMore != null
    val hasCap = lines.size > maxLines
    val stillCapped = hasCap && (openFull || !expanded)
    val visible = if (stillCapped) lines.subList(0, maxLines) else lines
    val hasWords = fileName != null || caption != null || added != null || removed != null
    val copyable = onCopy != null
    val labelledCopy = copyable && copyWords != null && (hasWords || lines.size > 1)
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
            val text = visible.maxByOrNull { it.length }.orEmpty()
            if (text.isEmpty()) 0f else with(density) { measurer.measure(text, mono, maxLines = 1).size.width.toDp().value }
        }
    val copyName =
        copyLabel
            ?: stringResource(
                when (kind) {
                    KitCodeKind.Code -> Res.string.kitCodeCopyCode
                    KitCodeKind.Command -> Res.string.kitCodeCopyCommand
                    KitCodeKind.Output -> Res.string.kitCodeCopyOutput
                },
            )
    val copy = { onCopy?.invoke(display) ?: Unit }
    val toggleWrap = {
        val next = !wrap
        if (onWrapChanged != null) onWrapChanged(next) else wrapOwn = next
    }

    BoxWithConstraints(modifier.kitMaxWidth(CapRole.List).fillMaxWidth()) {
        val plan =
            codePlan(
                maxWidth = maxWidth.value,
                hasWords = hasWords,
                labelledCopy = labelledCopy,
                copyable = copyable,
                offersWrap = showWrapToggle && kind != KitCodeKind.Command,
                longest = longest,
                gutter = if (hasGutter) gutterWidth else null,
            )
        val showCopy = copyable && !plan.inline
        val headerWrap = plan.wrapToggle && !plan.inline
        val header = hasWords || headerWrap || showCopy
        val padding =
            when {
                plan.inline ->
                    Modifier.padding(
                        start = Space.S4.dp,
                        top = Space.S1.dp,
                        end = if (copyable) codeCopyEnd().dp else Space.S1.dp,
                        bottom = Space.S1.dp,
                    ) // F: lib/ui/kit/kit_code_block.dart:614-620
                header && (plan.wrapToggle || (copyable && !labelledCopy)) ->
                    Modifier.padding(start = Space.S4.dp, top = Space.S1.dp, end = Space.S1.dp, bottom = Space.S4.dp)
                else -> Modifier.padding(Space.S4.dp)
            }
        Column(Modifier.fillMaxWidth().codeSurface(fill).then(padding)) {
            if (header) {
                CodeHeader(
                    fileName, caption, added, removed, mono, colors,
                    wrapToggle = if (headerWrap) ({ CodeWrapToggle(wrap, toggleWrap) }) else null,
                    copy =
                        if (!showCopy) null
                        else if (labelledCopy) ({ m: Modifier -> CodeCopyButton(copyWords!!, copy, m) })
                        else ({ m: Modifier -> CodeCopyIcon(copyName, copiedLabel, copy, m) }),
                    labelledCopy = showCopy && labelledCopy,
                )
                Spacer(Modifier.height(Space.S2.dp))
            }
            val lineHeight = with(density) { mono.lineHeight.toDp() }
            val body =
                @Composable {
                    CodeBody(
                        lines = lines,
                        visibleCount = if (hasCap) maxLines else lines.size,
                        expanded = expanded && !openFull,
                        tokens = tokens,
                        prompts = prompts,
                        lineNumbers = hasGutter && kind != KitCodeKind.Command,
                        gutterWidth = gutterWidth.dp,
                        wrap = wrap,
                        mono = mono,
                        colors = colors,
                        fill = fill,
                        inline = plan.inline,
                        lineHeight = lineHeight,
                        cap =
                            if (!stillCapped) null
                            else if (openFull) (moreLabel!!(lines.size - maxLines) to onMore!!)
                            else (pluralStringResource(Res.plurals.kitCodeShowAll, lines.size, lines.size) to { expanded = true }),
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
    fileName: String?,
    caption: String?,
    added: Int?,
    removed: Int?,
    mono: TextStyle,
    colors: KitCodeColors,
    wrapToggle: (@Composable () -> Unit)?,
    copy: (@Composable (Modifier) -> Unit)?,
    labelledCopy: Boolean,
) {
    val leading = fileName != null || caption != null || added != null || removed != null
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
                        if (fileName != null) {
                            KitMonoText(fileName, Modifier.weight(1f, fill = false), cut = KitMonoCut.Middle, maxLines = 1)
                        } else if (caption != null) {
                            Box(Modifier.weight(1f, fill = false)) { KitText(caption, role = TypeRole.Secondary) }
                        }
                        if (added != null || removed != null) {
                            CodeCounts(
                                added, removed, mono, colors,
                                if (fileName != null || caption != null) Modifier.padding(start = Space.S2.dp) else Modifier,
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
    BasicText(text, modifier.clearAndSetSemantics { contentDescription = spoken }, style = mono, maxLines = 1)
}

/**
 * The bounded body (Dart `_buildBounded`): the head lines; the tail that unfolds in place after "Show all"; one
 * sideways scroller for every line unless they wrap; at least one 48 dp target tall, or, with Copy on the first line,
 * padded so that line centres on the Copy target. A capped block ends in a hairline and its [cap] button.
 */
@Composable
private fun CodeBody(
    lines: List<String>,
    visibleCount: Int,
    expanded: Boolean,
    tokens: List<List<CodeToken>>,
    prompts: List<Boolean>?,
    lineNumbers: Boolean,
    gutterWidth: Dp,
    wrap: Boolean,
    mono: TextStyle,
    colors: KitCodeColors,
    fill: Color,
    inline: Boolean,
    lineHeight: Dp,
    cap: Pair<String, () -> Unit>?,
) {
    val head = 0 until minOf(visibleCount, lines.size)
    val tail = visibleCount until lines.size
    val linesColumn =
        @Composable {
            Column {
                CodeRows(lines, head, tokens, prompts, lineNumbers, gutterWidth, wrap, mono, colors)
                if (!tail.isEmpty()) {
                    KitReveal(visible = expanded) {
                        CodeRows(lines, tail, tokens, prompts, lineNumbers, gutterWidth, wrap, mono, colors)
                    }
                }
            }
        }
    val scrolled =
        @Composable {
            SelectionContainer {
                if (wrap) linesColumn() else CodeOverflow(fill, colors.gutter) { linesColumn() }
            }
        }
    val constrained =
        @Composable {
            if (!inline) {
                Box(Modifier.heightIn(min = FlutterKitTokens.MIN_TARGET.dp)) { scrolled() }
            } else {
                val room = maxOf(0f, FlutterKitTokens.MIN_TARGET - lineHeight.value)
                val top = kotlin.math.floor(room / 2f) // F: lib/ui/kit/kit_code_block.dart:1012
                Box(Modifier.padding(top = top.dp, bottom = (room - top).dp)) { scrolled() }
            }
        }
    if (cap == null) {
        constrained()
        return
    }
    Column {
        constrained()
        CodeCapRule()
        // The block does not pull a text action back to its edge: its words keep the button's own side inset
        // (`KitButton.tertiaryInset`, the kit's text-action reach).
        DisableSelection {
            Box(Modifier.padding(start = Space.S2.dp)) { KitButton(cap.first, cap.second, role = KitButtonRole.Tertiary) }
        }
    }
}

/** Flutter's `Divider(height: space3, thickness: hairline, color: hairline)`: a hairline centred in a `space3` band. */
@Composable
private fun CodeCapRule() {
    val line = LocalKitColors.current.hairline
    val width = kitHairline()
    Box(
        Modifier.fillMaxWidth().height(Space.S3.dp).drawBehind { // F: lib/ui/kit/kit_code_block.dart:1041
            drawLine(line, Offset(0f, center.y), Offset(size.width, center.y), width.toPx())
        },
    )
}

/**
 * The lines in [range]. Without a gutter every line joins one paragraph (one selectable text node per block); with
 * one, each line is a row: the `$` prompt (only where a command starts) or the line number in `text3`, end-aligned
 * in the gutter, `space2`, then the line.
 */
@Composable
private fun CodeRows(
    lines: List<String>,
    range: IntRange,
    tokens: List<List<CodeToken>>,
    prompts: List<Boolean>?,
    lineNumbers: Boolean,
    gutterWidth: Dp,
    wrap: Boolean,
    mono: TextStyle,
    colors: KitCodeColors,
) {
    // Unwrapped lines sit in the sideways scroller at their natural width: they never need a cut.
    val overflow = TextOverflow.Clip
    if (prompts == null && !lineNumbers) {
        val text =
            remember(lines, range, tokens, colors) {
                buildAnnotatedString {
                    for (i in range) {
                        if (i > range.first) append('\n')
                        append(lineText(lines[i], tokens.getOrElse(i) { emptyList() }, colors))
                    }
                }
            }
        BasicText(text, style = mono, softWrap = wrap, overflow = overflow)
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
                    lineText(lines[i], tokens.getOrElse(i) { emptyList() }, colors),
                    if (wrap) Modifier.weight(1f) else Modifier,
                    style = mono,
                    softWrap = wrap,
                    overflow = overflow,
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
        val tokens = lineTokens(lines, language, highlight && kind == KitCodeKind.Code && language != CodeLanguage.Plain)
        val prompts = remember(lines, kind) { if (kind == KitCodeKind.Command) commandLineStarts(lines) else null }
        val hasGutter = kind == KitCodeKind.Command || lineNumbers
        val gutter =
            if (!hasGutter) 0f
            else
                remember(mono, lines.size, kind) {
                    val sample = if (kind == KitCodeKind.Command) "$" else "9".repeat(lines.size.toString().length)
                    with(density) { measurer.measure(sample, mono).size.width.toDp().value }
                }
        val extent = with(density) { mono.lineHeight.toDp() }
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
                                    markLine(lineText(line, tokens[i], colors), starts[i], marks, activeMark, accent)
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

/** Each line's start offset into the joined text (Dart `_lineStarts`). */
internal fun codeLineStarts(lines: List<String>): List<Int> {
    var cursor = 0
    return lines.map { line -> cursor.also { cursor += line.length + 1 } }
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
