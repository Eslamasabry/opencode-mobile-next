package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.engine.agents.models.CatalogModel
import io.github.eslamasabry.oc.engine.agents.models.ModelCost
import io.github.eslamasabry.oc.engine.agents.models.ModelKeys
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.engine.agents.models.ModelStore
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsData
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsHost
import io.github.eslamasabry.oc.feature.settings.presentation.NoModelDefaultsHost

/** The picker stores only this app's default; selecting it never changes an existing chat. */
@ContributesBinding(AppScope::class, replaces = [NoModelDefaultsHost::class])
@Inject
class LiveModelDefaultsHost(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
) : ModelDefaultsHost {
    private var captured: Pair<ProfileId, ConnectionGraph>? = null
    private var available = emptyList<ModelRef>()
    private var capturedLocation: io.github.eslamasabry.oc.core.model.ServerLocation? = null
    private var capturedRevision = 0L

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    override suspend fun read(profile: ProfileId?): Outcome<ModelDefaultsData> {
        val profile = profile ?: stores.profiles.activeId.value ?: return stale()
        val graph = registry.live.value[profile] ?: return stale()
        val gateway = graph.session.server
        val location = gateway.location.value
        val revision = gateway.scopeRevision
        val operations = gateway.facet<ServerOperationsGateway>()
        val providers =
            when (val result = gateway.providers()) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
        val catalog =
            if (gateway.capabilities.value.serverCatalog)
                (operations?.catalog() as? Outcome.Ok)?.value
            else null
        val defaults =
            if (gateway.capabilities.value.serverCatalog)
                (operations?.chatDefaults() as? Outcome.Ok)?.value
            else null
        if (
            registry.live.value[profile] !== graph ||
                gateway.location.value != location ||
                gateway.scopeRevision != revision
        )
            return stale()
        val models =
            providers.providers.flatMap { provider ->
                provider.modelIds.map { id ->
                    val info =
                        catalog?.models?.firstOrNull { it.providerId == provider.id && it.id == id }
                    CatalogModel(
                        id,
                        provider.id,
                        info?.name ?: id,
                        info?.enabled ?: true,
                        cost =
                            info?.cost?.let { ModelCost(it.inputPerMillion, it.outputPerMillion) },
                    )
                }
            }
        captured = profile to graph
        capturedLocation = location
        capturedRevision = revision
        available = models.filter { it.enabled }.map { it.ref }
        val modelStore = ModelStore(KvAgentPrefs(stores.prefs, stores.lanes.forProfile(profile)))
        val stored =
            stores.prefs
                .string("oc.model.${profile.raw}")
                ?.split('|')
                ?.takeIf { it.size >= 2 }
                ?.let { ModelRef(it[0], it[1]) }
        val serverDefaults =
            listOfNotNull(
                defaults?.model?.let { ModelRef(it.providerId, it.modelId) },
                providers.defaultProviderId?.let { provider ->
                    providers.defaultModelId?.let { ModelRef(provider, it) }
                },
            )
        return Outcome.Ok(
            ModelDefaultsData(
                graph.profile.name,
                graph.profile.managedOnPhone,
                models,
                stored ?: serverDefaults.firstOrNull(),
                serverDefaults,
                modelStore.modelLibrary(profile),
            )
        )
    }

    override suspend fun choose(model: ModelRef?): Outcome<Unit> {
        val (profile, graph) = captured ?: return stale()
        if (
            registry.live.value[profile] !== graph ||
                graph.session.server.location.value != capturedLocation ||
                graph.session.server.scopeRevision != capturedRevision ||
                stores.profiles.profile(profile) == null ||
                model != null && available.none { it.sameModel(model) }
        )
            return stale()
        val store = ModelStore(KvAgentPrefs(stores.prefs, stores.lanes.forProfile(profile)))
        val library = model?.let { store.modelLibrary(profile).remember(it) }
        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
            if (model == null) remove("oc.model.${profile.raw}")
            else putString("oc.model.${profile.raw}", "${model.providerId}|${model.modelId}")
            if (library != null)
                putString(ModelKeys.modelLibrary(profile), library.toJson().toString())
        }
    }
}
