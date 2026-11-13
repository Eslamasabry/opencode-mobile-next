package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * What the list's acts need from the app, besides opening ([io.github.eslamasabry.oc.ui.routes.AppNavigator.switchTo]),
 * answering (the shared [io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers]) and stopping ([HeldStops]).
 * The app binds it over the dock controller (`DockController`: one pin store, one rename, hide and restore), the
 * new-conversation and All-conversations routes, and the Settings choice.
 */
public interface LampPort {
    public fun pin(ref: ConversationRef)

    public fun unpin(ref: ConversationRef)

    /** Moves pinned [from] to where pinned [to] is. */
    public fun reorder(from: ConversationRef, to: ConversationRef)

    /** Renames the conversation itself (the home's rename); [onFailed] runs when the server did not take it. */
    public fun rename(ref: ConversationRef, name: String, onFailed: () -> Unit)

    /** Hides it from this list until it needs the person again or [restore]. */
    public fun hide(ref: ConversationRef)

    public fun restore(ref: ConversationRef)

    public fun newConversation()

    public fun allConversations()

    /** Saves the place per device. */
    public fun place(place: LampPlace)

    /** Sets the Settings choice to Hidden. */
    public fun turnOff()
}
