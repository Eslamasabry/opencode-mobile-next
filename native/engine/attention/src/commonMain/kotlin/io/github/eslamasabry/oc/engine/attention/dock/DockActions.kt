package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * What the dock's sheet can do to a conversation, implemented in the app by delegating to the engines that already
 * do it (the home list's permission answer, the chat's stop). The dock adds no protocol call of its own.
 */
public interface DockActions {
    /** Answers the waiting permission [requestId] of [ref] once (allow) or rejects it. */
    public suspend fun answerPermission(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit>

    /** Stops the running turn of [ref]. */
    public suspend fun stop(ref: ConversationRef): Outcome<Unit>
}
