package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.withContext

/** What a [KitCodeBlock] holds (Dart `KitCodeKind`). */
public enum class KitCodeKind {
    /** Source code: syntax colour from the block's language. */
    Code,

    /** A command the person runs elsewhere: a `$` before each command, outside the copied text (B-CHAT-055). */
    Command,

    /** Output of a tool or process: no colour. */
    Output,
}

/** The colour of each code role in the current theme (opaque, B-KIT-041). Plain = the block's own text colour. */
@Immutable
public data class KitCodeColors(val roles: Map<CodeRole, Color>, val text: Color, val gutter: Color, val fill: Color) {
    public operator fun get(role: CodeRole): Color = roles[role] ?: text

    public companion object {
        public fun of(c: KitColors): KitCodeColors =
            KitCodeColors(
                roles =
                    mapOf(
                        CodeRole.Keyword to c.codeKeyword,
                        CodeRole.String to c.codeString,
                        CodeRole.Type to c.codeType,
                        CodeRole.Number to c.codeType,
                        CodeRole.Function to c.accent,
                        CodeRole.Comment to c.text3,
                        CodeRole.Meta to c.text3,
                        CodeRole.Punctuation to c.text2,
                        CodeRole.Addition to c.success,
                        CodeRole.Deletion to c.attention,
                    ),
                text = c.text1,
                gutter = c.text3,
                fill = c.surface2,
            )
    }
}

/** One displayed line: its text and its offset in the displayed (redacted) source. */
private class CodeLine(val text: String, val start: Int)

/**
 * A code, command or output block (plan 09 §9.2; Dart `KitCodeBlock`).
 *
 * - **Displays** `Redactor.redact(source)` (R18, SEC-2); [onCopy] receives the redacted source, never the display
 *   decorations (prompts, the line cap, break chances).
 * - Code is coloured through [LocalKitCodeTokenizer] on [LocalKitChatWork], once per closed source (the cache keeps it);
 *   an [open] fence is never coloured (plan 09 §3.8), and the first frame of a closed block is plain until its tokens
 *   arrive.
 * - Capped at [maxLines] lines; the rest is one tap away through [moreLabel] / [onMore] ("Open full output").
 * - Always left to right (R15), mono reading text at full scale (§9.7). Lines scroll sideways unless [wrap]; a wrapping
 *   code or output line breaks only at spaces and after punctuation (B-CHAT-055), a command keeps its words whole.
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
    moreLabel: ((hiddenLines: Int) -> String)? = null,
    onMore: (() -> Unit)? = null,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    val lines =
        remember(display) {
            var at = 0
            display.split('\n').map { CodeLine(it, at).also { _ -> at += it.length + 1 } }
        }
    val colourable = kind == KitCodeKind.Code && !open && language != CodeLanguage.Plain
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val tokens by produceState<ImmutableList<CodeToken>>(persistentListOf(), display, colourable, language, tokenizer) {
        value = if (colourable) withContext(work) { tokenizer.tokenize(display, language) } else persistentListOf()
    }
    val prompts = remember(lines, kind) { if (kind == KitCodeKind.Command) commandLineStarts(lines.map { it.text }) else null }
    val colors = KitCodeColors.of(LocalKitColors.current)
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val shown = if (lines.size > maxLines) lines.subList(0, maxLines) else lines
    val hidden = lines.size - shown.size

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .background(colors.fill, RoundedCornerShape(8.dp))
                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
        ) {
            if (copyLabel != null && onCopy != null) {
                DisableSelection {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        KitTappable(onClick = { onCopy(display) }) {
                            KitText(copyLabel, role = TypeRole.Label, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                        }
                    }
                }
            }
            val lineStyle = mono.copy(color = colors.text, textDirection = TextDirection.Ltr)
            val body = @Composable {
                Column {
                    shown.forEachIndexed { i, line ->
                        Row {
                            if (prompts != null) {
                                DisableSelection {
                                    BasicText(
                                        if (prompts[i]) "$" else "",
                                        Modifier.width(Space.S4.dp),
                                        style = lineStyle.copy(color = colors.gutter, textAlign = TextAlign.Start),
                                    )
                                }
                            }
                            BasicText(
                                text = lineText(line, tokens, colors, breakable = wrap && kind != KitCodeKind.Command),
                                style = lineStyle,
                                softWrap = wrap,
                            )
                        }
                    }
                }
            }
            if (wrap) body() else Row(Modifier.horizontalScroll(rememberScrollState())) { body() }
            if (hidden > 0 && moreLabel != null && onMore != null) {
                DisableSelection {
                    KitTappable(onClick = onMore, modifier = Modifier.align(Alignment.Start)) {
                        KitText(moreLabel(hidden), role = TypeRole.Label, tone = TextTone.Accent, kind = KitTextKind.Frame)
                    }
                }
            }
        }
    }
}

/** Plan 09 §9.2: code and output in the transcript stop at 12 lines. */
public const val TRANSCRIPT_MAX_LINES: Int = 12

/** [line] with the colours of the [tokens] that cross it (tokens are offsets into the whole displayed source). */
private fun lineText(line: CodeLine, tokens: List<CodeToken>, colors: KitCodeColors, breakable: Boolean): AnnotatedString {
    fun piece(s: String) = if (breakable) codeBreakable(s) else s
    if (tokens.isEmpty()) return AnnotatedString(piece(line.text))
    val end = line.start + line.text.length
    return buildAnnotatedString {
        var cursor = line.start
        for (t in tokens) {
            if (t.end <= line.start) continue
            if (t.start >= end) break
            val from = maxOf(t.start, line.start)
            val to = minOf(t.end, end)
            if (from > cursor) append(piece(line.text.substring(cursor - line.start, from - line.start)))
            withStyle(SpanStyle(color = colors[t.role])) { append(piece(line.text.substring(from - line.start, to - line.start))) }
            cursor = to
        }
        if (cursor < end) append(piece(line.text.substring(cursor - line.start)))
    }
}

