package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * The Flutter 2.1.4 (build 62) readers of every schedule key in `FlutterSharedPreferences.xml`, written from the
 * released source and kept independent of [ScheduleCodec] on purpose:
 *
 * - `oc.scheduledPrompts.<p>`: lib/scheduling/store.dart `_State.decode` + models.dart `fromJson`/`validate`
 *   (Dart `as` casts, `byName` enums, identifier `^[a-zA-Z0-9_-]{1,128}$`, error codes `^[a-z][a-z0-9_]{0,79}$`).
 * - `oc.scheduleIndex.<p>`: ScheduledPromptStore.entries (`id` `[A-Za-z0-9_.-]{1,128}`, revision >= 0, dueAtMs > 0).
 * - `oc.scheduleClaims.<p>`: ScheduledPromptClaims.kt (`{"<id>:<rev>:<due>": {"at": ms, "finished": bool}}`).
 * - `oc.scheduleReceipts.<p>`: ScheduledPromptReceipts.kt (a JSON array of objects).
 * - `oc.draft.schedule.<id|new>.<p>`: KitDraft text (any string).
 *
 * A reader returns a description of what Flutter would see, or throws [Refused] where Flutter would report
 * `storage_corrupt` or skip the row. Keys outside this list are never read by Flutter.
 */
internal object Flutter214Schedules {
    class Refused(why: String) : Exception(why)

    /** What the Flutter build reads from one schedule. */
    data class Schedule(val id: String, val profileFingerprint: String, val enabled: Boolean, val nextRunAt: String?, val revision: Long)

    data class Run(val id: String, val scheduleId: String, val status: String, val errorCode: String?)

    data class Envelope(val schedules: List<Schedule>, val runs: List<Run>)

    data class IndexEntry(val id: String, val revision: Long, val dueAtMs: Long)

    data class ClaimRow(val at: Long, val finished: Boolean)

    private val identifier = Regex("^[a-zA-Z0-9_-]{1,128}$")
    private val nativeId = Regex("^[A-Za-z0-9_.-]{1,128}$")
    private val errorCode = Regex("^[a-z][a-z0-9_]{0,79}$")

    // Dart DateTime.parse for the `toUtc().toIso8601String()` forms (and explicit offsets).
    private val dartIso = Regex("""^\d{4,6}-\d{2}-\d{2}[T ]\d{2}(:\d{2}(:\d{2}([.,]\d{1,6})?)?)?(Z|[+-]\d{2}(:?\d{2})?)?$""")

    /** Every Flutter-known key family, by prefix. */
    val FLUTTER_PREFIXES: List<String> =
        listOf("oc.scheduledPrompts.", "oc.scheduleIndex.", "oc.scheduleClaims.", "oc.scheduleReceipts.", "oc.draft.schedule.")

    /** Decodes [value] stored at [key] exactly as Flutter 2.1.4 would, or throws [Refused]. */
    fun read(key: String, value: String): Any =
        when {
            key.startsWith("oc.scheduledPrompts.") -> envelope(value, key.removePrefix("oc.scheduledPrompts."))
            key.startsWith("oc.scheduleIndex.") -> index(value)
            key.startsWith("oc.scheduleClaims.") -> claims(value)
            key.startsWith("oc.scheduleReceipts.") -> receipts(value)
            key.startsWith("oc.draft.schedule.") -> value
            else -> throw Refused("not a Flutter schedule key")
        }

    fun envelope(raw: String, profileId: String): Envelope {
        if (raw.encodeToByteArray().size > 4 * 1024 * 1024) throw Refused("too large")
        val json = obj(json(raw))
        if (json["schemaVersion"] != 1L) throw Refused("schemaVersion")
        val schedules = list(json["schedules"]).map { schedule(obj(it)) }
        val runs = list(json["runs"]).map { run(obj(it)) }
        if (schedules.size > 100 || runs.size > 500 || schedules.any { it.second != profileId } ||
            schedules.map { it.first.id }.toSet().size != schedules.size || runs.map { it.id }.toSet().size != runs.size
        ) {
            throw Refused("envelope bounds")
        }
        digest(obj(json["digest"]))
        return Envelope(schedules.map { it.first }, runs)
    }

