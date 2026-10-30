package io.github.eslamasabry.opencode_mobile

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

private const val DIGEST_ID = "__digest"
private const val SCHEDULED_PREFIX = "flutter.oc.scheduledPrompts."

internal fun ScheduledPromptStore.profiles(): List<String> = synchronized(ScheduledPromptStore.lock) {
    prefs.all.keys.filter { it.startsWith(SCHEDULED_PREFIX) }
        .map { it.removePrefix(SCHEDULED_PREFIX) }
}

internal fun ScheduledPromptStore.all(): List<ScheduledPromptStore.Entry> = synchronized(ScheduledPromptStore.lock) {
    prefs.all.keys.filter { it.startsWith("flutter.oc.scheduleIndex.") }
        .flatMap { entries(it.removePrefix("flutter.oc.scheduleIndex.")) }
}

internal fun ScheduledPromptStore.occurrenceCurrent(entry: ScheduledPromptStore.Entry): Boolean =
    synchronized(ScheduledPromptStore.lock) {
        if (!profileExists(entry.profileId)) return@synchronized false
        currentEntries(entry.profileId).any {
            it.id == entry.id && it.revision == entry.revision && it.due == entry.due
        }
    }

internal fun ScheduledPromptStore.currentEntries(profile: String): List<ScheduledPromptStore.Entry> =
    synchronized(ScheduledPromptStore.lock) {
        val raw = prefs.getString("$SCHEDULED_PREFIX$profile", null) ?: return@synchronized emptyList()
        val state = JSONObject(raw)
        require(state.optInt("schemaVersion") == 1)
        val entries = scheduleEntries(profile, state.getJSONArray("schedules")).toMutableList()
        val digest = state.getJSONObject("digest")
        if (digest.optBoolean("enabled")) entries.add(digestEntry(profile, digest))
        entries
    }

private fun scheduleEntries(profile: String, array: JSONArray): List<ScheduledPromptStore.Entry> =
    (0 until array.length()).mapNotNull { i ->
        val row = array.getJSONObject(i)
        if (!row.optBoolean("enabled") || row.isNull("nextRunAt")) return@mapNotNull null
        val id = row.getString("id")
        require(ScheduledPromptPolicy.validId(id))
        val revision = row.getLong("revision")
        ScheduledPromptStore.Entry(profile, id, revision, Instant.parse(row.getString("nextRunAt")).toEpochMilli())
    }

private fun ScheduledPromptStore.digestEntry(profile: String, digest: JSONObject): ScheduledPromptStore.Entry {
    val now = ZonedDateTime.now()
    var due = now.withHour(digest.getInt("hour")).withMinute(digest.getInt("minute")).withSecond(0).withNano(0)
    if (!due.isAfter(now)) due = due.plusDays(1)
    val previous = entries(profile).firstOrNull { it.id == DIGEST_ID }
    return previous?.takeIf { digestStillPending(profile, it, digest, now.zone) }
        ?: ScheduledPromptStore.Entry(profile, DIGEST_ID, 0, due.toInstant().toEpochMilli())
}

/** An overdue digest stays due until its claim finishes or its delivery time moves. */
private fun ScheduledPromptStore.digestStillPending(
    profile: String,
    previous: ScheduledPromptStore.Entry,
    digest: JSONObject,
    zone: ZoneId,
): Boolean {
    val delivered = digest.optString("lastDeliveredAt").takeIf { it.isNotEmpty() && it != "null" }
        ?.let { Instant.parse(it).toEpochMilli() } ?: 0L
    val previousLocal = Instant.ofEpochMilli(previous.due).atZone(zone)
    val claims = claimsFor(profile)
    val sameTime = previousLocal.hour == digest.getInt("hour") && previousLocal.minute == digest.getInt("minute")
    val unfinished = claims.optJSONObject(previous.occurrence)?.optBoolean("finished") != true
    return previous.due > delivered && sameTime && unfinished
}

internal fun ScheduledPromptStore.rebaseCalendar(profile: String) = synchronized(ScheduledPromptStore.lock) {
    if (!profileExists(profile)) return@synchronized
    val key = "$SCHEDULED_PREFIX$profile"
    val raw = prefs.getString(key, null) ?: return@synchronized
    val state = JSONObject(raw)
    require(state.optInt("schemaVersion") == 1)
    val now = System.currentTimeMillis()
    val schedules = state.getJSONArray("schedules")
    for (index in 0 until schedules.length()) rebaseRow(schedules.getJSONObject(index), now)
    check(prefs.edit().putString(key, state.toString()).commit())
}

private fun rebaseRow(row: JSONObject, now: Long) {
    val recurrence = row.getJSONObject("recurrence")
    val kind = recurrence.optString("kind")
    val wallClock = row.optBoolean("enabled") && (kind == "weekdays" || kind == "daily") &&
        !row.isNull("nextRunAt")
    if (!wallClock) return
    val previous = Instant.parse(row.getString("nextRunAt")).toEpochMilli()
    // Preserve overdue occurrences for the user's catch-up/skip policy.
    if (previous <= now) return
    val hour = recurrence.getInt("hour")
    val minute = recurrence.getInt("minute")
    val zone = ZoneId.systemDefault()
    val next = if (kind == "daily") {
        ScheduledPromptPolicy.nextDaily(hour, minute, now, zone)
    } else {
        ScheduledPromptPolicy.nextWeekday(hour, minute, now, zone)
    }
    row.put("nextRunAt", Instant.ofEpochMilli(next).toString())
}
