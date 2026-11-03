package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One conversation's project files (B-NAV-026). Every conversation of a server shares one gateway, so its shared
 * location is not this conversation's folder: the folder comes from the conversation itself (read once), and every
 * request carries it ([LocatedFileGateway]). A failed read of the conversation fails the request; it never falls
 * back to another project's tree.
 */
public class ConversationFiles(private val gateway: ServerGateway, private val session: SessionId) : FileGateway {
    private val lock = Mutex()
    private var resolved: Pair<FileGateway, String?>? = null

    private suspend fun target(): Outcome<Pair<FileGateway, String?>> =
        lock.withLock {
            resolved?.let { return@withLock Outcome.Ok(it) }
            val read = gateway.session(session)
            if (read is Outcome.Failed) return@withLock read
            val conversation = (read as Outcome.Ok).value
            val directory = conversation.directory?.takeIf { it.isNotEmpty() }
            val located = gateway as? LocatedFileGateway
            val files =
                if (directory != null && located != null) {
                    located.filesAt(ServerLocation(directory, conversation.workspaceId?.raw))
                } else {
                    gateway
                }
            val pair = files to (directory ?: gateway.location.value.directory)
            resolved = pair
            Outcome.Ok(pair)
        }

    /** The conversation's own folder (for the file mention), or the gateway's when the conversation has none. */
    public suspend fun directory(): String? = (target() as? Outcome.Ok)?.value?.second

    private suspend inline fun <T> on(block: (FileGateway) -> Outcome<T>): Outcome<T> =
        when (val t = target()) {
            is Outcome.Failed -> t
            is Outcome.Ok -> block(t.value.first)
        }

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> = on { it.listFiles(path) }

    override suspend fun fileContent(path: String): Outcome<FileContent> = on { it.fileContent(path) }

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> = on { it.findFile(query) }

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> = on { it.findText(pattern) }
}
