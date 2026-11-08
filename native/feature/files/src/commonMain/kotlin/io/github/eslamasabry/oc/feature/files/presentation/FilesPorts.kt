package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.settings.reader.ReaderPreferencesStore
import kotlinx.collections.immutable.ImmutableList

/**
 * What Files needs from the conversation's connection. Bound to the live graph in `FilesGraph.kt`.
 */
public interface FilesConnections {
    /** Opaque identity of this exact live source; checked again after every permission wait. */
    public fun sourceIdentity(ref: ConversationRef): Any? = null

    /** The conversation's own gateway, or null while that server is not connected. */
    public fun gateway(ref: ConversationRef): FileGateway?

    /** Whether that server lets the app browse files (`ServerCapabilities.fileBrowsing`). */
    public fun isCurrent(ref: ConversationRef, gateway: FileGateway): Boolean =
        this.gateway(ref) != null

    public fun canBrowse(ref: ConversationRef): Boolean

    public fun canTerminal(ref: ConversationRef): Boolean = false

    /** The project tools that server offers, for the ⋯ menu (Flutter `ProjectHub.toolsFor`). */
    public fun tools(ref: ConversationRef): FilesProjectTools = FilesProjectTools.None

    /**
     * The conversation's project folder on the server (for the file mention's `file:` URL); null
     * when unknown.
     */
    public suspend fun directory(ref: ConversationRef): String?

    public fun canSearchText(ref: ConversationRef): Boolean = false

    public fun canSearchSymbols(ref: ConversationRef): Boolean = false

    public suspend fun symbols(
        ref: ConversationRef,
        query: String,
    ): Outcome<ImmutableList<WorkspaceSymbol>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("workspaceSymbols")))

    public suspend fun changes(ref: ConversationRef): Outcome<ImmutableList<VersionControlFile>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectManagement")))

    /**
     * One project's files with no conversation (the project page's Files row): that server's live
     * connection, scoped to [directory]. Null while the server is not connected.
     */
    public fun project(profile: ProfileId, directory: String): ProjectFilesSource? = null
}

/**
 * A project folder on one live connection. [identity] is that exact live connection; every read is
 * checked against it again, so a reconnect never shows another source's tree.
 */
public class ProjectFilesSource(
    public val identity: Any,
    public val gateway: FileGateway,
    public val canBrowse: Boolean,
    public val canTerminal: Boolean = false,
    public val tools: FilesProjectTools = FilesProjectTools.None,
    public val canSearchText: Boolean = false,
    public val canSearchSymbols: Boolean = false,
    public val symbols: suspend (String) -> Outcome<ImmutableList<WorkspaceSymbol>> = {
        Outcome.Failed(Failure.Unavailable(FeatureId("workspaceSymbols")))
    },
    public val changes: suspend () -> Outcome<ImmutableList<VersionControlFile>> = {
        Outcome.Failed(Failure.Unavailable(FeatureId("projectManagement")))
    },
)

/** Puts a project file into the conversation's draft: `@path ` in the text and its file mention. */
public fun interface ComposerMentions {
    public suspend fun mention(
        ref: ConversationRef,
        path: String,
        directory: String?,
    ): Outcome<Unit>

    public suspend fun mentionAt(
        ref: ConversationRef,
        path: String,
        directory: String?,
        line: Int?,
    ): Outcome<Unit> = mention(ref, path, directory)

    public fun canAttach(): Boolean = false

    public suspend fun attach(ref: ConversationRef, file: FileData): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))
}

/** Platform graph owns storage; the feature sees only the existing profile-scoped reader engine. */
public fun interface FilesReaderPreferences {
    public fun open(profile: ProfileId): ReaderPreferencesStore
}

/** Policy-approved external links; a false result is shown as a plain refusal. */
public interface FilesExternalLinks {
    public fun check(url: String): FilesLinkTicket

    public fun open(token: Any): Boolean
}

public sealed interface FilesLinkTicket {
    public data class Open(val token: Any) : FilesLinkTicket

    public data class AskFirst(val host: String, val token: Any) : FilesLinkTicket

    public data object Refused : FilesLinkTicket
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

/**
 * The project's other pages the Files ⋯ menu opens (Flutter project_hub_screen.dart `_toolsMenu`), each gated on the
 * server's capabilities as Flutter's `ProjectHub.toolsFor` gates it.
 */
public data class FilesProjectTools(
    val health: Boolean = false,
    val worktrees: Boolean = false,
    val services: Boolean = false,
    val switchProject: Boolean = false,
) {
    public companion object {
        public val None: FilesProjectTools = FilesProjectTools()

        public fun of(
            projectManagement: Boolean,
            worktreeBrowsing: Boolean,
            developmentServices: Boolean,
        ): FilesProjectTools =
            FilesProjectTools(
                health = projectManagement,
                worktrees = projectManagement || worktreeBrowsing,
                services = developmentServices,
                switchProject = projectManagement,
            )
    }
}
