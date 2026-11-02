package io.github.eslamasabry.oc.feature.prompts.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PromptsRoute

/** One server command as the list shows it (`GET /api/command`: name and description). */
public data class CommandItem(val name: String, val description: String?)

/** Agent task states (the bundled todo schema). Anything else reads as [Pending]. */
public enum class TaskState { Pending, InProgress, Completed, Cancelled }

public enum class TaskPriority { High, Medium, Low }

public data class TaskItem(val content: String, val state: TaskState, val priority: TaskPriority?) {
    override fun toString(): String = "TaskItem(chars=${content.length}, state=$state)"
}

/** A read of a list the server may not offer. */
public sealed interface ServerList<out T> {
    public val server: String

    public data class Shown<T>(val items: List<T>, override val server: String) : ServerList<T>

    /** The server does not offer this list: say so plainly; never show a broken empty list. */
    public data class NotOnServer(override val server: String) : ServerList<Nothing>
}

/** Reads the server lists of one conversation. */
public interface ConversationLists {
    public suspend fun commands(ref: ConversationRef): Outcome<ServerList<CommandItem>>

    public suspend fun tasks(ref: ConversationRef): Outcome<ServerList<TaskItem>>
}

/** This unit's view of a live connection's gateways (plan 03 S4). */
@ContributesTo(ConnectionScope::class)
public interface PromptsGraphAccessors {
    public val promptsGateway: ServerGateway
    public val promptsOperations: ServerOperationsGateway
}

/** [ConversationLists] over the conversation's live connection graph. No connection = an offline failure. */
@Inject
@ContributesBinding(AppScope::class)
public class RegistryConversationLists(private val registry: ConnectionRegistry) : ConversationLists {
    private fun accessors(ref: ConversationRef): Pair<String, PromptsGraphAccessors>? {
        val graph = registry.graphFor(ref) ?: return null
        val accessors = graph as? PromptsGraphAccessors ?: return null
        return graph.profile.name to accessors
    }

    override suspend fun commands(ref: ConversationRef): Outcome<ServerList<CommandItem>> {
        val (server, graph) = accessors(ref) ?: return Failure.Network(NetworkWhy.Offline).failed()
        if (!graph.promptsGateway.capabilities.value.slashCommands) return ServerList.NotOnServer(server).ok()
        return when (val read = graph.promptsOperations.commands()) {
            is Outcome.Ok ->
                ServerList.Shown(
                        read.value
                            .filter { it.name.isNotBlank() }
                            .map { CommandItem(it.name, it.description?.takeIf { d -> d.isNotBlank() }) }
                            .sortedBy { it.name.lowercase() },
                        server,
                    )
                    .ok()
            is Outcome.Failed ->
                if (read.failure is Failure.Unavailable) ServerList.NotOnServer(server).ok() else read
        }
    }

    override suspend fun tasks(ref: ConversationRef): Outcome<ServerList<TaskItem>> {
        val (server, graph) = accessors(ref) ?: return Failure.Network(NetworkWhy.Offline).failed()
        if (!graph.promptsGateway.capabilities.value.sessionTodos) return ServerList.NotOnServer(server).ok()
        return when (val read = graph.promptsGateway.todos(ref.sessionId)) {
            is Outcome.Ok -> ServerList.Shown(read.value.map { TaskRules.item(it.content, it.status, it.priority) }, server).ok()
            is Outcome.Failed ->
                if (read.failure is Failure.Unavailable) ServerList.NotOnServer(server).ok() else read
        }
    }
}

/** Pure task-list rules (B-CHAT-065). */
public object TaskRules {
    public fun state(raw: String): TaskState =
        when (raw.trim().lowercase()) {
            "in_progress", "in-progress", "inprogress" -> TaskState.InProgress
            "completed", "complete", "done" -> TaskState.Completed
            "cancelled", "canceled" -> TaskState.Cancelled
            else -> TaskState.Pending
        }

    public fun priority(raw: String?): TaskPriority? =
        when (raw?.trim()?.lowercase()) {
            "high" -> TaskPriority.High
            "medium" -> TaskPriority.Medium
            "low" -> TaskPriority.Low
            else -> null
        }

    public fun item(content: String, status: String, priority: String?): TaskItem =
        TaskItem(content.trim(), state(status), priority(priority))

    /** Done out of tracked: cancelled tasks are not tracked. */
    public fun progress(items: List<TaskItem>): Pair<Int, Int> {
        val tracked = items.filter { it.state != TaskState.Cancelled }
        return tracked.count { it.state == TaskState.Completed } to tracked.size
    }

    /** "Show unfinished only" keeps pending and in-progress tasks. */
    public fun unfinished(items: List<TaskItem>): List<TaskItem> =
        items.filter { it.state == TaskState.Pending || it.state == TaskState.InProgress }
}

/** Back out of the prompts pages to the conversation that opened them (never a second copy of the chat). */
internal fun AppNavigator.returnToChat() {
    repeat(3) { if (top.value is PromptsRoute) back() }
}
