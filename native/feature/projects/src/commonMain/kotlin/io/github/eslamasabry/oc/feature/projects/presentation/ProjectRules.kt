package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.engine.agents.feed.FeedFolders

/** Why a typed folder path cannot be opened; the screen says it in plain words. */
public enum class PathProblem { NotAbsolute, BadParts, HomeOrRoot }

/** One project as the picker lists it. [isGit] null = the server did not say. */
public data class ProjectSummary(
    val directory: String,
    val name: String,
    val conversations: Int,
    val running: Int,
    val updatedMs: Long,
    val isGit: Boolean? = null,
)

/** Pure rules of the projects picker and the project page (Dart projects_screen, project_folder_actions). */
public object ProjectRules {
    /** "Opened before" shows the first few (Dart `_recentFolders`: `take(6)`). */
    public const val OPENED_BEFORE: Int = 6

    /** How many opened folders are kept per server. */
    public const val REMEMBERED: Int = 20

    /** "In other projects" lists at most this many. */
    public const val ELSEWHERE: Int = 5

    /** Pages read to find a server's projects from its conversations when it cannot list projects. */
    public const val PROJECT_PAGES: Int = 3

    /** The search field shows from this many projects (kit `SearchRules.SHOW_ABOVE`). */
    public const val SEARCH_FROM: Int = 8

    private val driveRoot = Regex("^[A-Za-z]:/")

    /** Trimmed, forward slashes, no trailing slash (except the root itself). */
    public fun normalize(path: String): String {
        var value = path.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith('/') && !value.endsWith(":/")) value = value.dropLast(1)
        return value
    }

    /** A typed folder must be a full path to a real project folder: never `..`, a line break, the root or a home. */
    public fun pathProblem(raw: String): PathProblem? {
        if (raw.contains('\n') || raw.contains('\r')) return PathProblem.BadParts
        val path = normalize(raw)
        if (path.isEmpty() || !(path.startsWith("/") || driveRoot.containsMatchIn(path))) return PathProblem.NotAbsolute
        if (path.split('/').any { it == ".." }) return PathProblem.BadParts
        if (path == "/" || FeedFolders.isProtectedHome(path)) return PathProblem.HomeOrRoot
        return null
    }

    public fun isRunning(status: SessionStatus): Boolean = status == SessionStatus.Busy || status == SessionStatus.Retry

    public fun updatedAt(session: Session): Long = session.time?.updatedMs ?: session.time?.createdMs ?: 0L

    /** B-NAV-011: newest first, ties keep the server's order, unknown dates (zero or below) last in the server's order. */
    public fun order(projects: List<WorkspaceProject>): List<WorkspaceProject> =
        projects
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<WorkspaceProject>>(
                    { if (it.value.updatedAtMs <= 0L) 1 else 0 },
                    { if (it.value.updatedAtMs <= 0L) 0L else -it.value.updatedAtMs },
                    { it.index },
                )
            )
            .map { it.value }

    /**
     * The AI Team's own folders, never the person's projects (Dart `isAiTeamDirectory`, team_directories.dart): the
     * team's home `/root/aiteam` and below, anything inside `.gc`, anything inside `.oc/aiteam`; `.` dropped, `..` applied.
     */
    public fun isTeamFolder(directory: String?): Boolean {
        val value = directory?.trim()?.replace('\\', '/')
        if (value.isNullOrEmpty()) return false
        val segments = ArrayList<String>()
        value.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += part
            }
        }
        if (segments.isEmpty()) return false
        if (segments.size >= 2 && segments[0] == "root" && segments[1] == "aiteam") return true
        return segments.indices.any { i ->
            segments[i] == ".gc" || (segments[i] == ".oc" && segments.getOrNull(i + 1) == "aiteam")
        }
    }

    /** A project's name: its own name, else its folder's last segment. */
    public fun name(directory: String, given: String? = null): String =
        given?.takeIf { it.isNotBlank() } ?: FeedFolders.folderName(directory)

    /** Root conversations of the person: subagents stay with their parent, the AI Team's work on its own screen. */
    private fun roots(sessions: List<Session>): List<Session> =
        sessions.filter { it.parentId == null && !isTeamFolder(it.directory) }

    private fun belongs(session: Session, directories: Set<String>): Boolean =
        session.directory?.let { normalize(it) in directories } == true

    /** The server's own project list (when it has one), with conversation counts from what was read. */
    public fun fromServer(
        projects: List<WorkspaceProject>,
        sessions: List<Session>,
        running: Set<SessionId>,
    ): List<ProjectSummary> =
        order(projects)
            .filterNot { FeedFolders.isOtherFolder(it.directory) || isTeamFolder(it.directory) }
            .map { p ->
                val dirs = (listOf(p.directory) + p.worktrees).map(::normalize).toSet()
                val mine = roots(sessions).filter { belongs(it, dirs) }
                ProjectSummary(
                    directory = normalize(p.directory),
                    name = name(p.directory, p.name),
                    conversations = mine.size,
                    running = mine.count { it.id in running },
                    updatedMs = p.updatedAtMs,
                    isGit = p.isGit,
                )
            }

    /** Servers that cannot list projects (OpenCode 2 today): the folders its conversations work in, newest first. */
    public fun fromSessions(sessions: List<Session>, running: Set<SessionId>): List<ProjectSummary> {
        val byDir = LinkedHashMap<String, MutableList<Session>>()
        roots(sessions)
            .sortedByDescending(::updatedAt)
            .forEach { s ->
                val dir = s.directory?.let(::normalize) ?: return@forEach
                if (FeedFolders.isOtherFolder(dir)) return@forEach
                byDir.getOrPut(dir) { mutableListOf() } += s
            }
        return byDir.map { (dir, list) ->
            ProjectSummary(
                directory = dir,
                name = name(dir),
                conversations = list.size,
                running = list.count { it.id in running },
                updatedMs = list.maxOf(::updatedAt),
            )
        }
    }

    /** This project's conversations, newest first; subagents stay with their parent. */
    public fun inProject(sessions: List<Session>, directory: String): List<Session> {
        val here = setOf(normalize(directory))
        return roots(sessions).filter { belongs(it, here) }.sortedByDescending(::updatedAt)
    }

    /** B-NAV-010: other projects only, running first, then most recent; subagents left out. */
    public fun elsewhere(sessions: List<Session>, running: Set<SessionId>, directory: String): List<Session> {
        val here = normalize(directory)
        return roots(sessions)
            .filter { s -> s.directory?.let { normalize(it) != here } == true }
            .sortedWith(compareBy<Session>({ if (it.id in running) 0 else 1 }, { -updatedAt(it) }))
            .take(ELSEWHERE)
    }

    /** B-ANDR-078: the newest first, each folder once, at most [REMEMBERED]. */
    public fun remembered(old: List<String>, directory: String): List<String> {
        val dir = normalize(directory)
        return (listOf(dir) + old.filterNot { normalize(it) == dir }).take(REMEMBERED)
    }

    /** What "Opened before" shows: stored folders that can still be opened, the first [OPENED_BEFORE]. */
    public fun openedBefore(stored: List<String>): List<String> =
        stored.map(::normalize).filter { pathProblem(it) == null }.distinct().take(OPENED_BEFORE)
}

