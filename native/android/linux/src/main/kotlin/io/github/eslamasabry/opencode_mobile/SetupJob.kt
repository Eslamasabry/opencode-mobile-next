package io.github.eslamasabry.opencode_mobile

import org.json.JSONArray
import org.json.JSONObject

/**
 * The `::oc` lines install scripts print (docs/design/phone-setup-v2-2026-09-24.md,
 * "Progress protocol"). Pure, so the rules live in one small place:
 *
 *     ::oc stage <label…>
 *     ::oc bytes <done> <total>
 *     ::oc percent <0-100>
 *     ::oc version <text>
 *
 * Anything else, including a malformed `::oc` line, is plain log text.
 */
object SetupProtocol {
    sealed class Event {
        data class Stage(val label: String) : Event()
        data class Bytes(val done: Long, val total: Long) : Event()
        data class Percent(val value: Double) : Event()
        data class Version(val text: String) : Event()
    }

    private const val PREFIX = "::oc "

    fun parse(line: String): Event? {
        if (!line.startsWith(PREFIX)) return null
        val rest = line.substring(PREFIX.length).trim()
        val verb = rest.substringBefore(' ')
        val args = rest.substringAfter(' ', "").trim()
        return when (verb) {
            "stage" -> args.takeIf { it.isNotEmpty() }?.let { Event.Stage(it.take(120)) }
            "bytes" -> {
                val parts = args.split(Regex("\\s+"))
                val done = parts.getOrNull(0)?.toLongOrNull()
                val total = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (done == null || done < 0 || total < 0) null else Event.Bytes(done, total)
            }
            "percent" -> args.toDoubleOrNull()
                ?.takeIf { !it.isNaN() }
                ?.let { Event.Percent(it.coerceIn(0.0, 100.0)) }
            "version" -> args.takeIf { it.isNotEmpty() }?.let { Event.Version(it.take(80)) }
            else -> null
        }
    }
}

/** One component of a job, as setup.json stores it. */
class SetupComponentStatus(
    val id: String,
    /** pending | running | done | failed | skipped */
    var state: String = "pending",
    val weight: Double = 1.0,
    var stage: String? = null,
    var done: Long? = null,
    var total: Long? = null,
    var percent: Double? = null,
    var version: String? = null,
    var error: String? = null,
    var startedAt: Long? = null,
    var endedAt: Long? = null,
    /** Opaque values the app gave the step and reads back (job steps use it). */
    val data: Map<String, String> = emptyMap(),
) {
    /** A new stage starts measuring from nothing. */
    fun apply(event: SetupProtocol.Event) {
        when (event) {
            is SetupProtocol.Event.Stage -> {
                stage = event.label
                done = null
                total = null
                percent = null
            }
            is SetupProtocol.Event.Bytes -> {
                done = event.done
                total = event.total
            }
            is SetupProtocol.Event.Percent -> percent = event.value
            is SetupProtocol.Event.Version -> version = event.text
        }
    }

    /** The most this component has shown while running, kept in memory only. */
    private var reached = 0.0

    /**
     * Its share of the job: real bytes or percent, half-way on a bare stage.
     * A new stage measures from zero again; the notification must not go
     * backwards for that, so a running component never shows less than it
     * already reached.
     */
    fun fraction(): Double = when (state) {
        "done", "skipped" -> 1.0
        "running" -> {
            val t = total
            val d = done
            val now = when {
                t != null && t > 0 && d != null -> (d.toDouble() / t).coerceIn(0.0, 1.0)
                percent != null -> (percent!! / 100).coerceIn(0.0, 1.0)
                else -> 0.5
            }.coerceAtMost(0.98)
            reached = maxOf(reached, now)
            reached
        }
        else -> 0.0
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("state", state)
        put("weight", weight)
        put("stage", stage ?: JSONObject.NULL)
        put("done", done ?: JSONObject.NULL)
        put("total", total ?: JSONObject.NULL)
        put("percent", percent ?: JSONObject.NULL)
        put("version", version ?: JSONObject.NULL)
        put("error", error ?: JSONObject.NULL)
        put("startedAt", startedAt ?: JSONObject.NULL)
        put("endedAt", endedAt ?: JSONObject.NULL)
        if (data.isNotEmpty()) put("data", JSONObject(data))
    }

    companion object {
        fun fromJson(id: String, json: JSONObject): SetupComponentStatus {
            fun str(key: String) = if (json.isNull(key)) null else json.optString(key)
            fun long(key: String) = if (json.isNull(key) || !json.has(key)) null else json.optLong(key)
            val data = json.optJSONObject("data")
            return SetupComponentStatus(
                id = id,
                state = json.optString("state", "pending"),
                weight = json.optDouble("weight", 1.0),
                stage = str("stage"),
                done = long("done"),
                total = long("total"),
                percent = if (json.isNull("percent") || !json.has("percent")) null else json.optDouble("percent"),
                version = str("version"),
                error = str("error"),
                startedAt = long("startedAt"),
                endedAt = long("endedAt"),
                data = data?.keys()?.asSequence()?.associateWith { data.optString(it) } ?: emptyMap(),
            )
        }
    }
}

