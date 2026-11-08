package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeStatus
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class PhoneNodeUi(
    val node: PhoneNodeStatus = PhoneNodeStatus(),
    val confirmRemove: Boolean = false,
    /** Memory was removed from this phone. */
    val removed: Boolean = false,
    /** The node's log, once asked for (Details only). */
    val log: String? = null,
)

public interface PhoneNodeActions {
    public fun back()

    public fun runHere()

    public fun start()

    public fun stop()

    public fun restart()

    public fun askRemove()

    public fun remove()

    public fun cancelRemove()

    public fun showLog()

    public fun openCapture()

    public fun setUpPhone()

    public fun ownServer()

    public fun dismiss()
}

/**
 * Memory on this phone (F: phone_node_screen.dart): the first-run path. One button installs FactsBook in the built-in
 * Linux, starts it and connects this app to it, then hands over to Add something. Stop and Restart stay here; Remove,
 * the log and the way to the person's own server sit under Details.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PhoneNodeViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), PhoneNodeActions {
    private data class Local(val confirmRemove: Boolean = false, val removed: Boolean = false, val log: String? = null, val ranHere: Boolean = false)

    private val local = MutableStateFlow(Local())
    private val node get() = access.phoneNode

    public val state: StateFlow<PhoneNodeUi> =
        combine(access.phoneNode.status, local) { s, own -> PhoneNodeUi(s, own.confirmRemove, own.removed, own.log) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhoneNodeUi())

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            node.refresh()
        }
        // When a run started on this page ends with the app connected, the page hands over to Add something.
        viewModelScope.launch {
            node.status.collect { s ->
                if (local.value.ranHere && !s.busy && s.state == PhoneNodeState.Running && s.pairedHere) {
                    local.update { it.copy(ranHere = false) }
                    nav.back()
                    nav.open(FactsBookRoute.Capture)
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun runHere() {
        local.update { it.copy(ranHere = true, removed = false) }
        access.background { node.runHere() }
    }

    override fun start() {
        local.update { it.copy(ranHere = true) }
        access.background {
            node.start()
            if (node.status.value.state == PhoneNodeState.Running) access.engine.refresh()
        }
    }

    override fun stop(): Unit = access.background { node.stop() }

    override fun restart(): Unit = access.background { node.restart() }

    override fun askRemove(): Unit = local.update { it.copy(confirmRemove = true) }

    override fun cancelRemove(): Unit = local.update { it.copy(confirmRemove = false) }

    override fun remove() {
        local.update { it.copy(confirmRemove = false) }
        access.background {
            node.remove()
            if (node.status.value.state != PhoneNodeState.Failed) local.update { it.copy(removed = true, log = null) }
        }
    }

    override fun showLog() {
        viewModelScope.launch {
            val text = node.log()
            local.update { it.copy(log = text) }
        }
    }

    override fun openCapture(): Unit = nav.open(FactsBookRoute.Capture)

    override fun setUpPhone(): Unit = nav.open(PhoneSetupRoute.Setup)

    override fun ownServer(): Unit = nav.open(FactsBookRoute.Connect)

    override fun dismiss(): Unit = local.update { it.copy(removed = false) }
}
