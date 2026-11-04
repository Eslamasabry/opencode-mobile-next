package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * Profile and location captured by the row's server; successful storage alone changes the visible
 * pins.
 */
public interface ConversationPins {
    public fun ids(server: ConversationServer): Set<SessionId>

    public suspend fun set(
        server: ConversationServer,
        id: SessionId,
        pinned: Boolean,
    ): Outcome<Unit>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoConversationPins : ConversationPins {
    override fun ids(server: ConversationServer): Set<SessionId> = emptySet()

    override suspend fun set(
        server: ConversationServer,
        id: SessionId,
        pinned: Boolean,
    ): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("conversationPins")))
}
