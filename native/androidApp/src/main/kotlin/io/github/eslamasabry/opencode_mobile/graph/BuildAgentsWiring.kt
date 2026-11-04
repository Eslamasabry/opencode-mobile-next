package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.GateNotOpen
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.agents.models.ModelKeys
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.engine.agents.models.ModelStore
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.feature.chat.world.RecentModelsBacking
import io.github.eslamasabry.oc.feature.chat.world.SavedSessionModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * P12-BUILD-AGENTS: Android bindings for the agents-port rows that reach a screen.
 */

/**
 * B-STATE-032: the composer picker's Recent group, saved per server in the Flutter
 * `oc.modelLibrary.<profileId>` format through `:engine:agents`' [ModelStore] (favorites in the
 * same record are kept as they are). Saves run on the app scope in pick order and never block the
 * pick; a refused save keeps the in-memory list for this run.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<RecentModelsBacking>())
class SavedRecentModels(
    private val stores: PreviewStores,
    private val appScope: AppCoroutineScope,
    private val profiles: ProfileStore,
) : RecentModelsBacking {
    private fun modelStore(profile: String): ModelStore =
        ModelStore(KvAgentPrefs(stores.prefs, stores.lanes.forProfile(ProfileId(profile))))

    private val writes = Mutex()

    private fun admits(profile: String): Boolean =
        profiles.profile(ProfileId(profile)) != null &&
            stores.lanes.forProfile(ProfileId(profile)).isAdmitting

    override fun load(profile: String): List<Pair<String, String>>? =
        try {
            if (!admits(profile)) null
            else
                modelStore(profile).modelLibrary(ProfileId(profile)).recent.map {
                    it.providerId to it.modelId
                }
        } catch (e: GateNotOpen) {
            null // before the migration gate opens: asked again on the next read
        }
    }

    override fun favorites(profile: String): List<Pair<String, String>>? {
        return try {
            val id = ProfileId(profile)
            if (stores.profiles.profile(id) == null) return emptyList()
            store(id).modelLibrary(id).favorites.map { it.providerId to it.modelId }
        } catch (_: GateNotOpen) {
            null
        }
    }

    override suspend fun setFavorites(
        profile: String,
        favorites: List<Pair<String, String>>,
    ): Outcome<Unit> = writes.withLock {
        val id = ProfileId(profile)
        if (stores.profiles.profile(id) == null)
            return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        val store = store(id)
        val saved =
            try {
                store.modelLibrary(id)
            } catch (_: GateNotOpen) {
                return@withLock Outcome.Failed(Failure.StorageRefused())
            }
        store.setModelLibrary(
            id,
            ModelLibrary(
                favorites =
                    favorites.distinct().map { (provider, model) -> ModelRef(provider, model) },
                recent = saved.recent,
            ),
        )
    }

    override fun save(profile: String, recent: List<Pair<String, String>>) {
        val refs = recent.map { (provider, model) -> ModelRef(provider, model) }
        appScope.launch {
            writes.withLock {
                if (!admits(profile)) return@launch
                val id = ProfileId(profile)
                if (stores.profiles.profile(id) == null) return@launch
                val store = store(id)
                val saved =
                    try {
                        modelStore(profile).modelLibrary(id)
                    } catch (e: GateNotOpen) {
                        return@launch
                    }
                modelStore(profile)
                    .setModelLibrary(id, ModelLibrary(favorites = saved.favorites, recent = refs))
            }
        }
    }

    override fun session(profile: String, session: String): SavedSessionModel? =
        try {
            if (!admits(profile)) null
            else {
                val raw =
                    (stores.prefs.get("oc.sessionDisplayModels.$profile") as? PrefValue.Str)?.value
                val rows = raw?.let(DartJson::decodeOrNull) as? Map<*, *>
                val value = rows?.get(session) as? Map<*, *>
                val provider = value?.get("providerID") as? String
                val model = value?.get("modelID") as? String
                if (provider.isNullOrBlank() || model.isNullOrBlank()) null
                else
                    SavedSessionModel(
                        provider,
                        model,
                        modelStore(profile)
                            .sessionModels(ProfileId(profile))[session]
                            ?.variant
                            .orEmpty(),
                    )
            }
        } catch (e: GateNotOpen) {
            null
        }

    override suspend fun rememberSession(
        profile: String,
        session: String,
        choice: SavedSessionModel?,
    ): Outcome<Unit> = writes.withLock {
        if (!admits(profile)) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
        val id = ProfileId(profile)
        val displayKey = "oc.sessionDisplayModels.$profile"
        val raw = (stores.prefs.get(displayKey) as? PrefValue.Str)?.value
        val old = raw?.let(DartJson::decodeOrNull) as? Map<*, *>
        val rows = LinkedHashMap<String, Any?>()
        old?.forEach { (key, value) ->
            if (
                key is String &&
                    value is Map<*, *> &&
                    value["providerID"] is String &&
                    value["modelID"] is String
            )
                rows[key] = value
        }
        rows.remove(session)
        if (choice != null)
            rows[session] =
                linkedMapOf("providerID" to choice.providerId, "modelID" to choice.modelId)
        while (rows.size > ModelStore.SESSION_MODELS_CAP) rows.remove(rows.keys.first())
        val choices = LinkedHashMap(modelStore(profile).sessionModels(id))
        choices.remove(session)
        if (choice != null)
            choices[session] =
                io.github.eslamasabry.oc.engine.agents.models.SessionModelChoice(
                    ModelRef(choice.providerId, choice.modelId),
                    choice.variant,
                )
        while (choices.size > ModelStore.SESSION_MODELS_CAP) choices.remove(choices.keys.first())
        stores.prefs.commit(stores.lanes.forProfile(id)) {
            if (rows.isEmpty()) remove(displayKey) else putString(displayKey, DartJson.encode(rows))
            val key = ModelKeys.sessionModels(id)
            if (choices.isEmpty()) remove(key)
            else
                putString(
                    key,
                    DartJson.encode(
                        choices.mapValues { (_, value) ->
                            "${value.model.providerId}|${value.model.modelId}|${value.variant}"
                        }
                    ),
                )
        }
    }

    override fun favorites(profile: String): List<Pair<String, String>> =
        try {
            if (!admits(profile)) emptyList()
            else
                modelStore(profile).modelLibrary(ProfileId(profile)).favorites.map {
                    it.providerId to it.modelId
                }
        } catch (e: GateNotOpen) {
            emptyList()
        }

    override suspend fun toggleFavorite(
        profile: String,
        provider: String,
        model: String,
    ): Outcome<Unit> = writes.withLock {
        if (!admits(profile)) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
        val id = ProfileId(profile)
        modelStore(profile)
            .setModelLibrary(
                id,
                modelStore(profile).modelLibrary(id).toggleFavorite(ModelRef(provider, model)),
            )
    }

    override suspend fun prune(
        profile: String,
        available: Set<Pair<String, String>>,
    ): Outcome<Unit> = writes.withLock {
        if (!admits(profile)) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
        val id = ProfileId(profile)
        val before = modelStore(profile).modelLibrary(id)
        val next = before.retainWhere { it.providerId to it.modelId in available }
        if (before == next) Unit.ok() else modelStore(profile).setModelLibrary(id, next)
    }
}
