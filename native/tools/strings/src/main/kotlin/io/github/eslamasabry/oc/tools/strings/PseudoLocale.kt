package io.github.eslamasabry.oc.tools.strings

/**
 * The pseudo-locale: English made about 40 % longer with accents, so a screen that clips it would clip the longest real
 * translations (German-length copy, Russian, Vietnamese). Format tokens (`%1$s`) and line breaks are kept exactly, so the
 * result is a drop-in replacement for the English in a render test. The kit render test in `:ui:kit`
 * (`PseudoLocaleOverflowTest`) carries a copy of [expand], because no module may depend on a tool.
 */
object PseudoLocale {
    private val token = Regex("""%(?:\d+\$)?[sd%]|\\[ntu]""")
    private val swaps = mapOf('a' to 'à', 'e' to 'é', 'i' to 'î', 'o' to 'ö', 'u' to 'ü', 'c' to 'ç', 'n' to 'ñ', 's' to 'š', 'y' to 'ý')

    fun expand(text: String, growth: Double = 0.4): String {
        val out = StringBuilder()
        var last = 0
        var letters = 0
        for (m in token.findAll(text)) {
            letters += accent(text.substring(last, m.range.first), out)
            out.append(m.value)
            last = m.range.last + 1
        }
        letters += accent(text.substring(last), out)
        // Pad the end with words of the same width so a wrapped line grows like real copy does.
        val extra = (letters * growth).toInt()
        if (extra > 0) out.append(' ').append("ŵŵŵŵŵŵŵŵ".repeat(extra / 8 + 1).take(extra))
        return out.toString()
    }

    private fun accent(s: String, out: StringBuilder): Int {
        var n = 0
        for (c in s) {
            out.append(swaps[c] ?: c)
            if (c.isLetter()) n++
        }
        return n
    }
}
