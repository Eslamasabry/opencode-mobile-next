package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

/**
 * Colours and the mono face of inline Markdown, taken from the kit theme once per theme. [link] is `roles.partner`
 * (Flutter `kit_markdown.dart` `_link`, C-017); a confirmed path is [pathLink], the accent (Flutter `_KitMdPathChip`).
 * Inline code and paths are [mono] at [monoSize] (the Mono role, 13 sp; C-016) and carry no tint.
 */
@Immutable
public data class KitMdInlineStyle(
    val link: Color,
    val mono: FontFamily,
    val monoSize: TextUnit = TextUnit.Unspecified,
    val pathLink: Color = link,
)

/** Where taps on inline links and confirmed path chips go. Plain lambdas (C5); the kit never opens anything (R17). */
public fun interface KitMdLinkSink {
    public fun open(target: KitSpanKind)
}

/** First strong isolate / pop directional isolate (plan 09 §9.1 RTL: inline code and paths are isolated). */
internal const val FSI: Char = '⁨'
internal const val PDI: Char = '⁩'

/** Test hook: how many inline strings were built (plan 09 §9.1: once per block instance). */
internal object KitMdCounters {
    var inlineBuilds: Int = 0
}

/**
 * [inlines] as an [AnnotatedString] (plan 09 §9.1). A **total** function: spans out of range, reversed, empty or
 * overlapping are clamped or skipped, never thrown on (an exception in composition kills the process, ADR §9.6).
 * Inline code and paths are wrapped in FSI…PDI so a path never reorders inside Arabic prose. A link becomes a
 * `LinkAnnotation.Clickable` whose listener calls [sink]; a path becomes clickable only when [confirmed] says the
 * engine found the file (B-CHAT-064); otherwise it is a plain chip.
 */
public fun inlineAnnotated(
    inlines: KitInlines,
    style: KitMdInlineStyle,
    sink: KitMdLinkSink,
    confirmed: (String) -> Boolean = { false },
): AnnotatedString {
    KitMdCounters.inlineBuilds++
    val text = inlines.text
    val n = text.length
    val spans =
        inlines.spans.mapNotNull { s ->
            val a = s.start.coerceIn(0, n)
            val b = s.end.coerceIn(0, n)
            if (b > a) Triple(a, b, s.kind) else null
        }
    if (spans.isEmpty()) return AnnotatedString(text)
    val isolates = spans.filter { it.third == KitSpanKind.Code || it.third is KitSpanKind.Path }
    val opensAt = IntArray(n + 1)
    val closesAt = IntArray(n + 1)
    isolates.forEach {
        opensAt[it.first]++
        closesAt[it.second]++
    }
    val before = IntArray(n + 1) // output offset before the marks inserted at index i
    val afterClose = IntArray(n + 1) // output offset after the PDIs at index i (before the FSIs)
    val out = StringBuilder(n + 2 * isolates.size)
    for (i in 0..n) {
        before[i] = out.length
        repeat(closesAt[i]) { out.append(PDI) }
        afterClose[i] = out.length
        repeat(opensAt[i]) { out.append(FSI) }
        if (i < n) out.append(text[i])
    }
    val builder = AnnotatedString.Builder(out.toString())
    for ((a, b, kind) in spans) {
        val from = before[a]
        val to = afterClose[b]
        when (kind) {
            // C-023: Flutter draws `**bold**` at w700 (kit_markdown.dart `_spansOf`).
            KitSpanKind.Bold -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, to)
            KitSpanKind.Italic -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), from, to)
            KitSpanKind.Strike -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), from, to)
            // C-016: the mono face at the Mono size marks code; no tint and no padding (Flutter `_codeSpan`: a tinted
            // run beside thin prose fails the contrast check).
            KitSpanKind.Code ->
                builder.addStyle(SpanStyle(fontFamily = style.mono, fontSize = style.monoSize), from, to)
            is KitSpanKind.Link ->
                builder.addLink(
                    LinkAnnotation.Clickable(
                        tag = kind.url,
                        styles = TextLinkStyles(SpanStyle(color = style.link, textDecoration = TextDecoration.Underline)),
                    ) { sink.open(kind) },
                    from,
                    to,
                )
            is KitSpanKind.Picture ->
                builder.addLink(
                    LinkAnnotation.Clickable(
                        tag = kind.path,
                        styles = TextLinkStyles(SpanStyle(color = style.link, textDecoration = TextDecoration.Underline)),
                    ) { sink.open(kind) },
                    from,
                    to,
                )
            is KitSpanKind.Path -> {
                builder.addStyle(SpanStyle(fontFamily = style.mono, fontSize = style.monoSize), from, to)
                if (confirmed(kind.path)) {
                    // Flutter `_KitMdPathChip`: a readable path is accent, underlined mono.
                    val look = SpanStyle(color = style.pathLink, textDecoration = TextDecoration.Underline)
                    builder.addLink(
                        LinkAnnotation.Clickable(tag = kind.path, styles = TextLinkStyles(look)) {
                            sink.open(kind)
                        },
                        from,
                        to,
                    )
                }
            }
        }
    }
    return builder.toAnnotatedString()
}
