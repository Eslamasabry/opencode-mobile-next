package io.github.eslamasabry.oc.migration.readers.prefs

import io.github.eslamasabry.oc.migration.readers.xml.AndroidLoad
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The two stores behind Flutter's `SharedPreferencesAsync` / `SharedPreferencesWithCache` on Android
 * (`shared_preferences_android` 2.4.27, `SharedPreferencesPlugin.kt`):
 *
 * - DataStore (the default): `files/datastore/FlutterSharedPreferences.preferences_pb`, created by
 *   `preferencesDataStore("FlutterSharedPreferences")`. Keys have **no** `flutter.` prefix; doubles are real
 *   double keys; lists are strings with [FlutterPrefsCodec.JSON_LIST_PREFIX].
 * - SharedPreferences backend of the async API: with no `fileName` it uses
 *   `PreferenceManager.getDefaultSharedPreferences` = `shared_prefs/<package>_preferences.xml`, also without a
 *   prefix, with the same string encodings as the legacy file.
 *
 * This app has 0 call sites of either API (`grep -rn SharedPreferencesAsync lib` → nothing, 2026-11-01), so
 * both files are expected to be absent. They are read for the census only: if one appears, its keys are
 * counted and kept, never adopted silently.
 */
public object AsyncBackends {
    public const val DATASTORE_RELATIVE_PATH: String = "files/datastore/FlutterSharedPreferences.preferences_pb"

    public fun defaultPrefsName(packageName: String): String = "${packageName}_preferences"

    public sealed interface DataStoreRead {
        public data object Missing : DataStoreRead
        public class Parsed(public val entries: LinkedHashMap<String, RawPref>) : DataStoreRead {
            override fun toString(): String = "Parsed(entries=${entries.size})"
        }
        /** DataStore throws `CorruptionException` on such a file; the plugin has no corruption handler. */
        public data class Corrupt(val offset: Int) : DataStoreRead
    }

    public fun readDataStore(file: File): DataStoreRead =
        if (!file.exists()) DataStoreRead.Missing else readDataStore(file.readBytes())

    /**
     * Decode `PreferenceMap` (androidx.datastore.preferences, `preferences.proto`):
     * `message PreferenceMap { map<string, Value> preferences = 1; }`,
     * `message Value { oneof { bool boolean = 1; float float = 2; int32 integer = 3; int64 long = 4;
     *   string string = 5; StringSet string_set = 6; double double = 7; bytes bytes = 8; } }`,
     * `message StringSet { repeated string strings = 1; }`.
     * Values are mapped onto [RawPref] so the same [FlutterPrefsCodec] decodes them. `bytes` values (never
     * written by Flutter) become [RawPref.Other].
     */
    public fun readDataStore(bytes: ByteArray): DataStoreRead {
        val out = LinkedHashMap<String, RawPref>()
        val r = Proto(bytes)
        try {
            while (!r.eof()) {
                val (field, wire) = r.tag()
                if (field == 1 && wire == 2) {
                    val entry = Proto(r.lengthDelimited())
                    var key: String? = null
                    var value: RawPref? = null
                    while (!entry.eof()) {
                        val (f, w) = entry.tag()
                        when {
                            f == 1 && w == 2 -> key = String(entry.lengthDelimited(), Charsets.UTF_8)
                            f == 2 && w == 2 -> value = readValue(Proto(entry.lengthDelimited()))
                            else -> entry.skip(w)
                        }
                    }
                    if (key != null) out[key] = value ?: RawPref.Null
                } else {
                    r.skip(wire)
                }
            }
        } catch (e: ProtoException) {
            return DataStoreRead.Corrupt(e.offset)
        }
        return DataStoreRead.Parsed(out)
    }

    private fun readValue(v: Proto): RawPref? {
        var result: RawPref? = null
        while (!v.eof()) {
            val (f, w) = v.tag()
            result = when {
                f == 1 && w == 0 -> RawPref.Bool(v.varint() != 0L)
                f == 2 && w == 5 -> RawPref.F32(java.lang.Float.intBitsToFloat(v.fixed32()))
                f == 3 && w == 0 -> RawPref.Int32(v.varint().toInt())
                f == 4 && w == 0 -> RawPref.Int64(v.varint())
                f == 5 && w == 2 -> RawPref.Str(String(v.lengthDelimited(), Charsets.UTF_8))
                f == 6 && w == 2 -> {
                    val set = Proto(v.lengthDelimited())
                    val strings = LinkedHashSet<String>()
                    while (!set.eof()) {
                        val (sf, sw) = set.tag()
                        if (sf == 1 && sw == 2) strings += String(set.lengthDelimited(), Charsets.UTF_8) else set.skip(sw)
                    }
                    RawPref.StrSet(strings)
                }
                f == 7 && w == 1 -> RawPref.F64(java.lang.Double.longBitsToDouble(v.fixed64()))
                f == 8 && w == 2 -> RawPref.Other("bytes", v.lengthDelimited().size.toString())
                else -> {
                    v.skip(w)
                    result
                }
            }
        }
        return result
    }

