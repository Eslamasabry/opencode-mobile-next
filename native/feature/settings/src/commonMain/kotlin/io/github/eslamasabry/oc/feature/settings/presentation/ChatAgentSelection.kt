package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.engine.settings.scope.ChatAgentChoices

/** The agent a server's new chats start with (`oc.agent.<profileId>`, Flutter `ProfileStore.agent` / `setAgent`). */
public interface ProfileAgentDefault {
    public fun agentFor(profileId: ProfileId): String?

    public suspend fun set(profileId: ProfileId, agent: String): Outcome<Unit>
}

/** [ProfileAgentDefault] in the device store, written in the profile's own lane (swept with the profile). */
public class StoredProfileAgentDefault(private val store: KeyValueStore, private val lanes: WriterLanes) : ProfileAgentDefault {
    override fun agentFor(profileId: ProfileId): String? = store.string(key(profileId))

    override suspend fun set(profileId: ProfileId, agent: String): Outcome<Unit> =
        store.commit(lanes.forProfile(profileId)) { putString(key(profileId), agent) }

    public companion object {
        public const val PREFIX: String = "oc.agent."

        public fun key(profileId: ProfileId): String = PREFIX + profileId.raw
    }
}

/**
 * Which agent one chat runs with (B-AGENT-229; Flutter `agentForSession` / `selectAgentForSession` / `selectAgent`,
 * amended to OpenCode 2 chats): the chat's own choice when it has one, else the server's default. Choosing for one chat
 * leaves the other chats and the default alone; changing the default moves only the chats without their own choice.
 * Choices are per server and survive a restart (`oc.sessionAgents.<profileId>`); a choice shows only once storage
 * acknowledged it ([ChatAgentChoices]).
 */
public class ChatAgentSelection(private val chats: ChatAgentChoices, private val defaults: ProfileAgentDefault) {
    public fun agentFor(profileId: ProfileId, sessionId: String): String? =
        chats.agentFor(profileId, sessionId) ?: defaults.agentFor(profileId)

    public suspend fun chooseForChat(profileId: ProfileId, sessionId: String, agent: String): Outcome<Unit> =
        chats.set(profileId, sessionId, agent)

    public suspend fun chooseDefault(profileId: ProfileId, agent: String): Outcome<Unit> = defaults.set(profileId, agent)
}
