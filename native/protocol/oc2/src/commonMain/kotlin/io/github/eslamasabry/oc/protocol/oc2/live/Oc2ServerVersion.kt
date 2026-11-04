package io.github.eslamasabry.oc.protocol.oc2.live

/** Flutter `isExactServerVersion` (product_repository.dart, B-PROTO-083): an upgrade target is an exact SemVer only. */
public object Oc2ServerVersion {
    private val Exact =
        Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
        )

    public fun isExact(value: String): Boolean = value.isNotEmpty() && Exact.matches(value)
}
