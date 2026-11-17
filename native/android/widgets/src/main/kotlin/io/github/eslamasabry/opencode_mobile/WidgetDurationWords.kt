package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.NativeStrings
import io.github.eslamasabry.oc.android.widgets.R

/** Base fe32cd0628 behavior behind the new WidgetDurationWords interface (red proof). */
internal object WidgetDurationWords {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * 24L

    fun ago(context: Context, nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return ""
        val e = WidgetElapsedPolicy.elapsed(nowMs, thenMs)
        return when (e.kind) {
            WidgetLayoutPolicy.Ago.NOW -> NativeStrings.get(context, R.string.widget_v2_ago_now)
            WidgetLayoutPolicy.Ago.MINUTES -> NativeStrings.get(context, R.string.widget_v2_ago_min, e.count)
            WidgetLayoutPolicy.Ago.HOURS -> NativeStrings.get(context, R.string.widget_v2_ago_hour, e.count)
            WidgetLayoutPolicy.Ago.YESTERDAY -> NativeStrings.get(context, R.string.widget_v2_ago_yesterday)
            else -> e.label
        }
    }

    fun since(context: Context, nowMs: Long, startedAtMs: Long): String {
        val since = WidgetElapsedPolicy.sinceUnit(nowMs, startedAtMs) ?: return ""
        return when (since.first) {
            "min" -> NativeStrings.get(context, R.string.widget_v2_span_min, since.second)
            else -> NativeStrings.get(context, R.string.widget_v2_span_hour, since.second)
        }
    }

    fun spanSince(context: Context, nowMs: Long, sinceMs: Long): String? {
        if (sinceMs <= 0L) return null
        val minutes = (nowMs - sinceMs).coerceAtLeast(0L) / MILLIS_PER_MINUTE
        return when {
            minutes < 1L -> null
            minutes < MINUTES_PER_HOUR -> NativeStrings.get(context, R.string.widget_v2_span_min, minutes.toInt())
            minutes < MINUTES_PER_DAY -> NativeStrings.get(context, R.string.widget_v2_span_hour, (minutes / MINUTES_PER_HOUR).toInt())
            else -> NativeStrings.get(context, R.string.widget_status_span_day, (minutes / MINUTES_PER_DAY).toInt())
        }
    }

    fun compactDuration(context: Context, millis: Long): String {
        val minutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(0L)
        return when {
            minutes < 1L -> NativeStrings.get(context, R.string.native_widget_now)
            minutes < MINUTES_PER_HOUR -> NativeStrings.get(context, R.string.native_widget_minutes, minutes)
            minutes < MINUTES_PER_DAY -> NativeStrings.get(context, R.string.native_widget_hours, minutes / MINUTES_PER_HOUR)
            else -> NativeStrings.get(context, R.string.native_widget_days, minutes / MINUTES_PER_DAY)
        }
    }
}
