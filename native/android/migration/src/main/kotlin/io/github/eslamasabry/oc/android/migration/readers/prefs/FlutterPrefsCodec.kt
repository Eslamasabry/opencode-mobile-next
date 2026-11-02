package io.github.eslamasabry.oc.android.migration.readers.prefs

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.math.BigInteger
import java.util.Base64

/** The decoded value Dart code would see through `SharedPreferences.get*`. Matches plan/05 §2.1. */
internal sealed interface PrefValue {
    data class Bool(val value: Boolean) : PrefValue
    data class Int64(val value: Long) : PrefValue
    data class Dbl(val value: Double) : PrefValue

    class Str(public val value: String) : PrefValue {
        override fun equals(other: Any?): Boolean = other is Str && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Str(len=${value.length})"
    }

    class StrList(public val value: List<String>) : PrefValue {
        override fun equals(other: Any?): Boolean = other is StrList && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "StrList(size=${value.size})"
    }

    /** Never carries the raw value. */
    data class Undecodable(val kind: UndecodableKind) : PrefValue
}

internal enum class UndecodableKind {
    /** `…Qu!` prefix, but the rest is not a JSON array of strings. */
    BadJsonList,
    /** Old `…Qu` prefix, but Base64 / Java serialization fails or contains a class outside the allowlist. */
    BadJavaList,
    /** Double prefix, but `toDouble()` fails. */
    BadDouble,
    /** BigInteger prefix, but the base-36 text is invalid, or the number does not fit a Dart (64-bit) int. */
    BadBigInteger,
    /** A tag SharedPreferences never writes (`int-array`, `map`, …). */
    UnknownType,
}

/** How the value was stored. Counted by the census (M1) and kept with every decoded value. */
internal enum class StoredKind {
    Bool, Long, Int, Float, Double, JsonList, JavaList, PrefixedDouble, BigInteger, StringSet, PlainString, Null, Other
}

/** One decoded preference. [raw] is kept so that adoption never has to re-encode an untouched value. */
internal class DecodedPref(public val raw: RawPref, public val kind: StoredKind, public val value: PrefValue?) {
    /** True when Dart's legacy `getAll()` would not return the key at all (`<null>` values). */
    val invisibleToDart: Boolean get() = value == null
    override fun toString(): String = "DecodedPref(kind=$kind, value=$value)"
}

/**
 * The value encodings of `shared_preferences_android` 2.4.27 (`LegacySharedPreferencesPlugin.kt`,
 * `SharedPreferencesPlugin.kt`, `StringListObjectInputStream.kt`, `lib/src/strings.dart`) and the Dart side of
 * `shared_preferences` 2.5.5 (`flutter.` key prefix).
 */
internal object FlutterPrefsCodec {
    /** `SHARED_PREFERENCES_NAME` → `shared_prefs/FlutterSharedPreferences.xml`. */
    const val FILE_NAME: String = "FlutterSharedPreferences"

    /** Dart `SharedPreferences` adds this to every key (legacy API; the app never calls `setPrefix`). */
    const val KEY_PREFIX: String = "flutter."

    /** Base64 of "This is the prefix for a list." */
    const val LIST_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    /** `!` cannot occur in Base64, so it marks the JSON form written since `shared_preferences_android` 2.4.3. */
    const val JSON_LIST_PREFIX: String = "$LIST_PREFIX!"

    /** Base64 of "This is the prefix for Double." */
    const val DOUBLE_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    /** Base64 of "This is the prefix for BigInteger" (legacy plugin only; never written by this app). */
    const val BIG_INTEGER_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"

    /** `StringListObjectInputStream`'s allowlist, verbatim. */
    val JAVA_LIST_ALLOWED_CLASSES: Set<String> =
        setOf("java.util.Arrays\$ArrayList", "java.util.ArrayList", "java.lang.String", "[Ljava.lang.String;")

    fun physicalKey(logicalKey: String): String = KEY_PREFIX + logicalKey

