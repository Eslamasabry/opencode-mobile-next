package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * Change review (unit P12-REVIEW, Flutter `staged_revert_screen.dart`, `review_workspace.dart`, `chat_files.dart`).
 * Owned by `:feature:review`. Routes carry ids only; every page reads the server again through `:engine:review`.
 */
@Serializable
public sealed interface ReviewRoute : AppRoute {
    /** The files one turn changed ([messageId] is any message of that turn), or the whole conversation (null). */
    @Serializable public data class Changes(val ref: ConversationRef, val messageId: String? = null) : ReviewRoute

    /** One file's diff from [Changes] with the same [messageId]; [staged] reads it from the staged undo instead. */
    @Serializable
    public data class File(
        val ref: ConversationRef,
        val path: String,
        val messageId: String? = null,
        val staged: Boolean = false,
    ) : ReviewRoute

    /** "Undo from this prompt?" for the prompt [messageId]. */
    @Serializable
    public data class UndoFromHere(val ref: ConversationRef, val messageId: String) : ReviewRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }

    /** "Review the undo": the conversation's staged undo, kept or put back. */
    @Serializable public data class Staged(val ref: ConversationRef) : ReviewRoute
}
