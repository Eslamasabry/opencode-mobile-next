package io.github.eslamasabry.oc.engine.composer.store

/**
 * The composer's view of the app preferences file (Flutter `SharedPreferences`, logical keys
 * without the `flutter.` prefix). Reads are synchronous over acknowledged values; [commit] applies
 * every write of one edit or none.
 *
 * The production adapter wraps `:core:data` `KeyValueStore` (its `commit` is all-or-nothing and
 * publishes only acknowledged values, so the Flutter "optimistic cache after a refused write"
 * problem cannot happen here).
 */
public interface ComposerPrefs {
    public fun string(key: String): String?

    public fun bool(key: String): Boolean?

    public fun contains(key: String): Boolean

    public fun keys(): Set<String>

    /** True when every write landed; false when refused, and then nothing changed. */
    public suspend fun commit(edit: PrefsEdit): Boolean

    /** Refuses when any acknowledged source value changed while a migration copied its files. */
    public suspend fun compareAndCommit(expected: Map<String, String?>, edit: PrefsEdit): Boolean =
        false
}

/** One atomic set of writes. */
public class PrefsEdit {
    internal val writes: LinkedHashMap<String, PrefWrite> = LinkedHashMap()

    public fun putString(key: String, value: String): PrefsEdit = apply {
        writes[key] = PrefWrite.Str(value)
    }

    public fun putBool(key: String, value: Boolean): PrefsEdit = apply {
        writes[key] = PrefWrite.Bool(value)
    }

    public fun remove(key: String): PrefsEdit = apply { writes[key] = PrefWrite.Remove }

    public val entries: Map<String, PrefWrite>
        get() = writes

    public val isEmpty: Boolean
        get() = writes.isEmpty()
}

public sealed interface PrefWrite {
    public data class Str(val value: String) : PrefWrite {
        override fun toString(): String = "Str(${value.length} chars)"
    }

    public data class Bool(val value: Boolean) : PrefWrite

    public data object Remove : PrefWrite
}

public suspend fun ComposerPrefs.commit(build: PrefsEdit.() -> Unit): Boolean =
    commit(PrefsEdit().apply(build))

/**
 * An in-memory [ComposerPrefs] for previews and tests; [refuse] makes the next commits fail without
 * changes.
 */
public class MemoryComposerPrefs(initial: Map<String, Any> = emptyMap()) : ComposerPrefs {
    private val values = LinkedHashMap<String, Any>(initial)

    /** While true every commit is refused (a full disk, a failed platform write). */
    public var refuse: Boolean = false

    /** Number of commits that landed (tests prove "no late write"). */
    public var commits: Int = 0
        private set

    override fun string(key: String): String? = values[key] as? String

    override fun bool(key: String): Boolean? = values[key] as? Boolean

    override fun contains(key: String): Boolean = key in values

    override fun keys(): Set<String> = values.keys.toSet()

    override suspend fun commit(edit: PrefsEdit): Boolean {
        if (refuse) return false
        for ((key, write) in edit.writes) {
            when (write) {
                is PrefWrite.Str -> values[key] = write.value
                is PrefWrite.Bool -> values[key] = write.value
                PrefWrite.Remove -> values.remove(key)
            }
        }
        commits++
        return true
    }

    override suspend fun compareAndCommit(
        expected: Map<String, String?>,
        edit: PrefsEdit,
    ): Boolean {
        if (expected.any { (key, value) -> values[key] != value }) return false
        return commit(edit)
    }

    /** Test seam: write as another process or an older build would, outside any engine. */
    public fun rawPut(key: String, value: Any?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
