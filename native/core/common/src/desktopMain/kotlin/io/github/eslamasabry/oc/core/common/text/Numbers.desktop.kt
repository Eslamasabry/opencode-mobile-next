package io.github.eslamasabry.oc.core.common.text

import java.text.DecimalFormatSymbols
import java.util.Locale

internal actual fun phoneNumberSymbols(): NumberSymbols {
    val s = DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT))
    return NumberSymbols(s.zeroDigit, s.groupingSeparator, s.decimalSeparator, s.minusSign)
}
