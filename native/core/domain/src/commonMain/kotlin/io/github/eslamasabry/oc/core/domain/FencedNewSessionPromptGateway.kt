package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission

/**
 * The first prompt of a newly-created session, including backends whose create step is a local draft. The fence
 * runs immediately before the transport mutation, after asynchronous preparation. It never authorizes a retry.
 */
public interface FencedNewSessionPromptGateway : CorrelatedPromptGateway {
    public suspend fun promptNewSessionWithMessageId(
        session: SessionId,
        messageId: MessageId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission>
}
