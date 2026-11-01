package io.github.eslamasabry.oc.core.data.kv

import io.github.eslamasabry.oc.core.data.dart.DartJson

/**
 * Decodes the Java-serialised string list of `shared_preferences_android` before 2.4.3 (`ListEncoder` +
 * `StringListObjectInputStream`). Needs `java.io.ObjectInputStream`, so the JVM platforms supply it; null = unreadable.
 */
public fun interface JavaListDecoder {
    public fun decode(serialized: ByteArray): List<String>?

    public companion object {
        /** For platforms with no Flutter Android install to adopt (desktop, iOS): every legacy list is unreadable. */
        public val Unsupported: JavaListDecoder = JavaListDecoder { null }
    }
}

/** One decoded preference. [raw] is kept so adoption never has to re-encode an untouched value. */
public class DecodedPref(public val raw: RawPref, public val kind: StoredKind, public val value: PrefValue?) {
    /** True when Dart's `getAll()` would not return the key at all (`<null>` values). */
    public val invisibleToDart: Boolean get() = value == null

    override fun toString(): String = "DecodedPref(kind=$kind, value=$value)"
}

/** A value a native writer must refuse, as the plugin does (plan 05 §2.2 last row). */
public class PrefixClash internal constructor() : IllegalArgumentException(
    "This string cannot be stored as it clashes with special identifier prefixes",
)

/**
 * The value encodings of `shared_preferences_android` 2.4.27 (`LegacySharedPreferencesPlugin.kt`, `ListEncoder`,
 * `StringListObjectInputStream`) and `shared_preferences` 2.5.5 (`flutter.` key prefix). Plan 05 §2.2.
 *
 * Copied from `native/tools/flutter-migration-readers` (`prefs/FlutterPrefsCodec.kt`, P5) and made multiplatform: the
 * Java-list step is injected ([JavaListDecoder]) and the Android Base64 decoder works on plain byte arrays.
 */
public class FlutterPrefsCodec(private val javaLists: JavaListDecoder) {

    /** Decode one raw value the way `LegacySharedPreferencesPlugin.transformPref` + the Dart side do, without writing. */
    public fun decode(raw: RawPref): DecodedPref =
        when (raw) {
            is RawPref.Bool -> DecodedPref(raw, StoredKind.Bool, PrefValue.Bool(raw.value))
            is RawPref.Int64 -> DecodedPref(raw, StoredKind.Long, PrefValue.Int64(raw.value))
            // Dart sees `int` for a Java Integer and `double` for Float / Double.
            is RawPref.Int32 -> DecodedPref(raw, StoredKind.Int, PrefValue.Int64(raw.value.toLong()))
            is RawPref.F32 -> DecodedPref(raw, StoredKind.Float, PrefValue.Dbl(raw.value.toDouble()))
            is RawPref.F64 -> DecodedPref(raw, StoredKind.Double, PrefValue.Dbl(raw.value))
            is RawPref.StrSet -> DecodedPref(raw, StoredKind.StringSet, PrefValue.StrList(raw.value.toList()))
            RawPref.Null -> DecodedPref(raw, StoredKind.Null, null)
            is RawPref.Other -> DecodedPref(raw, StoredKind.Other, PrefValue.Undecodable(UndecodableKind.UnknownType))
            is RawPref.Str -> decodeString(raw)
        }

    private fun decodeString(raw: RawPref.Str): DecodedPref {
        val s = raw.value
        return when {
            s.startsWith(JSON_LIST_PREFIX) ->
                DecodedPref(raw, StoredKind.JsonList, decodeJsonList(s.substring(JSON_LIST_PREFIX.length)))
            s.startsWith(LIST_PREFIX) ->
                DecodedPref(raw, StoredKind.JavaList, decodeJavaList(s.substring(LIST_PREFIX.length)))
            s.startsWith(BIG_INTEGER_PREFIX) ->
                DecodedPref(raw, StoredKind.BigInteger, decodeBigInteger(s.substring(BIG_INTEGER_PREFIX.length)))
            s.startsWith(DOUBLE_PREFIX) ->
                DecodedPref(raw, StoredKind.PrefixedDouble, decodeDouble(s.substring(DOUBLE_PREFIX.length)))
            else -> DecodedPref(raw, StoredKind.PlainString, PrefValue.Str(s))
        }
    }

    private fun decodeJsonList(json: String): PrefValue {
        val parsed = DartJson.decodeOrNull(json) as? List<*> ?: return PrefValue.Undecodable(UndecodableKind.BadJsonList)
        // Dart: (jsonDecode(..) as List).cast<String>().toList(): any non-string element throws.
        if (!parsed.all { it is String }) return PrefValue.Undecodable(UndecodableKind.BadJsonList)
        return PrefValue.StrList(parsed.filterIsInstance<String>())
    }

    private fun decodeDouble(text: String): PrefValue =
        // The plugin calls Kotlin String.toDouble() (java.lang.Double.parseDouble on Android).
        text.toDoubleOrNull()?.let { PrefValue.Dbl(it) } ?: PrefValue.Undecodable(UndecodableKind.BadDouble)

    private fun decodeBigInteger(text: String): PrefValue =
        // The plugin returns BigInteger(text, 36); the message codec sends it to Dart as an int when it fits 64 bits.
        text.toLongOrNull(BIG_INTEGER_RADIX)?.let { PrefValue.Int64(it) }
            ?: PrefValue.Undecodable(UndecodableKind.BadBigInteger)

