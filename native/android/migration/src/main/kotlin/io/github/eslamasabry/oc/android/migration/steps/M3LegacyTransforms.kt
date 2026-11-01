package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.FlutterPrefsFile
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.prefs.ImagePickerCache
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.readers.xml.SharedPrefsFile
import java.io.File

/**
 * M3, prefs adoption (plan 05 §7.4 M3). Adoption itself is no copy and no conversion; M3 only finishes the one-shot
 * Dart transforms the Flutter app may not have finished. Each is a line-for-line port, idempotent, guarded by its own
 * marker, and committed in ONE atomic commit each (stronger than Dart's key-by-key writes, same end state).
 */
internal class M3LegacyTransforms(
    private val paths: MigrationPaths,
    private val prefs: FlutterPrefsFile,
) {
    sealed interface Result {
        data class Done(val counts: Map<String, Any?>) : Result
        data class WriteRefused(val transform: String) : Result
    }

    fun run(): Result {
        val counts = LinkedHashMap<String, Any?>()
        for ((id, transform) in listOf<Pair<String, (LogicalPrefs) -> Pair<String, PrefsEdit>>>(
            "L1" to ::retire, "L2" to ::notifyMerge, "L3" to ::readStateConsent, "L4b" to ::pickerPhoto,
        )) {
            val (outcome, edit) = transform(LogicalPrefs(prefs.live()))
            counts[id] = outcome
            if (!edit.isEmpty && !prefs.commit(edit.changes)) return Result.WriteRefused(id)
        }
        return Result.Done(counts)
    }

    /** L1: `ProfileStore._retireQuotaBudgets` + `_retirePreferences` (`lib/state/profiles.dart`). */
    internal fun retire(p: LogicalPrefs): Pair<String, PrefsEdit> {
        val edit = PrefsEdit()
        val keys = p.map.keys.filter { it.startsWith(DeclaredWrites.BUDGETS) || it in DeclaredWrites.RETIRED }.sorted()
        keys.forEach(edit::remove)
        return (if (keys.isEmpty()) "none" else "removed:${keys.size}") to edit
    }

    /**
     * L2: `NotificationPreferences.migrate(profileIDs)` with the pure `mergeLegacyNotifyRules`
     * (`lib/state/notification_preferences.dart`). Marker `oc.notify.version == 1`. The legacy per-server records are
     * read and never deleted. The order is the Dart one: the active server first, then every saved server.
     */
    internal fun notifyMerge(p: LogicalPrefs): Pair<String, PrefsEdit> {
        val edit = PrefsEdit()
        if (p.long(VERSION) == 1L) return "already" to edit
        val active = p.string(LogicalPrefs.ACTIVE_PROFILE)?.takeIf { it.isNotEmpty() }
        val order = listOfNotNull(active) + p.profileIds().filter { it != active }
        val merged = NotifyMerge.readLegacy(p, order)
        fun number(key: String, value: Long?) { if (value == null) edit.remove(key) else edit.putLong(key, value) }
        number("oc.notify.quietStart", if (merged.quietEnabled) merged.quietStart else null)
        number("oc.notify.quietEnd", if (merged.quietEnabled) merged.quietEnd else null)
        edit.putBool("oc.notify.wifiOnly", merged.wifiOnly)
        number("oc.notify.checkInAfterMinutes", merged.checkInAfterMinutes)
        edit.putBool("oc.notify.quotaAlerts", merged.quotaAlerts)
        edit.putLong(VERSION, 1L) // in the same commit: an interrupted run simply runs again
        return "merged" to edit
    }

    /**
     * L3: `ReadStateConsentStore.migrateExistingProfiles(ids)` (`lib/state/read_state_consent.dart`). Marker
     * `oc.readStateConsentMigration.v1.complete`. A journal Dart would reject is left alone (Dart blocks sharing and
     * writes nothing; the engine keeps that fail-closed read).
     */
    internal fun readStateConsent(p: LogicalPrefs): Pair<String, PrefsEdit> {
        val edit = PrefsEdit()
        val present = p.profileIds().filter { it.isNotEmpty() }.toSet()
        val raw = p.map[DeclaredWrites.READ_STATE_JOURNAL]
        var ids: List<String>
        val legacy: Boolean
        if (raw != null && raw != RawPref.Null) {
            val j = (raw as? RawPref.Str)?.value?.let { MiniJson.parseOrNull(it) } as? Map<*, *>
            val jIds = j?.get("ids") as? List<*>
            if (j == null || j["version"] != 1L || j["complete"] !is Boolean || jIds == null || jIds.any { it !is String } ||
                j["legacy"] !is Boolean
            ) {
                return "unreadable" to edit
            }
            if (j["complete"] == true) return "already" to edit
            ids = jIds.filterIsInstance<String>()
            legacy = j["legacy"] as Boolean
        } else {
            // Missing used to mean on. Invalid data must not grant sharing.
            val legacyRaw = p.map["oc.shareSessionViews"]
            legacy = legacyRaw == null || legacyRaw == RawPref.Bool(true)
            ids = present.sorted()
        }
        var migrated = 0
        for (id in ids) {
            if (id !in present) continue
            if (p.contains("oc.shareSessionViews.$id")) continue // an explicit destination wins
            edit.putString("oc.shareSessionViewsOrigin.$id", "migrated")
            edit.putBool("oc.shareSessionViews.$id", legacy)
            migrated++
        }
        // Completion in the same commit; a completed journal retains no ids.
        edit.putString(
            DeclaredWrites.READ_STATE_JOURNAL,
            MiniJson.encode(linkedMapOf("version" to 1L, "complete" to true, "ids" to emptyList<String>(), "legacy" to legacy)),
        )
        return "migrated:$migrated" to edit
    }

    /**
     * L4b (audit 04+05 #11, Astra B2): the plugin half of `PromptPhotoStore.recoverLostData()`
     * (`lib/state/prompt_photos.dart`). Only for the case only the plugin can recover: the origin record exists,
     * `ref == null`, `path == null` and `failed != true`. Rewrites the ONE JSON value with `path` and `name` set (or
     * `failed = true`), every other field byte-identical. Never copies into a vault; the picker file stays.
     */
    internal fun pickerPhoto(p: LogicalPrefs): Pair<String, PrefsEdit> {
        val edit = PrefsEdit()
        val raw = p.string(DeclaredWrites.PENDING_PHOTO) ?: return "none" to edit
        @Suppress("UNCHECKED_CAST")
        val record = (MiniJson.parseOrNull(raw) as? LinkedHashMap<String, Any?>) ?: return "unreadable" to edit
        if (record["id"] !is String || record["profileID"] !is String || record["sessionID"] !is String) return "unreadable" to edit
        if (record["ref"] != null || record["path"] != null || record["failed"] == true) return "notNeeded" to edit
        val lost = SharedPrefsFile(paths.sharedPrefs, ImagePickerCache.FILE_NAME).read()?.androidMap?.let(ImagePickerCache::read)
            ?: return "noResult" to edit
        val file = (lost.paths + listOfNotNull(lost.pendingCameraUri)).asSequence()
            .mapNotNull(::localFile).firstOrNull { it.isFile && it.length() > 0 }
        when {
            file != null -> {
                // XFile(path).name is the basename; withResult(path, name) leaves failed = false.
                record["path"] = file.path
                record["name"] = file.name
                record["failed"] = false
            }
            lost.hasError -> record["failed"] = true
            else -> return "noFile" to edit // Flutter's _discard is the engine's, never the migration's
        }
        edit.putString(DeclaredWrites.PENDING_PHOTO, MiniJson.encode(record))
        return (if (file != null) "recovered" else "failed") to edit
    }

    private fun localFile(pathOrUri: String): File? = when {
        pathOrUri.startsWith("file://") -> File(pathOrUri.removePrefix("file://"))
        pathOrUri.startsWith("/") -> File(pathOrUri)
        else -> null // content:// and other schemes are not files the engine can read back
    }

    private companion object {
        const val VERSION = "oc.notify.version"
    }
}

