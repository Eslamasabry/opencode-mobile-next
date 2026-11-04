package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Device-local pin storage. Profile and location are captured by the row's server; successful
 * storage alone changes the visible pins. Unsupported hosts ([supported] false) do not offer a pin
 * action.
 */
public interface ConversationPins {
    public val supported: Boolean
        get() = true

    /** Emits after stored pins change outside this page (for example another surface). */
    public val changes: Flow<Unit>
        get() = emptyFlow()

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
    override val supported: Boolean = false

    override fun ids(server: ConversationServer): Set<SessionId> = emptySet()

    override suspend fun set(
        server: ConversationServer,
        id: SessionId,
        pinned: Boolean,
    ): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("conversationPins")))
}

/**
 * Optional capability added without changing the existing conversation actions contract (ANDR-1:
 * the row's visible pin action).
 */
public interface ChatsPinActions {
    public fun togglePin(key: String)
}
