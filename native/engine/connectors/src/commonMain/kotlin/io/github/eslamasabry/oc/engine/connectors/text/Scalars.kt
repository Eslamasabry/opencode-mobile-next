package io.github.eslamasabry.oc.engine.connectors.text

/** Unicode scalar helpers (Dart `String.runes`): a surrogate pair counts once, a lone surrogate counts once. */
internal object Scalars {
    fun count(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            i += width(s, i)
            n++
        }
        return n
    }

    /** The first [max] scalars of [s], never splitting a surrogate pair. */
    fun take(s: String, max: Int): String {
        var n = 0
        var i = 0
        while (i < s.length && n < max) {
            i += width(s, i)
            n++
        }
        return s.substring(0, i)
    }

    private fun width(s: String, i: Int): Int =
        if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
}
