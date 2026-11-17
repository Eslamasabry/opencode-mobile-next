package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.NativeStrings
import io.github.eslamasabry.oc.android.widgets.R

/**
 * Shared duration and relative-age formatting for all Android widgets, backed by plurals and [NativeStrings]
 * so units, CLDR plural forms, and localized digits match across all widget surfaces.
 */
internal object WidgetDurationWords {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * 24L

    fun ago(context: Context, nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return ""
        val e = WidgetElapsedPolicy.elapsed(nowMs, thenMs)
        return when (e.kind) {
            WidgetLayoutPolicy.Ago.NOW -> NativeStrings.get(context, R.string.widget_v2_ago_now)
            WidgetLayoutPolicy.Ago.MINUTES -> NativeStrings.quantity(context, R.plurals.widget_v2_ago_min, e.count, e.count)
            WidgetLayoutPolicy.Ago.HOURS -> NativeStrings.quantity(context, R.plurals.widget_v2_ago_hour, e.count, e.count)
            WidgetLayoutPolicy.Ago.YESTERDAY -> NativeStrings.get(context, R.string.widget_v2_ago_yesterday)
            else -> e.label
        }
    }

    fun compactAgo(context: Context, nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return ""
        val e = WidgetElapsedPolicy.elapsed(nowMs, thenMs)
        return when (e.kind) {
            WidgetLayoutPolicy.Ago.NOW -> NativeStrings.get(context, R.string.widget_v2_ago_now)
            WidgetLayoutPolicy.Ago.MINUTES -> NativeStrings.quantity(context, R.plurals.widget_v2_span_min, e.count, e.count)
            WidgetLayoutPolicy.Ago.HOURS -> NativeStrings.quantity(context, R.plurals.widget_v2_span_hour, e.count, e.count)
            WidgetLayoutPolicy.Ago.YESTERDAY -> NativeStrings.get(context, R.string.widget_v2_ago_yesterday)
            else -> e.label
        }
    }

    fun since(context: Context, nowMs: Long, startedAtMs: Long): String {
        val since = WidgetElapsedPolicy.sinceUnit(nowMs, startedAtMs) ?: return ""
        return when (since.first) {
            "min" -> NativeStrings.quantity(context, R.plurals.widget_v2_span_min, since.second, since.second)
            else -> NativeStrings.quantity(context, R.plurals.widget_v2_span_hour, since.second, since.second)
        }
    }

    fun spanSince(context: Context, nowMs: Long, sinceMs: Long): String? {
        if (sinceMs <= 0L) return null
        val minutes = (nowMs - sinceMs).coerceAtLeast(0L) / MILLIS_PER_MINUTE
        return when {
            minutes < 1L -> null
            minutes < MINUTES_PER_HOUR ->
                NativeStrings.quantity(context, R.plurals.widget_v2_span_min, minutes.toInt(), minutes)
            minutes < MINUTES_PER_DAY -> {
                val hours = (minutes / MINUTES_PER_HOUR).toInt()
                NativeStrings.quantity(context, R.plurals.widget_v2_span_hour, hours, hours)
            }
            else -> {
                val days = (minutes / MINUTES_PER_DAY).toInt()
                NativeStrings.quantity(context, R.plurals.widget_status_span_day, days, days)
            }
        }
    }

    fun spanHours(context: Context, hours: Int): String =
        NativeStrings.quantity(context, R.plurals.widget_v2_span_hour, hours, hours)

    fun compactDuration(context: Context, millis: Long): String {
        val minutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(1L)
        return when {
            minutes < MINUTES_PER_HOUR ->
                NativeStrings.quantity(context, R.plurals.native_widget_minutes, minutes.toInt(), minutes)
            minutes < MINUTES_PER_DAY -> {
                val hours = (minutes / MINUTES_PER_HOUR).toInt()
                NativeStrings.quantity(context, R.plurals.native_widget_hours, hours, hours)
            }
            else -> {
                val days = (minutes / MINUTES_PER_DAY).toInt()
                NativeStrings.quantity(context, R.plurals.native_widget_days, days, days)
            }
        }
    }

    fun shortWindowHours(context: Context, hours: Int): String =
        NativeStrings.quantity(context, R.plurals.usage_widget_short_hours, hours, hours)

    fun shortWindowDays(context: Context, days: Int): String =
        NativeStrings.quantity(context, R.plurals.usage_widget_short_days, days, days)
}
