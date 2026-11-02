package io.github.eslamasabry.oc.core.common.text

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.currentLocale

internal actual fun phoneNumberSymbols(): NumberSymbols {
    val f = NSNumberFormatter()
    f.locale = NSLocale.currentLocale
    val zero = f.stringFromNumber(NSNumber(int = 0))?.firstOrNull() ?: '0'
    return NumberSymbols(
        zeroDigit = zero,
        grouping = f.groupingSeparator?.firstOrNull(),
        decimal = f.decimalSeparator?.firstOrNull() ?: '.',
        minus = f.minusSign?.firstOrNull() ?: '-',
    )
}
