package io.github.eslamasabry.oc.core.common.text

import android.content.res.Resources
import android.icu.text.DecimalFormatSymbols
import java.util.Locale

@Volatile private var cached: Pair<Locale, NumberSymbols>? = null

/**
 * The system locale, not the app's: per-app language changes words, while digits follow the phone. ICU honours the
 * `-u-nu-` keyword the Regional preferences "Numbers" choice adds to it.
 */
internal actual fun phoneNumberSymbols(): NumberSymbols {
    val locale = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
    cached?.let { (l, s) -> if (l == locale) return s }
    val s = DecimalFormatSymbols.getInstance(locale)
    val symbols = NumberSymbols(s.zeroDigit, s.groupingSeparator, s.decimalSeparator, s.minusSign)
    cached = locale to symbols
    return symbols
}
