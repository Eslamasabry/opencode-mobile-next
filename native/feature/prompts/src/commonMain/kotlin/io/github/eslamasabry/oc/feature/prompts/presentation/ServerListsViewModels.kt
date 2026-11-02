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

    public data class Shown<T>(val items: List<T>, val server: String) : ListPhase<T>

    public data class NotOnServer(val server: String) : ListPhase<Nothing>

    public data class Failed(val failure: Failure) : ListPhase<Nothing>
}

public data class CommandsUi(val phase: ListPhase<CommandItem> = ListPhase.Loading)

public data class TasksUi(val phase: ListPhase<TaskItem> = ListPhase.Loading, val unfinishedOnly: Boolean = false) {
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
    private val nav: AppNavigator,
) : ViewModel(), CommandsActions {
    private val local = MutableStateFlow(CommandsUi())
    public val state: StateFlow<CommandsUi> = local.asStateFlow()

    init {
        retry()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        local.update { it.copy(phase = ListPhase.Loading) }
        viewModelScope.launch {
            val read = lists.commands(route.ref).phase()
            local.update { it.copy(phase = read) }
        }
    }

    override fun choose(name: String) {
        val shown = local.value.phase as? ListPhase.Shown ?: return
        if (shown.items.none { it.name == name }) return
        inbox.offer(ComposerInbox.target(route.ref.profileId.raw, route.ref.sessionId.raw), "/$name ")
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
) : ViewModel(), TasksActions {
    private val local = MutableStateFlow(TasksUi())
    public val state: StateFlow<TasksUi> = local.asStateFlow()

    init {
        retry()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        local.update { it.copy(phase = ListPhase.Loading) }
        viewModelScope.launch {
            val read = lists.tasks(route.ref).phase()
            local.update { it.copy(phase = read) }
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
