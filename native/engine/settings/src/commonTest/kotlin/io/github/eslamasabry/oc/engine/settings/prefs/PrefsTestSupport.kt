package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertTrue
import kotlin.test.fail

/** A disk that can refuse (return false) or throw, keyed on the LOGICAL key, and records every commit it saw. */
internal class TestDisk(initial: Map<String, RawPref> = emptyMap()) : InMemoryPrefsBackend(initial) {
    var refuse: ((key: String, value: RawPref?) -> Boolean)? = null
    var throwing: Boolean = false
    val commits: MutableList<Map<String, RawPref?>> = mutableListOf()

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        commits += LinkedHashMap(changes)
        if (throwing) throw IllegalStateException("disk full")
        val refused = refuse
        if (refused != null && changes.any { (k, v) -> refused(k.removePrefix(FlutterPrefsCodec.KEY_PREFIX), v) }) return false
        return super.commit(changes)
    }
}

internal fun storeOver(disk: TestDisk, io: CoroutineDispatcher): PrefsKeyValueStore =
    PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), io)

/** Seeds values as an earlier Flutter install would have left them (one commit on its own lane). */
internal suspend fun KeyValueStore.seed(vararg pairs: Pair<String, Any>) {
    val result = commit(WriterLane("seed")) {
        for ((key, value) in pairs) {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBool(key, value)
                is Long -> putLong(key, value)
                is Int -> putLong(key, value.toLong())
                is List<*> -> putStringList(key, value.map { it as String })
                else -> fail("unsupported seed type")
            }
        }
    }
    assertTrue(result is Outcome.Ok, "seed refused")
}

/**
 * Records what each commit would write, then holds it on [gate] before the real store sees it. With [inLane] the
 * hold happens after the write was admitted to its lane (a write already on its way to the disk).
 */
internal class GatedStore(private val inner: KeyValueStore, private val inLane: Boolean = false) : KeyValueStore by inner {
    var gate: CompletableDeferred<Unit>? = null
    val entered: CompletableDeferred<Unit> = CompletableDeferred()
    val calls: MutableList<Map<String, Any?>> = mutableListOf()

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val recorded = LinkedHashMap<String, Any?>()
        val recorder = object : PrefEdit {
            override fun putString(key: String, value: String) { recorded[key] = value }
            override fun putBool(key: String, value: Boolean) { recorded[key] = value }
            override fun putLong(key: String, value: Long) { recorded[key] = value }
            override fun putStringList(key: String, value: List<String>) { recorded[key] = value }
            override fun putDouble(key: String, value: Double) { recorded[key] = value }
            override fun remove(key: String) { recorded[key] = null }
        }
        recorder.edit()
        calls += recorded
        if (inLane) {
            return lane.write {
                entered.complete(Unit)
                gate?.await()
                inner.commit(WriterLane("inner"), edit)
            }
        }
        entered.complete(Unit)
        gate?.await()
        return inner.commit(lane, edit)
    }
}

/** A consent authority whose epoch the test moves by hand (the SETTINGS-B store does it on every consent change). */
internal class FakeConsentAuthority : ConsentAuthority {
    val mutableEpoch = MutableStateFlow(0L)
    override val epoch: StateFlow<Long> get() = mutableEpoch
}