/** The shared notification rule (`SharedNotifyRules`). Minutes after local midnight. */
internal data class SharedNotifyRules(
    val quietStart: Long? = null,
    val quietEnd: Long? = null,
    val wifiOnly: Boolean = false,
    val checkInAfterMinutes: Long? = null,
    val quotaAlerts: Boolean = false,
) {
    val quietEnabled: Boolean get() = quietStart != null && quietEnd != null
}

/** Pure port of `mergeLegacyNotifyRules` and `NotificationPreferences.readLegacy`. */
internal object NotifyMerge {
    const val DEFAULT_QUIET_START: Long = 22 * 60
    const val DEFAULT_QUIET_END: Long = 8 * 60

    data class ProfileRules(val wifiOnly: Boolean, val quietStart: Long?, val quietEnd: Long?, val checkInAfterMinutes: Long?)

    data class QuotaRules(val notifications: Boolean, val wifiOnly: Boolean, val quietHours: Boolean)

    fun merge(profileRules: List<ProfileRules>, quotaRules: List<QuotaRules>): SharedNotifyRules {
        val pair = profileRules.firstOrNull { it.quietStart != null && it.quietEnd != null }
        val quotaQuiet = quotaRules.any { it.quietHours }
        return SharedNotifyRules(
            quietStart = pair?.quietStart ?: if (quotaQuiet) DEFAULT_QUIET_START else null,
            quietEnd = pair?.quietEnd ?: if (quotaQuiet) DEFAULT_QUIET_END else null,
            wifiOnly = profileRules.any { it.wifiOnly } || quotaRules.any { it.wifiOnly },
            checkInAfterMinutes = profileRules.firstNotNullOfOrNull { it.checkInAfterMinutes },
            quotaAlerts = quotaRules.any { it.notifications },
        )
    }

    /** `ProfileNotifyRules.fromJson` (`lib/domain/profile_monitor.dart`). Dart `is int` = a JSON integer (Long). */
    fun profileRules(m: Map<*, *>): ProfileRules {
        fun minute(v: Any?): Long? = (v as? Long)?.takeIf { it in 0 until 1440 }
        val checkIn = (m["checkInAfterMinutes"] as? Long)?.takeIf { it in 1..(24 * 60) }
        return ProfileRules(m["wifiOnly"] == true, minute(m["quietStart"]), minute(m["quietEnd"]), checkIn)
    }

    fun readLegacy(p: LogicalPrefs, order: List<String>): SharedNotifyRules {
        val profiles = ArrayList<ProfileRules>()
        val quota = ArrayList<QuotaRules>()
        for (id in order) {
            // A corrupt record read as "off" before; it still does.
            p.string("oc.notifyRules.$id")?.let { MiniJson.parseOrNull(it) as? Map<*, *> }?.let { profiles += profileRules(it) }
            val raw = p.string("oc.quotaMonitor.$id") ?: continue
            if (raw.length > 65536) continue
            val rules = (MiniJson.parseOrNull(raw) as? Map<*, *>)?.get("rules") as? Map<*, *> ?: continue
            for (rule in rules.values) {
                if (rule !is Map<*, *>) continue
                quota += QuotaRules(
                    notifications = rule["notifications"] == true,
                    wifiOnly = rule["wifiOnly"] == true,
                    quietHours = rule["quietStart"] is Long && rule["quietEnd"] is Long,
                )
            }
        }
        return merge(profiles, quota)
    }
}
