package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule

/** What the person confirmed for this deletion that changes the edit (plan 16 §1.4). */
public data class StructuralPlan(val keepQueuedPromptsAsDrafts: Boolean = false)

/** The result of one embedded edit on a prefs map. */
public sealed interface EmbeddedEditResult {
    /** [removed] records of the profile left the value (0 = nothing of it was there). */
    public data class Edited(val removed: Int) : EmbeddedEditResult

    /** The value cannot be read, so it cannot be shown to hold nothing of the profile (the queue then refuses). */
    public data object Unreadable : EmbeddedEditResult
}

/**
 * The profile's records EMBEDDED inside an app-wide value (plan 05 §3.5 steps 3–5 and 9, plan 16 §1.4 Astra M1).
 * One edit works on a prefs map (logical key → value) and is used for the live file and every retained copy, so
 * they cannot drift. [keys] are the app-wide keys it may change.
 */
public interface EmbeddedRecordEdit {
    public val owner: String
    public val keys: Set<String>

    /** Edits [entries] in place for [profileId]. */
    public fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult
}

/** The Flutter 2.1.0+57 embedded-record shapes, each ported from the Flutter clear rule named on it. */
public object FlutterEraEdits {
    /** `oc.offlineQueue`: JSON list of queued prompts; entries with `profileID == id` leave (Flutter deletion step 1). */
    public val OfflineQueue: EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = ":engine:delivery"
        override val keys = setOf(QUEUE, KEPT)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult {
            val raw = entries.string(QUEUE) ?: return EmbeddedEditResult.Edited(0)
            val list = DartJson.decodeOrNull(raw) as? List<*> ?: return EmbeddedEditResult.Unreadable
            val (mine, kept) = list.partition { (it as? Map<*, *>)?.get("profileID") == profileId.raw }
            if (mine.isEmpty()) return EmbeddedEditResult.Edited(0)
            entries[QUEUE] = PrefValue.Str(DartJson.encode(kept))
            if (plan.keepQueuedPromptsAsDrafts) {
                // The person chose Keep: the prompts move to Saved prompts (app-owned from here on).
                val saved = entries.string(KEPT)?.let { DartJson.decodeOrNull(it) as? List<*> } ?: emptyList<Any?>()
                entries[KEPT] = PrefValue.Str(DartJson.encode(saved + mine))
            }
            return EmbeddedEditResult.Edited(mine.size)
        }
    }

    /**
     * `oc.keptQueuedPrompts` in a RETAINED COPY: a copy keeps the profile's queued prompts only as the drafts the
     * person explicitly chose to keep (plan 16 §1.4). The live key is app-owned and never swept (Flutter
     * `QueuedPromptRemoval.draftsKey`), so this edit is for copies only.
     */
    public val KeptQueuedPromptsInCopies: EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = ":engine:delivery"
        override val keys = setOf(KEPT)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult {
            if (plan.keepQueuedPromptsAsDrafts) return EmbeddedEditResult.Edited(0)
            return entries.editList(KEPT) { (it as? Map<*, *>)?.get("profileID") == profileId.raw }
        }
    }

    /**
     * `oc.sessionDrafts`: JSON list of drafts; the profile's rows AND every unattributed (`profileID == ''`) row leave
     * (Flutter `SessionDraftStore.withoutProfile`, plan 05 §3.5 step 4).
     */
    public val SessionDrafts: EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = ":engine:composer"
        override val keys = setOf(DRAFTS)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult =
            entries.editList(DRAFTS) { row ->
                val owner = (row as? Map<*, *>)?.get("profileID") as? String
                owner.isNullOrEmpty() || owner == profileId.raw
            }
    }

    /** The profile's element of `oc.profiles` (step 9). */
    public val ProfileRow: EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = ProfileKeys.OWNER
        override val keys = setOf(ProfileKeys.PROFILES)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult =
            entries.editList(ProfileKeys.PROFILES) { (it as? Map<*, *>)?.get("id") == profileId.raw }
    }

    /** `oc.activeProfile` when it names the profile (step 9). */
    public val ActivePointer: EmbeddedRecordEdit = pointer(ProfileKeys.OWNER, ProfileKeys.ACTIVE, tombstone = null)

    /** `oc.builtinServerOwner` is tombstoned to `""` when it names the profile (B-STATE-061). */
    public val BuiltinServerOwner: EmbeddedRecordEdit = pointer(":engine:runtime", "oc.builtinServerOwner", tombstone = "")

    /** `oc.pendingPromptPhoto` when its `profileID` is the profile's (step 5). */
    public val PendingPhoto: EmbeddedRecordEdit = ownedObject(":engine:composer", "oc.pendingPromptPhoto", unattributedIsMine = false)

    /** `oc.widgetSessions`: the profile's snapshot, or one with no readable owner (B-STATE-061, Flutter clearForProfile). */
    public val WidgetSnapshot: EmbeddedRecordEdit = ownedObject(":android:widgets", "oc.widgetSessions", unattributedIsMine = true)

    /** `oc.pinnedShortcuts` (launcher pinned-session shortcuts), same ownership rule. */
    public val PinnedShortcuts: EmbeddedRecordEdit = ownedObject(":android:background", "oc.pinnedShortcuts", unattributedIsMine = true)

    /** `oc.attentionTile` (Quick Settings tile count), same ownership rule. */
    public val AttentionTile: EmbeddedRecordEdit = ownedObject(":android:background", "oc.attentionTile", unattributedIsMine = true)

    /** Every edit a retained Flutter-era copy gets (plan 16 §1.4). */
    public val forCopies: List<EmbeddedRecordEdit> = listOf(
        OfflineQueue, KeptQueuedPromptsInCopies, SessionDrafts, PendingPhoto, WidgetSnapshot, PinnedShortcuts,
        AttentionTile, BuiltinServerOwner, ProfileRow, ActivePointer,
    )

    public const val QUEUE: String = "oc.offlineQueue"
    public const val KEPT: String = "oc.keptQueuedPrompts"
    public const val DRAFTS: String = "oc.sessionDrafts"

    private fun pointer(owner: String, key: String, tombstone: String?): EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = owner
        override val keys = setOf(key)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult {
            if (entries.string(key) != profileId.raw) return EmbeddedEditResult.Edited(0)
            if (tombstone == null) entries.remove(key) else entries[key] = PrefValue.Str(tombstone)
            return EmbeddedEditResult.Edited(1)
        }
    }

    private fun ownedObject(owner: String, key: String, unattributedIsMine: Boolean): EmbeddedRecordEdit = object : EmbeddedRecordEdit {
        override val owner = owner
        override val keys = setOf(key)

        override fun apply(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): EmbeddedEditResult {
            val value = entries[key] ?: return EmbeddedEditResult.Edited(0)
            // An unreadable payload cannot be shown to be another profile's: it goes (Flutter clearForProfile).
            val decoded = (value as? PrefValue.Str)?.value?.let(DartJson::decodeOrNull)
            val recordOwner: String? = when (decoded) {
                is Map<*, *> -> decoded["profileID"]?.let { if (it is String) it else DartJson.encode(it) } ?: ""
                null -> ""
                else -> null // a readable non-object: Dart leaves it
            }
            val mine = recordOwner != null &&
                (recordOwner == profileId.raw || (unattributedIsMine && recordOwner.isEmpty()))
            if (!mine) return EmbeddedEditResult.Edited(0)
            entries.remove(key)
            return EmbeddedEditResult.Edited(1)
        }
    }

    private fun MutableMap<String, PrefValue>.string(key: String): String? = (this[key] as? PrefValue.Str)?.value

    /** Removes the matching elements of the JSON list at [key]; an unreadable list is [EmbeddedEditResult.Unreadable]. */
    private fun MutableMap<String, PrefValue>.editList(key: String, doomed: (Any?) -> Boolean): EmbeddedEditResult {
        val raw = string(key) ?: return if (key in this) EmbeddedEditResult.Unreadable else EmbeddedEditResult.Edited(0)
        val list = DartJson.decodeOrNull(raw) as? List<*> ?: return EmbeddedEditResult.Unreadable
        val kept = list.filterNot(doomed)
        if (kept.size == list.size) return EmbeddedEditResult.Edited(0)
        this[key] = PrefValue.Str(DartJson.encode(kept))
        return EmbeddedEditResult.Edited(list.size - kept.size)
    }
}

