package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session

/** Labels only, never status, capabilities, authorization or actionable session truth. */
public interface ConversationPreviews {
    public fun scope(server: ConversationServer): String?

    public fun read(server: ConversationServer): List<Session>

    public suspend fun save(
        server: ConversationServer,
        scope: String,
        sessions: List<Session>,
    ): Outcome<Unit>
}

/**
 * The desktop gallery has no owner data; Android replaces this with its adopted preferences store.
 */
@ContributesBinding(AppScope::class)
@Inject
public class NoConversationPreviews : ConversationPreviews {
    override fun scope(server: ConversationServer): String? = null

    override fun read(server: ConversationServer): List<Session> = emptyList()

    override suspend fun save(
        server: ConversationServer,
        scope: String,
        sessions: List<Session>,
    ): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("conversationPreview")))
}