    /** Writer for fixtures (same schema). */
    public fun writeDataStore(entries: Map<String, RawPref>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((k, v) in entries) {
            val value = ByteArrayOutputStream()
            when (v) {
                is RawPref.Bool -> { tag(value, 1, 0); varint(value, if (v.value) 1 else 0) }
                is RawPref.F32 -> { tag(value, 2, 5); fixed32(value, java.lang.Float.floatToRawIntBits(v.value)) }
                is RawPref.Int32 -> { tag(value, 3, 0); varint(value, v.value.toLong()) }
                is RawPref.Int64 -> { tag(value, 4, 0); varint(value, v.value) }
                is RawPref.Str -> { tag(value, 5, 2); bytes(value, v.value.toByteArray(Charsets.UTF_8)) }
                is RawPref.StrSet -> {
                    val set = ByteArrayOutputStream()
                    for (s in v.value) { tag(set, 1, 2); bytes(set, s.toByteArray(Charsets.UTF_8)) }
                    tag(value, 6, 2); bytes(value, set.toByteArray())
                }
                is RawPref.F64 -> { tag(value, 7, 1); fixed64(value, java.lang.Double.doubleToRawLongBits(v.value)) }
                RawPref.Null, is RawPref.Other -> throw IllegalArgumentException("DataStore cannot hold ${v.tag}")
            }
            val entry = ByteArrayOutputStream()
            tag(entry, 1, 2); bytes(entry, k.toByteArray(Charsets.UTF_8))
            tag(entry, 2, 2); bytes(entry, value.toByteArray())
            tag(out, 1, 2); bytes(out, entry.toByteArray())
        }
        return out.toByteArray()
    }

    /** Result of looking for both async stores under an app data dir (`/data/data/<package>`). */
    public class Census(
        public val dataStoreKeys: Int?,
        public val dataStoreCorrupt: Boolean,
        public val defaultPrefsKeys: Int?,
        public val defaultPrefsLoadsEmpty: Boolean,
    ) {
        public val anyPresent: Boolean get() = dataStoreKeys != null || dataStoreCorrupt || defaultPrefsKeys != null
        override fun toString(): String =
            "Census(dataStoreKeys=$dataStoreKeys, dataStoreCorrupt=$dataStoreCorrupt, defaultPrefsKeys=$defaultPrefsKeys, defaultPrefsLoadsEmpty=$defaultPrefsLoadsEmpty)"
    }

    public fun census(appDataDir: File, packageName: String): Census {
        val ds = readDataStore(File(appDataDir, DATASTORE_RELATIVE_PATH))
        val sp = io.github.eslamasabry.oc.migration.readers.xml.SharedPrefsFile(
            File(appDataDir, "shared_prefs"), defaultPrefsName(packageName),
        ).read()
        return Census(
            dataStoreKeys = (ds as? DataStoreRead.Parsed)?.entries?.size,
            dataStoreCorrupt = ds is DataStoreRead.Corrupt,
            defaultPrefsKeys = sp?.entries?.size,
            defaultPrefsLoadsEmpty = sp?.load == AndroidLoad.LoadsEmpty,
        )
    }

    // ---- protobuf wire helpers ----

    private class ProtoException(val offset: Int) : Exception(null, null, false, false)

    private class Proto(private val b: ByteArray) {
        private var i = 0
        fun eof() = i >= b.size
        fun tag(): Pair<Int, Int> {
            val t = varint()
            if (t <= 0 || t > Int.MAX_VALUE) throw ProtoException(i)
            return (t ushr 3).toInt() to (t and 7).toInt()
        }
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (i >= b.size || shift > 63) throw ProtoException(i)
                val x = b[i++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }
        fun fixed32(): Int {
            if (i + 4 > b.size) throw ProtoException(i)
            var r = 0
            for (k in 0 until 4) r = r or ((b[i + k].toInt() and 0xFF) shl (8 * k))
            i += 4
            return r
        }
        fun fixed64(): Long {
            if (i + 8 > b.size) throw ProtoException(i)
            var r = 0L
            for (k in 0 until 8) r = r or ((b[i + k].toLong() and 0xFF) shl (8 * k))
            i += 8
            return r
        }
        fun lengthDelimited(): ByteArray {
            val len = varint()
            if (len < 0 || i + len > b.size) throw ProtoException(i)
            val out = b.copyOfRange(i, i + len.toInt())
            i += len.toInt()
            return out
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> { if (i + 8 > b.size) throw ProtoException(i); i += 8 }
                2 -> lengthDelimited()
                5 -> { if (i + 4 > b.size) throw ProtoException(i); i += 4 }
                else -> throw ProtoException(i)
            }
        }
    }

    private fun tag(o: ByteArrayOutputStream, field: Int, wire: Int) = varint(o, ((field shl 3) or wire).toLong())
    private fun varint(o: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) { o.write(v.toInt()); return }
            o.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
    private fun bytes(o: ByteArrayOutputStream, b: ByteArray) { varint(o, b.size.toLong()); o.write(b) }
    private fun fixed32(o: ByteArrayOutputStream, v: Int) { for (k in 0 until 4) o.write((v ushr (8 * k)) and 0xFF) }
    private fun fixed64(o: ByteArrayOutputStream, v: Long) { for (k in 0 until 8) o.write(((v ushr (8 * k)) and 0xFF).toInt()) }
}
