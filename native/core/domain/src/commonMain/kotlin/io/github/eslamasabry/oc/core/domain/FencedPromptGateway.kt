package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission

/**
 * A final pre-dispatch fence on backends that have no client message-id contract. This does not claim idempotency,
 * correlation or replay. A changed target sends nothing; an unconfirmed mutation remains uncertain.
 */
public interface FencedPromptGateway {
    public suspend fun promptFenced(session: SessionId, prompt: OutgoingPrompt, beforeSend: () -> Boolean): Outcome<PromptAdmission>
}
