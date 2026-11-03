package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update

// Port of lib/demo/demo_store.dart (`DemoPreferences`, `DemoProfileStore`).

/**
 * A route-owned preference map for the offline demo. It never replaces the app's store, so a live connection keeps its
 * real preferences; two demo stores share nothing. Lists are copied in and out, so neither the writer's list nor a
 * reader's copy can change what is stored.
 */
public class DemoPreferences : KeyValueStore {
    private val values = MutableStateFlow<Map<String, PrefValue>>(emptyMap())
    private val mutableChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)
    override val changes: SharedFlow<Set<String>> = mutableChanges

    override fun get(key: String): PrefValue? = values.value[key]?.copied()

    override fun keys(prefix: String): Set<String> = values.value.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val ops = LinkedHashMap<String, PrefValue?>()
        object : PrefEdit {
            override fun putString(key: String, value: String) { ops[key] = PrefValue.Str(value) }
            override fun putBool(key: String, value: Boolean) { ops[key] = PrefValue.Bool(value) }
            override fun putLong(key: String, value: Long) { ops[key] = PrefValue.Int64(value) }
            override fun putStringList(key: String, value: List<String>) { ops[key] = PrefValue.StrList(ArrayList(value)) }
            override fun putDouble(key: String, value: Double) { ops[key] = PrefValue.Dbl(value) }
            override fun remove(key: String) { ops[key] = null }
        }.edit()
        values.update { now -> LinkedHashMap(now).apply { for ((k, v) in ops) if (v == null) remove(k) else put(k, v) } }
        mutableChanges.tryEmit(ops.keys.toSet())
        return Outcome.Ok(Unit)
    }

    override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> {
        while (true) {
            val now = values.value
            val current = now[key]
            val matches = if (expected == null) current == null else (current as? PrefValue.Str)?.value == expected
            if (!matches) return Outcome.Ok(false)
            val updated = LinkedHashMap(now).apply { if (next == null) remove(key) else put(key, PrefValue.Str(next)) }
            if (values.compareAndSet(now, updated)) {
                mutableChanges.tryEmit(setOf(key))
                return Outcome.Ok(true)
            }
        }
    }

    /** Empties this demo map only. */
    public fun clear() {
        values.value = emptyMap()
    }

    private fun PrefValue.copied(): PrefValue = if (this is PrefValue.StrList) PrefValue.StrList(ArrayList(value)) else this
}

/**
 * The synthetic demo identity. It exists only in the demo route's runtime: no profile JSON (`oc.profiles`,
 * `oc.activeProfile`) or credential is ever written into a store for it.
 */
public object DemoProfile {
    public val ID: ProfileId = ProfileId("offline-demo")

    public fun profiles(): List<ProfileId> = listOf(ID)
}
