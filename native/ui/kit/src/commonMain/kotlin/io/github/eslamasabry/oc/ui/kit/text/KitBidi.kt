package io.github.eslamasabry.oc.ui.kit.text

/**
 * Unicode isolates for technical text inside a sentence (B-L10N-006): a path, command or address keeps its own order
 * in an Arabic line. Text that is already isolated is returned as it is, and empty text is left alone.
 */
public object KitBidi {
    private const val LRI = '⁦'
    private const val FSI = '⁨'
    private const val PDI = '⁩'

    private fun isolated(text: String): Boolean =
        text.length >= 2 && (text.first() == LRI || text.first() == FSI) && text.last() == PDI

    /** Left-to-right isolate: a command, file name or address. */
    public fun ltr(text: String): String = if (text.isEmpty() || isolated(text)) text else "$LRI$text$PDI"

    /** First-strong isolate: a name the person typed, in whichever script it is. */
    public fun auto(text: String): String = if (text.isEmpty() || isolated(text)) text else "$FSI$text$PDI"
}
