package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.WidgetLayoutPolicy.Ago
import io.github.eslamasabry.opencode_mobile.WidgetLayoutPolicy.Elapsed
import io.github.eslamasabry.opencode_mobile.WidgetLayoutPolicy.Lead
import io.github.eslamasabry.opencode_mobile.WidgetLayoutPolicy.Summary

/** Which widget size class a launcher-reported size falls into. */
object WidgetSizePolicy {
    private const val MAX_LAUNCHER_SIZE_DP = 2000f
    private const val MAX_LAUNCHER_VARIANTS = 16

    /** Legacy (pre Android 12) launcher bounds, used when no size list is reported. */
    data class Bounds(val minWidth: Int, val maxWidth: Int, val minHeight: Int, val maxHeight: Int)

    /** Launcher sizes are untrusted input; RemoteViews supports at most 16 variants. */
    fun launcherSizes(sizes: List<Pair<Float, Float>>): List<Pair<Int, Int>> = sizes.asSequence()
        .filter { (width, height) -> usable(width) && usable(height) }
        .map { (width, height) -> width.toInt() to height.toInt() }
        .distinct().take(MAX_LAUNCHER_VARIANTS).toList()

    private fun usable(dp: Float): Boolean = dp in 1f..MAX_LAUNCHER_SIZE_DP

    /**
     * The one size the widget has right now. Some hosts (ZTE MiFavor) pick the wrong entry
     * from a size-keyed RemoteViews map and drew the short landscape strip inside a 4x2 box,
     * so the widget renders once for this size instead. Launcher3 reports a portrait entry
     * (narrower, taller) and a landscape one (wider, shorter); without that list, portrait is
     * MIN_WIDTH x MAX_HEIGHT and landscape MAX_WIDTH x MIN_HEIGHT. Null: nothing usable.
     */
    fun currentSize(launcher: List<Pair<Int, Int>>, portrait: Boolean, legacy: Bounds): Pair<Int, Int>? {
        if (launcher.isNotEmpty()) {
            return if (portrait) launcher.maxWith(compareBy({ it.second }, { it.first }))
            else launcher.maxWith(compareBy({ it.first }, { it.second }))
        }
        val width = if (portrait) legacy.minWidth else legacy.maxWidth
        val height = if (portrait) legacy.maxHeight else legacy.minHeight
        return if (width > 0 && height > 0) width to height else null
    }

    /** Narrow is two cells wide; wide is four. Launchers report 2 cells as ~110-180dp
     * and 4 cells as ~250-330dp. Short is one row (about 70-110dp). A list needs four rows. */
    private const val WIDE_MIN_WIDTH_DP = 250
    private const val MEDIUM_MIN_HEIGHT_DP = 130
    private const val LIST_MIN_HEIGHT_DP = 240

    /** Before Android 12 a launcher reports only the minimum height of the widget's cells, about
     * 40 to 70dp for one row and 110 to 180dp for two; Android 12 and later report real sizes
     * (a row is about 100dp there). Lift a legacy two-row minimum to the real two-row height so it
     * is not taken for a strip. */
    fun legacyHeight(minHeightDp: Int): Int =
        if (minHeightDp in LEGACY_TWO_ROWS_MIN_DP until MEDIUM_MIN_HEIGHT_DP) MEDIUM_MIN_HEIGHT_DP else minHeightDp
    private const val LEGACY_TWO_ROWS_MIN_DP = 95

    fun size(widthDp: Int, heightDp: Int): WidgetV2Policy.Size {
        val wide = widthDp >= WIDE_MIN_WIDTH_DP
        return when {
            heightDp < MEDIUM_MIN_HEIGHT_DP -> if (wide) WidgetV2Policy.Size.STRIP else WidgetV2Policy.Size.TINY
            !wide -> WidgetV2Policy.Size.CARD
            heightDp < LIST_MIN_HEIGHT_DP -> WidgetV2Policy.Size.WIDE
            else -> WidgetV2Policy.Size.LIST
        }
    }
}

/** Usage widget bar colours, type sizes and roles. */
object WidgetUsageStyle {
    private const val USAGE_TRACK_ALPHA = 0x38
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24
    private const val LONE_TITLE_SP = 16f
    private const val LONE_COLUMN_SP = 22f
    private const val LONE_DETAIL_SP = 14f
    private const val SHARED_TITLE_SP = 13f
    private const val SHARED_COLUMN_SP = 12f
    private const val SHARED_DETAIL_SP = 10f
    private const val RESERVED_MIN_SATURATION = 0.2f
    private const val AMBER_MIN_HUE = 70f
    private const val RED_MIN_HUE = 330f

    /** Usage bar colours: the part left is drawn in the text ink on a faint track
     * of the same ink, so 20% left and 100% left never look alike. A stale or
     * signed-out reading draws its fill in the muted ink instead. A small remaining
     * quota is measured data, not a request for action, so it never turns amber. */
    fun usageBar(text: Int, muted: Int, left: Int, stale: Boolean): Pair<Int, Int> {
        val fill = if (stale || left <= 0) muted else text
        val track = (text and RGB_MASK) or (USAGE_TRACK_ALPHA shl ALPHA_SHIFT)
        return fill to track
    }

