package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** What a project's page drew: its conversations and the ones in other projects. */
public data class KeptProjectPage(
    val conversations: List<ProjectChatUi>,
    val elsewhere: List<ProjectChatUi>,
)

/**
 * Cache-first (owner rule, 2026-11-09): the projects pages draw their real rows at once from what they last read, and
 * the live read replaces them in place. There is no loading or offline variant of these pages; a failed read with
 * kept rows keeps them and says so in the page's notice. The app binds this to its last-known store (per profile and
 * server, size-capped, cleared with the profile); without a binding every page opens cold as before.
 *
 * `peek*` answers from memory with no I/O, for the first frame; `read*` goes to storage when memory has nothing.
 */
public interface ProjectsLastKnown {
    /** The server's project rows (the picker's "Projects on ..." group). */
    public fun peekProjects(profile: ProfileId): List<FolderRowUi>?

    public suspend fun readProjects(profile: ProfileId): List<FolderRowUi>?

    public suspend fun keepProjects(profile: ProfileId, rows: List<FolderRowUi>)

    /** One project page's rows. */
    public fun peekPage(profile: ProfileId, directory: String): KeptProjectPage?

    public suspend fun readPage(profile: ProfileId, directory: String): KeptProjectPage?

    public suspend fun keepPage(profile: ProfileId, directory: String, page: KeptProjectPage)

    /** Open a project's listing: [path] is null for the server's projects, else the folder whose folders are listed. */
    public fun peekFolders(profile: ProfileId, path: String?): List<OpenFolderUi>?

    public suspend fun readFolders(profile: ProfileId, path: String?): List<OpenFolderUi>?

    public suspend fun keepFolders(profile: ProfileId, path: String?, folders: List<OpenFolderUi>)
}

/** No store: every page opens cold. The app replaces this. */
@ContributesBinding(AppScope::class)
@Inject
public class NoProjectsLastKnown : ProjectsLastKnown {
    override fun peekProjects(profile: ProfileId): List<FolderRowUi>? = null

    override suspend fun readProjects(profile: ProfileId): List<FolderRowUi>? = null

    override suspend fun keepProjects(profile: ProfileId, rows: List<FolderRowUi>) {}

    override fun peekPage(profile: ProfileId, directory: String): KeptProjectPage? = null

    override suspend fun readPage(profile: ProfileId, directory: String): KeptProjectPage? = null

    override suspend fun keepPage(profile: ProfileId, directory: String, page: KeptProjectPage) {}

    override fun peekFolders(profile: ProfileId, path: String?): List<OpenFolderUi>? = null

    override suspend fun readFolders(profile: ProfileId, path: String?): List<OpenFolderUi>? = null

    override suspend fun keepFolders(profile: ProfileId, path: String?, folders: List<OpenFolderUi>) {}
}

/*
 * The shapes the app's store writes. They live here, next to the port, because only feature and engine modules carry
 * the serialization plugin. Each holds just the fields the real row draws; nothing in them is a credential.
 */

@kotlinx.serialization.Serializable
public data class KeptFolderRow(
    val directory: String,
    val name: String,
    val conversations: Int = 0,
    val running: Int = 0,
    val isGit: Boolean? = null,
    val id: String? = null,
    val kind: String? = null,
    /** [FolderRowUi.opensOn]: a folder only This phone's agents work in opens on their connection. */
    val opensOn: String? = null,
)

@kotlinx.serialization.Serializable
public data class KeptProjectList(val rows: List<KeptFolderRow>) {
    public fun toRows(): List<FolderRowUi> =
        rows.map {
            FolderRowUi(
                it.directory,
                it.name,
                it.conversations,
                it.running,
                it.isGit,
                it.id,
                it.kind?.let { k ->
                    io.github.eslamasabry.oc.engine.files.PhoneProjectKind.entries.firstOrNull { e -> e.name == k }
                },
                opensOn = it.opensOn?.let { p -> io.github.eslamasabry.oc.core.common.ids.ProfileId(p) },
            )
        }

    public companion object {
        public fun of(rows: List<FolderRowUi>): KeptProjectList =
            KeptProjectList(
                rows.map {
                    KeptFolderRow(
                        it.directory,
                        it.name,
                        it.conversations,
                        it.running,
                        it.isGit,
                        it.id,
                        it.kind?.name,
                        it.opensOn?.raw,
                    )
                }
            )
    }
}

@kotlinx.serialization.Serializable
public data class KeptChatRow(
    val session: String,
    /** "text", "new" or "untitled". */
    val titleKind: String,
    val title: String = "",
    val project: String?,
    val running: Boolean,
)

@kotlinx.serialization.Serializable
public data class KeptPageRows(val conversations: List<KeptChatRow>, val elsewhere: List<KeptChatRow>) {
    public fun toPage(profile: ProfileId): KeptProjectPage {
        fun row(r: KeptChatRow) =
            ProjectChatUi(
                io.github.eslamasabry.oc.core.common.ids.ConversationRef(
                    profile,
                    io.github.eslamasabry.oc.core.common.ids.SessionId(r.session),
                ),
                when (r.titleKind) {
                    "text" -> ChatTitle.Text(r.title)
                    "new" -> ChatTitle.New
                    else -> ChatTitle.Untitled
                },
                r.project,
                r.running,
            )
        return KeptProjectPage(conversations.map(::row), elsewhere.map(::row))
    }

    public companion object {
        public fun of(page: KeptProjectPage): KeptPageRows {
            fun row(c: ProjectChatUi) =
                KeptChatRow(
                    c.ref.sessionId.raw,
                    when (c.title) {
                        is ChatTitle.Text -> "text"
                        ChatTitle.New -> "new"
                        ChatTitle.Untitled -> "untitled"
                    },
                    (c.title as? ChatTitle.Text)?.text.orEmpty(),
                    c.project,
                    c.running,
                )
            return KeptPageRows(page.conversations.map(::row), page.elsewhere.map(::row))
        }
    }
}

@kotlinx.serialization.Serializable
public data class KeptOpenFolder(val name: String, val path: String, val git: Boolean = false, val known: Boolean = false)

@kotlinx.serialization.Serializable
public data class KeptFolderList(val folders: List<KeptOpenFolder>) {
    public fun toFolders(): List<OpenFolderUi> = folders.map { OpenFolderUi(it.name, it.path, it.git, it.known) }

    public companion object {
        public fun of(folders: List<OpenFolderUi>): KeptFolderList =
            KeptFolderList(folders.map { KeptOpenFolder(it.name, it.path, it.git, it.known) })
    }
}
