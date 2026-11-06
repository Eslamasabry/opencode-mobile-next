package io.github.eslamasabry.oc.engine.diagnostics.problem

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * One store the update carries over, as Report a problem names it (E00-S15). [wire] is the census key; [label] is the
 * fixed English word the report prints (the report is English, like the rest of its text).
 */
public enum class MigrationStore(public val wire: String, public val label: String) {
    Servers("servers", "Servers"),

    /** Every saved sign-in the old app had: carried over plus the ones to enter again. */
    SignIns("signIns", "Saved sign-ins"),
    Drafts("drafts", "Drafts"),
    Queue("queue", "Queued prompts"),
    Schedules("schedules", "Schedules"),
    Projects("projects", "Projects"),
    VoicePacks("voicePacks", "Voice packs"),
}

/** One store's count after the update ([now]) and in the bridge census ([before]); null = not counted. */
public data class MigrationCheckLine(val store: MigrationStore, val now: Long?, val before: Long?) {
    /** Only a census can disagree: a store it counted that the update counted differently (or not at all). */
    val mismatch: Boolean get() = before != null && now != before
}

/**
 * "Did anything go missing in the update?" for Report a problem (E00-S15, plan 05 §10.4 step 8): the counts the
 * migration audit recorded, beside the counts the last Flutter (bridge) release recorded before the update, when that
 * census exists. Counts only: no value, path, server name or secret name ever reaches the text, because every line is
 * built from [MigrationStore.label] and numbers, and anything in the files that is not a non-negative whole number is
 * ignored.
 */
public class MigrationCheck private constructor(
    public val lines: List<MigrationCheckLine>,
    /** Saved sign-ins the update could not carry over (asked for again); part of [MigrationStore.SignIns]. */
    public val signInsToReenter: Long?,
    public val censusPresent: Boolean,
) {
    public val mismatches: List<MigrationStore> get() = lines.filter { it.mismatch }.map { it.store }

    /** The report section, one line per store; a mismatch line starts with [MISMATCH] and names its store. */
    public val text: String
        get() = buildString {
            append(if (censusPresent) HEADER_WITH_CENSUS else HEADER)
            for (line in lines) {
                append('\n')
                if (line.mismatch) append(MISMATCH)
                append(line.store.label).append(": ").append(line.now?.toString() ?: NOT_COUNTED)
                if (line.store == MigrationStore.SignIns && line.now != null && signInsToReenter != null) {
                    append(" (").append(signInsToReenter).append(" to enter again)")
                }
                if (censusPresent) append(" · before the update: ").append(line.before?.toString() ?: NOT_COUNTED)
            }
        }

    override fun toString(): String = "MigrationCheck(stores=${lines.size}, census=$censusPresent, mismatches=${mismatches.size})"

    public companion object {
        public const val HEADER: String = "Update check (counts only)"
        public const val HEADER_WITH_CENSUS: String = "Update check (counts only, compared with the old app's count)"
        public const val MISMATCH: String = "⚠ Mismatch: "
        public const val NOT_COUNTED: String = "not counted"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * [auditJson] is `files/native/migration-audit.json` (`:android:migration` M5); [censusJson] is the bridge
         * census, `{"v":1,"counts":{"servers":3,"signIns":4,…}}` with [MigrationStore.wire] keys (05 W13). Null when
         * neither is there or readable: the report then has no update section (a fresh install, the preview app).
         */
        public fun of(auditJson: String?, censusJson: String?): MigrationCheck? {
            val audit = auditJson?.let(::parse)
            val census = censusJson?.let(::parse)?.let { counts(it["counts"]) }
            if (audit == null && census == null) return null
            val now = audit?.let(::auditCounts).orEmpty()
            val reenter = audit?.let { sum(path(it, "secrets", "flagged")) }
            val lines = MigrationStore.entries.map { MigrationCheckLine(it, now[it], census?.get(it)) }
            return MigrationCheck(lines, reenter, census != null)
        }

        private fun auditCounts(audit: JsonObject): Map<MigrationStore, Long> {
            val carried = sum(path(audit, "secrets", "copied"))
            val reenter = sum(path(audit, "secrets", "flagged"))
            return buildMap {
                count(path(audit, "profiles", "count"))?.let { put(MigrationStore.Servers, it) }
                if (carried != null || reenter != null) put(MigrationStore.SignIns, (carried ?: 0L) + (reenter ?: 0L))
                count(path(audit, "data", "drafts"))?.let { put(MigrationStore.Drafts, it) }
                count(path(audit, "data", "queueEntries"))?.let { put(MigrationStore.Queue, it) }
                count(path(audit, "data", "scheduleItems"))?.let { put(MigrationStore.Schedules, it) }
                count(path(audit, "files", "projects"))?.let { put(MigrationStore.Projects, it) }
                count(path(audit, "files", "voicePacks"))?.let { put(MigrationStore.VoicePacks, it) }
            }
        }

        private fun counts(element: JsonElement?): Map<MigrationStore, Long>? {
            val obj = element as? JsonObject ?: return null
            return MigrationStore.entries.mapNotNull { s -> count(obj[s.wire])?.let { s to it } }.toMap()
        }

        private fun parse(text: String): JsonObject? =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
                null // SerializationException is an IllegalArgumentException: an unreadable file adds no section
            }

        private fun path(obj: JsonObject, vararg keys: String): JsonElement? =
            keys.fold(obj as JsonElement?) { at, key -> (at as? JsonObject)?.get(key) }

        /** A non-negative whole number, else null; a string is never read as a count. */
        private fun count(element: JsonElement?): Long? =
            (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }

        /** The sum of a `{kind: count}` map (M2's `copied` / `flagged`); null when it is not one. */
        private fun sum(element: JsonElement?): Long? = (element as? JsonObject)?.values?.sumOf { count(it) ?: 0L }
    }
}
