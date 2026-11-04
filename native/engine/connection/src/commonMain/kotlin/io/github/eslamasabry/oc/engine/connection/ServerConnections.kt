package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** Saved metadata only. Secrets are accepted transiently by [ServerConnections.save]. */
public data class SavedServer(
    val id: ProfileId,
    val name: String,
    val address: String,
    val connected: Boolean,
) {
    override fun toString(): String = "SavedServer(id=${id.raw}, connected=$connected)"
}

/**
 * Protocol-neutral server-management and new-conversation actions, implemented by the app runtime.
 */
public interface ServerConnections {
    public val servers: StateFlow<ImmutableList<SavedServer>>
    public val busy: StateFlow<Boolean>

    public suspend fun save(
        name: String,
        address: String,
        username: String,
        password: String,
        allowCleartext: Boolean,
    ): Outcome<ProfileId>

    public suspend fun connect(id: ProfileId): Outcome<Unit>

    public suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef>

    /** Stable owner identities from the new-chat route; retries keep both values. */
    public suspend fun createConversation(
        profile: ProfileId?,
        commandId: String,
        tabId: String,
    ): Outcome<ConversationRef> = createConversation(profile)

    /** Snapshot for an explicitly opened project action; the action owner fences later changes. */
    public fun currentLocation(profile: ProfileId): ServerLocation? = null

    public suspend fun remove(id: ProfileId): Outcome<Unit>
}
