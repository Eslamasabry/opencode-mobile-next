package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
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

    /** Ends only this live connection; saved metadata, drafts and queued prompts are retained. */
    public suspend fun disconnect(id: ProfileId): Outcome<Unit> =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("server.disconnect")
            )
        )

    public suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef>

    public suspend fun remove(id: ProfileId): Outcome<Unit>
}
