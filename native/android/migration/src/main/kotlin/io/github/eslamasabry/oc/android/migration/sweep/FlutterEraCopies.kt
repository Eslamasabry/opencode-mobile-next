package io.github.eslamasabry.oc.android.migration.sweep

import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidLoad
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.android.migration.readers.xml.PrefsXmlEdit
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.readers.xml.XmlProblem
import io.github.eslamasabry.oc.android.migration.steps.S0Backup
import java.io.File

/** Which rules a retained copy gets (`:engine:profiles` `RetainedCopyKind`; `:androidApp` maps one onto the other). */
public enum class FlutterEraCopyKind {
    /** A copy of `FlutterSharedPreferences.xml`: LOGICAL keys (without `flutter.`), decoded values. */
    Prefs,

    /** The Flutter secure-storage XML: secret names (`pw.<id>`, …) with the plugin's entry prefix removed. */
    SecureStorage,
}

/**
 * A value as the engines see it. `:android:migration` may not depend on `:engine:profiles`, so this mirrors
 * `:core:data`'s `PrefValue` and `:androidApp` converts. `toString()` never prints a string.
 */
public sealed interface FlutterEraValue {
    public data class Bool(val value: Boolean) : FlutterEraValue

    public data class Int64(val value: Long) : FlutterEraValue

    public data class Dbl(val value: Double) : FlutterEraValue

    public class Str(public val value: String) : FlutterEraValue {
        override fun equals(other: Any?): Boolean = other is Str && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Str(len=${value.length})"
    }

    public class StrList(public val value: List<String>) : FlutterEraValue {
        override fun equals(other: Any?): Boolean = other is StrList && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "StrList(size=${value.size})"
    }

    /** Kept exactly as it is and never rewritten: secure-storage ciphertext, a value Dart could not read either. */
    public data object Opaque : FlutterEraValue
}

public sealed interface FlutterEraRead {
    /** The copy is gone (cleaned up, or never existed). */
    public data object Missing : FlutterEraRead

    /** Android would load an empty map from it (or it is not UTF-8): never edited by guesswork. */
    public data object Unreadable : FlutterEraRead

    public class Entries(public val entries: Map<String, FlutterEraValue>) : FlutterEraRead {
        override fun toString(): String = "Entries(size=${entries.size})"
    }
}

/**
 * One retained Flutter-era file (plan 16 §1.4). Blocking: call from the IO dispatcher. [rewrite] takes the full new
 * entry map (the engine's structural edit applied to [read]) and writes only the difference, in place, through a
 * `.tmp` file, fsync and an atomic rename; every other entry keeps its exact bytes. A copy inside the S0 backup also
 * refreshes the backup's `manifest.json` so the backup still verifies.
 */
public interface FlutterEraCopy {
    /** A path relative to the app's data directory: no content, safe for logs. */
    public val label: String
    public val kind: FlutterEraCopyKind

    public fun read(): FlutterEraRead

    /** True when the copy now holds exactly [entries] (or already did). False: refused, nothing changed. */
    public fun rewrite(entries: Map<String, FlutterEraValue>): Boolean
}

/**
 * The Android `RetainedCopies` (E00-S08, CR16-14): every Flutter-era copy of a server's data that the native app keeps
 * after the update, computed at deletion time:
 *
 * - the live `shared_prefs/FlutterSecureStorage.xml` and its `.bak` twin (M2 copies secrets out of it but never deletes
 *   it, so a forgotten server's `pw.<id>`, `oc.codexToken.<id>`, `oc.githubToken.<id>` would stay there);
 * - the S0 byte backup `files/migration/flutter-v57/` (its secure-storage and prefs copies, each `.bak` twin);
 * - every Start fresh copy `files/migration/replaced-<at>/` (prefs and twin).
 *
 * The live `FlutterSharedPreferences.xml` is the native app's own store and is swept with the live data, not here.
 */