    /** `ListEncoder.decode`: Android `Base64.decode(s, DEFAULT)` then a restricted `ObjectInputStream`. */
    public fun decodeJavaList(base64: String): PrefValue {
        val bytes = decodeAndroidBase64(base64) ?: return PrefValue.Undecodable(UndecodableKind.BadJavaList)
        val list = javaLists.decode(bytes) ?: return PrefValue.Undecodable(UndecodableKind.BadJavaList)
        return PrefValue.StrList(list)
    }

    // ---- encoding: what a native writer must produce so Flutter (and the R1 rollback build) reads it back ----

    /** `setString`: refused when the value starts with any Flutter prefix, as the plugin does. */
    public fun encodeString(value: String): RawPref.Str {
        if (clashes(value)) throw PrefixClash()
        return RawPref.Str(value)
    }

    /** `setStringList`: `JSON_LIST_PREFIX + jsonEncode(list)` (the form written since plugin 2.4.3). */
    public fun encodeStringList(value: List<String>): RawPref.Str = RawPref.Str(JSON_LIST_PREFIX + DartJson.encode(value))

    /** `setDouble`: the plugin writes `DOUBLE_PREFIX + value.toString()` (Kotlin `Double.toString` on the device). */
    public fun encodeDouble(value: Double): RawPref.Str = RawPref.Str(DOUBLE_PREFIX + value.toString())

    /** `setInt`: always `putLong` (Dart int is 64-bit). */
    public fun encodeLong(value: Long): RawPref.Int64 = RawPref.Int64(value)

    public fun encodeBool(value: Boolean): RawPref.Bool = RawPref.Bool(value)

    public companion object {
        /** `SHARED_PREFERENCES_NAME` → `shared_prefs/FlutterSharedPreferences.xml`. */
        public const val FILE_NAME: String = "FlutterSharedPreferences"

        /** Dart `SharedPreferences` adds this to every key (legacy API; the app never calls `setPrefix`). */
        public const val KEY_PREFIX: String = "flutter."

        /** Base64 of "This is the prefix for a list." */
        public const val LIST_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

        /** `!` cannot occur in Base64, so it marks the JSON form written since `shared_preferences_android` 2.4.3. */
        public const val JSON_LIST_PREFIX: String = "$LIST_PREFIX!"

        /** Base64 of "This is the prefix for Double." */
        public const val DOUBLE_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

        /** Base64 of "This is the prefix for BigInteger" (legacy plugin only; never written by this app). */
        public const val BIG_INTEGER_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"

        /** `StringListObjectInputStream`'s allowlist, verbatim. */
        public val JAVA_LIST_ALLOWED_CLASSES: Set<String> =
            setOf("java.util.Arrays\$ArrayList", "java.util.ArrayList", "java.lang.String", "[Ljava.lang.String;")

        private const val BIG_INTEGER_RADIX = 36

        public fun physicalKey(logicalKey: String): String = KEY_PREFIX + logicalKey

        /** Null when the physical key lacks the `flutter.` prefix (not visible to Dart, never exposed). */
        public fun logicalKey(physicalKey: String): String? =
            if (physicalKey.startsWith(KEY_PREFIX)) physicalKey.substring(KEY_PREFIX.length) else null

        public fun clashes(value: String): Boolean =
            value.startsWith(LIST_PREFIX) || value.startsWith(BIG_INTEGER_PREFIX) || value.startsWith(DOUBLE_PREFIX)

        /**
         * A port of AOSP `android.util.Base64.decode(str, DEFAULT)`: every byte outside the alphabet is SKIPPED,
         * padding is optional; `=` in the first two positions of a group, a lone trailing sextet, or data after
         * padding are errors. Returns null where Android throws "bad base-64".
         */
        @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
        public fun decodeAndroidBase64(text: String): ByteArray? {
            val input = text.encodeToByteArray()
            val out = ByteSink(input.size * 3 / 4 + 3)
            var state = 0
            var value = 0
            for (b in input) {
                val d = ANDROID_DECODE[b.toInt() and 0xFF]
                when (state) {
                    0 -> if (d >= 0) { value = d; state = 1 } else if (d != SKIP) return null
                    1 -> if (d >= 0) { value = (value shl 6) or d; state = 2 } else if (d != SKIP) return null
                    2 -> if (d >= 0) {
                        value = (value shl 6) or d; state = 3
                    } else if (d == EQUALS) {
                        out.write(value shr 4); state = 4
                    } else if (d != SKIP) return null
                    3 -> if (d >= 0) {
                        value = (value shl 6) or d
                        out.write(value shr 16); out.write(value shr 8); out.write(value)
                        state = 0
                    } else if (d == EQUALS) {
                        out.write(value shr 10); out.write(value shr 2)
                        state = 5
                    } else if (d != SKIP) return null
                    4 -> if (d == EQUALS) state = 5 else if (d != SKIP) return null
                    5 -> if (d != SKIP) return null
                }
            }
            when (state) {
                1, 4 -> return null
                2 -> out.write(value shr 4)
                3 -> { out.write(value shr 10); out.write(value shr 2) }
                else -> Unit
            }
            return out.toByteArray()
        }

        private const val SKIP = -1
        private const val EQUALS = -2
        private val ANDROID_DECODE: IntArray = IntArray(256) { SKIP }.also { t ->
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> t[c.code] = i }
            t['='.code] = EQUALS
        }
    }
}

private class ByteSink(capacity: Int) {
    private var buf = ByteArray(maxOf(capacity, 8))
    private var size = 0

    fun write(b: Int) {
        if (size == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[size++] = b.toByte()
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}
