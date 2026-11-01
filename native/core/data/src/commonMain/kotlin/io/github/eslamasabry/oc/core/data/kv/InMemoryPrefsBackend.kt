package io.github.eslamasabry.oc.core.data.kv

/**
 * A [PrefsBackend] held in memory: the desktop store until the desktop phase (plan 05 §2.4) and the base of test
 * backends. Not thread-safe on its own; [PrefsKeyValueStore] only calls it under its file lock.
 */
public open class InMemoryPrefsBackend(initial: Map<String, RawPref> = emptyMap()) : PrefsBackend {
    protected val entries: LinkedHashMap<String, RawPref> = LinkedHashMap(initial)

    override fun load(): Map<String, RawPref> = LinkedHashMap(entries)

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        for ((k, v) in changes) if (v == null) entries.remove(k) else entries[k] = v
        return true
    }

    /** What a fresh process would load (tests: "after a restart"). */
    public fun persisted(): Map<String, RawPref> = LinkedHashMap(entries)
}