    private fun schedule(json: Map<*, *>): Pair<Schedule, String> {
        val id = str(json, "id")
        val profileId = str(json, "profileId")
        val fingerprint = str(json, "profileFingerprint")
        val prompt = str(json, "prompt")
        val projectPath = str(json, "projectPath")
        strOrNull(json, "workspace")
        val sessionId = strOrNull(json, "sessionId")
        strOrNull(json, "agent")
        val provider = strOrNull(json, "modelProviderId")
        val model = strOrNull(json, "modelId")
        recurrence(obj(json["recurrence"]))
        enum(str(json, "missedPolicy"), "catchUp", "skip")
        val enabled = bool(json, "enabled")
        val next = timeOrNull(json, "nextRunAt")
        time(json, "createdAt")
        val revision = int(json, "revision")
        if (!identifier.matches(id) || !identifier.matches(profileId) || fingerprint.isEmpty() || fingerprint.length > 256 ||
            prompt.isBlank() || prompt.length > 32000 || projectPath.isBlank() || projectPath.length > 4096 || revision < 1 ||
            (sessionId != null && sessionId.isEmpty()) || ((provider == null) != (model == null))
        ) {
            throw Refused("invalid scheduled prompt")
        }
        return Schedule(id, fingerprint, enabled, next, revision) to profileId
    }

    private fun recurrence(json: Map<*, *>) {
        val kind = enum(str(json, "kind"), "once", "daily", "weekdays", "interval")
        val at = timeOrNull(json, "at")
        var hour = 8L
        var minute = 0L
        var hours = 1L
        when (kind) {
            "once" -> at ?: throw Refused("once without at")
            "daily", "weekdays" -> {
                hour = int(json, "hour")
                minute = int(json, "minute")
            }
            "interval" -> {
                hours = int(json, "hours")
                at ?: throw Refused("interval without anchor")
            }
        }
        if (hour !in 0..23 || minute !in 0..59 || hours !in 1..168) throw Refused("invalid recurrence")
    }

    private fun run(json: Map<*, *>): Run {
        val id = str(json, "id")
        val scheduleId = str(json, "scheduleId")
        time(json, "scheduledAt")
        time(json, "startedAt")
        val status = enum(str(json, "status"), "running", "ran", "failed", "skipped")
        bool(json, "late")
        timeOrNull(json, "finishedAt")
        strOrNull(json, "sessionId")
        val code = strOrNull(json, "errorCode")
        if (id.isEmpty() || id.length > 512 || !identifier.matches(scheduleId) || (code != null && !errorCode.matches(code))) {
            throw Refused("invalid run")
        }
        return Run(id, scheduleId, status, code)
    }

    private fun digest(json: Map<*, *>) {
        bool(json, "enabled")
        val hour = int(json, "hour")
        val minute = int(json, "minute")
        timeOrNull(json, "lastDeliveredAt")
        if (hour !in 0..23 || minute !in 0..59) throw Refused("invalid digest")
    }

    /** Flutter skips an unreadable index row (it is never armed), so a row native writes must be readable. */
    fun index(raw: String): List<IndexEntry> =
        list(json(raw)).map { row ->
            val map = obj(row)
            val id = map["id"] as? String ?: throw Refused("index id")
            val revision = map["revision"] as? Long ?: throw Refused("index revision")
            val due = map["dueAtMs"] as? Long ?: throw Refused("index due")
            if (!nativeId.matches(id) || revision < 0 || due <= 0) throw Refused("index row")
            IndexEntry(id, revision, due)
        }

    fun claims(raw: String): Map<String, ClaimRow> =
        obj(json(raw)).entries.associate { (k, v) ->
            val key = k as String
            val parts = key.split(':')
            if (parts.size != 3 || !nativeId.matches(parts[0]) || parts[1].toLongOrNull() == null || parts[2].toLongOrNull() == null) {
                throw Refused("claim key")
            }
            val row = obj(v)
            val at = row["at"] as? Long ?: throw Refused("claim at")
            val finished = row["finished"] as? Boolean ?: throw Refused("claim finished")
            key to ClaimRow(at, finished)
        }

    fun receipts(raw: String): List<Map<*, *>> = list(json(raw)).map { obj(it) }

    // ---- Dart casts

    private fun json(raw: String): Any? = DartJson.decodeOrNull(raw) ?: throw Refused("not JSON")

    private fun obj(value: Any?): Map<*, *> = value as? Map<*, *> ?: throw Refused("not an object")

    private fun list(value: Any?): List<*> = value as? List<*> ?: throw Refused("not a list")

    private fun str(json: Map<*, *>, key: String): String = json[key] as? String ?: throw Refused(key)

    private fun strOrNull(json: Map<*, *>, key: String): String? =
        when (val v = json[key]) {
            null -> null
            is String -> v
            else -> throw Refused(key)
        }

    private fun int(json: Map<*, *>, key: String): Long = json[key] as? Long ?: throw Refused(key)

    private fun bool(json: Map<*, *>, key: String): Boolean = json[key] as? Boolean ?: throw Refused(key)

    private fun time(json: Map<*, *>, key: String): String = str(json, key).also { if (!dartIso.matches(it)) throw Refused(key) }

    private fun timeOrNull(json: Map<*, *>, key: String): String? = strOrNull(json, key)?.also { if (!dartIso.matches(it)) throw Refused(key) }

    private fun enum(value: String, vararg names: String): String = value.takeIf { it in names } ?: throw Refused("enum $value")
}
