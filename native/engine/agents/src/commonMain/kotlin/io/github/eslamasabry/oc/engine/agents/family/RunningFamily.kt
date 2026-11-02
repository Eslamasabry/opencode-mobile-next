package io.github.eslamasabry.oc.engine.agents.family

import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** What the strip needs of a session: its id, its parent (a sub-agent's) and when it was created. */
public data class FamilySession(val id: SessionId, val parentId: SessionId? = null, val createdMillis: Long = 0)

public enum class RunningAgentRelation { Parent, Current, Sibling, Child }

public data class RunningAgentEntry(
    val session: FamilySession,
    val busy: Boolean,
    val current: Boolean,
    val relation: RunningAgentRelation,
)

/**
 * The running-agents strip (B-AGENT-214, port of `runningAgentEntries` in `lib/ui/widgets/running_agents_strip.dart`):
 * the parent (when delegated), siblings sharing it, and children this session delegated to. Empty unless at least one
 * OTHER member of the family runs: the strip exists to switch between concurrently working agents, not to list
 * history. Order: running others, then the current session, then the idle rest; siblings and children oldest first.
 * Labels are the UI's (words), not decided here.
 */
public object RunningFamily {
    public fun entries(
        sessionId: SessionId,
        sessions: Map<SessionId, FamilySession>,
        busy: Set<SessionId>,
        includeIdle: Boolean = false,
    ): ImmutableList<RunningAgentEntry> {
        val current = sessions[sessionId] ?: return persistentListOf()
        val parentId = current.parentId
        val parent = parentId?.let { sessions[it] }
        val siblings = sessions.values.filter { parentId != null && it.parentId == parentId }.sortedBy { it.createdMillis }
        val children = sessions.values.filter { it.parentId == sessionId }.sortedBy { it.createdMillis }
        val all = buildList {
            if (parent != null) add(RunningAgentEntry(parent, parent.id in busy, false, RunningAgentRelation.Parent))
            if (parent == null && children.none { it.id == sessionId }) {
                add(RunningAgentEntry(current, sessionId in busy, true, RunningAgentRelation.Current))
            }
            for (s in siblings) {
                val self = s.id == sessionId
                add(RunningAgentEntry(s, s.id in busy, self, if (self) RunningAgentRelation.Current else RunningAgentRelation.Sibling))
            }
            for (s in children) add(RunningAgentEntry(s, s.id in busy, false, RunningAgentRelation.Child))
        }
        if (!includeIdle && all.none { !it.current && it.busy }) return persistentListOf()
        val running = all.filter { it.busy && !it.current }
        val self = all.filter { it.current }
        val idle = all.filter { !it.busy && !it.current }
        return (running + self + idle).toImmutableList()
    }
}
