package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.settings.presentation.PhraseSettings
import io.github.eslamasabry.oc.feature.settings.presentation.PhraseSettingsFacts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Settings observes and changes the same store the visible chat suggestions consume (the
 * acknowledged `oc.phraseChips.enabled` switch, after the migration fence).
 */
@SingleIn(AppScope::class)
@Inject
class AndroidPhraseSettings(
    private val source: NativePhraseSuggestions,
    private val stores: PreviewStores,
    scope: AppCoroutineScope,
) : PhraseSettings {
    private val mutable = MutableStateFlow<PhraseSettingsFacts?>(null)
    override val facts: StateFlow<PhraseSettingsFacts?> = mutable.asStateFlow()
    private val switch = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = switch.asStateFlow()

    init {
        scope.launch {
            if (stores.warm() is Outcome.Ok)
                combine(stores.profiles.activeId, source.changes, stores.profiles.profiles) {
                        id,
                        _,
                        _ ->
                        id
                    }
                    .collect { refresh(it) }
        }
    }

    private suspend fun refresh(id: ProfileId? = stores.profiles.activeId.value) {
        val snapshot = source.snapshot(id?.raw)
        if (snapshot.available) switch.value = snapshot.enabled
        if (stores.profiles.activeId.value == id)
            mutable.value =
                PhraseSettingsFacts(snapshot.enabled, snapshot.hiddenCount, id, snapshot.available)
    }

    override suspend fun setEnabled(on: Boolean): Outcome<Unit> {
        val saved = source.setEnabled(on)
        refresh()
        return if (saved) Unit.ok() else Failure.StorageRefused().failed()
    }

    override suspend fun showHidden(profile: ProfileId): Outcome<Unit> {
        val saved = source.showHidden(profile.raw)
        refresh()
        return if (saved) Unit.ok() else Failure.StorageRefused().failed()
    }
}
