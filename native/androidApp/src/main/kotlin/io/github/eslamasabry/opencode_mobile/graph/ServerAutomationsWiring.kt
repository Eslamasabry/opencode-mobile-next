package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.settings.presentation.ServerAutomationsSource
import kotlinx.collections.immutable.ImmutableList

/** Live server automation controls wired for a saved server profile (B-605). */
@ContributesBinding(AppScope::class, binding = binding<ServerAutomationsSource>())
@SingleIn(AppScope::class)
@Inject
class NativeServerAutomations(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
) : ServerAutomationsSource {
    override suspend fun automations(
        profile: ProfileId
    ): Outcome<ImmutableList<ServerAutomation>> =
        withAutomations(profile) { it.listAutomations() }

    override suspend fun pauseAutomation(
        profile: ProfileId,
        id: String,
    ): Outcome<ServerAutomation> = withAutomations(profile) { it.pauseAutomation(id) }

    override suspend fun resumeAutomation(
        profile: ProfileId,
        id: String,
    ): Outcome<ServerAutomation> = withAutomations(profile) { it.resumeAutomation(id) }

    override suspend fun deleteAutomation(profile: ProfileId, id: String): Outcome<Unit> =
        withAutomations(profile) { it.deleteAutomation(id) }

    private fun liveGraph(profile: ProfileId): Outcome<ConnectionGraph> {
        val loaded = stores.profiles.profile(profile)
        val graph = registry.live.value[profile]
        return when {
            loaded == null -> Failure.Refused(RefusalReason.ProfileDeleted).failed()
            graph == null -> Failure.Network().failed()
            loaded.toServerProfile() != graph.profile ->
                Failure.Refused(RefusalReason.StaleScope).failed()
            else -> graph.ok()
        }
    }

    private fun current(profile: ProfileId, graph: ConnectionGraph): Boolean =
        registry.live.value[profile] === graph &&
            stores.profiles.profile(profile)?.toServerProfile() == graph.profile

    private suspend fun <T> withAutomations(
        profile: ProfileId,
        block: suspend (ServerOperationsGateway) -> Outcome<T>,
    ): Outcome<T> {
        val warm = stores.warm()
        val ready = if (warm is Outcome.Failed) warm else liveGraph(profile)
        return when (ready) {
            is Outcome.Failed -> ready
            is Outcome.Ok -> {
                val graph = ready.value
                if (!graph.session.capabilities.value.serverAutomations) {
                    Failure.Unavailable(FeatureId("serverAutomations")).failed()
                } else {
                    val result = block((graph as ServerSettingsConnection).serverSettingsOperations)
                    if (current(profile, graph)) result
                    else Failure.Refused(RefusalReason.StaleScope).failed()
                }
            }
        }
    }
}
