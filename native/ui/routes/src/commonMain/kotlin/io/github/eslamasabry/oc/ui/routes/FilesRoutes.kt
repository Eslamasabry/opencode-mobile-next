package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * Project Files opened for a conversation (07 §5 D02/D03, P12-FILES): the conversation's own server and project.
 * Folders are routes, so predictive back walks up one level at a time. Owned by `:feature:files`.
 */
@Serializable
public sealed interface FilesRoute : AppRoute {
    public val from: ConversationRef

    /** One folder of the project; "" is the project root. */
    @Serializable public data class Folder(override val from: ConversationRef, val path: String = "") : FilesRoute

    /** One file in the viewer; [line] opens the code view there (a path chip with `:42`). */
    @Serializable
    public data class File(override val from: ConversationRef, val path: String, val line: Int? = null) : FilesRoute
}
