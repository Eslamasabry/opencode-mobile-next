package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.ui.routes.FilesPlace
import kotlinx.collections.immutable.ImmutableList

/**
 * Files for one [FilesPlace]: a conversation's folder through [FilesConnections]' conversation
 * reads, or a project's folder through [FilesConnections.project]. Acts that write into a
 * conversation (ask, attach, reference, review) exist only when [conversation] is not null.
 */
internal class PlaceFiles(val place: FilesPlace, private val connections: FilesConnections) {
    val conversation: ConversationRef? = (place as? FilesPlace.Conversation)?.ref

    private val project = place as? FilesPlace.Project

    /** The project source handed out last, so [isCurrent] can tell a reconnect from the same one. */
    private var handed: ProjectFilesSource? = null

    private fun source(): ProjectFilesSource? =
        project?.let { connections.project(it.profile, it.directory) }

    fun identity(): Any? =
        conversation?.let { connections.sourceIdentity(it) } ?: source()?.identity

    fun gateway(): FileGateway? {
        conversation?.let {
            return connections.gateway(it)
        }
        val next = source() ?: return null
        val held = handed
        if (held != null && held.identity === next.identity) return held.gateway
        handed = next
        return next.gateway
    }

    fun isCurrent(gateway: FileGateway): Boolean {
        conversation?.let {
            return connections.isCurrent(it, gateway)
        }
        val held = handed ?: return false
        return held.gateway === gateway && source()?.identity === held.identity
    }

    fun canBrowse(): Boolean =
        conversation?.let { connections.canBrowse(it) } ?: (source()?.canBrowse == true)

    fun canTerminal(): Boolean =
        conversation?.let { connections.canTerminal(it) } ?: (source()?.canTerminal == true)

    suspend fun runningTerminals(directory: String?): Int? =
        conversation?.let { connections.runningTerminals(it, directory) }
            ?: source()?.runningTerminals?.invoke()

    fun tools(): FilesProjectTools =
        conversation?.let { connections.tools(it) } ?: (source()?.tools ?: FilesProjectTools.None)

    fun canSearchText(): Boolean =
        conversation?.let { connections.canSearchText(it) } ?: (source()?.canSearchText == true)

    fun canSearchSymbols(): Boolean =
        conversation?.let { connections.canSearchSymbols(it) }
            ?: (source()?.canSearchSymbols == true)

    suspend fun directory(): String? =
        conversation?.let { connections.directory(it) } ?: project?.directory

    suspend fun symbols(query: String): Outcome<ImmutableList<WorkspaceSymbol>> {
        conversation?.let {
            return connections.symbols(it, query)
        }
        return source()?.symbols?.invoke(query) ?: unavailable("connection")
    }

    suspend fun changes(): Outcome<ImmutableList<VersionControlFile>> {
        conversation?.let {
            return connections.changes(it)
        }
        return source()?.changes?.invoke() ?: unavailable("connection")
    }

    private fun unavailable(feature: String): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId(feature)))
}
