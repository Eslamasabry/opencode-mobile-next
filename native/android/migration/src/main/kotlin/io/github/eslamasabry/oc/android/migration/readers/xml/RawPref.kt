package io.github.eslamasabry.oc.android.migration.readers.xml

/**
 * One value exactly as Android's `SharedPreferencesImpl` holds it after loading the XML file
 * (`XmlUtils.readMapXml`). Nothing is decoded here: Flutter's string prefixes are handled by
 * [io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec].
 *
 * `toString()` never prints the value. Preference values hold prompt text, server addresses and
 * file paths, and these types end up in assertion messages and logs.
 */
internal sealed interface RawPref {
    /** The XML tag Android writes for this value (`string`, `boolean`, `long`, …). */
    val tag: String

    class Str(public val value: String) : RawPref {
        override val tag: String get() = "string"
        override fun equals(other: Any?): Boolean = other is Str && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Str(len=${value.length})"
    }

    data class Bool(val value: Boolean) : RawPref {
        override val tag: String get() = "boolean"
    }

    data class Int32(val value: Int) : RawPref {
        override val tag: String get() = "int"
    }

    data class Int64(val value: Long) : RawPref {
        override val tag: String get() = "long"
    }

    data class F32(val value: Float) : RawPref {
        override val tag: String get() = "float"
    }

    /** `<double>`: readable by `XmlUtils`, never written by `SharedPreferences.Editor`. */
    data class F64(val value: Double) : RawPref {
        override val tag: String get() = "double"
    }

    /** `<set>` of `<string>` children (`putStringSet`). Order is file order; Android uses a HashSet. */
    class StrSet(public val value: Set<String>) : RawPref {
        override val tag: String get() = "set"
        override fun equals(other: Any?): Boolean = other is StrSet && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "StrSet(size=${value.size})"
    }

    /** `<null name="…" />`: Android keeps the key with a null value; Flutter's getAll skips it. */
    data object Null : RawPref {
        override val tag: String get() = "null"
    }

    /**
     * A tag `XmlUtils` can read but `SharedPreferences.Editor` never writes (`int-array`, `map`, `list`, …).
     * The raw element text is kept so that a rewrite of the file could carry it over byte-for-byte.
     */
    class Other(override val tag: String, public val rawXml: String) : RawPref {
        override fun equals(other: Any?): Boolean = other is Other && other.tag == tag && other.rawXml == rawXml
        override fun hashCode(): Int = tag.hashCode() * 31 + rawXml.hashCode()
        override fun toString(): String = "Other(tag=$tag)"
    }

    companion object {
        /**
         * Adapter for values from `SharedPreferences.getAll()` on Android, so the same readers work on a live
         * prefs map and on a raw file copy. Returns null for a type SharedPreferences cannot hold.
         */
        fun fromAndroidValue(value: Any?): RawPref? = when (value) {
            null -> Null
            is String -> Str(value)
            is Boolean -> Bool(value)
            is Int -> Int32(value)
            is Long -> Int64(value)
            is Float -> F32(value)
            is Double -> F64(value)
            is Set<*> -> if (value.all { it is String }) StrSet(value.filterIsInstance<String>().toCollection(LinkedHashSet())) else null
            else -> null
        }
    }
}

/**
 * A stable, typed byte encoding of a raw value, used for hashing (baselines and the "nothing else changed"
 * digest). Two values hash the same only when Android would hand back equal objects of the same type.
 */
internal fun RawPref.canonicalBytes(): ByteArray {
    val body = when (this) {
        is RawPref.Str -> "s:" + value
        is RawPref.Bool -> "b:" + value
        is RawPref.Int32 -> "i:" + value
        is RawPref.Int64 -> "l:" + value
        is RawPref.F32 -> "f:" + java.lang.Float.floatToRawIntBits(value)
        is RawPref.F64 -> "d:" + java.lang.Double.doubleToRawLongBits(value)
        // A HashSet has no order, so the canonical form is sorted.
        is RawPref.StrSet -> "S:" + value.sorted().joinToString("") { it.length.toString() + ":" + it }
        RawPref.Null -> "n:"
        is RawPref.Other -> "o:" + tag + ":" + rawXml
    }
    return body.toByteArray(Charsets.UTF_8)
}
