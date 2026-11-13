package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The five states a row of the list can be in. */
public enum class DockListState { NeedsYou, Failed, Working, Done, Idle }

/** A task list's progress: [done] of [total] (cancelled tasks are not counted). */
public data class DockProgress(val done: Int, val total: Int)

/** A sub-agent under a row, with its own state. */
public data class DockListSubAgent(
    val ref: ConversationRef,
    val name: String?,
    val state: DockListState,
    val progress: DockProgress?,
    val thinking: Boolean,
    /** What it waits on, when it needs the person and the dock has seen the words. */
    val request: DockRequestWords?,
)

/** A conversation the person hid from the list that is still live; [DockController.restore] brings it back. */
public data class DockListHidden(
    val ref: ConversationRef,
    val name: String?,
    val state: DockListState,
    /** The conversation this sub-agent belongs to; null for a conversation of its own. */
    val parent: ConversationRef?,
)

/**
 * One conversation as the edge light's list reads it. Everything a row needs, so the UI never asks the engine again.
 * Words are masked and one line; copy such as "3 of 7 done" or "Asks to edit" is the UI's.
 */
public data class DockListRow(
    val ref: ConversationRef,
    /** The nickname or the title; null when neither is known yet. */
    val name: String?,
    val state: DockListState,
    /** Done of total when the agent keeps a task list and the conversation is working; null otherwise. */
    val progress: DockProgress?,
    /** The newest part of the reply is reasoning (only without a task list). */
    val thinking: Boolean,
    /** What it waits on when [state] is [DockListState.NeedsYou] (its own or its sub-agent's), masked. */
    val request: DockRequestWords?,
    /** The sub-agent that raised [state] to NeedsYou or Failed. */
    val causedBy: ConversationRef?,
    /** The newest line of the conversation (a reply or a prompt), masked and short; null when nothing is cached. */
    val newestLine: String?,
    val subAgents: ImmutableList<DockListSubAgent>,
    /** Sub-agents beyond the cap that are not listed. */
    val subAgentsMore: Int,
    /** The server's name, only when conversations from more than one server are listed. */
    val serverName: String?,
    val serverMark: String?,
    val agent: String?,
    val pinned: Boolean,
    /** When it last did anything, epoch ms; null when unknown. */
    val lastActivityMs: Long?,
    /** The conversation on screen (it gets no row of its own in the list; the UI draws the header from it). */
    val isOpen: Boolean,
    /** Rises each time it enters NeedsYou or Failed. */
    val pulse: Int,
)

/** The whole list: what the face and the panel read. */
public data class DockListModel(
    val rows: ImmutableList<DockListRow> = persistentListOf(),
    /** Conversations beyond the reader's cap that are not in [rows]. */
    val more: Int = 0,
    val hidden: ImmutableList<DockListHidden> = persistentListOf(),
    val severalServers: Boolean = false,
) {
    val hiddenCount: Int get() = hidden.size

    /** The face's number: who needs you plus what failed. */
    val needsCount: Int get() = rows.count { it.state == DockListState.NeedsYou }
    val failedCount: Int get() = rows.count { it.state == DockListState.Failed }
    val workingCount: Int get() = rows.count { it.state == DockListState.Working }
    val count: Int get() = needsCount + failedCount
}

/** Builds [DockListModel] from a reader's [DockUi] and the facts the reducer does not hold. Pure. */
public object DockList {
    public const val MAX_LINE: Int = 140

    public fun build(
        ui: DockUi,
        facts: DockFacts,
        /** The newest line of a conversation; wire `DockPeeks.newest` here. Masked here. */
        newestLine: (ConversationRef) -> String? = { null },
    ): DockListModel {
        fun requestOf(ref: ConversationRef, requests: List<DockRequestRef>): DockRequestWords? =
            requests.firstNotNullOfOrNull { facts.requestWords(ref, it) }

        val rows =
            ui.entries.map { e ->
                val state = e.state.listState()
                val cause = e.children.firstOrNull { it.ref == e.causedBy }
                DockListRow(
                    ref = e.ref,
                    name = e.name,
                    state = state,
                    progress = (e.state as? DockState.WorkingWithTasks)?.takeIf { it.total > 0 }?.let { DockProgress(it.done, it.total) },
                    thinking = e.state == DockState.Thinking,
                    request =
                        if (state != DockListState.NeedsYou) null
                        else requestOf(e.ref, e.requests) ?: cause?.let { requestOf(it.ref, it.requests) },
                    causedBy = e.causedBy,
                    newestLine = newestLine(e.ref)?.let { NotificationText.redact(it, MAX_LINE) }?.takeIf { it.isNotBlank() },
                    subAgents =
                        e.children.map { c ->
                            val s = c.state.listState()
                            DockListSubAgent(
                                ref = c.ref,
                                name = c.name,
                                state = s,
                                progress = (c.state as? DockState.WorkingWithTasks)?.takeIf { it.total > 0 }?.let { DockProgress(it.done, it.total) },
                                thinking = c.state == DockState.Thinking,
                                request = if (s == DockListState.NeedsYou) requestOf(c.ref, c.requests) else null,
                            )
                        }.toImmutableList(),
                    subAgentsMore = e.childOverflow,
                    serverName = e.serverName?.takeIf { ui.severalServers && it.isNotBlank() },
                    serverMark = e.serverMark,
                    agent = e.agent,
                    pinned = e.pinned,
                    lastActivityMs = facts.lastActivity(e.ref),
                    isOpen = e.ref == ui.open,
                    pulse = e.pulse,
                )
            }
        return DockListModel(
            rows = rows.toImmutableList(),
            more = ui.overflow,
            hidden = ui.hidden.map { DockListHidden(it.ref, it.name, it.state.listState(), it.parent) }.toImmutableList(),
            severalServers = ui.severalServers,
        )
    }

    private fun DockState.listState(): DockListState =
        when (this) {
            is DockState.WorkingWithTasks, DockState.Working, DockState.Thinking -> DockListState.Working
            DockState.NeedsYou -> DockListState.NeedsYou
            DockState.Failed -> DockListState.Failed
            DockState.Finished -> DockListState.Done
            DockState.Idle -> DockListState.Idle
        }
}