    /** Null when the physical key lacks the `flutter.` prefix (not visible to Dart). */
    fun logicalKey(physicalKey: String): String? =
        if (physicalKey.startsWith(KEY_PREFIX)) physicalKey.substring(KEY_PREFIX.length) else null

    /**
     * Decode one raw value the way `LegacySharedPreferencesPlugin.transformPref` + the Dart side
     * (`SharedPreferencesAndroid.getAllWithParameters`) do, without the plugin's side effect (it rewrites a
     * `<set>` into the Java-list form with `apply()`; a reader must never write).
     */
    fun decode(raw: RawPref): DecodedPref = when (raw) {
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
        val parsed = MiniJson.parseOrNull(json) as? List<*> ?: return PrefValue.Undecodable(UndecodableKind.BadJsonList)
        // Dart: (jsonDecode(..) as List).cast<String>().toList() — any non-string element throws.
        if (!parsed.all { it is String }) return PrefValue.Undecodable(UndecodableKind.BadJsonList)
        return PrefValue.StrList(parsed.filterIsInstance<String>())
    }

    private fun decodeDouble(text: String): PrefValue = try {
        // The plugin calls Kotlin String.toDouble() = java.lang.Double.parseDouble.
        PrefValue.Dbl(java.lang.Double.parseDouble(text))
    } catch (e: NumberFormatException) {
        PrefValue.Undecodable(UndecodableKind.BadDouble)
    }

    private fun decodeBigInteger(text: String): PrefValue = try {
        // The plugin returns BigInteger(text, 36); the standard message codec sends it to Dart as an int.
        val big = BigInteger(text, Character.MAX_RADIX)
        if (big.bitLength() <= 63) PrefValue.Int64(big.toLong()) else PrefValue.Undecodable(UndecodableKind.BadBigInteger)
    } catch (e: NumberFormatException) {
        PrefValue.Undecodable(UndecodableKind.BadBigInteger)
    }

    /** `ListEncoder.decode`: Android `Base64.decode(s, DEFAULT)` then a restricted `ObjectInputStream`. */
    fun decodeJavaList(base64: String): PrefValue {
        val bytes = try {
            decodeAndroidBase64(base64)
        } catch (e: IllegalArgumentException) {
            return PrefValue.Undecodable(UndecodableKind.BadJavaList)
        }
        return try {
            AllowListObjectInputStream(ByteArrayInputStream(bytes)).use { ois ->
                val obj = ois.readObject() as? List<*> ?: return PrefValue.Undecodable(UndecodableKind.BadJavaList)
                if (!obj.all { it is String }) return PrefValue.Undecodable(UndecodableKind.BadJavaList)
                PrefValue.StrList(obj.filterIsInstance<String>())
            }
        } catch (e: Exception) {
            PrefValue.Undecodable(UndecodableKind.BadJavaList)
        }
    }

    /**
     * A port of AOSP `android.util.Base64.decode(str, DEFAULT)` (`Base64.Decoder.process`, read 2026-11-01), so a
     * value decodes here exactly when it decodes in the plugin: every byte outside the alphabet (line breaks,
     * spaces, but also any other junk) is SKIPPED, padding is optional, `=` in the first two positions of a
     * group, a lone trailing sextet, or data after padding are errors. Throws [IllegalArgumentException]
     * ("bad base-64") like Android does.
     */
    fun decodeAndroidBase64(text: String): ByteArray {
        val input = text.toByteArray(Charsets.UTF_8) // Android: str.getBytes()
        val out = java.io.ByteArrayOutputStream(input.size * 3 / 4)
        var state = 0
        var value = 0
        fun bad(): Nothing = throw IllegalArgumentException("bad base-64")
        for (b in input) {
            val d = ANDROID_DECODE[b.toInt() and 0xFF]
            when (state) {
                0 -> if (d >= 0) { value = d; state = 1 } else if (d != SKIP) bad()
                1 -> if (d >= 0) { value = (value shl 6) or d; state = 2 } else if (d != SKIP) bad()
                2 -> if (d >= 0) { value = (value shl 6) or d; state = 3 } else if (d == EQUALS) { out.write(value shr 4); state = 4 } else if (d != SKIP) bad()
                3 -> if (d >= 0) {
                    value = (value shl 6) or d
                    out.write(value shr 16); out.write(value shr 8); out.write(value)
                    state = 0
                } else if (d == EQUALS) {
                    out.write(value shr 10); out.write(value shr 2)
                    state = 5
                } else if (d != SKIP) bad()
                4 -> if (d == EQUALS) state = 5 else if (d != SKIP) bad()
                5 -> if (d != SKIP) bad()
            }
        }
        when (state) {
            0, 5 -> Unit
            1, 4 -> bad()
            2 -> out.write(value shr 4)
            3 -> { out.write(value shr 10); out.write(value shr 2) }
        }
        return out.toByteArray()
    }

