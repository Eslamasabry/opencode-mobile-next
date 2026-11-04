package io.github.eslamasabry.oc.feature.chat.queuemove

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
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class QueueMoveServer(val id: ProfileId, val name: String)

public data class QueueMovePrompt(val id: String, val text: String)

public data class QueueMoveDestination(val ref: ConversationRef, val title: String?)

public data class QueueMoveUi(
    val open: Boolean = false,
    val prompts: List<QueueMovePrompt> = emptyList(),
    val servers: List<QueueMoveServer> = emptyList(),
    val prompt: String? = null,
    val server: ProfileId? = null,
    val destinations: List<QueueMoveDestination> = emptyList(),
    val more: Boolean = false,
    val working: Boolean = false,
    val failure: Failure? = null,
)

public interface QueueMoveActions {
    public fun open()

    public fun close()

    public fun choosePrompt(id: String)

    public fun chooseServer(id: ProfileId)

    public fun loadMore()

    public fun move(destination: ConversationRef)
}

/** Explicit queue relocation; catalog and destination reads precede its one durable mutation. */
@AssistedInject
public class QueueMoveViewModel(
    @Assisted private val route: ChatRoute,
    private val registry: ConnectionRegistry,
    private val navigator: AppNavigator,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
) : ViewModel(), QueueMoveActions {
    private val mutable = MutableStateFlow(QueueMoveUi())
    public val state: StateFlow<QueueMoveUi> = mutable.asStateFlow()
    private var cursor: String? = null
    private var generation = 0
    private val mutableAvailable = MutableStateFlow(false)
    public val available: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            registry.live.collectLatest {
                val graph = registry.graphFor(route.ref)
                val access = graph as? ChatGraphAccessors
                val queue = access?.chatDelivery?.queue
                mutableAvailable.value = false
                if (access == null || queue == null) {
                    if (mutable.value.open) close()
                    return@collectLatest
                }
                queue.entries()
                combine(queue.state, access.chatGateway.capabilities) { entries, capabilities ->
                        capabilities !=
                            io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
                                .Unknown &&
                            !capabilities.promptSteering &&
                            entries.entries.any { entry ->
                                entry.ref == route.ref &&
                                    !entry.dispatched &&
                                    entry.id !in entries.sending
                            }
                    }
                    .collect { allowed ->
                        mutableAvailable.value = allowed
                        if (!allowed && mutable.value.open && !mutable.value.working) close()
                    }
            }
        }
    }

    override fun open() {
        if (!mutableAvailable.value) return
        val graph = registry.graphFor(route.ref)
        val queue =
            (graph as? ChatGraphAccessors)?.chatDelivery?.queue
                ?: registry.live.value.values.firstNotNullOfOrNull {
                    (it as? ChatGraphAccessors)?.chatDelivery?.queue
                }
        val entries =
            queue?.entries().orEmpty().filter {
                it.ref == route.ref && !it.dispatched && !queue!!.sending(it.id)
            }
        mutable.value =
            QueueMoveUi(
                open = true,
                prompts = entries.map { QueueMovePrompt(it.id, it.text) },
                prompt = entries.singleOrNull()?.id,
                servers =
                    registry.live.value.values.map {
                        QueueMoveServer(it.profile.id, it.profile.name)
                    },
            )
    }

    override fun close() {
        generation++
        mutable.value = QueueMoveUi()
        cursor = null
    }

    override fun choosePrompt(id: String) {
        if (!mutable.value.working && mutable.value.prompts.any { it.id == id })
            mutable.value = mutable.value.copy(prompt = id, failure = null)
    }

    override fun chooseServer(id: ProfileId) {
        if (mutable.value.working || mutable.value.servers.none { it.id == id }) return
        generation++
        cursor = null
        mutable.value =
            mutable.value.copy(
                server = id,
                destinations = emptyList(),
                failure = null,
                more = false,
            )
        readPage()
    }

    override fun loadMore() {
        if (mutable.value.more && !mutable.value.working) readPage()
    }

    private fun readPage() {
        val id = mutable.value.server ?: return
        val graph = registry.live.value[id] ?: return
        val run = generation
        val gateway =
            (graph as? ChatGraphAccessors)?.chatGateway
                ?: run {
                    mutable.value =
                        mutable.value.copy(failure = Failure.Refused(RefusalReason.StaleScope))
                    return
                }
        mutable.value = mutable.value.copy(working = true, failure = null)
        viewModelScope.launch {
            val page = gateway.sessionPage(cursor = cursor, limit = 50)
            if (run != generation || registry.live.value[id] !== graph) return@launch
            mutable.value =
                when (page) {
                    is Outcome.Failed -> mutable.value.copy(working = false, failure = page.failure)
                    is Outcome.Ok -> {
                        cursor = page.value.nextCursor
                        mutable.value.copy(
                            working = false,
                            more = cursor != null,
                            destinations =
                                (mutable.value.destinations +
                                        page.value.items
                                            .filter {
                                                it.time?.archivedMs == null && it.parentId == null
                                            }
                                            .map {
                                                QueueMoveDestination(
                                                    ConversationRef(id, it.id),
                                                    it.title,
                                                )
                                            }
                                            .filter { it.ref != route.ref })
                                    .distinctBy { it.ref },
                        )
                    }
                }
        }
    }

    override fun move(destination: ConversationRef) {
        val ui = mutable.value
        val id = ui.prompt ?: return
        if (!mutableAvailable.value || ui.working || ui.destinations.none { it.ref == destination })
            return
        val source = registry.graphFor(route.ref)
        val target = registry.graphFor(destination) ?: return
        val targetAccess =
            target as? ChatGraphAccessors
                ?: run {
                    mutable.value =
                        mutable.value.copy(failure = Failure.Refused(RefusalReason.StaleScope))
                    return
                }
        val sourceQueue =
            (source as? ChatGraphAccessors)?.chatDelivery?.queue
                ?: targetAccess.chatDelivery.queue
                ?: return
        val run = generation
        mutable.value = ui.copy(working = true, failure = null)
        viewModelScope.launch {
            val read = targetAccess.chatOperations.catalog()
            if (run != generation) return@launch
            if (
                registry.graphFor(route.ref) !== source || registry.graphFor(destination) !== target
            ) {
                mutable.value =
                    mutable.value.copy(
                        working = false,
                        failure = Failure.Refused(RefusalReason.StaleScope),
                    )
                return@launch
            }
            if (read is Outcome.Failed) {
                mutable.value = mutable.value.copy(working = false, failure = read.failure)
                return@launch
            }
            val session = targetAccess.chatGateway.session(destination.sessionId)
            if (run != generation) return@launch
            if (
                session is Outcome.Failed ||
                    registry.graphFor(destination) !== target ||
                    registry.graphFor(route.ref) !== source
            ) {
                mutable.value =
                    mutable.value.copy(
                        working = false,
                        failure =
                            (session as? Outcome.Failed)?.failure
                                ?: Failure.Refused(RefusalReason.StaleScope),
                    )
                return@launch
            }
            val catalog = (read as Outcome.Ok).value
            val providers = catalog.providers.filter { it.enabled }.mapTo(hashSetOf()) { it.id }
            val models =
                catalog.models
                    .filter { it.enabled && it.providerId in providers }
                    .mapTo(hashSetOf()) { ModelRef(it.providerId, it.id) }
            val agents =
                catalog.agents
                    .filter { !it.hidden && it.mode != "subagent" }
                    .mapTo(hashSetOf()) { it.id }
            if (
                !mutableAvailable.value ||
                    (source as? ChatGraphAccessors)
                        ?.chatGateway
                        ?.capabilities
                        ?.value
                        ?.promptSteering != false
            ) {
                mutable.value =
                    mutable.value.copy(
                        working = false,
                        failure = Failure.Refused(RefusalReason.StaleScope),
                    )
                return@launch
            }
            val result = sourceQueue.move(id, route.ref, destination, models, agents, redactor)
            if (run != generation) return@launch
            when (result) {
                is Outcome.Failed ->
                    mutable.value = mutable.value.copy(working = false, failure = result.failure)
                is Outcome.Ok ->
                    if (!result.value) {
                        mutable.value =
                            mutable.value.copy(
                                working = false,
                                failure = Failure.Refused(RefusalReason.StaleScope),
                            )
                    } else {
                        targetAccess.chatDelivery.queue?.reloadFromStorage()
                        close()
                        navigator.openChat(destination)
                    }
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): QueueMoveViewModel
    }
}