/**
 * The whole job: what `files/linux/setup.json` holds and `setupStatus`
 * returns. Components keep their install order in `order`, because a JSON
 * object's keys have none.
 */
class SetupJobState(
    val jobId: String,
    /** running | done | failed | cancelled | interrupted */
    var state: String,
    val components: List<SetupComponentStatus>,
    var current: String? = null,
    val startedAt: Long,
    var updatedAt: Long = startedAt,
    var error: String? = null,
    var logTail: String = "",
    var errorCode: String? = null,
) {
    fun component(id: String) = components.first { it.id == id }

    /** 0..1 over every component, weighted as the app asked. */
    fun overall(): Double {
        val total = components.sumOf { it.weight }
        if (total <= 0) return 0.0
        return components.sumOf { it.weight * it.fraction() } / total
    }

    /**
     * The process that ran this job is gone (the app was killed or the phone
     * restarted): nothing is running it any more. The component it was on
     * goes back to pending, keeping its numbers so the app can show how far
     * it got.
     */
    fun markInterrupted(now: Long) {
        if (state != "running") return
        state = "interrupted"
        for (component in components) {
            if (component.state == "running") component.state = "pending"
        }
        updatedAt = now
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("jobId", jobId)
        put("state", state)
        put("current", current ?: JSONObject.NULL)
        put("order", JSONArray(components.map { it.id }))
        put("components", JSONObject().apply { components.forEach { put(it.id, it.toJson()) } })
        put("overall", overall())
        put("startedAt", startedAt)
        put("updatedAt", updatedAt)
        put("error", error ?: JSONObject.NULL)
        put("logTail", logTail)
        put("errorCode", errorCode ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): SetupJobState {
            val map = json.optJSONObject("components") ?: JSONObject()
            val order = json.optJSONArray("order")
            val ids = if (order != null) {
                (0 until order.length()).map { order.getString(it) }
            } else {
                map.keys().asSequence().toList()
            }
            return SetupJobState(
                jobId = json.optString("jobId"),
                state = json.optString("state", "interrupted"),
                components = ids.filter { map.has(it) }.map {
                    SetupComponentStatus.fromJson(it, map.getJSONObject(it))
                },
                current = if (json.isNull("current")) null else json.optString("current"),
                startedAt = json.optLong("startedAt"),
                updatedAt = json.optLong("updatedAt"),
                error = if (json.isNull("error")) null else json.optString("error"),
                logTail = json.optString("logTail"),
                errorCode = if (json.isNull("errorCode")) null else json.optString("errorCode"),
            )
        }
    }
}

/** The last [cap] characters of the job's log, cut at a line start. */
class LogTail(private val cap: Int = 4096) {
    private val text = StringBuilder()

    @Synchronized
    fun append(line: String) {
        text.append(line).append('\n')
        if (text.length > cap * 2) trim()
    }

    @Synchronized
    fun value(): String {
        if (text.length > cap) trim()
        return text.toString()
    }

    private fun trim() {
        val cut = text.length - cap
        val lineStart = text.indexOf("\n", cut).let { if (it < 0) cut else it + 1 }
        text.delete(0, lineStart)
    }

    @Synchronized
    fun clear() = text.setLength(0)
}