    private const val SKIP = -1
    private const val EQUALS = -2
    private val ANDROID_DECODE: IntArray = IntArray(256) { SKIP }.also { t ->
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { i, c -> t[c.code] = i }
        t['='.code] = EQUALS
    }

    /** Android `Base64.encodeToString(bytes, DEFAULT)`: 76-character lines, each ending in `\n`. */
    fun encodeAndroidBase64Default(bytes: ByteArray): String {
        val flat = Base64.getEncoder().encodeToString(bytes)
        if (flat.isEmpty()) return ""
        return flat.chunked(76).joinToString(separator = "") { it + "\n" }
    }

    private class AllowListObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass?): Class<*>? {
            val name = desc?.name
            if (name != null && name !in JAVA_LIST_ALLOWED_CLASSES) throw ClassNotFoundException("not allowed")
            return super.resolveClass(desc)
        }
    }

    // ---- encoding (what a native writer must produce so the Flutter rollback build can still read it) ----

    class ClashException : IllegalArgumentException(
        "This string cannot be stored as it clashes with special identifier prefixes",
    )

    /** `setString`: refused when the value starts with any Flutter prefix, as the plugin does. */
    fun encodeString(value: String): RawPref.Str {
        if (clashes(value)) throw ClashException()
        return RawPref.Str(value)
    }

    /** True when a string starts with one of the plugin's type prefixes (so it is not a plain Dart string). */
    fun clashes(value: String): Boolean =
        value.startsWith(LIST_PREFIX) || value.startsWith(BIG_INTEGER_PREFIX) || value.startsWith(DOUBLE_PREFIX)

    /** `setStringList`: Dart writes `JSON_LIST_PREFIX + jsonEncode(list)` through `setEncodedStringList`. */
    fun encodeStringList(value: List<String>): RawPref.Str = RawPref.Str(JSON_LIST_PREFIX + MiniJson.encode(value))

    /** `setDouble`: the plugin writes `DOUBLE_PREFIX + value.toString()` (Kotlin/Java `Double.toString`). */
    fun encodeDouble(value: Double): RawPref.Str = RawPref.Str(DOUBLE_PREFIX + value.toString())

    /** `setInt`: always `putLong` (Dart int is 64-bit). */
    fun encodeInt(value: Long): RawPref.Int64 = RawPref.Int64(value)

    fun encodeBool(value: Boolean): RawPref.Bool = RawPref.Bool(value)

    /** Encode a decoded value back; used by round-trip tests and by the fixture generator. */
    fun encode(value: PrefValue): RawPref = when (value) {
        is PrefValue.Bool -> encodeBool(value.value)
        is PrefValue.Int64 -> encodeInt(value.value)
        is PrefValue.Dbl -> encodeDouble(value.value)
        is PrefValue.Str -> encodeString(value.value)
        is PrefValue.StrList -> encodeStringList(value.value)
        is PrefValue.Undecodable -> throw IllegalArgumentException("an undecodable value is never rewritten")
    }
}
