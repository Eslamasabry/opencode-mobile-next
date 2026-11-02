package io.github.eslamasabry.oc.feature.schedules.presentation

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
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.schedules.ScheduleCode
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.schedules.SchedulesState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** K01: one server's scheduled prompts. One tap admits one action; nothing is retried on its own. */
@AssistedInject
public class SchedulesViewModel(
    @Assisted private val route: SchedulesRoute.List,
    private val engine: ScheduleEngine,
    private val servers: ScheduleServers,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), SchedulesActions {
    private val profile = route.profileId.raw

    private data class Local(
        val target: ScheduleTarget? = null,
        val targetLoaded: Boolean = false,
        val confirmDelete: String? = null,
        val started: String? = null,
        val busy: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<SchedulesUi> =
        combine(engine.state(profile), local) { engineState, own -> present(engineState, own) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesUi(highlight = route.highlight))

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val target = servers.target(route.profileId)
            local.update { it.copy(target = target, targetLoaded = true) }
            if (target.available) engine.load(profile)
        }
    }

    private fun present(engineState: SchedulesState, own: Local): SchedulesUi {
        val target = own.target
        val unavailable =
            when {
                !own.targetLoaded -> null
                target?.available != true -> Unavailable.Server
                engineState.error == ScheduleCode.Unsupported && !engineState.platform.supported -> Unavailable.Phone
                engineState.error == ScheduleCode.StorageCorrupt && engineState.schedules.isEmpty() -> Unavailable.Storage
                else -> null
            }
        val phase =
            when {
                !own.targetLoaded || (unavailable == null && engineState.loading) -> SchedulesPhase.Loading
                unavailable != null -> SchedulesPhase.Unavailable
                engineState.schedules.isEmpty() -> SchedulesPhase.Empty
                else -> SchedulesPhase.List
            }
        val rows =
            engineState.schedules.map { schedule ->
                val last = engineState.runs.firstOrNull { it.scheduleId == schedule.id }
                ScheduleRowUi(
                    id = schedule.id,
                    prompt = schedule.prompt.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: schedule.prompt.trim(),
                    recurrence = schedule.recurrence,
                    nextRunAtMs = schedule.nextRunAtMs,
                    enabled = schedule.enabled,
                    continuesConversation = schedule.sessionId != null,
                    last =
                        last?.let {
                            LastRunUi(it.status, it.errorCode, it.finishedAtMs ?: it.startedAtMs, it.sessionId, it.late)
                        },
                )
            }
        return SchedulesUi(
            phase = phase,
            unavailable = unavailable,
            serverName = target?.serverName,
            rows = rows.toImmutableList(),
            highlight = route.highlight,
            digestOn = engineState.digest.enabled,
            error = engineState.error.takeIf { unavailable == null },
            confirmDelete = own.confirmDelete,
            started = own.started,
            busy = own.busy,
            nowMs = clock.now().toEpochMilliseconds(),
        )
    }

    override fun back(): Unit = nav.back()

    override fun create() {
        nav.open(SchedulesRoute.Edit(route.profileId, null, route.projectDir, route.from))
    }

    override fun edit(id: String) {
        nav.open(SchedulesRoute.Edit(route.profileId, id, route.projectDir, null))
    }

    override fun setEnabled(id: String, enabled: Boolean) = operation { engine.setEnabled(profile, id, enabled) }

    override fun runNow(id: String) = operation {
        engine.runNow(profile, id).also { if (it is Outcome.Ok) local.update { s -> s.copy(started = id) } }
    }

    override fun askDelete(id: String) {
        local.update { it.copy(confirmDelete = id) }
    }

    override fun cancelDelete() {
        local.update { it.copy(confirmDelete = null) }
    }

    override fun confirmDelete() {
        val id = local.value.confirmDelete ?: return
        local.update { it.copy(confirmDelete = null) }
        operation { engine.delete(profile, id) }
    }

    override fun openConversation(sessionId: String) {
        nav.openChat(ConversationRef(route.profileId, SessionId(sessionId)))
    }

    override fun setDigest(enabled: Boolean) = operation {
        engine.setDigest(profile, engine.state(profile).value.digest.copy(enabled = enabled))
    }

    override fun retry() {
        local.update { it.copy(targetLoaded = false) }
        load()
    }

    private fun operation(block: suspend () -> Outcome<Unit>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, started = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SchedulesRoute.List): SchedulesViewModel
    }
}
