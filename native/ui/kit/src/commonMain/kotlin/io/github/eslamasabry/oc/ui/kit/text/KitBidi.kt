package io.github.eslamasabry.oc.ui.kit.text

/**
 * Unicode isolates for technical text inside a sentence (B-L10N-006): a path, command or address
 * keeps its own order in an Arabic line. Text that is already isolated is returned as it is, and
 * empty text is left alone.
 */
public object KitBidi {
    private const val LRI = '⁦'
    private const val FSI = '⁨'
    private const val PDI = '⁩'

    private fun isolated(text: String): Boolean =
        text.length >= 2 && (text.first() == LRI || text.first() == FSI) && text.last() == PDI

    /** Left-to-right isolate: a command, file name or address. */
    public fun ltr(text: String): String =
        if (text.isEmpty() || isolated(text)) text else "$LRI$text$PDI"

    /** First-strong isolate: a name the person typed, in whichever script it is. */
    public fun auto(text: String): String =
        if (text.isEmpty() || isolated(text)) text else "$FSI$text$PDI"

    private val valueRuns =
        Regex(
            """(?:[A-Za-z0-9_\[\]•@+\-/%$]|[.,:](?=[A-Za-z0-9_\[\]•]))+(?: (?:[A-Za-z0-9_\[\]•@+\-/%$]|[.,:](?=[A-Za-z0-9_\[\]•]))+)*"""
        )

    /** Technical values in a fact stay together; existing isolates are never nested. */
    public fun isolateValues(text: String, rtl: Boolean): String {
        if (!rtl || text.isEmpty()) return text
        val out = StringBuilder()
        var start = 0
        var depth = 0
        text.forEachIndexed { index, char ->
            when (char) {
                '\u2066',
                '\u2067',
                '\u2068' -> {
                    if (depth == 0) {
                        out.append(
                            valueRuns.replace(text.substring(start, index)) { ltr(it.value) }
                        )
                        start = index
                    }
                    depth += 1
                }
                '\u2069' ->
                    if (depth > 0) {
                        depth -= 1
                        if (depth == 0) {
                            out.append(text.substring(start, index + 1))
                            start = index + 1
                        }
                    }
            }
        }
        val tail = text.substring(start)
        out.append(if (depth == 0) valueRuns.replace(tail) { ltr(it.value) } else tail)
        return out.toString()
    }
}
