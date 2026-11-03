package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role

/**
 * The status check's reply line (port of Flutter `safeWidgetAssistantPreview` / `safeWidgetPreview`): the latest
 * assistant prose only — never user prompts, synthetic text, reasoning, tool output or message errors — with
 * inline reasoning blocks and Markdown marks removed, secrets masked by [redact], unsafe and bidi controls replaced,
 * whitespace collapsed, at most 240 characters.
 */
public class WidgetRunPreview(private val redact: (String) -> String) {
    public fun of(messages: List<MessageWithParts>): String {
        for (message in messages.asReversed()) {
            if (message.info.role != Role.Assistant) continue
            val text =
                message.parts.filter { it.type == PartType.Text && !it.synthetic }.joinToString(" ") { it.text }
            val line = line(stripMarkdown(withoutInlineReasoning(text)), 240)
            if (line.isNotEmpty()) return line
        }
        return ""
    }

    private fun line(value: String, limit: Int): String {
        val safe =
            buildString {
                    for (c in redact(value)) append(if (unsafe(c.code)) ' ' else c)
                }
                .replace(WHITESPACE, " ")
                .trim()
        val end = safe.offsetByCodePointsOrLength(limit)
        return safe.substring(0, end)
    }

    private fun String.offsetByCodePointsOrLength(count: Int): Int {
        var index = 0
        var taken = 0
        while (index < length && taken < count) {
            index += if (this[index].isHighSurrogate() && index + 1 < length) 2 else 1
            taken++
        }
        return index
    }

    private fun unsafe(c: Int): Boolean =
        c < 0x20 || c == 0x7f || c == 0x061c || c == 0x200e || c == 0x200f || c in 0x202a..0x202e ||
            c in 0x2066..0x2069

    private fun stripMarkdown(value: String): String =
        value
            .replace(FENCE, "")
            .replace(LINK) { it.groupValues[1] }
            .replace(HEADING, "")
            .replace(QUOTE, "")
            .replace(LIST, "")
            .replace(CODE) { it.groupValues[1] }
            .replace(STRONG) { it.groupValues[2] }
            .replace(STAR) { it.groupValues[1] }
            .replace(UNDERSCORE) { it.groupValues[1] }
            .replace("`", "")

    /** Hides complete and still-open `<think>` blocks; an orphan close discards what came before it. */
    private fun withoutInlineReasoning(value: String): String {
        val visible = StringBuilder()
        var depth = 0
        var offset = 0
        for (marker in REASONING.findAll(value)) {
            if (depth == 0) visible.append(value, offset, marker.range.first)
            if (marker.groupValues[1].isEmpty()) depth += 1
            else if (depth > 0) {
                depth -= 1
                if (depth == 0) visible.append(' ')
            } else visible.setLength(0)
            offset = marker.range.last + 1
        }
        if (depth == 0) visible.append(value, offset, value.length)
        return visible.toString()
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val FENCE = Regex("""^\s*```[^\n]*$""", RegexOption.MULTILINE)
        val LINK = Regex("""!?\[([^\]]*)\]\([^)]*\)""")
        val HEADING = Regex("""^\s{0,3}#{1,6}\s+""", RegexOption.MULTILINE)
        val QUOTE = Regex("""^\s{0,3}>\s?""", RegexOption.MULTILINE)
        val LIST = Regex("""^\s*(?:[-*+]|\d+[.)])\s+""", RegexOption.MULTILINE)
        val CODE = Regex("""`+([^`]*)`+""")
        val STRONG = Regex("""(\*\*|__|~~)(.+?)\1""")
        val STAR = Regex("""(?<![\w*])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![\w*])""")
        val UNDERSCORE = Regex("""(?<![\w_])_(?!\s)([^_\n]+?)(?<!\s)_(?![\w_])""")
        val REASONING = Regex("""<\s*(/?)\s*(?:think|thinking)\s*>""", RegexOption.IGNORE_CASE)
    }
}
