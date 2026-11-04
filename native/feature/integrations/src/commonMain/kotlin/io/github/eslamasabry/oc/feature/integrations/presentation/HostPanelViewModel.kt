package io.github.eslamasabry.oc.feature.integrations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.integrations.host.ByoHostPhase
import io.github.eslamasabry.oc.engine.integrations.host.ByoHostTarget
import io.github.eslamasabry.oc.engine.integrations.host.HostResult
import io.github.eslamasabry.oc.engine.integrations.host.byoHostOwnerSetup
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class HostPanelViewModel(private val source: HostPanelSource) :
    ViewModel(), HostPanelActions {
    private val local = MutableStateFlow(HostPanelUi())
    public val state: StateFlow<HostPanelUi> = local.asStateFlow()

    init {
        refreshHosts()
    }

    private fun command(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun reload() {
        when (val result = source.machines()) {
            is HostResult.Ok -> local.update { it.copy(machines = result.value) }
            is HostResult.Failed -> local.update { it.copy(failure = result.code) }
        }
    }

    override fun refreshHosts() = command { reload() }

    override fun chooseHost(id: String) {
        if (local.value.busy) return
        val machine = local.value.machines.firstOrNull { it.id == id } ?: return
        local.update {
            it.copy(
                selected = id,
                phase = machine.phase,
                fingerprint = null,
                publicKey = null,
                failure = null,
                preparation = persistentListOf(),
            )
        }
    }

    override fun inspectHost(address: String, port: String) = command {
        val id = source.newId()
        local.update {
            it.copy(
                selected = null,
                fingerprint = null,
                publicKey = null,
                phase = ByoHostPhase.Idle,
                preparation = persistentListOf(),
            )
        }
        val target = port.toIntOrNull()?.let { ByoHostTarget.parse(address, it) }
        if (target != null)
            local.update {
                it.copy(
                    preparation =
                        byoHostOwnerSetup(target)
                            .map { step ->
                                HostPreparationStepUi(step.id, step.commands.joinToString("\n"))
                            }
                            .toImmutableList()
                )
            }
        when (val result = source.inspect(id, address, port)) {
            is HostResult.Ok ->
                local.update {
                    it.copy(
                        selected = id,
                        phase = ByoHostPhase.NeedsTrust,
                        fingerprint = result.value,
                        publicKey = null,
                    )
                }
            is HostResult.Failed -> local.update { it.copy(failure = result.code) }
        }
    }

    override fun prepareHostIdentity() {
        val id = local.value.selected ?: return
        val fingerprint = local.value.fingerprint ?: return
        command {
            when (val result = source.prepareIdentity(id, fingerprint)) {
                is HostResult.Ok -> local.update { it.copy(publicKey = result.value) }
                is HostResult.Failed -> local.update { it.copy(failure = result.code) }
            }
            reload()
        }
    }

    override fun resumeHost() {
        val id = local.value.selected ?: return
        command {
            when (val result = source.resume(id)) {
                is HostResult.Ok ->
                    local.update {
                        it.copy(
                            phase = result.value.phase,
                            fingerprint = result.value.fingerprint,
                            publicKey = null,
                        )
                    }
                is HostResult.Failed -> local.update { it.copy(failure = result.code) }
            }
            reload()
        }
    }

    override fun removeHost(revoke: Boolean) {
        val id = local.value.selected ?: return
        command {
            when (val result = source.remove(id, revoke)) {
                is HostResult.Ok ->
                    local.update { HostPanelUi(machines = it.machines, busy = true) }
                is HostResult.Failed -> local.update { it.copy(failure = result.code) }
            }
            reload()
        }
    }
}
