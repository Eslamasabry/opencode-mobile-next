package io.github.eslamasabry.oc.feature.phonesetup.presentation

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
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflight
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class SetupJourneyUi(
    val customizing: Boolean = false,
    val adding: Boolean = false,
    val options: SetupOptions? = null,
    val chosen: Set<String> = emptySet(),
    val checking: Boolean = true,
    val working: Boolean = false,
    val progress: SetupProgress = SetupProgress.Idle,
    val details: Redacted? = null,
    val failure: Failure? = null,
    val confirmStop: Boolean = false,
    val confirmRemoveVoice: Boolean = false,
    val storageUnopened: Boolean = false,
) {
    val install: List<io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent>
        get() = options?.let { SetupSelection.install(it, chosen, adding) }.orEmpty()

    val bytes: Long
        get() = SetupSelection.bytes(install)

    val seconds: Long
        get() = SetupSelection.seconds(install)

    val preflight: SetupPreflightResult?
        get() = options?.let { SetupPreflight.check(it.device, bytes) }

    val canConfirm: Boolean
        get() =
            !checking &&
                !working &&
                options != null &&
                preflight?.supported != false &&
                (!adding || install.isNotEmpty())
}

public interface SetupJourneyActions {
    public fun back()

    public fun retry()

    public fun toggle(id: String, checked: Boolean)

    public fun accept()

    public fun start()

    public fun customize()

    public fun continueSetup()

    public fun askStop()

    public fun cancelConfirm()

    public fun confirmStop()

    public fun askRemoveVoice()

    public fun confirmRemoveVoice()

    public fun openStorage()

    public fun done()
}

/**
 * Pure presentation over the shared durable owner; this class never starts a second runner or polls
 * setup.json.
 */
@AssistedInject
public class SetupJourneyViewModel(
    @Assisted private val route: PhoneSetupRoute,
    private val source: SetupJourneySource,
    private val nav: AppNavigator,
) : ViewModel(), SetupJourneyActions {
    private val customizing = route is PhoneSetupRoute.Customize
    private val adding = (route as? PhoneSetupRoute.Customize)?.adding == true
    private val mutable =
        MutableStateFlow(SetupJourneyUi(customizing = customizing, adding = adding))
    public val state: StateFlow<SetupJourneyUi> = mutable.asStateFlow()
    private var reads = 0
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            source.progress.collect { progress ->
                mutable.update { it.copy(progress = progress, details = source.details(progress)) }
            }
        }
        viewModelScope.launch {
            source.selection.collect { selected ->
                if (!adding && !customizing) mutable.update { it.copy(chosen = selected) }
            }
        }
        viewModelScope.launch { source.toolsChanged.drop(1).collect { retry() } }
        retry()
    }

    override fun retry() {
        val revision = ++reads
        mutable.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            val restore = source.restore()
            val options = source.options(adding)
            if (revision != reads) return@launch
            when (options) {
                is Outcome.Failed ->
                    mutable.update { it.copy(checking = false, failure = options.failure) }
                is Outcome.Ok -> {
                    val previous =
                        if (loadedOnce) state.value.chosen
                        else if (adding) emptySet() else source.selection.value
                    val known = options.value.components.map { it.id }.toSet()
                    val installed = options.value.installed
                    val chosen =
                        previous.filterTo(mutableSetOf()) { it in known && it !in installed }
                    loadedOnce = true
                    mutable.update {
                        it.copy(
                            checking = false,
                            options = options.value,
                            chosen = chosen,
                            failure = (restore as? Outcome.Failed)?.failure,
                        )
                    }
                }
            }
        }
    }

    override fun toggle(id: String, checked: Boolean) {
        val options = state.value.options ?: return
        if (state.value.checking || state.value.working) return
        val component = options.components.firstOrNull { it.id == id } ?: return
        if (component.required || id in options.installed) return
        mutable.update {
            it.copy(
                chosen =
                    SetupSelection.toggle(
                        options.components,
                        it.chosen,
                        options.installed,
                        id,
                        checked,
                    )
            )
        }
    }

    override fun accept() {
        if (!state.value.canConfirm) return
        if (adding) begin { source.run(state.value.chosen, true) }
        else {
            viewModelScope.launch {
                source.choose(state.value.chosen)
                nav.back()
            }
        }
    }

    override fun start() {
        if (!state.value.canConfirm) return
        begin { source.run(state.value.chosen, false) }
    }

    private fun begin(command: suspend () -> Outcome<Unit>) {
        if (state.value.working) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = command()
            mutable.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
            if (result is Outcome.Ok && customizing && adding) {
                nav.back()
                nav.open(PhoneSetupRoute.Setup)
            }
        }
    }

    override fun customize() {
        nav.open(PhoneSetupRoute.Customize())
    }

    override fun continueSetup() {
        begin { source.resume() }
    }

    override fun askStop() {
        if (state.value.progress.state == SetupState.Running)
            mutable.update { it.copy(confirmStop = true, failure = null) }
    }

    override fun cancelConfirm() {
        if (!state.value.working)
            mutable.update {
                it.copy(confirmStop = false, confirmRemoveVoice = false, failure = null)
            }
    }

    override fun confirmStop() {
        begin {
            source.cancel().also {
                if (it is Outcome.Ok) mutable.update { s -> s.copy(confirmStop = false) }
            }
        }
    }

    override fun askRemoveVoice() {
        if (!state.value.working)
            mutable.update { it.copy(confirmRemoveVoice = true, failure = null) }
    }

    override fun confirmRemoveVoice() {
        begin {
            source.removeVoice().also {
                if (it is Outcome.Ok) {
                    mutable.update { s -> s.copy(confirmRemoveVoice = false) }
                    retry()
                }
            }
        }
    }

    override fun openStorage() {
        mutable.update { it.copy(storageUnopened = !source.openStorage()) }
        retry()
    }

    override fun back() {
        nav.back()
    }

    override fun done() {
        nav.back()
        nav.open(PhoneRoute.ThisPhone)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PhoneSetupRoute): SetupJourneyViewModel
    }
}