/** What [ProfileStructuralEdit] did to one map. */
public data class StructuralEditReceipt(val removedKeys: Int, val removedRecords: Int, val unreadableKeys: Set<String>) {
    val changed: Boolean get() = removedKeys > 0 || removedRecords > 0 || unreadableKeys.isNotEmpty()
}

/**
 * `ProfileStructuralEdit.apply(map, id, plan)` (plan 16 §1.4, Astra M1): the profile's `oc.<what>.<id>` keys
 * (the Flutter sweep rule, [ProfileKeyRule]), its secure-storage names, and its embedded records. Used for every
 * retained Flutter-era copy; the live file runs the same [EmbeddedRecordEdit]s through [EmbeddedRecordCleaner].
 *
 * In a copy an unreadable embedded value is removed whole: it cannot be shown to hold nothing of the deleted server,
 * and a copy has no person waiting on it (the live queue refuses instead, Flutter `QueuedPromptRemovalException`).
 */
public class ProfileStructuralEdit(
    private val edits: List<EmbeddedRecordEdit> = FlutterEraEdits.forCopies,
    private val secretPrefixes: Set<String> = ProfileKeys.SECRET_PREFIXES.keys,
) {
    /** Edits a SharedPreferences map (logical keys) in place. */
    public fun applyPrefs(entries: MutableMap<String, PrefValue>, profileId: ProfileId, plan: StructuralPlan): StructuralEditReceipt {
        // Known ids come from the copy's own oc.profiles BEFORE its row leaves (longer dotted ids keep their keys).
        val known = StoredProfileJson.rows((entries[ProfileKeys.PROFILES] as? PrefValue.Str)?.value).orEmpty()
            .mapNotNullTo(LinkedHashSet()) { (it as? Map<*, *>)?.get("id") as? String }
        val scoped = ProfileKeyRule.scopedKeys(profileId.raw, entries.keys, known)
        scoped.forEach(entries::remove)
        var records = 0
        val unreadable = LinkedHashSet<String>()
        for (edit in edits) {
            when (val result = edit.apply(entries, profileId, plan)) {
                is EmbeddedEditResult.Edited -> records += result.removed
                EmbeddedEditResult.Unreadable -> {
                    edit.keys.filterTo(unreadable) { it in entries }
                    edit.keys.forEach(entries::remove)
                }
            }
        }
        return StructuralEditReceipt(scoped.size, records, unreadable)
    }

    /** Edits a secure-storage map (entry name → value) in place: every name `<prefix><id>` or `<prefix><id>.…`. */
    public fun applySecrets(entries: MutableMap<String, PrefValue>, profileId: ProfileId): StructuralEditReceipt {
        val doomed = entries.keys.filter { name -> secretPrefixes.any { owns(name, it, profileId) } }
        doomed.forEach(entries::remove)
        return StructuralEditReceipt(doomed.size, 0, emptySet())
    }

    private fun owns(name: String, prefix: String, id: ProfileId): Boolean =
        name == prefix + id.raw || name.startsWith(prefix + id.raw + ".")
}

