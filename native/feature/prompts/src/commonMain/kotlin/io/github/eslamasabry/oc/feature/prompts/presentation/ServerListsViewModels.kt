package io.github.eslamasabry.oc.feature.prompts.presentation

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
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PromptsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A server list page: loading, shown, not on this server, or failed (plain line + Details). */
public sealed interface ListPhase<out T> {
    public data object Loading : ListPhase<Nothing>

    /** [updating]: these are the last-seen rows and the live read is on its way (a quiet bar, no skeleton). */
    public data class Shown<T>(val items: List<T>, val server: String, val updating: Boolean = false) : ListPhase<T>

    public data class NotOnServer(val server: String) : ListPhase<Nothing>

    public data class Failed(val failure: Failure) : ListPhase<Nothing>
}

public data class CommandsUi(
    val phase: ListPhase<CommandItem> = ListPhase.Loading,
    val skills: List<io.github.eslamasabry.oc.core.model.SkillInfo> = emptyList(),
    val skillsLoading: Boolean = false,
    val skillWorking: String? = null,
    val skillResult: io.github.eslamasabry.oc.engine.integrations.catalog.SkillActivation? = null,
    val skillsFailure: Failure? = null,
)

public data class TasksUi(
    val phase: ListPhase<TaskItem> = ListPhase.Loading,
    val unfinishedOnly: Boolean = false,
) {
    val visible: List<TaskItem>
        get() =
            when (val p = phase) {
                is ListPhase.Shown -> if (unfinishedOnly) TaskRules.unfinished(p.items) else p.items
                else -> emptyList()
            }
}

public interface CommandsActions {
    public fun back()

    public fun retry()

    /** Puts `/name ` into the conversation's composer (the person adds arguments and sends). */
    public fun choose(name: String)

    public fun activateSkill(id: String)
}

public interface TasksActions {
    public fun back()

    public fun retry()

    public fun toggleUnfinished()
}

private fun <T> Outcome<ServerList<T>>.phase(): ListPhase<T> =
    when (this) {
        is Outcome.Failed -> ListPhase.Failed(failure)
        is Outcome.Ok ->
            when (val list = value) {
                is ServerList.Shown -> ListPhase.Shown(list.items, list.server)
                is ServerList.NotOnServer -> ListPhase.NotOnServer(list.server)
            }
    }

