package io.github.eslamasabry.oc.engine.connection.demo

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.concurrent.atomics.AtomicReference

/** Route-owned memory only. It never wraps, replaces or writes the application's preferences. */
public class DemoPreferences {
    private sealed interface Value {
        data class Text(val value: String) : Value

        data class Flag(val value: Boolean) : Value

        data class Texts(val value: List<String>) : Value
    }

    private val values = AtomicReference<Map<String, Value>>(emptyMap())

    public fun keys(): Set<String> = values.load().keys.toSet()

    public fun contains(key: String): Boolean = key in values.load()

    public fun string(key: String): String? = (values.load()[key] as? Value.Text)?.value

    public fun boolean(key: String): Boolean? = (values.load()[key] as? Value.Flag)?.value

    public fun stringList(key: String): List<String>? =
        (values.load()[key] as? Value.Texts)?.value?.toList()

    public fun setString(key: String, value: String): Unit = put(key, Value.Text(value))

    public fun setBoolean(key: String, value: Boolean): Unit = put(key, Value.Flag(value))

    public fun setStringList(key: String, value: List<String>): Unit =
        put(key, Value.Texts(value.toList()))

    private fun put(key: String, value: Value) {
        while (true) {
            val current = values.load()
            if (values.compareAndSet(current, current + (key to value))) return
        }
    }

    public fun clear() {
        values.store(emptyMap())
    }
}

/**
 * A synthetic identity only: no profile registry, secure storage, gateway or network client is
 * admitted.
 */
public class DemoProfileStore {
    public val profileId: ProfileId = ProfileId("offline-demo")
    public val prefs: DemoPreferences = DemoPreferences()
}
