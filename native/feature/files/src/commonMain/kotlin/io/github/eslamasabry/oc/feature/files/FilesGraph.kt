package io.github.eslamasabry.oc.feature.files

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.LocatedVcsGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.input.Mentions
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileHandoff
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.PdfPages
import io.github.eslamasabry.oc.engine.files.ShareHost
import io.github.eslamasabry.oc.engine.files.WifiShare
import io.github.eslamasabry.oc.feature.files.presentation.ComposerMentions
import io.github.eslamasabry.oc.feature.files.presentation.ConversationFiles
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import io.github.eslamasabry.oc.feature.files.presentation.FilesExternalLinks
import io.github.eslamasabry.oc.feature.files.presentation.FilesReaderPreferences
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageAccess
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

/**
 * Files reads its conversation's gateway from that conversation's own graph (ADR §9.1), like the
 * chat does.
 */
@ContributesTo(ConnectionScope::class)
public interface FilesGraphAccessors {
    public val filesGateway: ServerGateway
    public val filesOperations: ServerOperationsGateway
}

/** Platform halves; an empty set hides the action (desktop has none of them today). */
@ContributesTo(AppScope::class)
public interface FilesPlatformBindings {
    @Multibinds(allowEmpty = true) public val shareHosts: Set<ShareHost>

    @Multibinds(allowEmpty = true) public val fileHandoffs: Set<FileHandoff>

    @Multibinds(allowEmpty = true) public val pdfPages: Set<PdfPages>

    @Multibinds(allowEmpty = true) public val fileSavers: Set<FileSaver>

    @Multibinds(allowEmpty = true) public val readerPreferences: Set<FilesReaderPreferences>

    @Multibinds(allowEmpty = true) public val fileExternalLinks: Set<FilesExternalLinks>

    @Multibinds(allowEmpty = true) public val fileStorageAccess: Set<FilesStorageAccess>
}

@BindingContainer
@ContributesTo(AppScope::class)
public object FilesProviders {
    /** One Wi-Fi share for the whole app: starting another stops the first. */
    @Provides
    @SingleIn(AppScope::class)
    public fun wifiShare(hosts: Set<ShareHost>, clock: Clock, scope: AppCoroutineScope): WifiShare {
        check(hosts.size <= 1) { "multiple Wi-Fi share hosts" }
        return WifiShare(hosts.singleOrNull(), clock, scope)
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class GraphFilesConnections(private val registry: ConnectionRegistry) : FilesConnections {
    private fun live(ref: ConversationRef): ServerGateway? =
        (registry.graphFor(ref) as? FilesGraphAccessors)?.filesGateway

    // Each screen reads its conversation's own folder (B-NAV-026), never the gateway's shared
    // location.
    override fun gateway(ref: ConversationRef): FileGateway? =
        live(ref)?.let { ConversationFiles(it, ref.sessionId) }

    override fun isCurrent(ref: ConversationRef, gateway: FileGateway): Boolean =
        (gateway as? ConversationFiles)?.belongsTo(live(ref)) == true

    override fun canBrowse(ref: ConversationRef): Boolean =
        live(ref)?.capabilities?.value?.fileBrowsing == true

    override fun canSearchText(ref: ConversationRef): Boolean =
        live(ref)?.capabilities?.value?.textSearch == true

    override fun canSearchSymbols(ref: ConversationRef): Boolean =
        live(ref)?.capabilities?.value?.workspaceSymbols == true

    override suspend fun symbols(
        ref: ConversationRef,
        query: String,
    ): Outcome<ImmutableList<WorkspaceSymbol>> {
        val graph =
            registry.graphFor(ref) as? FilesGraphAccessors ?: return unavailable("connection")
        val gateway = graph.filesGateway
        if (!gateway.capabilities.value.workspaceSymbols) return unavailable("workspaceSymbols")
        val conversation = gateway.session(ref.sessionId)
        if (conversation is Outcome.Failed) return conversation
        val session = (conversation as Outcome.Ok).value
        val target =
            ServerLocation(
                session.directory ?: gateway.location.value.directory,
                session.workspaceId?.raw,
            )
        // No unscoped operation may read another conversation's project after a shared location
        // switch.
        if (gateway.location.value != target) return unavailable("workspaceSymbols")
        val result = graph.filesOperations.findWorkspaceSymbols(query)
        if (registry.graphFor(ref) !== graph || gateway.location.value != target)
            return unavailable("workspaceSymbols")
        return result
    }

    override suspend fun changes(ref: ConversationRef): Outcome<ImmutableList<VersionControlFile>> {
        val graph =
            registry.graphFor(ref) as? FilesGraphAccessors ?: return unavailable("connection")
        val gateway = graph.filesGateway
        val session = gateway.session(ref.sessionId)
        if (session is Outcome.Failed) return session
        val conversation = (session as Outcome.Ok).value
        val target =
            ServerLocation(
                conversation.directory ?: gateway.location.value.directory,
                conversation.workspaceId?.raw,
            )
        val scoped = gateway as? LocatedVcsGateway
        val result =
            if (scoped != null) scoped.workingTreeAt(target)
            else {
                if (gateway.location.value != target) return unavailable("projectManagement")
                val statuses = graph.filesOperations.listFileStatuses()
                if (registry.graphFor(ref) !== graph || gateway.location.value != target)
                    return unavailable("projectManagement")
                return statuses
            }
        if (registry.graphFor(ref) !== graph) return unavailable("projectManagement")
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                Outcome.Ok(
                    result.value
                        .map {
                            VersionControlFile(
                                it.file,
                                it.status ?: "modified",
                                it.additions ?: 0,
                                it.deletions ?: 0,
                            )
                        }
                        .toImmutableList()
                )
        }
    }

    private fun unavailable(feature: String): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId(feature)))

    override suspend fun directory(ref: ConversationRef): String? {
        val gateway = live(ref) ?: return null
        return ConversationFiles(gateway, ref.sessionId).directory()
    }
}

