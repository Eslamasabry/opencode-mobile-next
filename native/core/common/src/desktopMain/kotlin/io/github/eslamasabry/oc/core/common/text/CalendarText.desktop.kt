package io.github.eslamasabry.oc.core.common.text

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

public actual fun shortCalendarDate(atMs: Long, nowMs: Long, localeTag: String): String {
    val locale = Locale.forLanguageTag(localeTag)
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(atMs).atZone(zone)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val dayFirst = locale.language == "ar"
    val pattern = if (then.year == now.year) {
        if (dayFirst) "d MMM" else "MMM d"
    } else if (dayFirst) "d MMM yyyy" else "MMM d, yyyy"
    return Numbers.digits(DateTimeFormatter.ofPattern(pattern, locale).format(then))
}