/** A conversation title as the page draws it; the screen picks the words for [New] and [Untitled]. */
public sealed interface ChatTitle {
    public data class Text(val text: String) : ChatTitle

    public data object New : ChatTitle

    public data object Untitled : ChatTitle
}

/**
 * Display rules for session titles, the same as `:feature:chats` SessionTitles (Dart `presentedSessionTitleText`,
 * B-NAV-067). Features may not share code (K19); this belongs in a lower module once its owner takes it.
 */
public object ChatTitles {
    private val markers = listOf("<tool_call", "</tool_call", "<tool_use", "<function", "</function", "<|")
    private val truncatedMarker = Regex("""(<[A-Za-z_|/]*)\s*(?:…|\.\.\.)\s*$""")
    private val placeholder =
        Regex(
            """^(?:(?:New|Child)\s+session|Fork(?:ed)?(?:\s+session)?)?\s*[-:–]?\s*""" +
                """\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-]\d{2}:?\d{2})?$""",
            RegexOption.IGNORE_CASE,
        )
    private val trailingEllipsis = Regex("""[\s…]*(?:\.\.\.)?[\s…]*$""")
    private val isoStamp = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?""")

    /** Leaked markup cut off, a half-cut marker and its ellipsis too (Dart `displaySessionTitleText`). */
    private fun text(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return value
        val lower = value.lowercase()
        val at = markers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        var cut = false
        if (at != null) {
            value = value.substring(0, at)
            cut = true
        } else {
            val tail = truncatedMarker.find(value)
            if (tail != null) {
                val fragment = tail.groupValues[1].lowercase()
                if (markers.any { it.startsWith(fragment) }) {
                    value = value.substring(0, tail.range.first)
                    cut = true
                }
            }
        }
        if (cut) value = trailingEllipsis.replaceFirst(value, "")
        return value.trim()
    }

    public fun present(raw: String?): ChatTitle {
        val value = text(raw)
        if (value.isEmpty()) return ChatTitle.Untitled
        if (placeholder.matches(value)) return ChatTitle.New
        // Embedded stamps go too (Dart `stripIsoStamp`).
        val plain =
            value.replace(isoStamp, "")
                .replace(Regex("""\s*[-:–]\s*$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        return if (plain.isEmpty()) ChatTitle.New else ChatTitle.Text(plain)
    }
}
