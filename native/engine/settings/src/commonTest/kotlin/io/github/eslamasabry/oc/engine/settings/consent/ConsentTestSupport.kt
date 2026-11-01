package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharedFlow

internal val codec = FlutterPrefsCodec(JavaListDecoder.Unsupported)

internal fun pid(raw: String) = ProfileId(raw)

/** `oc.profiles` holding the given ids, as the Flutter app stores it. */
internal fun profilesJson(vararg ids: String): String = DartJson.encode(ids.map { linkedMapOf("id" to it) })

/** Physical Flutter entries from logical (key → String | Boolean | Long | Int | List<String>). */
internal fun seed(values: Map<String, Any>): Map<String, RawPref> = values.entries.associate { (key, value) ->
    FlutterPrefsCodec.physicalKey(key) to when (value) {
        is String -> codec.encodeString(value)
        is Boolean -> codec.encodeBool(value)
        is Long -> codec.encodeLong(value)
        is Int -> codec.encodeLong(value.toLong())
        is List<*> -> codec.encodeStringList(value.map { it as String })
        else -> error("unsupported seed")
    }
}

/** A backend whose disk can refuse chosen keys. Refused commits leave the disk unchanged. */
internal class Disk(initial: Map<String, RawPref>) : InMemoryPrefsBackend(initial) {
    var refuse: ((logicalKey: String, value: RawPref?) -> Boolean)? = null

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        val rule = refuse
        if (rule != null && changes.any { (k, v) -> rule(FlutterPrefsCodec.logicalKey(k) ?: k, v) }) return false
        return super.commit(changes)
    }

    /** Logical view of what a restarted process would load. */
    fun saved(): Map<String, RawPref> = persisted()
}

/**
 * The real [PrefsKeyValueStore] over a [Disk], with a seam that holds the next commit touching one key (the Dart
 * tests' `_Disk.hold`): the write has been asked for, nothing is acknowledged yet.
 */
internal class TestKv(initial: Map<String, Any> = emptyMap(), io: CoroutineDispatcher, raw: Map<String, RawPref>? = null) :
    KeyValueStore {
    val disk = Disk(raw ?: seed(initial))
    private val real = PrefsKeyValueStore(disk, codec, StateMigrationGate.alwaysOpen(), io)
    private var held: Pair<String, Hold>? = null
    val lanes = DefaultWriterLanes()

    class Hold {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    fun hold(key: String): Hold = Hold().also { held = key to it }

    override fun get(key: String): PrefValue? = real.get(key)

    override fun keys(prefix: String): Set<String> = real.keys(prefix)

    override val changes: SharedFlow<Set<String>> get() = real.changes

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val touched = KeyRecorder().apply(edit).keys
        val h = held
        if (h != null && h.first in touched) {
            held = null
            h.second.entered.complete(Unit)
            h.second.release.await()
        }
        return real.commit(lane, edit)
    }

    override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> =
        real.compareAndSet(lane, key, expected, next)

    fun string(key: String): String? = (get(key) as? PrefValue.Str)?.value

    fun bool(key: String): Boolean? = (get(key) as? PrefValue.Bool)?.value

    /** A fresh process over what the disk holds now. */
    fun restart(io: CoroutineDispatcher): TestKv = TestKv(io = io, raw = disk.saved())

    private class KeyRecorder : PrefEdit {
        val keys = LinkedHashSet<String>()
        override fun putString(key: String, value: String) { keys += key }
        override fun putBool(key: String, value: Boolean) { keys += key }
        override fun putLong(key: String, value: Long) { keys += key }
        override fun putStringList(key: String, value: List<String>) { keys += key }
        override fun putDouble(key: String, value: Double) { keys += key }
        override fun remove(key: String) { keys += key }
    }
}

internal fun jsonOf(text: String?): Map<*, *>? = text?.let { DartJson.decode(it) as? Map<*, *> }
