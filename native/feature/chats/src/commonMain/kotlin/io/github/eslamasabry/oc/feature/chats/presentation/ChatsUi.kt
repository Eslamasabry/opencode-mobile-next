package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** P1/P2 placeholder contract for the Conversations home. The real ChatsUi freezes in contracts/slice-2.md. */
public data class ChatsUi(
    val liveConnections: Int = 0,
    val rows: ImmutableList<ConversationRowUi> = persistentListOf(),
)

/** One conversation row. [ref] is also the grow key: the chat page grows out of this row (P2). */
public data class ConversationRowUi(val ref: ConversationRef, val title: String, val preview: String)

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    public fun showAll()

    public fun openConversation(ref: ConversationRef)
}

public data class ChatsAllUi(val filter: String?)

public interface ChatsAllActions {
    public fun back()
}

/** P2 proof page for ChatRoute until :feature:chat exists (its owner replaces the entry). */
public data class ChatPreviewUi(val ref: ConversationRef, val title: String, val lines: ImmutableList<String>)

public interface ChatPreviewActions {
    public fun back()
}
