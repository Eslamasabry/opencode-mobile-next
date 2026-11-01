package io.github.eslamasabry.oc.core.data.kv

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.flow.SharedFlow

/**
 * The one key-value contract (plan 05 §2.1, reconciled with plan 03 §10.5 as audit codex-01+03 BLOCKER 4 asks; the
 * choices are listed in `docs/native/units/S1-DATA.md`).
 *
 * - Keys are LOGICAL: never the `flutter.` prefix. On Android the physical file is the adopted
 *   `shared_prefs/FlutterSharedPreferences.xml`.
 * - Reads are in-memory, cheap and only valid once the migration gate is Open (plan 05 §2.2): before that, debug, qa
 *   and test builds throw [GateNotOpen] and release builds count an early read ([EarlyReadPolicy]). Code that must
 *   read earlier uses [MigrationReader]. Reads only ever see acknowledged values.
 * - Every mutation (commit, compare-and-set, the migrator's writes and the compensation after a refusal) runs under ONE
 *   file-wide lock, held from reading the previous values through the platform commit and the compensation
 *   (audit codex-04+05 MAJOR 8).
 */
public interface KeyValueStore {
    /**
     * Logical key → decoded value; null when absent or stored as `<null>` (invisible to Dart too). Before the gate is
     * Open: [GateNotOpen] in debug/qa/test builds (plan 05 §2.2).
     */
    public fun get(key: String): PrefValue?

    /** Logical keys starting with [prefix]. */
    public fun keys(prefix: String = ""): Set<String>

    /** Logical keys changed by acknowledged writes in this process. A refused write never appears (B-STATE-004). */
    public val changes: SharedFlow<Set<String>>

    /**
     * One atomic platform commit (`Editor.commit()`, never `apply()`), run in [lane] after the migration gate opens.
     * On refusal the touched keys are written back to their previous values, readers and [changes] never see the
     * refused values, and `Failure.StorageRefused` is returned. A string value starting with a Flutter prefix is refused before
     * anything is written, as the plugin does. A closed lane returns `Failure.Refused(ProfileClosing)`.
     */
    public suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit>

    /**
     * Atomic compare-and-set of one string key (schedule envelope CAS, widget snapshot patches). `Ok(false)` when the
     * current value is not [expected] (a non-string value never equals). [next] null removes the key.
     */
    public suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean>
}

/** No putInt, putFloat or putStringSet: the Flutter reader would mis-handle them (plan 05 §2.3 rule 2). */
public interface PrefEdit {
    public fun putString(key: String, value: String)

    public fun putBool(key: String, value: Boolean)

    public fun putLong(key: String, value: Long)

    public fun putStringList(key: String, value: List<String>)

    public fun putDouble(key: String, value: Double)

    public fun remove(key: String)
}

/** A read before the migration gate opened, in a debug, qa or test build (plan 05 §2.2). Names no key. */
public class GateNotOpen : IllegalStateException("a preference was read before the migration gate opened")

/**
 * What a read before the gate is Open does (plan 05 §2.2). [Throw] in debug, qa and test builds; release builds pass
 * a policy that adds one to the diagnostics `earlyRead` count and lets the read answer.
 */
public fun interface EarlyReadPolicy {
    public fun onEarlyRead()

    public companion object {
        public val Throw: EarlyReadPolicy = EarlyReadPolicy { throw GateNotOpen() }
    }
}

/**
 * Plan 05 §2.2: the only reads allowed before the gate is Open. Obtained from `PrefsKeyValueStore.migrationReader()`
 * (`@MigrationOnly`); no secrets.
 */
public interface MigrationReader {
    public fun get(key: String): PrefValue?

    public fun keys(prefix: String = ""): Set<String>
}

public fun KeyValueStore.string(key: String): String? = (get(key) as? PrefValue.Str)?.value

/** Dart ints are Longs on disk; a legacy `<int>` written by Kotlin reads as a Long too. Never `getInt`. */
public fun KeyValueStore.long(key: String): Long? = (get(key) as? PrefValue.Int64)?.value

public fun KeyValueStore.bool(key: String): Boolean? = (get(key) as? PrefValue.Bool)?.value

public fun KeyValueStore.double(key: String): Double? = (get(key) as? PrefValue.Dbl)?.value

/** Both Flutter list encodings (JSON `…Qu!` and the legacy Java form) and a Kotlin `<set>` read as a list. */
public fun KeyValueStore.stringList(key: String): List<String>? = (get(key) as? PrefValue.StrList)?.value

/**
 * The platform file under a [KeyValueStore]: PHYSICAL keys, raw values, blocking calls. The store calls it on its IO
 * dispatcher, always under its file-wide lock.
 */
public interface PrefsBackend {
    /** Every physical entry as the platform holds it. Called once. */
    public fun load(): Map<String, RawPref>

    /**
     * One atomic commit: a null value removes the key. True only when the platform acknowledged the disk write.
     * May throw; a throw counts as a refusal.
     */
    public fun commit(changes: Map<String, RawPref?>): Boolean
}