/** The server's slash commands (Flutter `command_sheet.dart`, server group). */
@AssistedInject
public class CommandsViewModel(
    @Assisted private val route: PromptsRoute.Commands,
    private val lists: ConversationLists,
    private val inbox: ComposerInbox,
    private val skillSources: Set<SkillsSource>,
    private val nav: AppNavigator,
    kept: Set<ServerListsLastKnown> = emptySet(),
) : ViewModel(), CommandsActions {
    private val shelf = kept.firstOrNull()
    private val local =
        MutableStateFlow(
            shelf?.peek(route.ref, ServerListItems.COMMANDS)?.let { commandsFrame(it) } ?: CommandsUi()
        )

    private fun commandsFrame(was: KeptServerList) =
        CommandsUi(
            phase = ListPhase.Shown(was.commandItems(), was.server, updating = true),
            skills = was.skillInfos(),
        )
    public val state: StateFlow<CommandsUi> = local.asStateFlow()

    init {
        if (shelf != null && local.value.phase == ListPhase.Loading) {
            // A cold start has only the disk copy: draw it when read, unless the live read was faster.
            viewModelScope.launch {
                val was = shelf.read(route.ref, ServerListItems.COMMANDS) ?: return@launch
                local.update { if (it.phase == ListPhase.Loading) commandsFrame(was).copy(skillsLoading = it.skillsLoading) else it }
            }
        }
        retry()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        local.update { if (it.phase is ListPhase.Shown) it else it.copy(phase = ListPhase.Loading) }
        viewModelScope.launch {
            val read = lists.commands(route.ref).phase()
            local.update { ui ->
                val standing = ui.phase as? ListPhase.Shown
                // A failed live read leaves the kept rows standing (no failure page over data the person can use).
                if (read is ListPhase.Failed && standing?.updating == true) ui.copy(phase = standing.copy(updating = false))
                else ui.copy(phase = read)
            }
            keepCommands()
        }
        val source = skillSources.singleOrNull()?.takeIf { it.available(route.ref) } ?: return
        local.update { it.copy(skillsLoading = true, skillsFailure = null) }
        viewModelScope.launch {
            when (val read = source.list(route.ref)) {
                is Outcome.Ok -> {
                    local.update { it.copy(skills = read.value, skillsLoading = false) }
                    keepCommands()
                }
                is Outcome.Failed ->
                    local.update { it.copy(skillsFailure = read.failure.takeIf { _ -> it.skills.isEmpty() }, skillsLoading = false) }
            }
        }
    }

    private suspend fun keepCommands() {
        val ui = local.value
        val shown = ui.phase as? ListPhase.Shown ?: return
        if (shown.updating) return
        shelf?.keep(route.ref, ServerListItems.COMMANDS, KeptServerList.ofCommands(shown.server, shown.items, ui.skills))
    }

    override fun activateSkill(id: String) {
        if (local.value.skills.none { it.id == id } || local.value.skillsLoading) return
        val source = skillSources.singleOrNull() ?: return
        // The engine rejects concurrent actions as Busy; the screen keeps their destination
        // explicit.
        local.update { it.copy(skillWorking = id, skillResult = null) }
        viewModelScope.launch {
            val result = source.activate(route.ref, id)
            local.update { it.copy(skillWorking = null, skillResult = result) }
        }
    }

    override fun choose(name: String) {
        val shown = local.value.phase as? ListPhase.Shown ?: return
        if (shown.items.none { it.name == name }) return
        inbox.offer(
            ComposerInbox.target(route.ref.profileId.raw, route.ref.sessionId.raw),
            "/$name ",
        )
        nav.returnToChat()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PromptsRoute.Commands): CommandsViewModel
    }
}

/** The agent's task list for the conversation (Flutter `mobile_task_view.dart`). */
@AssistedInject
public class TasksViewModel(
    @Assisted private val route: PromptsRoute.Tasks,
    private val lists: ConversationLists,
    private val nav: AppNavigator,
    kept: Set<ServerListsLastKnown> = emptySet(),
) : ViewModel(), TasksActions {
    private val shelf = kept.firstOrNull()
    private val local =
        MutableStateFlow(shelf?.peek(route.ref, ServerListItems.TASKS)?.let { tasksFrame(it) } ?: TasksUi())

    private fun tasksFrame(was: KeptServerList) =
        TasksUi(phase = ListPhase.Shown(was.taskItems(), was.server, updating = true))
    public val state: StateFlow<TasksUi> = local.asStateFlow()

    init {
        if (shelf != null && local.value.phase == ListPhase.Loading) {
            viewModelScope.launch {
                val was = shelf.read(route.ref, ServerListItems.TASKS) ?: return@launch
                local.update { if (it.phase == ListPhase.Loading) tasksFrame(was).copy(unfinishedOnly = it.unfinishedOnly) else it }
            }
        }
        retry()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        local.update { if (it.phase is ListPhase.Shown) it else it.copy(phase = ListPhase.Loading) }
        viewModelScope.launch {
            val read = lists.tasks(route.ref).phase()
            local.update { ui ->
                val standing = ui.phase as? ListPhase.Shown
                if (read is ListPhase.Failed && standing?.updating == true) ui.copy(phase = standing.copy(updating = false))
                else ui.copy(phase = read)
            }
            val shown = local.value.phase as? ListPhase.Shown
            if (shown != null && !shown.updating) {
                shelf?.keep(route.ref, ServerListItems.TASKS, KeptServerList.ofTasks(shown.server, shown.items))
            }
        }
    }

    override fun toggleUnfinished() {
        local.update { it.copy(unfinishedOnly = !it.unfinishedOnly) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PromptsRoute.Tasks): TasksViewModel
    }
}
