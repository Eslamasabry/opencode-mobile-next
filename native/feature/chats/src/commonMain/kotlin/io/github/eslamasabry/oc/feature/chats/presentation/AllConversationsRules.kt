package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedFolders
import kotlinx.collections.immutable.toImmutableList

/** What one server has answered so far: its loaded pages, the next cursor and its last failure. */
public data class SourcePages(
    val sessions: List<Session> = emptyList(),
    val cursor: String? = null,
    val loaded: Boolean = false,
    val failure: Failure? = null,
    val moreFailure: Failure? = null,
    val running: Set<SessionId> = emptySet(),
    val pinned: Set<SessionId> = emptySet(),
    val contextUsage: Map<SessionId, Double> = emptyMap(),
    val preview: Boolean = false,
)

/**
 * A row deleted inside the undo window, with what its DELETE needs: kept apart from the pages,
 * which a reload clears.
 */
public data class PendingDelete(val key: String, val session: Session)

/** Everything the page knows; [AllConversationsRules.build] turns it into what it shows. */
public data class AllModel(
    val servers: List<ServerName> = emptyList(),
    val known: Boolean = false,
    val sources: Map<ProfileId, SourcePages> = emptyMap(),
    val query: String = "",
    val running: Boolean = false,
    val archived: Boolean = false,
    val project: String? = null,
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    /** Rows deleted inside the undo window: hidden, not yet sent. */
    val pending: List<PendingDelete> = emptyList(),
    /**
     * Rows whose delete was sent and has not answered yet: still hidden; a refusal brings them
     * back.
     */
    val hiddenSent: Set<String> = emptySet(),
    val undoId: Int = 0,
    val titles: Map<String, String> = emptyMap(),
    val archivedNow: Set<String> = emptySet(),
    val loadingMore: Boolean = false,
    /** Older pages read so far, across servers. */
    val loadedPages: Int = 0,
    val problem: AllProblem? = null,
    val renaming: RenameUi? = null,
    val archiveCapable: Set<ProfileId> = emptySet(),
)

/** A server's id and the name the person gave it. */
public data class ServerName(val profile: ProfileId, val name: String)

/** Pure rules of All conversations (07 §5 B12, Dart GlobalSessionsScreen). */
public object AllConversationsRules {
    /** Rows per server page, as Flutter's All conversations asks (`limit: 50`). */
    public const val PAGE: Int = 50

    /** Recent-project chips the filter row offers. */
    public const val PROJECT_CHIPS: Int = 6

    public fun key(profile: ProfileId, session: SessionId): String = "${profile.raw}/${session.raw}"

    /** Busy and retrying sessions are running (the server's `/session/active` words). */
    public fun isRunning(status: SessionStatus): Boolean =
        status == SessionStatus.Busy || status == SessionStatus.Retry

    public fun updatedAt(session: Session): Long =
        session.time?.updatedMs ?: session.time?.createdMs ?: 0L

    /**
     * The project words of a row: the folder name, or "Home"/"/" for folders that are not projects.
     */
    public fun projectName(directory: String?): String? =
        when {
            directory.isNullOrBlank() -> null
            FeedFolders.isOtherFolder(directory) -> FeedFolders.otherFolderLabel(directory)
            else -> FeedFolders.folderName(directory)
        }

    /**
     * Appends an older page, dropping any session already shown (a page boundary can repeat a row).
     */
    public fun append(old: List<Session>, page: List<Session>): List<Session> {
        val seen = old.mapTo(HashSet()) { it.id }
        return old + page.filter { seen.add(it.id) }
    }

    private data class Entry(val profile: ProfileId, val session: Session, val running: Boolean)

