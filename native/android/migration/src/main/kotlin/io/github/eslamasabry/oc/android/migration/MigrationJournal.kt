package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import java.io.File

/** Step states (plan 05 §7.2 and §7.11): `running` is written BEFORE the step's first write, so a crash counts. */
internal enum class StepState(val wire: String) {
    Pending("pending"), Running("running"), Committed("committed"), Done("done"), Failed("failed");

    companion object {
        fun of(wire: Any?): StepState = entries.firstOrNull { it.wire == wire } ?: Pending
    }
}

/**
 * `files/native/migration.json` (plan 05 §7.2). A JSON object kept as an ordered map, so fields this build does not
 * know survive a rewrite (a newer build may have added them). It holds counts, states and times only: never a secret,
 * prompt text, server address or key name with user content.
 */
internal class MigrationJournal private constructor(private val root: LinkedHashMap<String, Any?>) {

    val schema: Long get() = (root["schema"] as? Long) ?: SCHEMA

    /** `flutter` | `none` | `startFresh`; null before S0 looked. */
    var sourceKind: String?
        get() = obj("source")["kind"] as? String
        set(value) { obj("source")["kind"] = value }

    fun putSource(key: String, value: Any?) { obj("source")[key] = value }

    val foundAt: Long? get() = obj("source")["foundAt"] as? Long

    /** `none` | `copying` | `done`. */
    var backupState: String
        get() = (obj("backup")["state"] as? String) ?: "none"
        set(value) { obj("backup")["state"] = value }

    fun putBackup(key: String, value: Any?) { obj("backup")[key] = value }

    fun stepState(id: String): StepState = StepState.of(step(id)["state"])

    fun attempts(id: String): Int = ((step(id)["attempts"] as? Long) ?: 0L).toInt()

    @Suppress("UNCHECKED_CAST")
    fun stepResult(id: String): Map<String, Any?> = (step(id)["result"] as? Map<String, Any?>) ?: emptyMap()

    fun setStep(id: String, state: StepState, attempts: Int, at: Long, result: Map<String, Any?>? = null) {
        val s = step(id)
        s["state"] = state.wire
        s["attempts"] = attempts.toLong()
        s["at"] = at
        if (result != null) s["result"] = result
    }

    /** "Try moving your data again" (plan 05 §7.3): only failed or interrupted steps start over. */
    @Suppress("UNCHECKED_CAST")
    fun resetUnfinishedSteps() {
        for ((_, v) in obj("steps")) {
            val s = v as? MutableMap<String, Any?> ?: continue
            if (s["state"] != StepState.Done.wire) {
                s["state"] = StepState.Pending.wire
                s["attempts"] = 0L
            }
        }
    }

    var v1Complete: Boolean
        get() = (obj("migrations")[V1] as? Map<*, *>)?.get("complete") == true
        set(value) { obj("migrations")[V1] = linkedMapOf<String, Any?>("complete" to value) }

    fun markV1CompleteAt(at: Long) { obj("migrations")[V1] = linkedMapOf<String, Any?>("complete" to true, "at" to at) }

    var firstOpenAt: Long?
        get() = obj("gate")["firstOpenAt"] as? Long
        set(value) { obj("gate")["firstOpenAt"] = value }

    /** The journal half of the one compatibility barrier (plan 05 §7.11). */
    var flutterReadable: Boolean?
        get() = obj("rollback")["flutterReadable"] as? Boolean
        set(value) { obj("rollback")["flutterReadable"] = value }

    var secretRetriesRemaining: Int
        get() = ((obj("secretRetries")["remaining"] as? Long) ?: 0L).toInt()
        set(value) { obj("secretRetries")["remaining"] = value.toLong() }

