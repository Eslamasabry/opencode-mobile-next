package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.model.BackendKind

/**
 * PORT. The per-profile switches the readers keep in the app preferences file (logical keys `oc.<what>.<profileId>`,
 * so the profile sweep removes them). The production adapter wraps `:core:data` `KeyValueStore` (bool reads, one
 * commit per write). Writes return false when refused; nothing changed then.
 */
public interface UsageSwitches {
    public fun bool(key: String): Boolean?

    public suspend fun putBool(key: String, value: Boolean): Boolean

    public suspend fun remove(key: String): Boolean

    public fun keys(): Set<String>
}

/** An in-memory [UsageSwitches] for previews and tests. */
public class MemoryUsageSwitches(initial: Map<String, Boolean> = emptyMap()) : UsageSwitches {
    private val values = LinkedHashMap(initial)

    override fun bool(key: String): Boolean? = values[key]

    override suspend fun putBool(key: String, value: Boolean): Boolean {
        values[key] = value
        return true
    }

    override suspend fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }

    override fun keys(): Set<String> = values.keys.toSet()
}

/**
 * The parts of a saved server profile the readers look at. [inApp] = the app's own In-app Ubuntu server;
 * [needsPassword] = it asks for its password again (never contacted then).
 */
public data class UsageServer(
    val id: String,
    val backend: BackendKind,
    val inApp: Boolean = false,
    val needsPassword: Boolean = false,
)
