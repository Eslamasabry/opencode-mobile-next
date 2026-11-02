package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.readers.xml.canonicalBytes
import java.io.File

/** A read-only view of the live Flutter map under LOGICAL keys (`flutter.` stripped). */
internal class LogicalPrefs(physical: Map<String, RawPref>) {
    val map: Map<String, RawPref> = buildMap {
        for ((k, v) in physical) FlutterPrefsCodec.logicalKey(k)?.let { put(it, v) }
    }

    fun keys(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }

    /** A plain string as Dart's `getString` would return it (the list and double prefixes are not plain strings). */
    fun string(key: String): String? {
        val v = (map[key] as? RawPref.Str)?.value ?: return null
        return if (FlutterPrefsCodec.clashes(v)) null else v
    }

    fun long(key: String): Long? = (map[key] as? RawPref.Int64)?.value ?: (map[key] as? RawPref.Int32)?.value?.toLong()

    fun bool(key: String): Boolean? = (map[key] as? RawPref.Bool)?.value

    fun contains(key: String): Boolean = map[key] != null && map[key] != RawPref.Null

    /** `jsonDecode(prefs.getString(key))` as a map; null on any problem (Dart readers fail closed the same way). */
    fun jsonObject(key: String): Map<String, Any?>? =
        string(key)?.let { MiniJson.parseOrNull(it) }?.let { m -> @Suppress("UNCHECKED_CAST") (m as? Map<String, Any?>) }

    /** `oc.profiles` read leniently: every element with a String `id`, in stored order (Dart `ServerProfile.fromJson`). */
    fun profiles(): List<Map<String, Any?>> {
        val raw = string(PROFILES) ?: return emptyList()
        val list = MiniJson.parseOrNull(raw) as? List<*> ?: return emptyList()
        return list.mapNotNull { e -> @Suppress("UNCHECKED_CAST") (e as? Map<String, Any?>)?.takeIf { it["id"] is String } }
    }

    fun profileIds(): List<String> = profiles().map { it["id"] as String }

    companion object {
        const val PROFILES = "oc.profiles"
        const val ACTIVE_PROFILE = "oc.activeProfile"
        const val BUILTIN_OWNER = "oc.builtinServerOwner"
        const val BARRIER = "oc.native.flutterReadable"
        const val SECRET_REENTRY = "oc.secretReentry."
        const val STALE_LEGACY_SECRETS = "oc.native.staleLegacySecrets"
    }
}

/** Physical-key edits for one commit. */
internal class PrefsEdit {
    val changes: LinkedHashMap<String, RawPref?> = LinkedHashMap()

    fun putString(key: String, value: String) { changes[FlutterPrefsCodec.physicalKey(key)] = FlutterPrefsCodec.encodeString(value) }
    fun putBool(key: String, value: Boolean) { changes[FlutterPrefsCodec.physicalKey(key)] = RawPref.Bool(value) }
    fun putLong(key: String, value: Long) { changes[FlutterPrefsCodec.physicalKey(key)] = RawPref.Int64(value) }
    fun putStringList(key: String, value: List<String>) { changes[FlutterPrefsCodec.physicalKey(key)] = FlutterPrefsCodec.encodeStringList(value) }
    fun remove(key: String) { changes[FlutterPrefsCodec.physicalKey(key)] = null }
    val isEmpty: Boolean get() = changes.isEmpty()
}

/**
 * The declared write sets of `native.migration.v1` (plan 05 §7.4 M5): M2's re-entry flags, M3's L1–L3 and L4b sets,
 * and the barrier. Every other key must be byte-identical after the run (`unchangedKeysDigest`).
 */
