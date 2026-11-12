package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
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

    /**
     * Renames the conversation itself, the call its own "Rename conversation" makes, so the home, the chat header and
     * the dock show one name. The app also refreshes the home list after it. Unavailable where there is no such path.
     */
    public suspend fun renameConversation(ref: ConversationRef, title: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("dockRename")))
}
