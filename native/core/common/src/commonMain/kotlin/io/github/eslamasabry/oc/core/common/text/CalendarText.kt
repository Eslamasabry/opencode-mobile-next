package io.github.eslamasabry.oc.core.common.text

/** Locale's short month/day; include the year only when it differs from [nowMs]. No clock reads. */
public expect fun shortCalendarDate(atMs: Long, nowMs: Long, localeTag: String): String
