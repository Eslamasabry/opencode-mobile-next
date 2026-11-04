package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Device-local pin storage. Unsupported hosts do not offer a pin action. */
public interface ConversationPins {
    public val supported: Boolean
    public val changes: Flow<Unit>

    public fun pinned(ref: ConversationRef, directory: String?, workspace: String?): Boolean

    public suspend fun set(
        ref: ConversationRef,
        directory: String?,
        workspace: String?,
        pinned: Boolean,
    ): Outcome<Unit>
}

@ContributesBinding(AppScope::class)
@Inject
public class UnsupportedConversationPins : ConversationPins {
    override val supported: Boolean = false
    override val changes: Flow<Unit> = emptyFlow()

    override fun pinned(ref: ConversationRef, directory: String?, workspace: String?): Boolean =
        false

    override suspend fun set(
        ref: ConversationRef,
        directory: String?,
        workspace: String?,
        pinned: Boolean,
    ): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("conversation.pins")))
}

/** Optional capability added without changing the existing conversation actions contract. */
public interface ChatsPinActions {
    public fun togglePin(key: String)
}
