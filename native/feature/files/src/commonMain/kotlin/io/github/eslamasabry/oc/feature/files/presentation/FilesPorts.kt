package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.FileGateway

/**
 * What Files needs from the conversation's connection. Bound to the live graph in `FilesGraph.kt`.
 */
public interface FilesConnections {
    /** Opaque identity of this exact live source; checked again after every permission wait. */
    public fun sourceIdentity(ref: ConversationRef): Any? = null

    /** The conversation's own gateway, or null while that server is not connected. */
    public fun gateway(ref: ConversationRef): FileGateway?

    /** Whether that server lets the app browse files (`ServerCapabilities.fileBrowsing`). */
    public fun canBrowse(ref: ConversationRef): Boolean

    /**
     * The conversation's project folder on the server (for the file mention's `file:` URL); null
     * when unknown.
     */
    public suspend fun directory(ref: ConversationRef): String?
}

/** Puts a project file into the conversation's draft: `@path ` in the text and its file mention. */
public fun interface ComposerMentions {
    public suspend fun mention(
        ref: ConversationRef,
        path: String,
        directory: String?,
    ): Outcome<Unit>
}
