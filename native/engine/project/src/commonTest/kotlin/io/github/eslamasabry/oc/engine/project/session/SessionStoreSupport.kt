package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineDispatcher

/** A disk that can refuse every commit; [persisted] is what a fresh process would load. */
internal class SessionTestDisk(initial: Map<String, RawPref> = emptyMap()) : InMemoryPrefsBackend(initial) {
    var refuse: Boolean = false

    override fun commit(changes: Map<String, RawPref?>): Boolean = if (refuse) false else super.commit(changes)
}

internal fun storeOver(disk: SessionTestDisk, io: CoroutineDispatcher): PrefsKeyValueStore =
    PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), io)

/** Runs [onCommit] inside the next commit, after the edit was built (a write already on its way). */
internal class HookedStore(private val inner: KeyValueStore) : KeyValueStore by inner {
    var onCommit: (suspend () -> Unit)? = null

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val hook = onCommit
        onCommit = null
        hook?.invoke()
        return inner.commit(lane, edit)
    }
}
