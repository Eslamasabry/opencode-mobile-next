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
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingInbox
import io.github.eslamasabry.oc.feature.servers.pairing.PairingRead
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.PairingRoute
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
) : ViewModel(), ServersActions {
    private val local = MutableStateFlow(ServersUi(newConversation = route != null))
    public val state: StateFlow<ServersUi> =
        combine(local, connections.servers, connections.busy, pairing.arrivals) { own, servers, busy, arrivals ->
                own.copy(servers = servers, busy = own.busy || busy, pairingArrival = arrivals)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                local.value.copy(
                    servers = connections.servers.value,
                    busy = connections.busy.value,
                    pairingArrival = pairing.arrivals.value,
                ),
            )

    override fun back(): Unit = nav.back()

    override fun openAgents(): Unit = nav.open(AgentsRoute.Home)

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
            is Outcome.Ok -> nav.openChat(result.value, focusComposer = true)
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
        local.update { it.copy(pairingProblem = null, issue = null, failure = null) }
        pairing.put(fill)
    }

    override fun takePairing(): PairingFill? = pairing.take()

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
