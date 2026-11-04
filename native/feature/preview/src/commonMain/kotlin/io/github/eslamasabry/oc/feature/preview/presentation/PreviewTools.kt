package io.github.eslamasabry.oc.feature.preview.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/** One explicit preview request uses the captured conversation's own durable send path. */
public enum class PreviewSent {
    Now,
    JoinsTurn,
    AfterReply,
    RestartsTurn,
}

public interface PreviewTools {
    public fun canAsk(ref: ConversationRef): Boolean

    public fun canReadLogs(ref: ConversationRef?): Boolean

    public fun delivery(ref: ConversationRef): PreviewSent = PreviewSent.Now

    public suspend fun send(
        ref: ConversationRef,
        text: String,
        allowRestart: Boolean,
    ): Outcome<PreviewSent>

    public suspend fun readLogs(ref: ConversationRef?, port: Int): Outcome<String>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoPreviewTools : PreviewTools {
    override fun canAsk(ref: ConversationRef): Boolean = false

    override fun canReadLogs(ref: ConversationRef?): Boolean = false

    override suspend fun send(
        ref: ConversationRef,
        text: String,
        allowRestart: Boolean,
    ): Outcome<PreviewSent> = Outcome.Failed(Failure.Unavailable(FeatureId("previewConversation")))

    override suspend fun readLogs(ref: ConversationRef?, port: Int): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("devServerLog")))
}
