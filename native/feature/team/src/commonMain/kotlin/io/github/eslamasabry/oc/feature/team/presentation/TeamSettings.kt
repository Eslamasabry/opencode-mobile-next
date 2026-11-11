package io.github.eslamasabry.oc.feature.team.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import kotlinx.coroutines.flow.flowOf
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The host's facts behind Technical details (Dart `TeamHostDetailsSheet`): raw values, redacted before they are shown. */
public data class TeamHostFacts(
    val provider: Redacted,
    /** Null until the engine has answered once. */
    val version: Redacted?,
    val address: Redacted,
    /** `proot` or `landlock` when the engine named its confinement. */
    val protection: String?,
)

/** Team settings (Dart `TeamSettingsScreen`) for the phone's team. */
public data class TeamSettingsUi(
    val phase: TeamPhase = TeamPhase.Loading,
    /** Dart `teamSettingsAgentsRow`: how many roles the team has. */
    val roles: Int = 0,
    /** The check that proves this phone safe can run again from here (Dart `settings-turn-on-phone`). */
    val canCheck: Boolean = false,
    val host: TeamHostFacts? = null,
    val detailsOpen: Boolean = false,
    /** Turn off is asking first. */
    val confirmingOff: Boolean = false,
    /** Turn off is under way. */
    val working: Boolean = false,
    /** The last Turn off could not stop the team on this phone, so it is still on. */
    val offFailed: Boolean = false,
    /**
     * The team runs on a computer (Gas City): no roles editor or phone check here, the computer's words in Technical
     * details, and Turn off only forgets it on this phone.
     */
    val computer: Boolean = false,
)

public interface TeamSettingsActions {
    public fun back() {}

    /** The Agents row: Roles. */
    public fun openAgents() {}

    /** The check again (Dart `openPhoneTeamSetup`). */
    public fun openSetup() {}

    public fun openDetails() {}

    public fun closeDetails() {}

    public fun askTurnOff() {}

    public fun cancelTurnOff() {}

    public fun confirmTurnOff() {}

    public fun dismissOffFailed() {}
}

/** Pure mapping from the engine's state to the settings page (tested without Compose). */
public object TeamSettingsPresent {
    public fun present(state: TeamState, redact: (String) -> Redacted, fleetVersion: String? = null): TeamSettingsUi {
        val config = state.config
        val phone = config?.provider == TeamProvider.PhoneEngine
        return TeamSettingsUi(
            phase = state.phase,
            roles = state.workspace?.roles?.size ?: 0,
            canCheck = phone,
            computer = config != null && !phone,
            host =
                config?.let {
                    TeamHostFacts(
                        provider =
                            redact(
                                when (it.provider) {
                                    TeamProvider.PhoneEngine -> "phoneEngine"
                                    TeamProvider.GasCity -> "gascity"
                                    TeamProvider.Fixture -> "fixture"
                                },
                            ),
                        version = (state.health?.engineVersion ?: fleetVersion?.takeIf { _ -> !phone })?.let(redact),
                        address = redact(it.url),
                        protection = state.health?.boundaryTier?.takeIf { tier -> tier == "proot" || tier == "landlock" },
                    )
                },
        )
    }
}

/**
 * Team settings (Dart `TeamSettingsScreen`, the part that applies to the phone's own team): the agents, the check that
 * proves this phone safe, how it runs here, and Turn off last, which asks first. The Gas City page's address, model and
 * heat rows belong to a computer's team.
 */
@AssistedInject
public class TeamSettingsViewModel(
    @Assisted private val route: TeamRoute.Settings,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val redactor: Redactor,
) : ViewModel(), TeamSettingsActions {
    private val profile = route.profileId.raw
    private val local = MutableStateFlow(TeamSettingsUi())

    public val state: StateFlow<TeamSettingsUi> =
        combine(engine.state(profile), local, engine.fleet?.state(profile) ?: flowOf(FleetState())) { team, mine, fleet ->
                TeamSettingsPresent.present(team, redactor::details, fleet.host?.version)
                    .copy(
                        detailsOpen = mine.detailsOpen,
                        confirmingOff = mine.confirmingOff,
                        working = mine.working,
                        offFailed = mine.offFailed,
                    )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamSettingsUi())

    init {
        viewModelScope.launch { engine.refresh(profile) }
    }

    override fun back(): Unit = nav.back()

    override fun openAgents() {
        nav.open(TeamRoute.Editor(route.profileId, EditorKind.Roles.wire))
    }

    override fun openSetup() {
        nav.open(TeamRoute.TurnOn(route.profileId))
    }

    override fun openDetails() = local.update { it.copy(detailsOpen = true) }

    override fun closeDetails() = local.update { it.copy(detailsOpen = false) }

    override fun askTurnOff() = local.update { if (it.working) it else it.copy(confirmingOff = true, offFailed = false) }

    override fun cancelTurnOff() = local.update { it.copy(confirmingOff = false) }

    override fun dismissOffFailed() = local.update { it.copy(offFailed = false) }

    /** Dart `turnOffTeam`: the team stops, its config and cached data go; on failure the team stays as it was. */
    override fun confirmTurnOff() {
        var admitted = false
        local.update {
            admitted = it.confirmingOff && !it.working
            if (admitted) it.copy(confirmingOff = false, working = true, offFailed = false) else it
        }
        if (!admitted) return
        viewModelScope.launch {
            val done = engine.turnOff(profile)
            local.update { it.copy(working = false, offFailed = done is Outcome.Failed) }
            // Off: there is nothing left to set up here, so the page leaves for the team page, which now says it is off.
            if (done is Outcome.Ok) nav.back()
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Settings): TeamSettingsViewModel
    }
}
