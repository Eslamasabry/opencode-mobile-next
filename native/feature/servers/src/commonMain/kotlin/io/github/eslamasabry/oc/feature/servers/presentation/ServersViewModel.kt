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
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingInbox
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProbe
import io.github.eslamasabry.oc.feature.servers.pairing.PairingRead
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.ui.routes.AgentAccountRoute
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute
import io.github.eslamasabry.oc.ui.routes.PairingRoute
import io.github.eslamasabry.oc.ui.routes.PlansRoute
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SetupReviewRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
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
    private val imported: ImportedProviderReview? = null,
    private val connectionChecks: Set<ServerConnectionCheck> = emptySet(),
    private val readSharing: Set<ServerReadSharing> = emptySet(),
    /** Per-row facts (waiting/working/queued, backend); last so positional callers keep working. */
    private val rows: ServerRowsPort = NoServerRows,
) : ViewModel(), ServersActions, ServerDisconnectActions {
    private val local =
        MutableStateFlow(
            ServersUi(
                newConversation = route != null,
                connectionCheckAvailable = connectionChecks.size == 1,
            )
        )
    public val state: StateFlow<ServersUi> =
        combine(local, connections.servers, connections.busy, rows.state) {
                own,
                servers,
                busy,
                facts ->
                // Rows that need the person (waiting, then working) come first; ties keep saved
                // order.
                val ordered =
                    servers
                        .withIndex()
                        .sortedWith(
                            compareBy(
                                { indexed ->
                                    val row = facts.facts[indexed.value.id]
                                    when {
                                        (row?.waiting ?: 0) > 0 -> 0
                                        (row?.running ?: 0) > 0 -> 1
                                        else -> 2
                                    }
                                },
                                { it.index },
                            )
                        )
                        .map { it.value }
                        .toImmutableList()
                own.copy(
                    servers = ordered,
                    rows = facts,
                    busy = own.busy || busy,
                    importedSignIns =
                        own.importedSignIns?.takeIf { review ->
                            servers.any { it.id == review.profile }
                        },
                    readSharing =
                        readSharing
                            .singleOrNull()
                            ?.let { source -> servers.associate { it.id to source.state(it.id) } }
                            .orEmpty(),
                )
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
        readSharing.singleOrNull()?.let { source ->
            viewModelScope.launch {
                source.revision.collect {
                    local.update { state ->
                        state.copy(
                            readSharing =
                                connections.servers.value.associate { server ->
                                    server.id to source.state(server.id)
                                }
                        )
                    }
                }
            }
        }
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
        pairing.recipient = "server-editor"
        pairing.put(fill)
        nav.open(ServersRoute.Edit(newConversation = route != null))
    }

    /**
     * A reviewed private-network address (Tailscale sheet) opens the checked editor at it, like a
     * share link: an address only, never a credential.
     */
    override fun addServerAt(address: String) {
        if (address.isBlank()) return
        choose(PairingFill(address, null, null, 1, fromLink = true))
    }

    private fun publish(fill: PairingFill) {
        ready = fill
        local.update { it.copy(pairingArrival = it.pairingArrival + 1) }
    }

    private var checkJob: Job? = null
    private var checkRevision = 0

    override fun checkConnection(id: ProfileId) {
        val checker = connectionChecks.singleOrNull() ?: return
        val server = connections.servers.value.firstOrNull { it.id == id } ?: return
        checkJob?.cancel()
        val revision = ++checkRevision
        val initial = ConnectionCheckUi(id, server.name)
        local.update { it.copy(connectionCheck = initial) }
        checkJob = viewModelScope.launch {
            val facts =
                when (val result = checker.inspect(id)) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed ->
                        ConnectionCheckFacts(result, ConnectionCheckStream.NotConnected, 0)
                }
            if (revision == checkRevision) {
                local.update {
                    it.copy(
                        connectionCheck =
                            if (connections.servers.value.any { saved -> saved.id == id })
                                initial.finish(facts)
                            else null
                    )
                }
            }
        }
    }

    override fun closeConnectionCheck() {
        ++checkRevision
        checkJob?.cancel()
        checkJob = null
        local.update { it.copy(connectionCheck = null) }
    }

    override fun setReadSharing(id: ProfileId, enabled: Boolean) {
        val source = readSharing.singleOrNull() ?: return
        if (!source.state(id).available || source.state(id).saving) return
        operation {
            when (val result = source.set(id, enabled)) {
                is Outcome.Failed -> failed(ServerIssue.SaveFailed, result.failure)
                is Outcome.Ok -> Unit
            }
        }
    }

    override fun openAccount(id: ProfileId) {
        nav.open(AgentAccountRoute(id))
    }

    override fun moveQueued(id: ProfileId) {
        nav.open(ServersRoute.MoveQueued(id))
    }

    override fun edit(id: ProfileId?) {
        nav.open(ServersRoute.Edit(id, newConversation = route != null))
    }

    override fun settings(id: ProfileId) {
        nav.open(ServerSettingsRoute(id))
    }

    override fun monitor(id: ProfileId, enabled: Boolean) = monitorOperation {
        it.enabled(id, enabled)
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

    override fun openSetup(id: ProfileId): Unit = nav.open(ServersRoute.SetupAssist(id))

    override fun reviewImportedSignIns(id: ProfileId) = operation {
        when (
            val result =
                imported?.read(id)
                    ?: Outcome.Failed(
                        Failure.Unavailable(
                            io.github.eslamasabry.oc.core.common.FeatureId(
                                "migration.providerReview"
                            )
                        )
                    )
        ) {
            is Outcome.Ok ->
                local.update { it.copy(importedSignIns = ImportedSignInUi(id, result.value)) }
            is Outcome.Failed ->
                local.update {
                    it.copy(importedSignIns = ImportedSignInUi(id, emptyList(), failed = true))
                }
        }
    }

    override fun closeImportedSignIns() {
        local.update { it.copy(importedSignIns = null) }
    }

    override fun openProviderSignIn() {
        closeImportedSignIns()
        nav.open(io.github.eslamasabry.oc.ui.routes.IntegrationsRoute)
    }

    override fun openConnectorCatalog(id: ProfileId) {
        val location = connections.currentLocation(id)
        nav.open(McpCatalogRoute(id, location?.directory, location?.workspace))
    }

    override fun openSetupReview(id: ProfileId) {
        val location = connections.currentLocation(id)
        nav.open(SetupReviewRoute(id, location?.directory, location?.workspace))
    }

    override fun openAgentTools(id: ProfileId) =
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentToolsRoute(id))

    override fun openMonitor(id: ProfileId) =
        nav.open(io.github.eslamasabry.oc.ui.routes.MonitorRoute(id))

    override fun openCodexProbe() = nav.open(io.github.eslamasabry.oc.ui.routes.CodexProbeRoute())

    private var consentAddress: String? = null

    override fun allowCleartext(allowed: Boolean, address: String) {
        if (!local.value.busy) {
            consentAddress = if (allowed) address.trim() else null
            local.update { it.copy(cleartextAllowed = allowed) }
        }
    }

    override fun save(name: String, address: String, username: String, password: String) {
        if (local.value.busy || connections.busy.value) return
        val addressProblem =
            io.github.eslamasabry.oc.engine.runtime.messages.SetupMessages.addressProblem(address)
        if (addressProblem != null) {
            local.update {
                it.copy(
                    issue = ServerIssue.InputRequired,
                    failure = null,
                    addressProblem = addressProblem,
                )
            }
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

    override val disconnectSupported: Boolean
        get() = connections is io.github.eslamasabry.oc.engine.connection.ServerDisconnects

    override fun disconnect(id: ProfileId) = operation {
        val owner =
            connections as? io.github.eslamasabry.oc.engine.connection.ServerDisconnects
                ?: return@operation
        when (val result = owner.disconnect(id)) {
            is Outcome.Ok -> local.update { it.copy(issue = null, failure = null) }
            is Outcome.Failed -> failed(ServerIssue.ConnectFailed, result.failure)
        }
    }

    override fun connect(id: ProfileId) = operation {
        when (val result = connections.connect(id)) {
            is Outcome.Ok -> local.update { it.copy(issue = ServerIssue.Connected) }
            is Outcome.Failed -> failed(ServerIssue.ConnectFailed, result.failure)
        }
    }

    override fun create(id: ProfileId) {
        // Every explicit New chat gets its own saved route owner. A retry stays on that route.
        if (route == null) {
            if (!local.value.busy && !connections.busy.value && nav.top.value !is ChatsRoute.New)
                nav.open(ChatsRoute.New(id))
            return
        }
        operation {
            when (val connected = connections.connect(id)) {
                is Outcome.Ok -> createNow(id)
                is Outcome.Failed -> failed(ServerIssue.ConnectFailed, connected.failure)
            }
        }
    }

    private suspend fun createNow(id: ProfileId) {
        val owner = route ?: return
        // Save the chosen profile in navigation before dispatch. Restore cannot retarget the
        // same command to a newly active server when the original chooser had profile=null.
        if (owner.profile == null) {
            if (nav.top.value == owner) {
                nav.back()
                nav.open(owner.copy(profile = id))
            }
            return
        }
        if (owner.profile != id) {
            failed(
                ServerIssue.CreateFailed,
                Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope),
            )
            return
        }
        when (val result = connections.createConversation(id, owner.commandId, owner.tabId)) {
            is Outcome.Ok -> {
                if (nav.top.value != owner) return
                // The chooser is done: Back from the new chat returns to where New chat was tapped.
                if (route != null && nav.top.value == route) nav.back()
                nav.openChat(result.value, focusComposer = true)
            }
            is Outcome.Failed -> failed(ServerIssue.CreateFailed, result.failure)
        }
    }

    override fun remove(id: ProfileId) {
        nav.open(ServersRoute.Remove(id))
    }

    override fun scanPairing() {
        pairing.recipient = null
        nav.open(PairingRoute.Scanner)
    }

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
                addressProblem = null,
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

    override fun openStateSetup(id: ProfileId): Unit =
        nav.open(io.github.eslamasabry.oc.ui.routes.SetupRoute.Home(id))

    override fun tryOffline(): Unit = nav.open(io.github.eslamasabry.oc.ui.routes.DemoRoute)

    override fun openSchedules(id: ProfileId) {
        nav.open(SchedulesRoute.List(id))
    }

    private fun operation(block: suspend () -> Unit) {
        if (local.value.busy || connections.busy.value) return
        local.update { it.copy(busy = true, issue = null, failure = null, addressProblem = null) }
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

/** No row facts: rows keep saved order and show only what the saved server says. */
private object NoServerRows : ServerRowsPort {
    override val state: StateFlow<ServerRowsState> = MutableStateFlow(ServerRowsState())
}
