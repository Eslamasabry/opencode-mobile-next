package io.github.eslamasabry.oc.feature.chats.presentation

/** P1 placeholder contract for the Conversations home. The real ChatsUi freezes in contracts/slice-2.md. */
public data class ChatsUi(val liveConnections: Int = 0)

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    public fun showAll()
}

public data class ChatsAllUi(val filter: String?)

public interface ChatsAllActions {
    public fun back()
}
