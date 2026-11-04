package io.github.eslamasabry.oc.engine.attention.widget

import io.github.eslamasabry.oc.core.security.Redactor

/**
 * Launcher-safe text (port of `safeWidgetTitle`/`safeWidgetProject`/`safeWidgetPreview` and the session-title
 * presentation, B-SEC-181). Every value is redacted, has control characters and bidirectional overrides/isolates
 * replaced, whitespace flattened, and is cut by code points.
 */
public class WidgetText(private val redactor: Redactor) {
    public fun title(value: String): String = safe(value, TITLE_LIMIT)

    public fun path(value: String): String = safe(value, PATH_LIMIT)

    /** Only a display name, even when a caller supplies a path; generic folder names give no line. */
    public fun project(value: String): String {
        val parts = value.split(PATH_SEPARATORS).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        val name = title(parts.last())
        return if (name.lowercase() in GENERIC_PROJECTS) "" else name
    }

    /** An assistant excerpt: inline reasoning hidden, markdown stripped, then the same safety, 240 code points. */
    public fun preview(value: String): String = safe(stripMarkdown(withoutInlineReasoning(value)), PREVIEW_LIMIT)

    /**
     * A session title as shown: leaked model markup cut, the server's dated placeholder collapsed to [untitled], an
     * ISO stamp in passing removed, [untitled] when nothing readable is left. Then made launcher-safe.
     */
    public fun sessionTitle(raw: String?, untitled: String): String = title(presentedTitle(raw, untitled))

    private fun safe(value: String, limit: Int): String {
        val sb = StringBuilder()
        var i = 0
        val text = redactor.redact(value)
        while (i < text.length) {
            val cp = codePointAt(text, i)
            val width = if (cp > 0xFFFF) 2 else 1
            if (unsafe(cp)) sb.append(' ') else sb.append(text, i, i + width)
            i += width
        }
        return takeCodePoints(sb.toString().replace(WHITESPACE, " ").trim(), limit)
    }

    public companion object {
        public const val TITLE_LIMIT: Int = 80
        public const val PATH_LIMIT: Int = 160
        public const val PREVIEW_LIMIT: Int = 240
        private val WHITESPACE = Regex("""\s+""")
        private val PATH_SEPARATORS = Regex("""[\\/]+""")
        private val GENERIC_PROJECTS = setOf("project", "projects", "workspace", "tmp", "temp", "home", "root", "src")
        private val MARKERS = listOf("<tool_call", "</tool_call", "<tool_use", "<function", "</function", "<|")
        private val TRUNCATED_MARKER = Regex("""(<[A-Za-z_|/]*)\s*(?:…|\.\.\.)\s*$""")
        private val TRAILING_ELLIPSIS = Regex("""[\s…]*(?:\.\.\.)?[\s…]*$""")
        private val PLACEHOLDER =
            Regex(
                """^(?:(?:New|Child)\s+session|Fork(?:ed)?(?:\s+session)?)?\s*[-:–]?\s*\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-]\d{2}:?\d{2})?$""",
                RegexOption.IGNORE_CASE,
            )
        private val ISO_STAMP = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?""")
        private val REASONING = Regex("""<\s*(/?)\s*(?:think|thinking)\s*>""", RegexOption.IGNORE_CASE)

        private fun unsafe(c: Int): Boolean =
            c < 0x20 || c == 0x7f || c == 0x061c || c == 0x200e || c == 0x200f || c in 0x202a..0x202e || c in 0x2066..0x2069

        internal fun presentedTitle(raw: String?, untitled: String): String {
            val title = displayTitle(raw)
            if (title.isEmpty() || PLACEHOLDER.matches(title.trim())) return untitled
            val plain = title.replace(ISO_STAMP, "").replace(Regex("""\s*[-:–]\s*$"""), "").replace(Regex("""\s{2,}"""), " ").trim()
            return plain.ifEmpty { untitled }
        }

        internal fun displayTitle(raw: String?): String {
            var value = raw?.trim().orEmpty()
            if (value.isEmpty()) return value
            var cut = false
            val lower = value.lowercase()
            val at = MARKERS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
            if (at != null) {
                value = value.substring(0, at)
                cut = true
            } else {
                val tail = TRUNCATED_MARKER.find(value)
                if (tail != null) {
                    val fragment = tail.groupValues[1].lowercase()
                    if (MARKERS.any { it.startsWith(fragment) }) {
                        value = value.substring(0, tail.range.first)
                        cut = true
                    }
                }
            }
            if (cut) value = TRAILING_ELLIPSIS.replaceFirst(value, "")
            return value.trim()
        }

        internal fun stripMarkdown(value: String): String {
            var text = value
                .replace(Regex("""^\s*```[^\n]*$""", RegexOption.MULTILINE), "")
                .replace(Regex("""!?\[([^\]]*)\]\([^)]*\)""")) { it.groupValues[1] }
                .replace(Regex("""^\s{0,3}#{1,6}\s+""", RegexOption.MULTILINE), "")
                .replace(Regex("""^\s{0,3}>\s?""", RegexOption.MULTILINE), "")
                .replace(Regex("""^\s*(?:[-*+]|\d+[.)])\s+""", RegexOption.MULTILINE), "")
                .replace(Regex("""`+([^`]*)`+""")) { it.groupValues[1] }
                .replace(Regex("""(\*\*|__|~~)(.+?)\1""")) { it.groupValues[2] }
                .replace(Regex("""(?<![\w*])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![\w*])""")) { it.groupValues[1] }
                .replace(Regex("""(?<![\w_])_(?!\s)([^_\n]+?)(?<!\s)_(?![\w_])""")) { it.groupValues[1] }
            text = text.replace("`", "")
            return text
        }

        /** Hides complete and still-open `<think>` blocks; an orphan close discards its prefix too. */
        internal fun withoutInlineReasoning(value: String): String {
            val visible = StringBuilder()
            var depth = 0
            var offset = 0
            for (marker in REASONING.findAll(value)) {
                if (depth == 0) visible.append(value, offset, marker.range.first)
                if (marker.groupValues[1].isEmpty()) {
                    depth += 1
                } else if (depth > 0) {
                    depth -= 1
                    if (depth == 0) visible.append(' ')
                } else {
                    visible.clear()
                }
                offset = marker.range.last + 1
            }
            if (depth == 0) visible.append(value, offset, value.length)
            return visible.toString()
        }

        private fun codePointAt(s: String, i: Int): Int {
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                return 0x10000 + ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00)
            }
            return c.code
        }

        internal fun takeCodePoints(text: String, limit: Int): String {
            var count = 0
            var i = 0
            while (i < text.length && count < limit) {
                i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                count++
            }
            return text.substring(0, i)
        }

        internal fun codePointCount(text: String): Int {
            var count = 0
            var i = 0
            while (i < text.length) {
                i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                count++
            }
            return count
        }
    }
}
