package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public enum class QueueMoveBlock {
    Uncertain,
    ServerFile,
    SecretMentions,
    Sending,
}

public data class QueueMoveItem(val id: String, val preview: String, val blocked: QueueMoveBlock?)

public data class QueueMoveConversation(val id: SessionId, val title: String?)

public data class ServerQueuePlan(
    val token: String,
    val sourceName: String,
    val destinationName: String,
    val items: ImmutableList<QueueMoveItem>,
    val conversations: ImmutableList<QueueMoveConversation>,
)

public data class ServerQueueMoved(
    val count: Int,
    val skipped: Int,
    val reset: Int,
    val conversation: ConversationRef,
)

public interface ServerQueueMovePort {
    public suspend fun prepare(source: ProfileId): Outcome<ServerQueuePlan>

    public suspend fun move(
        token: String,
        ids: Set<String>,
        conversation: SessionId?,
    ): Outcome<ServerQueueMoved>

    public suspend fun undo(token: String): Outcome<Int>

    public fun forget(token: String)
}

public data class ServerQueueUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val plan: ServerQueuePlan? = null,
    val selected: PersistentSet<String> = persistentSetOf(),
    val conversation: SessionId? = null,
    val moved: ServerQueueMoved? = null,
    val undone: Int? = null,
    val failure: Failure? = null,
)

public interface ServerQueueActions {
    public fun back()

    public fun refresh()

    public fun toggle(id: String)

    public fun choose(conversation: SessionId?)

    public fun move()

    public fun undo()

    public fun openConversation()
}

@AssistedInject
public class ServerQueueViewModel(
    @Assisted private val route: ServersRoute.MoveQueued,
    private val port: ServerQueueMovePort,
    private val nav: AppNavigator,
) : ViewModel(), ServerQueueActions {
    private val mutable = MutableStateFlow(ServerQueueUi())
    public val state: StateFlow<ServerQueueUi> = mutable.asStateFlow()
    private var generation = 0

    init {
        refresh()
    }

    override fun back() {
        if (!mutable.value.busy) nav.back()
    }

    override fun refresh() {
        if (mutable.value.busy) return
        mutable.value.plan?.let { port.forget(it.token) }
        val ticket = ++generation
        mutable.value = ServerQueueUi()
        viewModelScope.launch {
            val read = port.prepare(route.source)
            if (ticket != generation) {
                if (read is Outcome.Ok) port.forget(read.value.token)
                return@launch
            }
            when (read) {
                is Outcome.Ok ->
                    mutable.value =
                        ServerQueueUi(
                            loading = false,
                            plan = read.value,
                            selected =
                                read.value.items
                                    .filter { it.blocked == null }
                                    .map { it.id }
                                    .toPersistentSet(),
                        )
                is Outcome.Failed ->
                    mutable.value = ServerQueueUi(loading = false, failure = read.failure)
            }
        }
    }

    override fun toggle(id: String) {
        if (
            mutable.value.busy ||
                mutable.value.moved != null ||
                mutable.value.plan?.items?.none { it.id == id && it.blocked == null } != false
        )
            return
        mutable.update {
            it.copy(
                selected = if (id in it.selected) it.selected.remove(id) else it.selected.add(id)
            )
        }
    }

    override fun choose(conversation: SessionId?) {
        if (!mutable.value.busy) mutable.update { it.copy(conversation = conversation) }
    }

    override fun move() {
        val own = mutable.value
        val plan = own.plan ?: return
        if (own.busy || own.selected.isEmpty() || own.moved != null) return
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = port.move(plan.token, own.selected, own.conversation)) {
                    is Outcome.Ok -> mutable.update { it.copy(moved = result.value) }
                    is Outcome.Failed -> mutable.update { it.copy(failure = result.failure) }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    override fun undo() {
        val plan = mutable.value.plan ?: return
        if (mutable.value.busy || mutable.value.moved == null || mutable.value.undone != null)
            return
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = port.undo(plan.token)) {
                    is Outcome.Ok -> mutable.update { it.copy(undone = result.value) }
                    is Outcome.Failed -> mutable.update { it.copy(failure = result.failure) }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    override fun openConversation() {
        if (!mutable.value.busy) mutable.value.moved?.let { nav.openChat(it.conversation) }
    }

    override fun onCleared() {
        generation++
        mutable.value.plan?.let { port.forget(it.token) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServersRoute.MoveQueued): ServerQueueViewModel
    }
}
