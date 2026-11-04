package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.pairing.NoPairingProbe
import io.github.eslamasabry.oc.feature.servers.pairing.PairingChoice
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingInbox
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProbe
import io.github.eslamasabry.oc.feature.servers.pairing.PairingRead
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.PairingRoute
import io.github.eslamasabry.oc.ui.routes.PlansRoute
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One explicit tap admits one operation. A failed save/connect/create never navigates or retries.
 */
@AssistedInject
public class ServersViewModel(
    @Assisted private val route: ChatsRoute.New?,
    private val connections: ServerConnections,
    private val nav: AppNavigator,
    private val pairing: PairingInbox = PairingInbox(),
    /**
     * Picks among a code's addresses (B-PROTO-068); without one, the best-ordered address is used.
     */
    private val probe: PairingProbe = NoPairingProbe,
    private val monitoring: Set<ServerMonitoring> = emptySet(),
) : ViewModel(), ServersActions {
    private val local = MutableStateFlow(ServersUi(newConversation = route != null))
    public val state: StateFlow<ServersUi> =
        combine(local, connections.servers, connections.busy) { own, servers, busy ->
                own.copy(servers = servers, busy = own.busy || busy)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                local.value.copy(
                    servers = connections.servers.value,
                    busy = connections.busy.value,
                ),
            )

    /** The fill the form takes next: the inbox's code, once its address is chosen. Memory only. */
    private var ready: PairingFill? = null
    private var choosing = 0

    init {
        monitoring.singleOrNull()?.let { source ->
            viewModelScope.launch {
                source.rows.collect { rows -> local.update { it.copy(monitoring = rows) } }
            }
        }
        // A code from the scanner or a paste lands in the inbox; its address is picked here before
        // the form fills.
        viewModelScope.launch { pairing.arrivals.collect { pairing.take()?.let(::choose) } }
    }

    init {
        // QA wave 4 N7: "New chat" opens a new chat. When the server is known (named by the route,
        // or the only saved
        // one, or the only connected one), the conversation is created at once; this page shows
        // only to choose.
        if (route != null) {
            val saved = connections.servers.value
            val target =
                route.profile ?: saved.singleOrNull()?.id ?: saved.singleOrNull { it.connected }?.id
            if (target != null) create(target)
        }
    }

    private fun choose(fill: PairingFill) {
        val generation = ++choosing
        if (probe === NoPairingProbe || fill.fromLink) return publish(fill)
        viewModelScope.launch {
            val checks = PairingChoice.inspect(fill.candidates, probe, fill.credentials())
            if (generation == choosing) {
                local.update { it.copy(pairingChecks = checks.outcomes) }
                val chosen = checks.chosen
                publish(if (chosen == null || chosen == fill.address) fill else fill.at(chosen))
            }
        }
    }

    private fun publish(fill: PairingFill) {
        ready = fill
        local.update { it.copy(pairingArrival = it.pairingArrival + 1) }
    }

    override fun monitor(id: ProfileId, enabled: Boolean) = monitorOperation {
        it.enabled(id, enabled)
    }

    override fun monitorWifi(id: ProfileId, enabled: Boolean) = monitorOperation {
        it.wifiOnly(id, enabled)
    }

    override fun monitorCheckIn(id: ProfileId, minutes: Int?) = monitorOperation {
        it.checkIn(id, minutes)
    }

    private fun monitorOperation(change: suspend (ServerMonitoring) -> Outcome<Unit>) {
        val source = monitoring.singleOrNull() ?: return
        operation {
            when (val result = change(source)) {
                is Outcome.Failed -> failed(ServerIssue.SaveFailed, result.failure)
                is Outcome.Ok -> Unit
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun openAgents(): Unit = nav.open(AgentsRoute.Home)

    override fun openUsage(id: ProfileId): Unit = nav.open(UsageRoute.Hub(id))

    override fun openPlans(): Unit = nav.open(PlansRoute)

    private var consentAddress: String? = null

    override fun allowCleartext(allowed: Boolean, address: String) {
        if (!local.value.busy) {
            consentAddress = if (allowed) address.trim() else null
            local.update { it.copy(cleartextAllowed = allowed) }
        }
    }

    override fun save(name: String, address: String, username: String, password: String) {
        if (local.value.busy || connections.busy.value) return
        if (address.isBlank()) {
            local.update { it.copy(issue = ServerIssue.InputRequired, failure = null) }
            return
        }
        // Capture this explicit choice for exactly this submitted address; it is not a global
        // transport setting.
        val consent = local.value.cleartextAllowed && consentAddress == address.trim()
        operation {
            when (
                val saved =
                    connections.save(
                        name.trim(),
                        address.trim(),
                        username.trim(),
                        password,
                        consent,
                    )
            ) {
                is Outcome.Failed -> failed(ServerIssue.SaveFailed, saved.failure)
                is Outcome.Ok -> {
                    local.update {
                        it.copy(savedGeneration = it.savedGeneration + 1, cleartextAllowed = false)
                    }
                    when (val connected = connections.connect(saved.value)) {
                        is Outcome.Failed -> failed(ServerIssue.ConnectFailed, connected.failure)
                        is Outcome.Ok ->
                            if (route != null) createNow(saved.value)
                            else local.update { it.copy(issue = ServerIssue.Connected) }
                    }
                }
            }
        }
    }

    override fun connect(id: ProfileId) = operation {
        when (val result = connections.connect(id)) {
            is Outcome.Ok -> local.update { it.copy(issue = ServerIssue.Connected) }
            is Outcome.Failed -> failed(ServerIssue.ConnectFailed, result.failure)
        }
    }

    override fun create(id: ProfileId) = operation {
        when (val connected = connections.connect(id)) {
            is Outcome.Ok -> createNow(id)
            is Outcome.Failed -> failed(ServerIssue.ConnectFailed, connected.failure)
        }
    }

    private suspend fun createNow(id: ProfileId) {
        when (val result = connections.createConversation(id)) {
            is Outcome.Ok -> {
                // The chooser is done: Back from the new chat returns to where New chat was tapped.
                if (route != null && nav.top.value == route) nav.back()
                nav.openChat(result.value, focusComposer = true)
            }
            is Outcome.Failed -> failed(ServerIssue.CreateFailed, result.failure)
        }
    }

    override fun remove(id: ProfileId) = operation {
        when (val result = connections.remove(id)) {
            is Outcome.Ok -> Unit
            is Outcome.Failed -> failed(ServerIssue.RemoveFailed, result.failure)
        }
    }

    override fun scanPairing(): Unit = nav.open(PairingRoute.Scanner)

    override fun pastePairing(text: String?) {
        val read = PairingText.read(text.orEmpty())
        val fill = PairingFill.of(read)
        if (fill == null) {
            local.update { it.copy(pairingProblem = (read as? PairingRead.Problem)?.problem) }
            return
        }
        local.update {
            it.copy(
                pairingProblem = null,
                pairingChecks = kotlinx.collections.immutable.persistentListOf(),
                issue = null,
                failure = null,
            )
        }
        pairing.put(fill)
    }

    override fun takePairing(): PairingFill? = ready.also { ready = null }

    /**
     * The form's unsaved password while a page is on top of it: memory only, gone with the
     * ViewModel.
     */
    private var draftPassword = ""

    override fun holdPassword(text: String) {
        draftPassword = text
    }

    override fun heldPassword(): String = draftPassword

    override fun openSchedules(id: ProfileId) {
        nav.open(SchedulesRoute.List(id))
    }

    private fun operation(block: suspend () -> Unit) {
        if (local.value.busy || connections.busy.value) return
        local.update { it.copy(busy = true, issue = null, failure = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun failed(issue: ServerIssue, failure: Failure) {
        local.update { it.copy(issue = issue, failure = failure) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.New?): ServersViewModel
    }
}