/**
 * "Ask about this file" from a conversation (files_viewer.dart `_askAbout`): appends `@path ` to
 * that conversation's draft and, when the project folder is known, adds the file mention the
 * composer sends as a file part (Mentions.projectFile, B-CHAT-087). The draft owner persists it;
 * the chat's composer restores it on return.
 */
@Inject
@ContributesBinding(AppScope::class)
public class DraftComposerMentions(private val draftStores: Set<ConversationDrafts>) :
    ComposerMentions {
    override suspend fun mention(
        ref: ConversationRef,
        path: String,
        directory: String?,
    ): Outcome<Unit> = mentionAt(ref, path, directory, null)

    override suspend fun mentionAt(
        ref: ConversationRef,
        path: String,
        directory: String?,
        line: Int?,
    ): Outcome<Unit> {
        val drafts =
            draftStores.singleOrNull()
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))
        val session = drafts.open(ref)
        val ready = session.state.first { it.ready || it.failure != null }
        if (!ready.ready) return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
        val mentionPath = path + (line?.takeIf { it > 0 }?.let { ":$it" } ?: "")
        val mention = "@$mentionPath "
        val text = ready.text
        val next =
            when {
                text.contains(mention) -> text
                text.isEmpty() || text.endsWith(' ') || text.endsWith('\n') -> text + mention
                else -> "$text $mention"
            }
        session.changed(next)
        if (directory != null && ready.attachments.none { it.mentionPath == mentionPath }) {
            val added =
                session.replaceAttachments(
                    ready.attachments +
                        Mentions.projectFile(directory, path).copy(mentionPath = mentionPath)
                )
            if (added is Outcome.Failed) return added
        }
        return session.flush()
    }

    override fun canAttach(): Boolean = draftStores.size == 1

    override suspend fun attach(ref: ConversationRef, file: FileData): Outcome<Unit> {
        if (file.bytes.size.toLong() > AttachmentVault.MAX_DRAFT_BYTES)
            return Outcome.Failed(Failure.StorageRefused())
        val drafts =
            draftStores.singleOrNull()
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))
        val session = drafts.open(ref)
        val ready = session.state.first { it.ready || it.failure != null }
        if (!ready.ready) return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
        val mime = file.mime ?: "application/octet-stream"
        val attachment =
            PromptAttachment(mime, file.name, "data:$mime;base64," + Base64.encode(file.bytes))
        if (
            ready.attachments.any { it.filename == attachment.filename && it.url == attachment.url }
        )
            return Outcome.Ok(Unit)
        val result = session.replaceAttachments(ready.attachments + attachment)
        if (result is Outcome.Failed) return result
        return session.flush()
    }
}