/**
 * A live-file [ProfileResourceCleaner] that applies one [EmbeddedRecordEdit] to the live [KeyValueStore] in ONE
 * commit (steps 3–5). Until the owning engine registers its own cleaner (with its lane and in-memory cache), this is
 * the storage half; [refuseUnreadable] keeps the queue's "unknown is never empty" rule.
 */
public class EmbeddedRecordCleaner(
    private val kv: KeyValueStore,
    private val lane: WriterLane,
    private val edit: EmbeddedRecordEdit,
    override val order: Int,
    override val owner: String = edit.owner,
    private val refuseUnreadable: Boolean = true,
) : ProfileResourceCleaner {
    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        val before = LinkedHashMap<String, PrefValue>()
        edit.keys.forEach { key -> kv.get(key)?.let { before[key] = it } }
        val after = LinkedHashMap(before)
        val removed = when (val result = edit.apply(after, profileId, options.structuralPlan)) {
            is EmbeddedEditResult.Edited -> result.removed
            EmbeddedEditResult.Unreadable ->
                if (refuseUnreadable) return Failure.Storage(StorageProblem.Unreadable).failed() else 0
        }
        if (after == before) return removed.ok()
        return kv.commit(lane) {
            for (key in edit.keys) {
                when (val value = after[key]) {
                    null -> if (key in before) remove(key)
                    before[key] -> Unit
                    is PrefValue.Str -> putString(key, value.value)
                    is PrefValue.Bool -> putBool(key, value.value)
                    is PrefValue.Int64 -> putLong(key, value.value)
                    is PrefValue.Dbl -> putDouble(key, value.value)
                    is PrefValue.StrList -> putStringList(key, value.value)
                    is PrefValue.Undecodable -> Unit
                }
            }
        }.map { removed }
    }
}
