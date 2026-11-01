package io.github.eslamasabry.oc.engine.media.preview

/** [text] laid out, cut to [max] chars without splitting a surrogate pair; [cut] says whether anything was left out. */
public data class CappedText(val text: String, val cut: Boolean)

public object Truncate {
    /** The most source text a preview lays out; Copy and Save keep the whole file. */
    public const val MAX_SOURCE_CHARS: Int = 200_000

    public fun cap(text: String, max: Int = MAX_SOURCE_CHARS): CappedText {
        if (text.length <= max) return CappedText(text, cut = false)
        var end = max
        if (text[end - 1].isHighSurrogate()) end--
        return CappedText(text.substring(0, end), cut = true)
    }
}