public class FlutterEraCopies(private val paths: MigrationPaths) {
    public fun list(): List<FlutterEraCopy> = buildList {
        for (name in twins(FssFormat.DATA_FILE)) {
            File(paths.sharedPrefs, name).takeIf { it.isFile }?.let { add(SecureStorageCopy(it, "shared_prefs/$name", null)) }
        }
        val backup = paths.backup
        if (backup.isDirectory) {
            for (name in twins(FssFormat.DATA_FILE)) {
                File(backup, name).takeIf { it.isFile }?.let { add(SecureStorageCopy(it, label(it), backup)) }
            }
            for (name in twins(FlutterPrefsCodec.FILE_NAME)) {
                File(backup, name).takeIf { it.isFile }?.let { add(PrefsCopy(it, label(it), backup)) }
            }
        }
        val replaced = paths.backupRoot.listFiles { f -> f.isDirectory && f.name.startsWith(REPLACED_PREFIX) }.orEmpty()
        for (dir in replaced.sortedBy { it.name }) {
            for (name in twins(FlutterPrefsCodec.FILE_NAME)) {
                File(dir, name).takeIf { it.isFile }?.let { add(PrefsCopy(it, label(it), null)) }
            }
        }
    }

    private fun twins(store: String): List<String> = listOf("$store.xml", "$store.xml.bak")

    private fun label(file: File): String = file.relativeTo(paths.dataDir).invariantSeparatorsPath

    private abstract class XmlCopy(private val file: File, final override val label: String, private val backupDir: File?) :
        FlutterEraCopy {
        /** Physical entries of a file Android would load, or why there are none to edit. */
        protected fun physical(): Physical {
            if (!file.isFile) return Physical.Missing
            val read = AndroidPrefsXml.parse(file)
            if (read.load != AndroidLoad.Loaded || XmlProblem.MalformedUtf8 in read.problems) return Physical.Unreadable
            return Physical.Loaded(read.entries)
        }

        override fun read(): FlutterEraRead = when (val p = physical()) {
            Physical.Missing -> FlutterEraRead.Missing
            Physical.Unreadable -> FlutterEraRead.Unreadable
            is Physical.Loaded -> FlutterEraRead.Entries(logical(p.entries))
        }

        override fun rewrite(entries: Map<String, FlutterEraValue>): Boolean {
            val before = when (val p = physical()) {
                Physical.Missing -> return entries.isEmpty()
                Physical.Unreadable -> return false
                is Physical.Loaded -> p.entries
            }
            val change = plan(before, entries) ?: return false
            val bytes = file.readBytes()
            val edited = when (val r = PrefsXmlEdit.edit(bytes, change.first, change.second)) {
                PrefsXmlEdit.Result.Unchanged -> return true
                is PrefsXmlEdit.Result.Refused -> return false
                is PrefsXmlEdit.Result.Edited -> r.bytes
            }
            if (backupDir != null) {
                if (!S0Backup.markPending(backupDir, file.name, edited.size.toLong(), Fs.sha256Hex(edited))) return false
            }
            Fs.writeAtomically(file, edited)
            if (backupDir != null) S0Backup.commitPending(backupDir, file.name)
            return true
        }

        /** Logical view of the physical entries. */
        protected abstract fun logical(physical: Map<String, RawPref>): Map<String, FlutterEraValue>

        /** Physical names to remove and physical values to put so the file holds [wanted]; null to refuse. */
        protected abstract fun plan(
            physical: Map<String, RawPref>,
            wanted: Map<String, FlutterEraValue>,
        ): Pair<Set<String>, Map<String, RawPref>>?
    }

    /** `flutter_secure_storage` data file: only removals; a value is never written (it is ciphertext). */
    private class SecureStorageCopy(file: File, label: String, backupDir: File?) : XmlCopy(file, label, backupDir) {
        override val kind: FlutterEraCopyKind = FlutterEraCopyKind.SecureStorage

