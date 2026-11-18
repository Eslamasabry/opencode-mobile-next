package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * Words that never break inside a word (owner's look of build 10086: "Antigravit / y" in a narrow agent tile at text
 * 1.3). [KitWordText] ends a long line at a whole word, but a single word wider than the line still broke; this keeps
 * the words at [full] size whenever every line break falls between words, and otherwise shrinks them in small steps
 * until it does, never below 70 % of [full] (past that, the framework's own break stands).
 *
 * Null for an unspecified size (nothing to scale from).
 */
public fun kitWholeWords(full: TextUnit): TextAutoSize? = if (full.isSpecified) KitWholeWords(full) else null

private data class KitWholeWords(val full: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = full
        var factor = 1f
        while (factor > FLOOR) {
            if (KitWholeWordsRules.whole(performLayout(constraints, text, size), text.text)) return size
            factor -= STEP
            size = full * factor
        }
        return full * FLOOR
    }

    private companion object {
        const val FLOOR = 0.7f
        const val STEP = 0.05f
    }
}

/** The pure rule of [kitWholeWords]: where a line may start. */
public object KitWholeWordsRules {
    /** Every line of [layout] starts at a word's start (or the text's), never inside a word. */
    public fun whole(layout: TextLayoutResult, text: String): Boolean =
        (1 until layout.lineCount).all { line -> breaksBetweenWords(text, layout.getLineStart(line)) }

    /**
     * A line that starts at [index] of [text] breaks between words: after a space or a dash, at the text's start, or
     * between two characters of a script written without spaces (Chinese, Japanese, Thai), where any break is a word
     * break.
     */
    public fun breaksBetweenWords(text: String, index: Int): Boolean {
        if (index <= 0 || index >= text.length) return true
        val before = text[index - 1]
        val after = text[index]
        return !before.isLetterOrDigit() || !after.isLetterOrDigit() || unspaced(before) || unspaced(after)
    }

    private fun unspaced(c: Char): Boolean =
        c in '฀'..'๿' || // Thai
            c in '぀'..'ヿ' || // Hiragana, Katakana
            c in '㐀'..'鿿' || // CJK ideographs
            c in '豈'..'﫿'
}
