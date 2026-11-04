package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** An in-memory store whose platform commit can be refused or held, like the Dart tests' InMemorySharedPreferencesStore. */
internal class FakeKv(initial: Map<String, PrefValue> = emptyMap()) : KeyValueStore {
    val values = LinkedHashMap(initial)
    var refuse = false
    var gate: CompletableDeferred<Unit>? = null
    var started: CompletableDeferred<Unit>? = null
    var commits = 0

    override fun get(key: String): PrefValue? = values[key]

    override fun keys(prefix: String): Set<String> = values.keys.filter { it.startsWith(prefix) }.toSet()

    override val changes: SharedFlow<Set<String>> = MutableSharedFlow()

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val pending = LinkedHashMap<String, PrefValue?>()
        object : PrefEdit {
            override fun putString(key: String, value: String) { pending[key] = PrefValue.Str(value) }
            override fun putBool(key: String, value: Boolean) { pending[key] = PrefValue.Bool(value) }
            override fun putLong(key: String, value: Long) { pending[key] = PrefValue.Int64(value) }
            override fun putStringList(key: String, value: List<String>) { pending[key] = PrefValue.StrList(value) }
            override fun putDouble(key: String, value: Double) { pending[key] = PrefValue.Dbl(value) }
            override fun remove(key: String) { pending[key] = null }
        }.edit()
        if (pending.values.any { it != null }) {
            started?.complete(Unit)
            gate?.await()
        }
        if (refuse && pending.values.any { it != null }) return Outcome.Failed(Failure.StorageRefused())
        commits++
        for ((k, v) in pending) if (v == null) values.remove(k) else values[k] = v
        return Outcome.Ok(Unit)
    }

    override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> =
        Outcome.Failed(Failure.StorageRefused())

    fun str(key: String): String? = (values[key] as? PrefValue.Str)?.value

    /** The Flutter profile sweep over this store's keys. */
    fun scopedKeys(profileId: String, knownIds: Set<String> = emptySet()): Set<String> =
        ProfileKeyRule.scopedKeys(profileId, values.keys, knownIds)

    fun sweep(profileId: String) {
        scopedKeys(profileId).forEach { values.remove(it) }
    }
}

internal val LANE = WriterLane("test")
