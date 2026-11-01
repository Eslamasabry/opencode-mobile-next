package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.model.FlutterKey
import io.github.eslamasabry.oc.migration.readers.model.KeyClassifier
import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.migration.readers.xml.canonicalBytes
import java.io.File

/**
 * M1's baseline and M5's "nothing else changed" proof (plan/05 §7.4).
 *
 * Stored in `files/native/m1-baseline.json` as `sha256(physicalKey) → {shape, sha256(typed raw value)}`.
 * Key names are hashed (some carry directory paths, e.g. `oc.providerRuntimeRefresh.v1.<id>.<enc>`); the shape
 * name is kept so that write sets can be declared the way plan/05 declares them, by key family (L1 = retired
 * keys, L2 = `oc.notify.*`, M2 = `oc.secretReentry.*`, the barrier key, …). The file is written before the
 * first write of the process, so a crash between steps keeps the comparison against the pre-migration state.
 */
public class PrefsBaseline private constructor(public val entries: Map<String, Entry>) {

    public data class Entry(val shape: String, val valueHash: String)

    /** Number of keys outside [declared] whose value changed, appeared or disappeared since the baseline. */
    public fun undeclaredChanges(live: Map<String, RawPref>, declared: Set<FlutterKey>): Int {
        val names = declared.map { it.name }.toSet()
        val before = entries.filterValues { it.shape !in names }
        val now = entriesOf(live).filterValues { it.shape !in names }
        return (before.keys + now.keys).count { before[it] != now[it] }
    }

    /** SHA-256 over the sorted (key hash, value hash) pairs outside [declared]: the audit's `unchangedKeysDigest`. */
    public fun unchangedKeysDigest(declared: Set<FlutterKey>): String = digestOf(entries, declared)

    public fun save(file: File, faults: FaultPoints = FaultPoints.None) {
        val keys = entries.toSortedMap().mapValues { (_, e) -> linkedMapOf("shape" to e.shape, "value" to e.valueHash) }
        Fs.writeAtomically(file, MiniJson.encode(linkedMapOf("v" to 1L, "keys" to keys)).toByteArray(Charsets.UTF_8), faults, "baseline")
    }

    public companion object {
        /** Shape label for keys without one: `?` unknown under `flutter.`, `!` without the prefix. */
        public fun shapeOf(physicalKey: String): String {
            val logical = FlutterPrefsCodec.logicalKey(physicalKey) ?: return "!"
            return KeyClassifier.classify(logical).key?.name ?: "?"
        }

        public fun keyHash(physicalKey: String): String = Fs.sha256Hex(physicalKey.toByteArray(Charsets.UTF_8))

        public fun entriesOf(map: Map<String, RawPref>): Map<String, Entry> =
            map.entries.associate { (k, v) -> keyHash(k) to Entry(shapeOf(k), Fs.sha256Hex(v.canonicalBytes())) }

        public fun of(map: Map<String, RawPref>): PrefsBaseline = PrefsBaseline(entriesOf(map))

        public fun load(file: File): PrefsBaseline? {
            if (!file.exists()) return null
            val keys = (MiniJson.parseOrNull(file.readText()) as? Map<*, *>)?.get("keys") as? Map<*, *> ?: return null
            val out = LinkedHashMap<String, Entry>()
            for ((k, v) in keys) {
                val m = v as? Map<*, *> ?: return null
                out[k as String] = Entry(m["shape"] as? String ?: return null, m["value"] as? String ?: return null)
            }
            return PrefsBaseline(out)
        }

        private fun digestOf(entries: Map<String, Entry>, declared: Set<FlutterKey>): String {
            val names = declared.map { it.name }.toSet()
            val lines = entries.filterValues { it.shape !in names }.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value.valueHash}" }
            return Fs.sha256Hex(lines.toByteArray(Charsets.UTF_8))
        }

        /** Live-side digest over the same pairs, to compare with [unchangedKeysDigest]. */
        public fun liveDigest(live: Map<String, RawPref>, declared: Set<FlutterKey>): String = digestOf(entriesOf(live), declared)

        /** plan/05 §7.4 M3 write sets (L1–L3), M2's re-entry flags and the barrier. */
        public val V1_DECLARED_WRITES: Set<FlutterKey> = setOf(
            // L1
            FlutterKey.Budgets, FlutterKey.GlassLiquidActive, FlutterKey.GlassLiquidStrikes, FlutterKey.GlassLiquidOffUntil,
            FlutterKey.EffectsLiveBackground,
            // L2
            FlutterKey.NotifyQuietStart, FlutterKey.NotifyQuietEnd, FlutterKey.NotifyWifiOnly, FlutterKey.NotifyCheckInAfterMinutes,
            FlutterKey.NotifyQuotaAlerts, FlutterKey.NotifyVersion,
            // L3
            FlutterKey.ShareSessionViews, FlutterKey.ShareSessionViewsDefault, FlutterKey.ShareSessionViewsOrigin,
            FlutterKey.ReadStateConsentMigration,
            // M2, barrier
            FlutterKey.SecretReentry, FlutterKey.NativeFlutterReadable,
        )
    }
}
