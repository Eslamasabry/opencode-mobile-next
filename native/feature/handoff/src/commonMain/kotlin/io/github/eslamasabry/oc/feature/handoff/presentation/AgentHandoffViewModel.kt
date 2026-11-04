package io.github.eslamasabry.oc.feature.handoff.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@AssistedInject
public class AgentHandoffViewModel(
    @Assisted private val route: AgentHandoffRoute,
    private val host: AgentHandoffHost,
    private val nav: AppNavigator,
) : ViewModel(), AgentHandoffActions {
    private val mutable = MutableStateFlow(AgentHandoffUi())
    public val state: StateFlow<AgentHandoffUi> = mutable.asStateFlow()
    private var session: AgentHandoffSession? = null
    private var observer: Job? = null
    private var generation = 0

    init {
        refresh()
    }

    override fun back() {
        session?.close()
        nav.back()
    }

    override fun setup() = nav.open(AgentsRoute.Home)

    override fun refresh() {
        if (state.value.busy) return
        session?.close()
        observer?.cancel()
        session = null
        val run = ++generation
        mutable.value = AgentHandoffUi()
        viewModelScope.launch {
            when (val opened = host.open(route.source)) {
                is Outcome.Failed ->
                    if (run == generation)
                        mutable.update { it.copy(loading = false, failure = opened.failure) }
                is Outcome.Ok -> {
                    val owner = opened.value
                    if (run != generation) {
                        owner.close()
                        return@launch
                    }
                    session = owner
                    mutable.value =
                        AgentHandoffUi(
                            loading = false,
                            connected = true,
                            title = owner.title,
                            directory = owner.directory,
                            choices = owner.choices,
                            links = owner.links,
                        )
                    observer = viewModelScope.launch {
                        owner.state.collect { update ->
                            if (session === owner && run == generation)
                                mutable.update { it.copy(attempt = update) }
                        }
                    }
                }
            }
        }
    }

    private fun usable(): AgentHandoffSession? {
        val owner = session ?: return null
        if (owner.current()) return owner
        mutable.update {
            it.copy(
                connected = false,
                busy = false,
                failure = Failure.Refused(RefusalReason.StaleScope),
            )
        }
        return null
    }

    override fun select(id: String) {
        if (state.value.busy || state.value.attempt.phase != HandoffPhase.Idle) return
        if (state.value.choices.none { it.id == id && it.issue == null }) return
        mutable.update { it.copy(selected = id) }
    }

    override fun prepare() {
        val owner = usable() ?: return
        val choice = state.value.selected ?: return
        if (state.value.busy || state.value.attempt.phase != HandoffPhase.Idle) return
        perform(owner) { owner.prepare(choice) }
    }

    override fun editPrompt(value: String) {
        val owner = usable() ?: return
        if (state.value.busy || state.value.attempt.phase != HandoffPhase.Ready) return
        mutable.update { it.copy(prompt = owner.sanitizePrompt(value)) }
    }

    override fun send() {
        val owner = usable() ?: return
        if (state.value.busy || state.value.attempt.phase != HandoffPhase.Ready) return
        val prompt = state.value.prompt
        perform(owner) { owner.send(prompt) }
    }

    override fun retryLink() {
        val owner = usable() ?: return
        if (state.value.busy || state.value.attempt.failure != HandoffFailureKind.LinkFailed) return
        perform(owner) { owner.retryLink() }
    }

    private fun perform(owner: AgentHandoffSession, operation: suspend () -> Outcome<*>) {
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val result = operation()
            if (session !== owner) return@launch
            if (!owner.current()) {
                usable()
                return@launch
            }
            mutable.update {
                it.copy(
                    busy = false,
                    attempt = owner.state.value,
                    failure = (result as? Outcome.Failed)?.failure,
                )
            }
        }
    }

    override fun openOriginal() {
        if (usable() != null) nav.openChat(route.source)
    }

    override fun openCreated() {
        val owner = usable() ?: return
        owner.createdRef()?.let { nav.openChat(it) }
    }

    override fun openLink(id: String, continued: Boolean) {
        val owner = usable() ?: return
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            when (val link = owner.openLink(id, continued)) {
                is Outcome.Failed ->
                    mutable.update { it.copy(busy = false, failure = link.failure) }
                is Outcome.Ok -> {
                    mutable.update { it.copy(busy = false) }
                    if (owner.current()) nav.openChat(link.value)
                }
            }
        }
    }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }

    @AssistedFactory
    @ContributesIntoMap(AppScope::class)
    @ManualViewModelAssistedFactoryKey
    public interface Factory : ManualViewModelAssistedFactory {
        public fun create(route: AgentHandoffRoute): AgentHandoffViewModel
    }
}
