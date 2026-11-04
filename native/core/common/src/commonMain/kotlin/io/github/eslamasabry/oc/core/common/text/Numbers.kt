package io.github.eslamasabry.oc.core.common.text

import kotlin.concurrent.Volatile

/**
 * How the phone writes numbers: its digits and separators (owner decision 2026-11-03, replacing plan 06 §12.1
 * rule 8's fixed Latin digits). Read from the phone's own language and its number setting (Android 14+ "Regional
 * preferences › Numbers", carried as the `-u-nu-` keyword of the system locale), never from the app language alone.
 */
public data class NumberSymbols(
    val zeroDigit: Char,
    /** Null: the phone writes no thousands separator. */
    val grouping: Char?,
    val decimal: Char,
    val minus: Char = '-',
) {
    public companion object {
        public val Latin: NumberSymbols = NumberSymbols(zeroDigit = '0', grouping = ',', decimal = '.')
    }
}

/** The platform's reading of the phone's number setting (Android: system locale via ICU; desktop: FORMAT locale). */
internal expect fun phoneNumberSymbols(): NumberSymbols

/**
 * The one number formatter for every number a person reads: screens (through `:ui:words` / the kit) and
 * notifications use it, so a count shows the same digits in both. Format a number here and pass the string to the
 * template; never let a template turn an Int into text (Compose resources would write Latin, Android `%d` the app
 * language's digits).
 */
public object Numbers {
    /** Tests and previews may pin the symbols; null = read the phone each time. */
    @Volatile public var pinned: NumberSymbols? = null

    public fun symbols(): NumberSymbols = pinned ?: phoneNumberSymbols()

    /** A whole number with the phone's digits and thousands separator: 12345 → "12,345" / "١٢٬٣٤٥". */
    public fun format(value: Long, symbols: NumberSymbols = symbols()): String {
        val digits = if (value == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(value).toString()
        val out = StringBuilder(digits.length + digits.length / 3 + 1)
        if (value < 0) out.append(symbols.minus)
        for (i in digits.indices) {
            if (i > 0 && symbols.grouping != null && (digits.length - i) % 3 == 0) out.append(symbols.grouping)
            out.append(symbols.zeroDigit + (digits[i] - '0'))
        }
        return out.toString()
    }

    public fun format(value: Int, symbols: NumberSymbols = symbols()): String = format(value.toLong(), symbols)

    /** A decimal rounded half-up to [fractionDigits] places, trailing zeros kept (a price, a size: 1.50). */
    public fun decimal(value: Double, fractionDigits: Int, symbols: NumberSymbols = symbols()): String {
        require(fractionDigits in 0..9)
        if (value.isNaN() || value.isInfinite()) return value.toString()
        var scale = 1L
        repeat(fractionDigits) { scale *= 10 }
        val scaled = kotlin.math.round(kotlin.math.abs(value) * scale).toLong()
        val whole = format(scaled / scale, symbols.copy(minus = symbols.minus))
        val sign = if (value < 0 && scaled != 0L) symbols.minus.toString() else ""
        if (fractionDigits == 0) return sign + whole
        val fraction = (scaled % scale).toString().padStart(fractionDigits, '0')
        return sign + whole + symbols.decimal + digits(fraction, symbols)
    }

    /**
     * A US-dollar amount: English writes "$12.50", Arabic puts the symbol after the amount ("12.50 $"). [symbolAfter]
     * is the reading direction of the app language, so prices, usage and model labels agree.
     */
    public fun usd(value: Double, fractionDigits: Int, symbolAfter: Boolean, symbols: NumberSymbols = symbols()): String {
        val amount = decimal(value, fractionDigits, symbols)
        return if (symbolAfter) "$amount $" else "$$amount"
    }

    /** Rewrites the ASCII digits of already-built text (a time "12:30", a version) in the phone's digits. */
    public fun digits(text: String, symbols: NumberSymbols = symbols()): String {
        if (symbols.zeroDigit == '0') return text
        val out = StringBuilder(text.length)
        for (c in text) out.append(if (c in '0'..'9') symbols.zeroDigit + (c - '0') else c)
        return out.toString()
    }
}