        override fun logical(physical: Map<String, RawPref>): Map<String, FlutterEraValue> =
            physical.keys.mapNotNull(::secretName).associateWith { FlutterEraValue.Opaque }

        override fun plan(
            physical: Map<String, RawPref>,
            wanted: Map<String, FlutterEraValue>,
        ): Pair<Set<String>, Map<String, RawPref>>? {
            val have = logical(physical)
            if (wanted.keys.any { it !in have } || wanted.values.any { it != FlutterEraValue.Opaque }) return null
            val doomed = have.keys - wanted.keys
            // The plugin's own "migrate with backup" path leaves `<entry>_BACKUP` with the same secret: it goes too.
            val names = physical.keys.filter { secretName(it) in doomed }.toSet()
            return names to emptyMap()
        }

        private fun secretName(physical: String): String? =
            physical.takeIf { it.startsWith(ENTRY) }?.removePrefix(ENTRY)?.removeSuffix(FssFormat.BACKUP_SUFFIX)
                ?.takeIf { it.isNotEmpty() }

        private companion object {
            val ENTRY = FssFormat.ENTRY_PREFIX + "_"
        }
    }

    /** A `FlutterSharedPreferences.xml` copy: `flutter.`-prefixed keys, values through the plugin's codec. */
    private class PrefsCopy(file: File, label: String, backupDir: File?) : XmlCopy(file, label, backupDir) {
        override val kind: FlutterEraCopyKind = FlutterEraCopyKind.Prefs

        override fun logical(physical: Map<String, RawPref>): Map<String, FlutterEraValue> {
            val out = LinkedHashMap<String, FlutterEraValue>()
            for ((key, raw) in physical) {
                val logical = FlutterPrefsCodec.logicalKey(key) ?: continue
                out[logical] = toEra(FlutterPrefsCodec.decode(raw).value)
            }
            return out
        }

        override fun plan(
            physical: Map<String, RawPref>,
            wanted: Map<String, FlutterEraValue>,
        ): Pair<Set<String>, Map<String, RawPref>>? {
            val have = logical(physical)
            val remove = (have.keys - wanted.keys).map(FlutterPrefsCodec::physicalKey).toSet()
            val put = LinkedHashMap<String, RawPref>()
            for ((key, value) in wanted) {
                if (have[key] == value) continue
                put[FlutterPrefsCodec.physicalKey(key)] = encode(value) ?: return null
            }
            return remove to put
        }

        private fun toEra(value: PrefValue?): FlutterEraValue = when (value) {
            is PrefValue.Bool -> FlutterEraValue.Bool(value.value)
            is PrefValue.Int64 -> FlutterEraValue.Int64(value.value)
            is PrefValue.Dbl -> FlutterEraValue.Dbl(value.value)
            is PrefValue.Str -> FlutterEraValue.Str(value.value)
            is PrefValue.StrList -> FlutterEraValue.StrList(value.value)
            is PrefValue.Undecodable, null -> FlutterEraValue.Opaque
        }

        private fun encode(value: FlutterEraValue): RawPref? = when (value) {
            is FlutterEraValue.Bool -> FlutterPrefsCodec.encodeBool(value.value)
            is FlutterEraValue.Int64 -> FlutterPrefsCodec.encodeInt(value.value)
            is FlutterEraValue.Dbl -> FlutterPrefsCodec.encodeDouble(value.value)
            is FlutterEraValue.Str -> if (FlutterPrefsCodec.clashes(value.value)) null else FlutterPrefsCodec.encodeString(value.value)
            is FlutterEraValue.StrList -> FlutterPrefsCodec.encodeStringList(value.value)
            FlutterEraValue.Opaque -> null
        }
    }

    private sealed interface Physical {
        data object Missing : Physical
        data object Unreadable : Physical
        class Loaded(val entries: Map<String, RawPref>) : Physical
    }

    private companion object {
        const val REPLACED_PREFIX = "replaced-"
    }
}
