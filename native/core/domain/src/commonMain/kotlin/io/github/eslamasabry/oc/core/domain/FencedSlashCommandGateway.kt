package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * A user-requested slash command whose exact destination is checked again after asynchronous
 * permission preparation and immediately before dispatch. A false fence sends no command and never
 * falls back to a different session, queues, or retries.
 */
public interface FencedSlashCommandGateway {
    public suspend fun slashCommandFenced(
        session: SessionId,
        command: String,
        args: String,
        beforeSend: () -> Boolean,
    ): Outcome<Unit>
}
