package io.github.eslamasabry.oc.feature.servers.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelState
import io.github.eslamasabry.oc.ui.routes.AgentAccountRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Route-bound account reads. Reconnect refreshes truth; it never recreates or retries a device
 * login.
 */
@AssistedInject
public class AgentAccountViewModel(
    @Assisted private val route: AgentAccountRoute,
    private val port: AgentAccountPort,
    private val nav: AppNavigator,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
) : ViewModel(), AgentAccountActions {
    private val local = MutableStateFlow(AgentAccountUi())
    public val state: StateFlow<AgentAccountUi> = local.asStateFlow()
    private var pinned: Any? = null
    private var bound = false
    private var lost = false
    private var handle: AgentAccountHandle? = null
    private var observing: Job? = null
    private var opening = 0

    init {
        viewModelScope.launch {
            port.watch(route.profile).distinctUntilChanged().collect { source ->
                if (lost) return@collect
                if (!bound) {
                    bound = true
                    pinned = source.scope
                }
                if (source.scope != pinned) {
                    lost = true
                    retire()
                    local.value =
                        AgentAccountUi(
                            phase = AgentAccountPhase.ScopeLost,
                            profileName = source.name,
                        )
                    return@collect
                }
                local.update { it.copy(profileName = source.name) }
                if (!source.connected || source.scope == null) {
                    retire()
                    local.update {
                        it.copy(phase = AgentAccountPhase.Disconnected, panel = AccountPanelState())
                    }
                    return@collect
                }
                if (!source.supported) {
                    retire()
                    local.update {
                        it.copy(phase = AgentAccountPhase.Unsupported, panel = AccountPanelState())
                    }
                    return@collect
                }
                if (handle?.active == true) return@collect
                retire()
                val ticket = ++opening
                when (val opened = port.open(route.profile, source.scope)) {
                    is Outcome.Failed ->
                        if (ticket == opening)
                            local.update {
                                it.copy(
                                    phase = AgentAccountPhase.Disconnected,
                                    failure = opened.failure,
                                    panel = AccountPanelState(),
                                )
                            }
                    is Outcome.Ok -> {
                        if (ticket != opening || lost) {
                            appScope.launch { opened.value.close() }
                            return@collect
                        }
                        val owner = opened.value
                        handle = owner
                        observing = viewModelScope.launch {
                            owner.state.collect { panel ->
                                if (handle === owner && owner.active && !lost) publish(panel)
                            }
                        }
                        local.update { it.copy(phase = AgentAccountPhase.Panel, failure = null) }
                        viewModelScope.launch { owner.refresh() }
                    }
                }
            }
        }
    }

    private fun publish(panel: AccountPanelState) {
        val moments =
            panel.limits
                .orEmpty()
                .flatMap { listOfNotNull(it.primary, it.secondary) }
                .mapNotNull { it.resetsAtEpochMillis }
                .distinct()
                .associateWith(port::moment)
        local.update {
            it.copy(
                phase = AgentAccountPhase.Panel,
                panel = panel,
                nowMs = clock.now().toEpochMilliseconds(),
                updated = panel.updatedAt?.toEpochMilliseconds()?.let(port::moment),
                lifetime = panel.usage?.lifetimeTokens?.let(port::number),
                peak = panel.usage?.peakDailyTokens?.let(port::number),
                resetMoments = moments,
                copied = if (panel.deviceCode == it.panel.deviceCode) it.copied else false,
                askedHost = if (panel.deviceCode == null) null else it.askedHost,
            )
        }
    }

    private fun retire() {
        opening++
        observing?.cancel()
        observing = null
        val owner = handle
        handle = null
        if (owner != null) appScope.launch { owner.close() }
    }

    override fun back() {
        lost = true
        retire()
        local.value = AgentAccountUi()
        nav.back()
    }

    override fun refresh() {
        val owner = handle ?: return
        viewModelScope.launch { if (owner.active && handle === owner) owner.refresh() }
    }

    override fun signIn() {
        val owner = handle ?: return
        viewModelScope.launch { if (owner.active && handle === owner) owner.signIn() }
    }

    override fun cancel() {
        val owner = handle ?: return
        viewModelScope.launch { if (owner.active && handle === owner) owner.cancel() }
    }

    override fun openSignIn(): Unit = open(false)

    override fun confirmOpen(): Unit = open(true)

    override fun dismissOpen() {
        local.update { it.copy(askedHost = null) }
    }

    private fun open(confirmed: Boolean) {
        val scope = pinned ?: return
        val owner = handle ?: return
        val code = local.value.panel.deviceCode ?: return
        if (!owner.active || lost) return
        when (val opened = port.openLink(route.profile, scope, code.verificationUrl, confirmed)) {
            is Outcome.Failed ->
                local.update { it.copy(failure = opened.failure, askedHost = null) }
            is Outcome.Ok ->
                local.update {
                    it.copy(
                        askedHost = (opened.value as? AgentAccountLink.Ask)?.host,
                        failure = null,
                    )
                }
        }
    }

    override fun copyCode() {
        val scope = pinned ?: return
        val owner = handle ?: return
        val code = local.value.panel.deviceCode ?: return
        if (!owner.active || lost) return
        when (val copied = port.copyCode(route.profile, scope, code.userCode)) {
            is Outcome.Failed -> local.update { it.copy(failure = copied.failure) }
            is Outcome.Ok -> local.update { it.copy(copied = true) }
        }
    }

    override fun onCleared() {
        retire()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: AgentAccountRoute): AgentAccountViewModel
    }
}
