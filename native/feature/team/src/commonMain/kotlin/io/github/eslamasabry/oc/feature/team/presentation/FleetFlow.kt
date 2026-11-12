package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A computer's team for [profileId] while someone collects it: the team engine says the profile's team is on a
 * computer (Gas City), and the fleet engine keeps it connected (probe, reads, event stream) until the page stops
 * collecting or the team is turned off. The connection is the page's, not the app's: it ends a few seconds after the
 * page is left.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun TeamEngine.fleetState(profileId: String): Flow<FleetState> {
    val fleet = this.fleet ?: return flowOf(FleetState())
    return channelFlow {
        launch {
            state(profileId)
                .map { s -> s.config?.takeIf { s.phase == TeamPhase.Computer && it.provider == TeamProvider.GasCity } }
                .distinctUntilChanged()
                .collectLatest { config -> if (config != null) fleet.live(profileId, config) }
        }
        fleet.state(profileId).collect { send(it) }
    }
}
