package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListState

/**
 * What the panel remembers while it lives: which blocks the person folded or opened, and which conversations showed
 * sub-agents running at the same moment. Gone when the panel goes. Call from the controller's one dispatcher.
 */
public class LampFolds {
    private val chosen = HashMap<ConversationRef, Boolean>()
    private val overlap = HashSet<ConversationRef>()
    private val finished = HashSet<ConversationRef>()

    /** The person's choices: true folded, false opened. */
    public val folds: Map<ConversationRef, Boolean> get() = chosen

    /** Conversations seen with two sub-agents running or waiting at once. */
    public val overlapSeen: Set<ConversationRef> get() = overlap

    public fun toggle(ref: ConversationRef, folded: Boolean) {
        chosen[ref] = folded
    }

    /**
     * Reads one list: notes overlaps, and when every sub-agent of a block has just finished, forgets the person's choice
     * so the block folds itself (they can open it again). A block that starts working again is open again.
     */
    public fun observe(model: DockListModel) {
        val seen = HashSet<ConversationRef>()
        for (row in model.rows) {
            if (row.subAgents.isEmpty()) continue
            seen += row.ref
            if (LampTrees.overlaps(row.subAgents)) overlap += row.ref
            val allDone = row.subAgents.all { it.state == DockListState.Done || it.state == DockListState.Idle } && row.subAgentsMore == 0
            if (allDone && row.ref !in finished) {
                finished += row.ref
                chosen.remove(row.ref)
            } else if (!allDone) {
                finished -= row.ref
            }
        }
        finished.retainAll(seen)
    }
}