    public fun build(model: AllModel): AllConversationsUi {
        // No server: the route's filters still show as set, so they hold when a server comes back.
        if (model.known && model.servers.isEmpty()) {
            return AllConversationsUi(
                phase = AllPhase.NoServer,
                query = model.query,
                running = model.running,
                archived = model.archived,
            )
        }
        val names = model.servers.associate { it.profile to it.name }
        val many = model.servers.size > 1
        val hidden = model.pending.map { it.key }.toSet() + model.hiddenSent
        // Subagent sessions belong to their parent's page, never this list (Dart rootsOnly: true);
        // the AI Team's own
        // conversations belong to the AI Team screen (Dart isAiTeamConversation).
        val entries =
            model.servers.flatMap { server ->
                val source = model.sources[server.profile] ?: return@flatMap emptyList()
                source.sessions
                    .filter { it.parentId == null }
                    .filterNot { TeamFolders.isTeamFolder(it.directory) }
                    .filterNot { key(server.profile, it.id) in hidden }
                    .map { Entry(server.profile, it, it.id in source.running) }
            }
        fun archivedOf(e: Entry): Boolean =
            e.session.time?.archivedMs != null || key(e.profile, e.session.id) in model.archivedNow
        fun titleOf(e: Entry): String? =
            model.titles[key(e.profile, e.session.id)] ?: e.session.title
        val sorted =
            entries.sortedWith(
                compareByDescending<Entry> {
                        it.session.id in model.sources[it.profile]?.pinned.orEmpty()
                    }
                    .thenByDescending { updatedAt(it.session) }
            )
        val scoped = sorted.filter { archivedOf(it) == model.archived }
        val query = model.query.trim().lowercase()
        val shown = scoped.filter { e ->
            (!model.running || e.running) &&
                (model.project == null || e.session.directory == model.project) &&
                (query.isEmpty() ||
                    SessionTitles.text(titleOf(e)).lowercase().contains(query) ||
                    (projectName(e.session.directory)?.lowercase()?.contains(query) == true))
        }
        val rows = shown.map { e ->
            val k = key(e.profile, e.session.id)
            AllRowUi(
                key = k,
                ref = ConversationRef(e.profile, e.session.id),
                title = SessionTitles.present(titleOf(e)),
                project = projectName(e.session.directory),
                server = if (many) names[e.profile] else null,
                running = e.running,
                archived = archivedOf(e),
                selected = model.selecting && k in model.selected,
                canArchive =
                    e.profile in model.archiveCapable &&
                        !archivedOf(e) &&
                        model.sources[e.profile]?.preview != true,
                readOnly = model.sources[e.profile]?.preview == true,
                pinned = e.session.id in model.sources[e.profile]?.pinned.orEmpty(),
                contextPercent =
                    model.sources[e.profile]?.contextUsage?.get(e.session.id)?.let {
                        (it * 100).toInt()
                    },
            )
        }
        val chipDirs =
            scoped
                .mapNotNull { it.session.directory }
                .filterNot { FeedFolders.isOtherFolder(it) }
                .distinct()
                .take(PROJECT_CHIPS)
                .let {
                    if (model.project != null && model.project !in it) it + model.project else it
                }
        val sources = model.servers.mapNotNull { model.sources[it.profile] }
        val allLoaded =
            model.known && sources.size == model.servers.size && sources.all { it.loaded }
        val allFailed = allLoaded && sources.all { it.failure != null && it.sessions.isEmpty() }
        val phase =
            when {
                !allLoaded && rows.isEmpty() -> AllPhase.Opening
                allFailed -> AllPhase.Failed
                scoped.isEmpty() &&
                    !model.archived &&
                    query.isEmpty() &&
                    !model.running &&
                    model.project == null -> AllPhase.Empty
                rows.isEmpty() -> AllPhase.NoMatch
                else -> AllPhase.List
            }
        val selectedKeys = model.selected.intersect(rows.map { it.key }.toSet())
        return AllConversationsUi(
            phase = phase,
            rows = rows.toImmutableList(),
            query = model.query,
            projects =
                chipDirs
                    .map { ProjectChipUi(it, FeedFolders.folderName(it), it == model.project) }
                    .toImmutableList(),
            running = model.running,
            archived = model.archived,
            hasArchived = model.archived || sorted.any { archivedOf(it) },
            selecting = model.selecting,
            selectedCount = if (model.selecting) selectedKeys.size else 0,
            archiveAvailable =
                selectedKeys.isNotEmpty() &&
                    selectedKeys.all { profileOf(it) in model.archiveCapable },
            canLoadMore = allLoaded && !allFailed && sources.any { it.cursor != null },
            loadingMore = model.loadingMore,
            loadedPages = model.loadedPages,
            loadMoreFailure = sources.firstNotNullOfOrNull { it.moreFailure },
            failure = if (allFailed) sources.firstNotNullOfOrNull { it.failure } else null,
            incomplete =
                model.servers
                    .filter { s ->
                        model.sources[s.profile]?.let { it.failure != null && !allFailed } == true
                    }
                    .map { it.name }
                    .toImmutableList(),
            problem = model.problem,
            renaming = model.renaming,
            undo =
                if (model.pending.isEmpty()) null
                else
                    DeleteUndoUi(
                        model.undoId,
                        model.pending.size,
                        model.pending.singleOrNull()?.let { p ->
                            SessionTitles.present(model.titles[p.key] ?: p.session.title)
                        },
                    ),
        )
    }

    /**
     * The server a key belongs to (keys are `<profile>/<session>`; session ids never hold a slash).
     */
    public fun profileOf(key: String): ProfileId = ProfileId(key.substringBeforeLast('/'))

    /** The session a key names, with its server. */
    public fun find(model: AllModel, key: String): Pair<ProfileId, Session>? {
        val profile = profileOf(key)
        if (model.sources[profile]?.preview == true) return null
        val id = key.substringAfterLast('/')
        val session =
            model.sources[profile]?.sessions?.firstOrNull { it.id.raw == id } ?: return null
        return profile to session
    }
}

/**
 * The AI Team's own folders (Dart `isAiTeamDirectory`, lib/domain/team_directories.dart): the
 * team's home `/root/aiteam` and below, anything inside a Gas City state folder `.gc`, and anything
 * inside `.oc/aiteam`. A person's project the team works on matches none of these. `.` is dropped
 * and `..` applied first.
 */
public object TeamFolders {
    private val home = listOf("root", "aiteam")

    public fun isTeamFolder(directory: String?): Boolean {
        val segments = segments(directory) ?: return false
        if (segments.isEmpty()) return false
        if (segments.size >= home.size && home.indices.all { segments[it] == home[it] }) return true
        return segments.indices.any { i ->
            segments[i] == ".gc" || (segments[i] == ".oc" && segments.getOrNull(i + 1) == "aiteam")
        }
    }

    private fun segments(directory: String?): List<String>? {
        val value = directory?.trim()?.replace('\\', '/')
        if (value.isNullOrEmpty()) return null
        val out = ArrayList<String>()
        value.split('/').forEach { part ->
            when (part) {
                "",
                "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out += part
            }
        }
        return out
    }
}
