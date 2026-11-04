package io.github.eslamasabry.oc.feature.voice.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceConversationSession

/**
 * A retained view of the same world already used by the composer; no second transport or send
 * pipeline.
 */
public class VoiceConversationHandle(
    public val session: VoiceConversationSession,
    public val release: () -> Unit,
)

public interface VoiceConversations {
    public fun open(ref: ConversationRef): VoiceConversationHandle?
}

/** Desktop gallery has no connected conversation, so the conversation control is absent. */
@Inject
@ContributesBinding(AppScope::class)
public class NoVoiceConversations : VoiceConversations {
    override fun open(ref: ConversationRef): VoiceConversationHandle? = null
}

/** The one app-wide teaching controller, read only after the adopted preferences are warm. */
public interface VoiceTeachingHub {
    public suspend fun controller():
        io.github.eslamasabry.oc.engine.settings.teaching.TipController?
}

@Inject
@ContributesBinding(AppScope::class)
public class NoVoiceTeachingHub : VoiceTeachingHub {
    override suspend fun controller():
        io.github.eslamasabry.oc.engine.settings.teaching.TipController? = null
}