internal object DeclaredWrites {
    val RETIRED: Set<String> = setOf("oc.glassLiquidActive", "oc.glassLiquidStrikes", "oc.glassLiquidOffUntil", "oc.effectsLiveBackground")
    const val BUDGETS = "oc.budgets."
    val NOTIFY: Set<String> = setOf(
        "oc.notify.quietStart", "oc.notify.quietEnd", "oc.notify.wifiOnly", "oc.notify.checkInAfterMinutes",
        "oc.notify.quotaAlerts", "oc.notify.version",
    )
    const val READ_STATE_JOURNAL = "oc.readStateConsentMigration.v1"
    val READ_STATE_PREFIXES: List<String> = listOf("oc.shareSessionViews.", "oc.shareSessionViewsOrigin.", "oc.shareSessionViewsDefault.")
    const val PENDING_PHOTO = "oc.pendingPromptPhoto"

    fun isDeclared(logicalKey: String): Boolean =
        logicalKey.startsWith(BUDGETS) || logicalKey in RETIRED || logicalKey in NOTIFY ||
            logicalKey == READ_STATE_JOURNAL || READ_STATE_PREFIXES.any { logicalKey.startsWith(it) } ||
            logicalKey == PENDING_PHOTO || logicalKey.startsWith(LogicalPrefs.SECRET_REENTRY) || logicalKey == LogicalPrefs.BARRIER

    fun isDeclaredPhysical(physicalKey: String): Boolean = FlutterPrefsCodec.logicalKey(physicalKey)?.let(::isDeclared) ?: false
}

/**
 * M1's baseline (plan 05 §7.4 M1 item 1): `sha256(physicalKey) → {declared, sha256(typed raw value)}` for every live key,
 * written before the first write of the process. Key names are hashed (some carry directory paths); values are
 * hashed (prefs hold no secrets, plan 05 §7.4). M4 and M5 compare against it, so a crash between steps keeps the
 * comparison valid. Deleted when v1 completes.
 */
internal class PrefsBaseline private constructor(val entries: Map<String, Entry>) {
    data class Entry(val declared: Boolean, val valueHash: String)

    fun valueHashOf(physicalKey: String): String? = entries[keyHash(physicalKey)]?.valueHash

    /** The audit's `unchangedKeysDigest` over the baseline. */
    fun unchangedKeysDigest(): String = digestOf(entries)

    fun save(file: File, faults: FaultPoints = FaultPoints.None) {
        val keys = entries.toSortedMap().mapValues { (_, e) -> linkedMapOf("d" to e.declared, "v" to e.valueHash) }
        Fs.writeAtomically(file, MiniJson.encode(linkedMapOf("v" to 1L, "keys" to keys)).toByteArray(Charsets.UTF_8), faults, "baseline")
    }

    companion object {
        fun keyHash(physicalKey: String): String = Fs.sha256Hex(physicalKey.toByteArray(Charsets.UTF_8))

        fun valueHash(value: RawPref): String = Fs.sha256Hex(value.canonicalBytes())

        fun entriesOf(live: Map<String, RawPref>): Map<String, Entry> =
            live.entries.associate { (k, v) -> keyHash(k) to Entry(DeclaredWrites.isDeclaredPhysical(k), valueHash(v)) }

        fun of(live: Map<String, RawPref>): PrefsBaseline = PrefsBaseline(entriesOf(live))

        /** The same digest over the live map now. */
        fun liveDigest(live: Map<String, RawPref>): String = digestOf(entriesOf(live))

        fun load(file: File): PrefsBaseline? {
            if (!file.isFile) return null
            val keys = (MiniJson.parseOrNull(file.readText(Charsets.UTF_8)) as? Map<*, *>)?.get("keys") as? Map<*, *> ?: return null
            val out = LinkedHashMap<String, Entry>()
            for ((k, v) in keys) {
                val m = v as? Map<*, *> ?: return null
                out[k as String] = Entry(m["d"] == true, m["v"] as? String ?: return null)
            }
            return PrefsBaseline(out)
        }

        private fun digestOf(entries: Map<String, Entry>): String {
            val lines = entries.filterValues { !it.declared }.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value.valueHash}" }
            return Fs.sha256Hex(lines.toByteArray(Charsets.UTF_8))
        }
    }
}
