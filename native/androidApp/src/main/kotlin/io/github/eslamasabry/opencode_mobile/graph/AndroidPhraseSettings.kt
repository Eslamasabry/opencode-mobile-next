package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.prefs.StoredPhraseSettings
import io.github.eslamasabry.oc.feature.settings.presentation.PhraseSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings and the composer use the same acknowledged switch, after the migration fence. */
internal class AndroidPhraseSettings(stores: PreviewStores, scope: CoroutineScope) :
    PhraseSettings {
    private val ready = CompletableDeferred<StoredPhraseSettings?>()
    private val current = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = current.asStateFlow()

    init {
        scope.launch {
            if (stores.warm() is Outcome.Failed) {
                ready.complete(null)
                return@launch
            }
            val settings = StoredPhraseSettings(stores.prefs, stores.lanes.app, scope)
            current.value = settings.enabled.value
            ready.complete(settings)
            settings.enabled.collect { current.value = it }
        }
    }

    override suspend fun setEnabled(on: Boolean): Outcome<Unit> =
        ready.await()?.setEnabled(on) ?: Outcome.Failed(Failure.StorageRefused())
}
