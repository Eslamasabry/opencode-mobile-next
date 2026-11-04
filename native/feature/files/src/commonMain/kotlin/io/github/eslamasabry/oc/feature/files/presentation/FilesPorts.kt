package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.FileGateway

/**
 * What Files needs from the conversation's connection. Bound to the live graph in `FilesGraph.kt`.
 */
public interface FilesConnections {
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

/**
 * Persisted reader choices. Platform bindings wait for storage admission before returning success.
 */
public interface ReaderSettings {
    public suspend fun read(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId
    ): io.github.eslamasabry.oc.engine.settings.reader.ReaderPreferences?

    public suspend fun update(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        sourceFirst: Boolean? = null,
        wrapCode: Boolean? = null,
    ): Boolean
}
