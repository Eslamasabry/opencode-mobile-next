package io.github.eslamasabry.oc.ui.kit.dock

/*
 * The dock's sentences, built from the host's [KitDockWords]. Pure, so the label rules are tested without Compose.
 */

/** The state word of one ring or child: "Working · 3 of 7" comes from [KitDockWords.workingTasks]. */
internal fun KitDockWords.stateWord(state: KitDockRingState): String =
    when (state) {
        is KitDockRingState.WorkingWithTasks -> workingTasks(state.done, state.total)
        KitDockRingState.Working -> working()
        KitDockRingState.Thinking -> thinking()
        KitDockRingState.NeedsYou -> needsYou()
        KitDockRingState.Finished -> finished()
        KitDockRingState.Failed -> failed()
        KitDockRingState.Idle -> idle()
    }

/** What leads the state line: a sub-agent's alert speaks for its conversation ("explore needs you"). */
internal fun KitDockWords.headWords(ring: KitDockRing): String {
    val child = ring.causedBy?.let { k -> ring.children.firstOrNull { it.key == k } }
    if (child != null) {
        when (ring.state) {
            KitDockRingState.NeedsYou -> return childNeedsYou(child.name)
            KitDockRingState.Failed -> return childFailed(child.name)
            else -> Unit
        }
    }
    return stateWord(ring.state)
}

/** The number of sub-agents, including those past the model's cap. */
internal val KitDockRing.childTotal: Int get() = children.size + childOverflow

/**
 * The state line in the swell, the peek and the list: "Working · 3 of 7 · 3 sub-agents · on Mac mini". The server is
 * named only when the host gave one (more than one server live).
 */
internal fun KitDockWords.stateLine(ring: KitDockRing, withCount: Boolean = true): String {
    val sb = StringBuilder(headWords(ring))
    if (withCount && ring.childTotal > 0) sb.append(separator).append(subAgents(ring.childTotal))
    ring.server?.let { sb.append(separator).append(onServer(it)) }
    return sb.toString()
}

/** The ring's label for assistive technology: "Bank API, Working · 3 of 7, 3 sub-agents, explore needs you". */
internal fun KitDockWords.ringLabel(ring: KitDockRing): String {
    val sb = StringBuilder(ring.name).append(", ").append(stateWord(ring.state))
    if (ring.childTotal > 0) sb.append(", ").append(subAgents(ring.childTotal))
    val child = ring.causedBy?.let { k -> ring.children.firstOrNull { it.key == k } }
    if (child != null && (ring.state == KitDockRingState.NeedsYou || ring.state == KitDockRingState.Failed)) {
        sb.append(", ").append(headWords(ring))
    }
    ring.server?.let { sb.append(", ").append(onServer(it)) }
    return sb.toString()
}

/** The word on a swell target. */
internal fun KitDockWords.targetWord(kind: DockTargetKind): String =
    when (kind) {
        DockTargetKind.Open -> open
        DockTargetKind.Allow -> allow
        DockTargetKind.Deny -> deny
        DockTargetKind.Stop -> stop
        DockTargetKind.Rename -> rename
        DockTargetKind.Remove -> remove
        DockTargetKind.Header, DockTargetKind.Child, DockTargetKind.More -> ""
    }
