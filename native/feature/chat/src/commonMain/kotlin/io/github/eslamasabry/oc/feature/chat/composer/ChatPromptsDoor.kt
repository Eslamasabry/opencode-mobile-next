package io.github.eslamasabry.oc.feature.chat.composer

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PromptsRoute

/**
 * P12-PROMPTS: the chat's way to Saved prompts (a visible top-bar action) and back. Text chosen there arrives through
 * [ComposerInbox] as the composer's [InsertRequest] for this conversation only.
 */
public class ChatPromptsDoor(
    private val ref: ConversationRef,
    private val inbox: ComposerInbox,
    private val navigator: AppNavigator,
) {
    private val target = ComposerInbox.target(ref.profileId.raw, ref.sessionId.raw)

    public fun open(): Unit = navigator.open(PromptsRoute.Saved(ref))

    /** Read while drawing: snapshot state, so a new offer redraws the composer. */
    public fun pending(): InsertRequest? = inbox.pendingFor(target)?.let { InsertRequest(it.id, it.text) }

    public fun done(id: Long): Unit = inbox.done(id)
}
