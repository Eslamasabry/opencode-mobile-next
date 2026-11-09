package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import kotlinx.collections.immutable.toImmutableList

/** While a server has not answered, the rows saved last time stand in for its list, as the same feed rows. */
public object HomeCachedRows {
    /**
     * [snapshot] with the opening server's saved rows added, until the live feed holds any of that server's rows.
     * Cached and live rows share one identity, so the list updates them in place instead of re-laying out.
     */
    public fun merge(snapshot: ChatFeedSnapshot, opening: ChatsOpeningUi?): ChatFeedSnapshot =
        if (opening == null || opening.rows.isEmpty() || snapshot.items.any { it.profileId == opening.profile }) snapshot
        else snapshot.copy(items = (snapshot.items + opening.rows).toImmutableList(), loading = false)
}
