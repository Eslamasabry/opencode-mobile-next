package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.GateNotOpen
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.engine.agents.models.ModelStore
import io.github.eslamasabry.oc.feature.chat.world.RecentModelsBacking
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * P12-BUILD-AGENTS: Android bindings for the agents-port rows that reach a screen.
 */

/**
 * B-STATE-032: the composer picker's Recent group, saved per server in the Flutter `oc.modelLibrary.<profileId>`
 * format through `:engine:agents`' [ModelStore] (favorites in the same record are kept as they are). Saves run on the
 * app scope in pick order and never block the pick; a refused save keeps the in-memory list for this run.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<RecentModelsBacking>())
class SavedRecentModels(stores: PreviewStores, private val appScope: AppCoroutineScope) : RecentModelsBacking {
    private val store = ModelStore(KvAgentPrefs(stores.prefs, stores.lanes.app))
    private val writes = Mutex()

    override fun load(profile: String): List<Pair<String, String>>? =
        try {
            store.modelLibrary(ProfileId(profile)).recent.map { it.providerId to it.modelId }
        } catch (e: GateNotOpen) {
            null // before the migration gate opens: asked again on the next read
        }

    override fun save(profile: String, recent: List<Pair<String, String>>) {
        val refs = recent.map { (provider, model) -> ModelRef(provider, model) }
        appScope.launch {
            writes.withLock {
                val id = ProfileId(profile)
                val saved =
                    try {
                        store.modelLibrary(id)
                    } catch (e: GateNotOpen) {
                        return@launch
                    }
                store.setModelLibrary(id, ModelLibrary(favorites = saved.favorites, recent = refs))
            }
        }
    }
}