    var blocked: JournalBlock?
        get() = (root["blocked"] as? Map<*, *>)?.let {
            JournalBlock(it["reason"] as? String ?: BlockReason.StepCrashed.name, it["step"] as? String, it["neededBytes"] as? Long)
        }
        set(value) {
            root["blocked"] = value?.let { linkedMapOf<String, Any?>("reason" to it.reason, "step" to it.step, "neededBytes" to it.neededBytes) }
        }

    /** Census flags for S2-GATEUI's one-time notice (plan 02 §3.7 item 4). */
    var deferredFeatures: Set<DeferredFeature>
        get() = ((obj("notices")["deferred"] as? List<*>) ?: emptyList<Any?>())
            .mapNotNull { w -> DeferredFeature.entries.firstOrNull { it.name == w } }.toSet()
        set(value) { obj("notices")["deferred"] = value.map { it.name }.sorted() }

    var noticesAcknowledgedAt: Long?
        get() = obj("notices")["acknowledgedAt"] as? Long
        set(value) { obj("notices")["acknowledgedAt"] = value }

    /** The native versionCode that last ran (for `app_updated`). */
    var lastVersionCode: Long?
        get() = obj("app")["lastVersionCode"] as? Long
        set(value) { obj("app")["lastVersionCode"] = value }

    var migrationDoneSent: Boolean
        get() = obj("signals")["migrationDone"] == true
        set(value) { obj("signals")["migrationDone"] = value }

    fun encode(): String = MiniJson.encode(root)

    @Suppress("UNCHECKED_CAST")
    private fun obj(key: String): LinkedHashMap<String, Any?> =
        (root[key] as? LinkedHashMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { root[key] = it }

    @Suppress("UNCHECKED_CAST")
    private fun step(id: String): LinkedHashMap<String, Any?> {
        val steps = obj("steps")
        return (steps[id] as? LinkedHashMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { steps[id] = it }
    }

    companion object {
        const val SCHEMA: Long = 1
        const val V1: String = "native.migration.v1"

        fun fresh(): MigrationJournal = MigrationJournal(
            linkedMapOf(
                "schema" to SCHEMA,
                "source" to LinkedHashMap<String, Any?>(),
                "backup" to linkedMapOf<String, Any?>("state" to "none"),
                "steps" to LinkedHashMap<String, Any?>(),
                "migrations" to LinkedHashMap<String, Any?>(),
                "secretRetries" to linkedMapOf<String, Any?>("remaining" to 0L),
                "gate" to linkedMapOf<String, Any?>("firstOpenAt" to null),
                "rollback" to linkedMapOf<String, Any?>("flutterReadable" to null, "lastReturnHandledAt" to null, "returnsHandled" to 0L),
                "pendingSwap" to null,
                "followUp" to emptyList<Any?>(),
                "blocked" to null,
            ),
        )

        /** Null when the text is not a JSON object (the runner then blocks and never overwrites the file). */
        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): MigrationJournal? =
            (MiniJson.parseOrNull(text) as? LinkedHashMap<String, Any?>)?.let { MigrationJournal(it) }
    }
}

internal data class JournalBlock(val reason: String, val step: String?, val neededBytes: Long?)

/** Atomic load and save: `migration.json.tmp`, fsync, rename, fsync of `files/native/` (plan 05 §7.2). */
internal class JournalStore(val file: File, private val faults: FaultPoints = FaultPoints.None) {
    sealed interface Loaded {
        data class Ok(val journal: MigrationJournal, val existed: Boolean) : Loaded
        data object Unreadable : Loaded
    }

    fun exists(): Boolean = file.exists()

    fun load(): Loaded {
        if (!file.exists()) return Loaded.Ok(MigrationJournal.fresh(), existed = false)
        val parsed = MigrationJournal.parse(file.readText(Charsets.UTF_8)) ?: return Loaded.Unreadable
        return Loaded.Ok(parsed, existed = true)
    }

    fun save(journal: MigrationJournal) {
        Fs.writeAtomically(file, journal.encode().toByteArray(Charsets.UTF_8), faults, "journal")
    }
}
