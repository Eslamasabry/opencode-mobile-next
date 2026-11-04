package io.github.eslamasabry.oc.core.common.text

import android.icu.text.DateTimePatternGenerator
import java.util.Calendar
import java.util.Date
import java.util.Locale

public actual fun shortCalendarDate(atMs: Long, nowMs: Long, localeTag: String): String {
    val locale = Locale.forLanguageTag(localeTag)
    val then = Calendar.getInstance().apply { timeInMillis = atMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    val skeleton = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "MMMd" else "yMMMd"
    val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)
    return Numbers.digits(android.icu.text.SimpleDateFormat(pattern, locale).format(Date(atMs)))
}
