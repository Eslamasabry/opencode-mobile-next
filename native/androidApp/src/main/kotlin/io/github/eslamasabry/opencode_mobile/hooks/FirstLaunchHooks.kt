package io.github.eslamasabry.opencode_mobile.hooks

import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * E00-S07: the first-launch hooks after the Flutter → native update (plan 05 §7.9, marker `hooks.firstLaunch.v1`).
 * They put back what Android keeps outside the app's files: the per-app language, widget and tile drawings, pinned
 * shortcuts, armed schedules, live mode and the phone server that was running.
 *
 * - They run only when the update adopted Flutter data ([AdoptedSource]: the journal says `source.kind = flutter` and
 *   `native.migration.v1` is complete). A fresh install or the preview package never runs them.
 * - They run once. The marker is written before the first step (attempts + 1), and each finished step is written at
 *   once, so a launch killed half-way resumes at the first unfinished step.
 * - A step that cannot finish yet (the stores are not readable, the app is not in front for a foreground start)
 *   returns [StepResult.Later] and is tried on the next launch. After [MAX_ATTEMPTS] launches the marker is closed,
 *   with the unfinished steps listed, so a step that can never finish does not run forever.
 *
 * The marker is its own file next to the migration journal (`files/native/first-launch.json`), so this runner never
 * writes the runner-owned journal. It holds step ids, states, counts and times only.
 */
internal class FirstLaunchHooks(
    private val source: AdoptedSource,
    private val marker: HookMarkerStore,
    private val steps: List<HookStep>,
    private val nowMs: () -> Long,
) {
    /** What one [run] did; for the log and the tests. */
    data class Report(val ran: List<String>, val later: List<String>, val state: HookState)

    suspend fun run(): Report? {
        if (!source.adoptedFlutterData()) return null
        val start = marker.read()
        return if (start.state == HookState.Done) Report(emptyList(), emptyList(), HookState.Done) else resume(start)
    }

    private suspend fun resume(start: HookRecord): Report {
        var current = start.copy(state = HookState.Running, attempts = start.attempts + 1, at = nowMs())
        marker.write(current) // before any step: a crash still counts as an attempt
        val ran = mutableListOf<String>()
        val later = mutableListOf<String>()
        for (step in steps) {
            if (step.id in current.done) continue
            val result =
                try {
                    step.run()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    StepResult.Later
                }
            when (result) {
                StepResult.Done -> {
                    ran += step.id
                    current = current.copy(done = current.done + step.id, at = nowMs())
                    marker.write(current)
                }
                StepResult.Later -> later += step.id
            }
        }
        val finished = later.isEmpty() || current.attempts >= MAX_ATTEMPTS
        if (finished) {
            current = current.copy(state = HookState.Done, gaveUp = later.toSet(), at = nowMs())
            marker.write(current)
        }
        return Report(ran, later, current.state)
    }

    companion object {
        const val MARKER_ID: String = "hooks.firstLaunch.v1"
        const val MAX_ATTEMPTS: Int = 3
    }
}

/** One hook. Must be idempotent: a killed launch runs it again. */
internal interface HookStep {
    val id: String

    suspend fun run(): StepResult
}

internal enum class StepResult {
    Done,

    /** Not possible now (stores not readable, app not in front); try on the next launch. */
    Later,
}

internal enum class HookState(val wire: String) {
    Pending("pending"),
    Running("running"),
    Done("done");

    companion object {
        fun of(wire: String?): HookState = entries.firstOrNull { it.wire == wire } ?: Pending
    }
}

internal data class HookRecord(
    val state: HookState = HookState.Pending,
    val attempts: Int = 0,
    val at: Long? = null,
    val done: Set<String> = emptySet(),
    val gaveUp: Set<String> = emptySet(),
)

/** True when this install adopted Flutter data and the day-1 migration finished. */
internal fun interface AdoptedSource {
    fun adoptedFlutterData(): Boolean
}

internal interface HookMarkerStore {
    fun read(): HookRecord

    fun write(record: HookRecord)
}

/** Reads `files/native/migration.json` (plan 05 §7.2) without ever writing it. Unknown or unreadable: not adopted. */
internal class JournalSource(private val journal: File) : AdoptedSource {
    override fun adoptedFlutterData(): Boolean {
        val root = readObject(journal) ?: return false
        val kind = (root["source"] as? JsonObject)?.string("kind")
        val v1 = ((root["migrations"] as? JsonObject)?.get("native.migration.v1") as? JsonObject)
        return kind == "flutter" && (v1?.get("complete") as? JsonPrimitive)?.booleanOrNull == true
    }
}

/**
 * `files/native/first-launch.json`: `{"schema":1,"hooks":{"hooks.firstLaunch.v1":{...}}}`, written atomically (temp
 * file, sync, rename). An unreadable file reads as a new record, so the hooks run again (they are idempotent).
 */
internal class FileHookMarker(private val file: File) : HookMarkerStore {
    override fun read(): HookRecord {
        val hook = (readObject(file)?.get("hooks") as? JsonObject)?.get(FirstLaunchHooks.MARKER_ID) as? JsonObject
        return if (hook == null) HookRecord() else HookRecord(
            state = HookState.of(hook.string("state")),
            attempts = (hook["attempts"] as? JsonPrimitive)?.intOrNull ?: 0,
            at = (hook["at"] as? JsonPrimitive)?.longOrNull,
            done = hook.strings("done"),
            gaveUp = hook.strings("gaveUp"),
        )
    }

    override fun write(record: HookRecord) {
        val hook = buildJsonObject {
            put("state", record.state.wire)
            put("attempts", record.attempts)
            record.at?.let { put("at", it) }
            put("done", JsonArray(record.done.sorted().map { JsonPrimitive(it) }))
            if (record.gaveUp.isNotEmpty())
                put("gaveUp", JsonArray(record.gaveUp.sorted().map { JsonPrimitive(it) }))
        }
        val root = buildJsonObject {
            put("schema", 1)
            put("hooks", JsonObject(mapOf(FirstLaunchHooks.MARKER_ID to hook)))
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        check(tmp.renameTo(file)) { "first-launch marker not saved" }
    }
}

private fun readObject(file: File): JsonObject? =
    try {
        if (file.isFile) Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject else null
    } catch (_: Exception) {
        null
    }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.strings(key: String): Set<String> =
    ((this[key] as? JsonArray) ?: return emptySet())
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .toSet()

