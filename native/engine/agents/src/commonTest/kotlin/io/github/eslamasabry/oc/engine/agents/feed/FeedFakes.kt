package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun row(
    id: String,
    status: ChatStatus = ChatStatus.Idle,
    agent: String = "codex",
    time: Long = 1,
    title: String? = "Conversation",
    directory: String = "/work/project",
    parent: String? = null,
) = ChatFeedItem(
    sessionId = id,
    title = title,
    directory = directory,
    projectName = "Project",
    isGit = false,
    status = status,
    lastActivityMillis = time,
    parentId = parent,
    agentId = AgentId(agent),
    agentLabel = "Agent",
)

internal class FakeFeedSource(initial: SourceFeed = SourceFeed()) : ChatFeedSource {
    val state = MutableStateFlow(initial)
    override val feed: StateFlow<SourceFeed> get() = state
    var reads = 0
    var onRefresh: suspend () -> Outcome<Unit> = { Unit.ok() }

    override suspend fun refresh(): Outcome<Unit> {
        reads++
        return onRefresh()
    }

    fun rows(vararg items: ChatFeedItem, complete: Boolean = true, loading: Boolean = false, across: Boolean = true) {
        state.value = SourceFeed(items.toList().toImmutableList(), acrossProjects = across, loading = loading, complete = complete)
    }
}
