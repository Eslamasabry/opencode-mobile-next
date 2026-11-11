package io.github.eslamasabry.oc.feature.team.fleet

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
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.ComputerTeamTest
import io.github.eslamasabry.oc.engine.team.fleet.FleetAddress
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * Connect a team on a computer / Change address (Flutter lib/ui/widgets/team_host_form.dart `TeamHostForm`, the intro's
 * "Enter the address" and Team settings' Change address). One address and an optional team name; one submit tests the
 * address and, when a team answers, turns it on there. An address that did not answer can be saved anyway. No
 * credential is asked for, stored or sent: the address rule is the whole boundary.
 */

/** Why the form did not save (Dart `teamVerdictCopy` and the form's own checks); each has plain words and a way on. */
public enum class FleetConnectProblem {
    /** Nothing typed. */
    AddressRequired,

    /** `http://` to an address that is neither this device nor a Tailscale address; no request was made. */
    TailnetRequired,

    /** Not a web address, so no request was made (said as "no answer", as in Flutter). */
    BadAddress,

    /** Something answered, but it is not an AI team host: the way forward is the set-up guide. */
    NotGasCity,

    /** The host answers but the team is not running yet. */
    CityNotRunning,

    /** The address tested fine (or was kept anyway) but this phone could not save it. */
    SaveFailed,
}

public data class FleetConnectUi(
    /** The saved address has been read: the form can be drawn with it. */
    val ready: Boolean = false,
    val initialUrl: String = "",
    val initialCity: String = "",
    /** The test is running. */
    val testing: Boolean = false,
    /** The address is being saved. */
    val saving: Boolean = false,
    val problem: FleetConnectProblem? = null,
    /** The address did not answer, and may be saved anyway (the computer may be asleep). */
    val canSaveAnyway: Boolean = false,
    /** The address that did not answer, for Connection details (no user info can be in it). */
    val asked: Redacted? = null,
    /** The computer's set-up guide is open. */
    val guide: Boolean = false,
)

public interface FleetConnectActions {
    public fun back() {}

    /** Test and turn on: [url] and [city] are the fields' text. */
    public fun test(url: String, city: String) {}

    public fun cancelTest() {}

    /** A field changed: a test that is running is dropped and the last verdict no longer applies. */
    public fun edited() {}

    public fun saveAnyway() {}

    public fun openGuide() {}

    public fun closeGuide() {}
}

/** Pure rules of the form (tested without Compose). */
public object FleetConnectRules {
    /**
     * Flutter `_submit`'s checks before any request: something typed, a web address with a host, and `http://` only to
     * this device or a Tailscale address. Null when the address may be tested.
     */
    public fun check(url: String): FleetConnectProblem? {
        val address = url.trim()
        if (address.isEmpty()) return FleetConnectProblem.AddressRequired
        val scheme = address.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return FleetConnectProblem.BadAddress
        if (FleetAddress.host(address) == null) return FleetConnectProblem.BadAddress
        if (!FleetAddress.allowed(address)) return FleetConnectProblem.TailnetRequired
        return null
    }

    /** Dart `teamVerdictCopy`. */
    public fun problem(failure: FleetFailure): FleetConnectProblem =
        when (failure) {
            FleetFailure.NotGasCity -> FleetConnectProblem.NotGasCity
            FleetFailure.CityNotRunning -> FleetConnectProblem.CityNotRunning
            FleetFailure.PlainHttpRefused -> FleetConnectProblem.TailnetRequired
            FleetFailure.Unreachable -> FleetConnectProblem.BadAddress
        }

    /** Only a host that did not answer at all may be kept anyway; a host that answered wrongly may not. */
    public fun mayKeepAnyway(failure: FleetFailure): Boolean = failure == FleetFailure.Unreachable

    /** "How to set up the computer" goes with the one verdict the guide answers. */
    public fun showsGuide(problem: FleetConnectProblem?): Boolean = problem == FleetConnectProblem.NotGasCity
}

@AssistedInject
public class FleetConnectViewModel(
    @Assisted private val route: TeamRoute.Address,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val redactor: Redactor,
) : ViewModel(), FleetConnectActions {
    private val profile = route.profileId.raw
    private val local = MutableStateFlow(FleetConnectUi())
    private var running: Job? = null

    /** The address and team name that did not answer, kept for "Save the address anyway". */
    private var unanswered: Pair<String, String>? = null

    public val state: StateFlow<FleetConnectUi> = local.asStateFlow()

    init {
        // Change address starts from the saved address; turning a team on starts empty.
        viewModelScope.launch {
            engine.refresh(profile)
            val saved = engine.state(profile).value.config?.takeIf { it.provider == TeamProvider.GasCity }
            local.update { it.copy(ready = true, initialUrl = saved?.url.orEmpty(), initialCity = saved?.city.orEmpty()) }
        }
    }

    override fun back() {
        running?.cancel()
        nav.back()
    }

    override fun test(url: String, city: String) {
        if (local.value.testing || local.value.saving) return
        FleetConnectRules.check(url)?.let { problem ->
            local.update { it.copy(problem = problem, canSaveAnyway = false, asked = null) }
            return
        }
        unanswered = null
        local.update { it.copy(testing = true, problem = null, canSaveAnyway = false, asked = null) }
        running =
            viewModelScope.launch {
                when (val found = engine.testComputerTeam(url, city.trim().ifEmpty { null })) {
                    is ComputerTeamTest.Found -> save(url, found.city, found.front)
                    is ComputerTeamTest.NotFound -> {
                        val keep = FleetConnectRules.mayKeepAnyway(found.failure)
                        if (keep) unanswered = url.trim() to city.trim()
                        local.update {
                            it.copy(
                                testing = false,
                                problem = FleetConnectRules.problem(found.failure),
                                canSaveAnyway = keep,
                                asked = if (keep) redactor.details(url.trim()) else null,
                            )
                        }
                    }
                }
            }
    }

    private suspend fun save(url: String, city: String, front: Boolean) {
        local.update { it.copy(testing = false, saving = true) }
        when (engine.saveComputerTeam(profile, url, city, front)) {
            // The page behind this one now shows the team at its new address.
            is Outcome.Ok -> nav.back()
            is Outcome.Failed ->
                local.update { it.copy(saving = false, problem = FleetConnectProblem.SaveFailed, canSaveAnyway = false) }
        }
    }

    override fun cancelTest() {
        if (!local.value.testing) return
        running?.cancel()
        local.update { it.copy(testing = false) }
    }

    override fun edited() {
        if (local.value.saving) return
        running?.cancel()
        unanswered = null
        local.update { it.copy(testing = false, problem = null, canSaveAnyway = false, asked = null) }
    }

    /** Saves an address that did not answer (the computer may be asleep): read-only until it answers. */
    override fun saveAnyway() {
        val (url, city) = unanswered ?: return
        if (local.value.saving) return
        running = viewModelScope.launch { save(url, city, front = false) }
    }

    override fun openGuide() = local.update { it.copy(guide = true) }

    override fun closeGuide() = local.update { it.copy(guide = false) }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Address): FleetConnectViewModel
    }
}
