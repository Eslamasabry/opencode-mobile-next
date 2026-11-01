package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.R
import io.github.eslamasabry.oc.android.widgets.NativeStrings

internal object UsageWidgetText {
    private const val SECONDS_PER_HOUR = 3600L
    private const val SECONDS_PER_DAY = 86400L
    private const val WEEK_MIN_SECONDS = 6L * SECONDS_PER_DAY
    private const val WEEK_MAX_SECONDS = 8L * SECONDS_PER_DAY
    private const val MONTH_MIN_SECONDS = 28L * SECONDS_PER_DAY
    private const val MONTH_MAX_SECONDS = 31L * SECONDS_PER_DAY
    private const val HOURS_LABEL_LIMIT = 48
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * 24

    fun detailText(
        context: Context,
        plan: List<UsageWidgetRow>,
        now: Long,
        multiProfile: Boolean
    ): String {
        val row = plan.first()
        val parts = mutableListOf<String>()
        if (row.signInOnly) {
            parts += setupDetail(context, row)
        } else {
            parts += numericDetail(context, plan, now)
        }
        if (multiProfile && row.profileName.isNotBlank()) parts += row.profileName
        return parts.joinToString(" · ")
    }

    private fun setupDetail(context: Context, row: UsageWidgetRow): String = NativeStrings.get(
        context,
        when {
            row.authNeeded -> R.string.usage_widget_auth
            row.fixAction == "set_up" -> R.string.usage_widget_cant_read
            row.fixAction == "add_key" -> R.string.usage_widget_no_key
            else -> R.string.usage_widget_not_signed_in
        }
    )

    private fun numericDetail(context: Context, plan: List<UsageWidgetRow>, now: Long): List<String> {
        val row = plan.first()
        val parts = mutableListOf<String>()
        // The tightest window's reset; numbers that are old say so instead.
        val tight = plan.filter { it.resetsAt > now }.minByOrNull { it.left }
        val outdated = row.forceStale || now - row.fetchedAt > UsageWidgetPlans.STALE_AFTER_MS ||
            plan.any { it.resetsAt > 0L && now >= it.resetsAt }
        if (row.authNeeded) {
            parts += NativeStrings.get(context, R.string.usage_widget_auth)
        }
        if (outdated || row.authNeeded) {
            parts += NativeStrings.get(
                context,
                R.string.usage_widget_updated,
                duration(context, now - row.fetchedAt)
            )
        } else if (tight != null) {
            parts += NativeStrings.get(
                context,
                R.string.usage_widget_resets,
                duration(context, tight.resetsAt - now)
            )
        }
        return parts
    }

    fun providerName(context: Context, provider: String, displayName: String): String =
        when (provider) {
            "codex" -> NativeStrings.get(context, R.string.usage_widget_provider_codex)
            "minimax" -> NativeStrings.get(context, R.string.usage_widget_provider_minimax)
            "glm" -> NativeStrings.get(context, R.string.usage_widget_provider_glm)
            "claude" -> NativeStrings.get(context, R.string.usage_widget_provider_claude)
            else -> displayName.ifBlank { provider }
        }

    fun shortWindowName(context: Context, seconds: Long, label: String): String {
        val hours = seconds / SECONDS_PER_HOUR
        return when {
            seconds <= 0L && label.isNotBlank() -> label
            seconds in WEEK_MIN_SECONDS..WEEK_MAX_SECONDS ->
                NativeStrings.get(context, R.string.usage_widget_short_week)
            seconds in MONTH_MIN_SECONDS..MONTH_MAX_SECONDS ->
                NativeStrings.get(context, R.string.usage_widget_short_month)
            hours < HOURS_LABEL_LIMIT && hours >= 1 ->
                NativeStrings.get(context, R.string.usage_widget_short_hours, hours.toInt())
            else -> NativeStrings.get(
                context,
                R.string.usage_widget_short_days,
                (seconds / SECONDS_PER_DAY).coerceAtLeast(1).toInt()
            )
        }
    }

    private fun duration(context: Context, millis: Long): String {
        val minutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(0)
        return when {
            minutes < 1 -> NativeStrings.get(context, R.string.native_widget_now)
            minutes < MINUTES_PER_HOUR -> NativeStrings.get(context, R.string.native_widget_minutes, minutes)
            minutes < MINUTES_PER_DAY ->
                NativeStrings.get(context, R.string.native_widget_hours, minutes / MINUTES_PER_HOUR)
            else -> NativeStrings.get(context, R.string.native_widget_days, minutes / MINUTES_PER_DAY)
        }
    }
}
