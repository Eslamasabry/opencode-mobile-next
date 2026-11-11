package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** The home list's two engine facts about a connection, read from the pieces that already own them (H4, H6). */
internal object ListRowFacts {
    /**
     * Sessions whose newest turn the person stopped from this app on [profile]: the connection's delivery already
     * remembers every Stop (Flutter `markStoppedByPerson`, kept in memory only, so no stored key and nothing for the
     * profile deletion sweep).
     */
    fun stopped(profile: ProfileId, graph: ConnectionGraph): Flow<Set<SessionId>> =
        (graph as? ChatGraphAccessors)
            ?.chatDelivery
            ?.stoppedByPerson
            ?.map { refs -> refs.filter { it.profileId == profile }.mapTo(hashSetOf()) { it.sessionId } }
            ?: emptyFlow()

    /** Whether reopening [session] needs a new conversation, from the agents helper's own records; null = unknown. */
    fun needsNewChat(graph: ConnectionGraph?, session: SessionId): Boolean? =
        (graph?.session?.server as? PaseoGateway)?.reopenNeedsNewChat(session)
}
