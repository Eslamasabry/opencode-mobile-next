package io.github.eslamasabry.oc.core.data.kv

/**
 * The decoded value Dart code would see through `SharedPreferences.get*` (plan 05 §2.1). Logical keys only.
 * `toString()` of the string-carrying variants never prints the value.
 */
public sealed interface PrefValue {
    public data class Bool(val value: Boolean) : PrefValue

    /** Dart `int` is always a Long on disk (`putLong`); never read through `getInt`. */
    public data class Int64(val value: Long) : PrefValue

    public data class Dbl(val value: Double) : PrefValue

    /** Raw string. JSON inside it is decoded by the owning engine. */
    public class Str(public val value: String) : PrefValue {
        override fun equals(other: Any?): Boolean = other is Str && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Str(len=${value.length})"
    }

    public class StrList(public val value: List<String>) : PrefValue {
        override fun equals(other: Any?): Boolean = other is StrList && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "StrList(size=${value.size})"
    }

    /** A stored value Dart could not read either. Never carries the raw value; never rewritten by the migrator. */
    public data class Undecodable(val kind: UndecodableKind) : PrefValue
}

public enum class UndecodableKind {
    /** A tag SharedPreferences never writes (`int-array`, `map`, …). */
    UnknownType,

    /** `…Qu!` prefix, but the rest is not a JSON array of strings. */
    BadJsonList,

    /** Old `…Qu` prefix, but Base64 / Java serialization fails or holds a class outside the allowlist. */
    BadJavaList,

    /** Double prefix, but `toDouble()` fails. */
    BadDouble,

    /** BigInteger prefix, but the base-36 text is invalid or does not fit a Dart (64-bit) int. */
    BadBigInteger,
}

/** The value types a native writer may put into the adopted file (plan 05 §2.3 rule 2). Used by key shapes. */
public enum class PrefType { Bool, Int64, Dbl, Str, StrList }

/** How a value was stored; the census counts these, and a decoded value always keeps its raw form. */
public enum class StoredKind {
    Bool, Long, Int, Float, Double, JsonList, JavaList, PrefixedDouble, BigInteger, StringSet, PlainString, Null, Other,
}