    /** Text sizes in sp for a usage row (title, column, detail). Every render sets
     * them, because a launcher re-applies new RemoteViews over the old views and a
     * size set for a lone provider would otherwise stick when a second one appears. */
    fun usageTextSizes(loneInTallWidget: Boolean): Triple<Float, Float, Float> =
        if (loneInTallWidget) Triple(LONE_TITLE_SP, LONE_COLUMN_SP, LONE_DETAIL_SP)
        else Triple(SHARED_TITLE_SP, SHARED_COLUMN_SP, SHARED_DETAIL_SP)

    fun usageRole(left: Int): String = when {
        left <= 0 -> "neutral"
        else -> "running"
    }

    /** Amber belongs to needs-you; red is never a widget status/accent. */
    fun reservedStatusHue(hue: Float, saturation: Float): Boolean =
        saturation > RESERVED_MIN_SATURATION && (hue < AMBER_MIN_HUE || hue > RED_MIN_HUE)
}

/** Which state words and ordering a conversation row gets. */
object WidgetStatusRules {
    fun chronometerBase(startedAt: Long, wallNow: Long, elapsedNow: Long): Long =
        elapsedNow - (wallNow - startedAt).coerceIn(0L, elapsedNow)

    /** Cached busy state cannot prove a turn is still running after disconnect. */
    fun timerTicks(busy: Boolean, startedAt: Long, connected: Boolean): Boolean =
        connected && busy && startedAt > 0

    /** A project row from AI Team is not a conversation. With no conversations
     * to show it stays out of the list unless it is waiting on the user. */
    fun showRow(isTeam: Boolean, teamAction: String, requestID: String, anyConversation: Boolean): Boolean =
        !isTeam || anyConversation || teamAction.isNotBlank() || requestID.isNotBlank()

    fun rowNeedsSettings(connection: WidgetV2Policy.Connection): Boolean =
        connection == WidgetV2Policy.Connection.BACKGROUND_SETTINGS

    fun status(request: Boolean, busy: Boolean, connected: Boolean): String = when {
        request -> "needsYou"
        busy && connected -> "working"
        busy -> "lastWorking"
        else -> "ready"
    }

    /** Which header phrase to show: only non-zero counts, or a calm one when nothing needs attention. */
    fun summary(running: Int, needs: Int): Summary = when {
        running > 0 && needs > 0 -> Summary.BOTH
        needs > 0 -> Summary.NEEDS
        running > 0 -> Summary.RUNNING
        else -> Summary.IDLE
    }

    /** A turn waiting for the person is "needs you", not also "working". */
    fun workingCount(running: Int, busyAndWaiting: Int): Int = (running - busyAndWaiting).coerceAtLeast(0)

    /** Needs you first, then working, then the rest; the incoming order is kept inside each. */
    fun rank(request: Boolean, busy: Boolean): Int = if (request) 0 else if (busy) 1 else 2

    /** What a working card says under its title: the live step when one is known, else the latest
     * reply text, else nothing. The same text the list's top row carries. */
    fun liveBody(step: String, lastReply: String): String = step.ifBlank { lastReply }

    /** Which words lead a card or row. Offline wins over everything but a request. */
    fun lead(request: Boolean, busy: Boolean, offline: Boolean): Lead = when {
        request -> Lead.NEEDS_YOU
        offline -> Lead.OFFLINE
        busy -> Lead.WORKING
        else -> Lead.READY
    }
}

/** Elapsed-time wording for a conversation row. */
object WidgetElapsedPolicy {
    private const val MS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60
    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_DAY = 86_400_000L
    private const val DAYS_PER_WEEK = 7

    /** "Working · 3 min": minutes or hours since the turn began; null under a minute. */
    fun sinceUnit(nowMs: Long, startedMs: Long): Pair<String, Int>? {
        val minutes = ((nowMs - startedMs).coerceAtLeast(0L) / MS_PER_MINUTE).toInt()
        return when {
            startedMs <= 0 || minutes < 1 -> null
            minutes < MINUTES_PER_HOUR -> "min" to minutes
            else -> "hour" to minutes / MINUTES_PER_HOUR
        }
    }

    /** When something last moved, in the words of a phone: 3 min, 1 h, yesterday, Mon, 3 Oct. */
    fun elapsed(
        nowMs: Long,
        thenMs: Long,
        zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        locale: java.util.Locale = java.util.Locale.getDefault(),
    ): Elapsed {
        val diff = (nowMs - thenMs).coerceAtLeast(0L)
        val minutes = diff / MS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        fun midnight(ms: Long): Long = java.util.Calendar.getInstance(zone).apply {
            timeInMillis = ms
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = ((midnight(nowMs) - midnight(thenMs) + MS_PER_HOUR) / MS_PER_DAY).toInt()
        fun format(pattern: String) = java.text.SimpleDateFormat(pattern, locale).apply { timeZone = zone }
            .format(java.util.Date(thenMs))
        return when {
            minutes < 1 -> Elapsed(Ago.NOW)
            hours < 1 -> Elapsed(Ago.MINUTES, minutes.toInt())
            days < 1 -> Elapsed(Ago.HOURS, hours.toInt())
            days == 1 -> Elapsed(Ago.YESTERDAY)
            days < DAYS_PER_WEEK -> Elapsed(Ago.WEEKDAY, label = format("EEE"))
            else -> Elapsed(Ago.DATE, label = format("d MMM"))
        }
    }
}
