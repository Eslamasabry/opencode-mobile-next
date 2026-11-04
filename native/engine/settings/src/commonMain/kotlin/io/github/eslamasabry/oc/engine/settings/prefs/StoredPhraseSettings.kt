package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Created only after PreviewStores' migration gate has opened. */
public class StoredPhraseSettings(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    scope: CoroutineScope,
) {
    public val enabled: StateFlow<Boolean> =
        store.changes
            .map { store.bool(KEY) ?: true }
            .stateIn(scope, SharingStarted.Eagerly, store.bool(KEY) ?: true)

    public suspend fun setEnabled(on: Boolean): Outcome<Unit> =
        store.commit(lane) { putBool(KEY, on) }

    public companion object {
        public const val KEY: String = "oc.phraseChips.enabled"
    }
}
