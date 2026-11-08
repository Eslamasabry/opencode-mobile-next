package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * A number and its unit as one left-to-right run (audit C09, 2026-11-09). Inside an Arabic line the bidi algorithm
 * moved a Latin unit to the far side of its number, so "215.0 MB" read "MB 215.0" and "3.0%" read "%3.0". Each run
 * here sits in a left-to-right isolate ([KitBidi.ltr]), so it keeps its order in either direction while the line
 * around it keeps its own.
 */
public object KitUnits {
    /** "[number] [unit]" ("215.0 MB") as one isolated run. */
    public fun quantity(number: String, unit: String): String = KitBidi.ltr("$number $unit")

    /** "[number]%" as one isolated run. */
    public fun percent(number: String): String = KitBidi.ltr("$number%")

    /**
     * Isolates every number-with-unit run already in [text] (a line built from a translated template): digits, an
     * optional decimal part, then a percent sign or a byte unit. Runs already isolated are left alone, so this is safe
     * to apply twice.
     */
    public fun isolate(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var last = 0
        for (m in RUN.findAll(text)) {
            val start = m.range.first
            val end = m.range.last + 1
            val before = if (start > 0) text[start - 1] else null
            val after = if (end < text.length) text[end] else null
            out.append(text, last, start)
            if (before == KitBidi.LRI && after == KitBidi.PDI) out.append(m.value) else out.append(KitBidi.ltr(m.value))
            last = end
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    /** Western or Arabic-Indic digits, a decimal part, then "%", "٪" or a byte unit as a whole word. */
    private val RUN =
        Regex("""[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?(?:\s?[%٪]|\s(?:B|KB|MB|GB|TB)(?![\p{L}\p{N}]))""")
}

/**
 * [KitUnits.isolate] in a right-to-left layout, where a unit would otherwise jump to the far side of its number; a
 * left-to-right line already keeps the run in order and is returned as it is.
 */
@Composable
public fun kitUnits(text: String): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) KitUnits.isolate(text) else text
