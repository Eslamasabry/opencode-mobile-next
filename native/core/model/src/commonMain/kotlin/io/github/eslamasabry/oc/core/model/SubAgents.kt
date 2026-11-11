package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * One helper a conversation started (an OpenCode 2 task child, a Claude Code sub-agent behind Paseo), whatever the
 * server. The dock and the chat read only this, never the server's kind.
 *
 * [id] is the key in lists: the child's own session id when it has one. [parent] is the conversation (or the
 * sub-agent, when nested) that started it. [name] is the agent or task name when the server says it, else the
 * title; [title] is the server's own title.
 */
public data class SubAgent(
    val id: SessionId,
    val parent: SessionId,
    val name: String?,
    val title: String?,
    val state: SubAgentState,
    val open: SubAgentOpen,
    val createdMs: Long? = null,
)

/** Where a sub-agent is in its work, as far as one read can tell. */
public enum class SubAgentState {
    Working,
    Done,
    Failed,
    Stopped,

    /** The read could not tell (no status was read with it). */
    Unknown,
}

/** How the app can show a sub-agent. Only [Conversation] opens a chat today. */
public sealed interface SubAgentOpen {
    /** A real child conversation, opened like any chat; [readOnly] when it takes no replies. */
    public data class Conversation(val session: SessionId, val readOnly: Boolean) : SubAgentOpen

    /** A view over the parent's transcript, the messages its tool call marks (no server marks them yet). */
    public data class TranscriptSlice(val parent: SessionId, val toolCallId: String) : SubAgentOpen

    /** Only the card in the parent: its state and final text, nothing more. */
    public data class SummaryOnly(val parent: SessionId, val toolCallId: String) : SubAgentOpen
}
