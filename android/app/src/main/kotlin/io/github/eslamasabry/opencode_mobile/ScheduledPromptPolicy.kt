package io.github.eslamasabry.opencode_mobile

/** Platform-independent identity and bounded execution policy. */
internal object ScheduledPromptPolicy {
    const val WORKER_BUDGET_MS = 480_000L
    const val DART_BUDGET_MS = 420_000L
    private const val EXPEDITED_MIN_SDK = 31
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private const val WEEK_LOOKAHEAD_DAYS = 7L
    private const val LAST_WEEKDAY = 5
    private val identifier = Regex("[A-Za-z0-9_.-]{1,128}")
    fun validId(value: String) = identifier.matches(value)
    fun validOccurrence(id: String, revision: Long, due: Long) = validId(id) && revision >= 0 && due > 0
    fun occurrence(id: String, revision: Long, due: Long): String {
        require(validOccurrence(id, revision, due))
        return "$id:$revision:$due"
    }
    fun isDue(due: Long, now: Long) = due <= now
    // WorkManager emulates expedited jobs with a foreground service below
    // API 31. This bounded worker deliberately has no such path.
    fun expediteAlarm(sdk: Int, fromAlarm: Boolean, delay: Long, digest: Boolean) =
        sdk >= EXPEDITED_MIN_SDK && fromAlarm && delay == 0L && !digest
    fun remaining(started: Long, now: Long): Long =
        (WORKER_BUDGET_MS - (now - started).coerceAtLeast(0)).coerceIn(0, WORKER_BUDGET_MS)
    fun nextWeekday(hour: Int, minute: Int, after: Long, zone: java.time.ZoneId): Long =
        nextWallClock(hour, minute, after, zone, weekdaysOnly = true)

    /** Every day at a wall-clock time, kept across daylight-saving changes. */
    fun nextDaily(hour: Int, minute: Int, after: Long, zone: java.time.ZoneId): Long =
        nextWallClock(hour, minute, after, zone, weekdaysOnly = false)

    private fun nextWallClock(
        hour: Int, minute: Int, after: Long, zone: java.time.ZoneId, weekdaysOnly: Boolean,
    ): Long {
        require(hour in 0..LAST_HOUR && minute in 0..LAST_MINUTE)
        val now = java.time.Instant.ofEpochMilli(after).atZone(zone)
        for (offset in 0L..WEEK_LOOKAHEAD_DAYS) {
            val day = now.toLocalDate().plusDays(offset)
            if (weekdaysOnly && day.dayOfWeek.value > LAST_WEEKDAY) continue
            val candidate = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            if (candidate > after) return candidate
        }
        error("invalid_schedule")
    }
    fun canClaim(currentRevision: Long?, currentDue: Long?, revision: Long, due: Long, claimed: Boolean) =
        !claimed && currentRevision == revision && currentDue == due
}
