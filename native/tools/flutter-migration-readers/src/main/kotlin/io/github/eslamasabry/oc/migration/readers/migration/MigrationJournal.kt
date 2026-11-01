package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import java.io.File

/**
 * Storage generations (audit 04+05 finding 6). R2 ("restore data from before the update") restores only
 * preferences and secret files, so it is offered only while the generation is [PrefsV1]. The Room import moves
 * the generation to [RoomV2] in the same transaction that makes Room authoritative, which retires R2 for good
 * unless a later recovery transaction that also covers the database is specified.
 */
public enum class StorageGeneration(public val wire: String) {
    PrefsV1("prefs-v1"), RoomV2("room-v2");

    public companion object {
        public fun fromWire(s: String?): StorageGeneration? = entries.firstOrNull { it.wire == s }
    }
}

public enum class StepState(public val wire: String) {
    Pending("pending"),
    /** Intent recorded; the step's writes may be partly done. Re-run applies again (steps are idempotent). */
    Running("running"),
    /** Commit marker: every write is done. Re-run only verifies. */
    Committed("committed"),
    /** Verified after commit. Re-run re-verifies and skips. */
    Verified("verified"),
    Failed("failed");

    public companion object {
        public fun fromWire(s: String?): StepState = entries.firstOrNull { it.wire == s } ?: Pending
    }
}

public enum class BackupState(public val wire: String) {
    None("none"), Copying("copying"), Published("published");

    public companion object {
        public fun fromWire(s: String?): BackupState = entries.firstOrNull { it.wire == s } ?: None
    }
}

/** R2 progress; every value except [Idle] means "resume before opening any store". */
public enum class RestoreState(public val wire: String) {
    Idle("idle"), Staged("staged"), MovingAside("movingAside"), Swapping("swapping"), Resetting("resetting");

    public companion object {
        public fun fromWire(s: String?): RestoreState = entries.firstOrNull { it.wire == s } ?: Idle
    }
}

public data class BackupFile(val name: String, val bytes: Long, val sha256: String, val authoritative: Boolean)

public data class StepRecord(val state: StepState, val attempts: Int)

public data class Blocked(val reason: String, val step: String?)

/**
 * `files/native/migration.json`. Holds states, counts and times only: never a secret, prompt text, server
 * address or key name with user content. Unknown fields are kept and written back (a newer build may have added
 * them).
 */
public class MigrationJournal(
    public var generation: StorageGeneration = StorageGeneration.PrefsV1,
    public var sourceKind: String? = null,
    public var backupState: BackupState = BackupState.None,
    public var backupFiles: List<BackupFile> = emptyList(),
    /** One durable compatibility barrier (audit finding 4): mirrored in `flutter.oc.native.flutterReadable`. */
    public var flutterReadable: Boolean? = null,
    public val steps: LinkedHashMap<String, StepRecord> = LinkedHashMap(),
    public var restoreState: RestoreState = RestoreState.Idle,
    public var restoreAsideDir: String? = null,
    /** After an R2 restore: hold restored outbound work (queue flushes, schedule re-arms) for review (finding 2). */
    public var outboundHold: Boolean = false,
    public var blocked: Blocked? = null,
    private val unknownFields: LinkedHashMap<String, Any?> = LinkedHashMap(),
) {
    public fun step(id: String): StepRecord = steps[id] ?: StepRecord(StepState.Pending, 0)

    public fun toJson(): String {
        val m = LinkedHashMap<String, Any?>()
        m["schema"] = SCHEMA
        m["generation"] = generation.wire
        m["source"] = linkedMapOf("kind" to sourceKind)
        m["backup"] = linkedMapOf(
            "state" to backupState.wire,
            "files" to backupFiles.map {
                linkedMapOf("name" to it.name, "bytes" to it.bytes, "sha256" to it.sha256, "authoritative" to it.authoritative)
            },
        )
        m["barrier"] = linkedMapOf("flutterReadable" to flutterReadable)
        m["steps"] = LinkedHashMap<String, Any?>().also { s ->
            for ((id, r) in steps) s[id] = linkedMapOf("state" to r.state.wire, "attempts" to r.attempts)
        }
        m["restore"] = linkedMapOf("state" to restoreState.wire, "asideDir" to restoreAsideDir)
        m["outbound"] = linkedMapOf("hold" to outboundHold)
        m["blocked"] = blocked?.let { linkedMapOf("reason" to it.reason, "step" to it.step) }
        for ((k, v) in unknownFields) if (k !in m) m[k] = v
        return MiniJson.encode(m)
    }

    public companion object {
        public const val SCHEMA: Long = 1

        /** Throws [MiniJson.ParseException] for a journal that is not JSON; the caller blocks with `JournalUnreadable`. */
        public fun fromJson(text: String): MigrationJournal {
            val m = MiniJson.parse(text) as? Map<*, *> ?: throw MiniJson.ParseException("journal is not an object", 0)
            fun obj(k: String) = m[k] as? Map<*, *>
            val backup = obj("backup")
            val files = (backup?.get("files") as? List<*>).orEmpty().mapNotNull { f ->
                val fm = f as? Map<*, *> ?: return@mapNotNull null
                BackupFile(
                    name = fm["name"] as? String ?: return@mapNotNull null,
                    bytes = (fm["bytes"] as? Long) ?: 0L,
                    sha256 = fm["sha256"] as? String ?: return@mapNotNull null,
                    authoritative = fm["authoritative"] == true,
                )
            }
            val steps = LinkedHashMap<String, StepRecord>()
            (obj("steps"))?.forEach { (k, v) ->
                val sm = v as? Map<*, *> ?: return@forEach
                steps[k as String] = StepRecord(StepState.fromWire(sm["state"] as? String), ((sm["attempts"] as? Long) ?: 0L).toInt())
            }
            val blocked = obj("blocked")?.let { Blocked(it["reason"] as? String ?: "Unknown", it["step"] as? String) }
            val known = setOf("schema", "generation", "source", "backup", "barrier", "steps", "restore", "outbound", "blocked")
            val unknown = LinkedHashMap<String, Any?>()
            for ((k, v) in m) if (k !in known) unknown[k as String] = v
            return MigrationJournal(
                generation = StorageGeneration.fromWire(m["generation"] as? String) ?: StorageGeneration.PrefsV1,
                sourceKind = obj("source")?.get("kind") as? String,
                backupState = BackupState.fromWire(backup?.get("state") as? String),
                backupFiles = files,
                flutterReadable = obj("barrier")?.get("flutterReadable") as? Boolean,
                steps = steps,
                restoreState = RestoreState.fromWire(obj("restore")?.get("state") as? String),
                restoreAsideDir = obj("restore")?.get("asideDir") as? String,
                outboundHold = obj("outbound")?.get("hold") == true,
                blocked = blocked,
                unknownFields = unknown,
            )
        }
    }
}

/** Loads and saves the journal atomically. */
public class JournalStore(public val file: File, private val faults: FaultPoints = FaultPoints.None) {
    public fun exists(): Boolean = file.exists()

    public fun load(): MigrationJournal = if (file.exists()) MigrationJournal.fromJson(file.readText()) else MigrationJournal()

    public fun save(j: MigrationJournal) {
        Fs.writeAtomically(file, j.toJson().toByteArray(Charsets.UTF_8), faults, "journal")
    }

    public inline fun update(block: (MigrationJournal) -> Unit): MigrationJournal {
        val j = load()
        block(j)
        save(j)
        return j
    }
}
