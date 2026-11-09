package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * What the Conversations home can show before its server answers: the last list that server gave, as the same feed
 * rows the live list uses (same identity, so cached and live rows are one row that updates in place). The shared
 * cache layer replaces the implementation, not this seam.
 */
public interface HomeFeedCache {
    /** The rows saved for [profile], newest first; empty when none are saved or they may not be trusted. */
    public fun cachedRows(profile: ProfileId): List<ChatFeedItem>

    /** The last-message lines the home has read, by row identity, to save with the rows. */
    public fun rememberPreviews(lines: Map<String, String>)
}
