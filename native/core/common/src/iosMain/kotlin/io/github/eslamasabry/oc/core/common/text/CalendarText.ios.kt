package io.github.eslamasabry.oc.core.common.text

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentCalendar
import platform.Foundation.dateWithTimeIntervalSince1970

public actual fun shortCalendarDate(atMs: Long, nowMs: Long, localeTag: String): String {
    val then = NSDate.dateWithTimeIntervalSince1970(atMs / 1000.0)
    val now = NSDate.dateWithTimeIntervalSince1970(nowMs / 1000.0)
    val calendar = NSCalendar.currentCalendar
    val sameYear = calendar.component(NSCalendarUnitYear, then) == calendar.component(NSCalendarUnitYear, now)
    val format = NSDateFormatter()
    format.locale = NSLocale(localeIdentifier = localeTag)
    format.setLocalizedDateFormatFromTemplate(if (sameYear) "MMMd" else "yMMMd")
    return Numbers.digits(format.stringFromDate(then))
}
