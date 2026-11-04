package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role

/** The last actual user prompt, with its measured server timestamp; no text or tool data crosses this facade. */
public data class PromptRunIdentity(val marker: String, val startedAtMs: Long)

/** One bounded read. Implementations must not resume, subscribe, hydrate a chat or start a polling watcher. */
public interface PromptRunIdentityGateway {
    public suspend fun lastPromptIdentity(session: SessionId): Outcome<PromptRunIdentity?>
}

/** Timeline records encoded as role=user are not turns (the same rule as attention's RunIdentity). */
public fun lastPromptIdentity(messages: Iterable<MessageWithParts>): PromptRunIdentity? {
    val last = messages.filter { it.info.role == Role.User && it.parts.none { part -> part.rawType.startsWith("v2:") } }
        .maxByOrNull { it.info.time?.createdMs ?: 0 }?.info ?: return null
    val started = last.time?.createdMs?.takeIf { it > 0 } ?: return null
    return last.id.takeIf { it.isNotBlank() && it.length <= 512 }?.let { PromptRunIdentity(it, started) }
}
