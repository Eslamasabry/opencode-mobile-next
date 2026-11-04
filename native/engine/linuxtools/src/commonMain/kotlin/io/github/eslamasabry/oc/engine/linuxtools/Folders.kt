package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Outcome

/** Where the managed servers keep projects (lib/domain/workspace_paths.dart `managedProjectsDirectory`). */
public const val MANAGED_PROJECTS_DIRECTORY: String = "/root/projects"

/**
 * One folder in a listing: its name, its full path inside Ubuntu, and whether it is a git repository (it has a `.git`
 * of its own, a folder or a worktree's file).
 */
public data class FolderEntry(
    val name: String,
    val path: String,
    val isGit: Boolean = false,
    val inside: List<String>? = null,
    val more: Int = 0,
)

/** Why a folder could not be listed (port of `FolderListProblem`). */
public enum class FolderListProblem {
    /** Ubuntu is not unpacked on this phone (or its files cannot be found). */
    NotInstalled,

    /** The folder is not there (any more). */
    Missing,

    /** The app may not read it. */
    Denied,

    /** It is a link; its target is only right inside Ubuntu, so the app does not follow it from outside. */
    Linked,

    /** The path is not a plain absolute path. */
    Invalid,

    /** No answer in time (Termux, where each listing is a round trip). */
    TimedOut,

    /** Anything else the filesystem said. */
    Failed,
}

/** A folder answer: the value, or the problem plus the filesystem's own words for Details. */
public sealed interface FolderResult<out T> {
    public data class Ok<out T>(val value: T) : FolderResult<T>

    public data class Problem(val problem: FolderListProblem, val detail: String = "") : FolderResult<Nothing>
}

public fun <T> FolderResult<T>.valueOrNull(): T? = (this as? FolderResult.Ok)?.value

public fun FolderResult<*>.problemOrNull(): FolderListProblem? = (this as? FolderResult.Problem)?.problem

/** What a path is on the phone, read without following links. */
public enum class FsType { NotFound, Link, Directory, File }

/** One child of a folder, read without following links. */
public data class FsChild(val name: String, val type: FsType)

/**
 * The phone's file system as the folder lister needs it. Implementations never follow links. [children] answers the
 * direct children or the problem the OS reported (ENOENT → Missing, EPERM/EACCES → Denied, else Failed).
 */
public interface RootfsFileSystem {
    public suspend fun type(path: String): FsType

    public suspend fun children(path: String): FolderResult<List<FsChild>>
}

/**
 * Lists folders inside the app's own Ubuntu straight from its files on the phone, without running anything in Ubuntu
 * and without OpenCode (port of `BuiltinRootfsFolders`). Projects live at `<app files>/projects` and proot binds them
 * at `/root/projects`; retained projects can be browsed even after removing Ubuntu. `/dev`, `/proc` and `/sys` are
 * runtime bindings and are left out of `/`. Links are never followed: an absolute link target means a path inside
 * Ubuntu, which read from outside would point somewhere else on the phone.
 */
public class BuiltinRootfsFolders(
    private val fs: RootfsFileSystem,
    /** Ubuntu's root folder on this phone, or null when it is not installed (`linux/ubuntu` with `ubuntu.ready`). */
    private val locate: suspend () -> String?,
    /** Android's retained project storage (`<app files>/projects`), or null. */
    private val locateProjects: suspend () -> String? = { null },
) {
    /** The phone folder that holds Ubuntu's [path], or null when missing, not a real folder, or not installed. */
    public suspend fun hostDirectory(path: String): String? {
        val ubuntuPath = normalize(path) ?: return null
        val projects = locateProjects()
        val external =
            when (val found = projects?.let { hostPath(it, "/") }) {
                null, HostPath.Absent -> null
                is HostPath.Found -> found.path
                is HostPath.Refused -> return null
            }
        if (external != null && inProjects(ubuntuPath)) {
            return (hostPath(external, ubuntuPath.substring(MANAGED_PROJECTS_DIRECTORY.length)) as? HostPath.Found)?.path
        }
        val rootfs = locate() ?: return null
        return (hostPath(rootfs, ubuntuPath) as? HostPath.Found)?.path
    }

    /** The folders directly inside [path] (hidden ones left out), sorted by name, case-insensitively. */
    public suspend fun list(path: String): FolderResult<List<FolderEntry>> {
        val ubuntuPath = normalize(path) ?: return FolderResult.Problem(FolderListProblem.Invalid, path)
        // Resolve each time: native migration/removal can happen while the browser remains alive.
        val projects = locateProjects()
        val external: String? =
            if (projects == null) {
                null
            } else {
                when (val found = hostPath(projects, "/")) {
                    is HostPath.Found -> found.path
                    HostPath.Absent -> null
                    is HostPath.Refused -> return FolderResult.Problem(found.problem, ubuntuPath)
                }
            }
        val rootfs = locate()
        val inProjects = inProjects(ubuntuPath)
        val virtualParent = external != null && (ubuntuPath == "/" || ubuntuPath == "/root")
        if (rootfs == null && !(external != null && inProjects) && !virtualParent) {
            return FolderResult.Problem(FolderListProblem.NotInstalled)
        }
        val hostAnswer: HostPath =
            when {
                external != null && inProjects ->
                    hostPath(external, ubuntuPath.substring(MANAGED_PROJECTS_DIRECTORY.length))
                rootfs == null -> HostPath.Absent
                else -> hostPath(rootfs, ubuntuPath)
            }
        val host =
            when (hostAnswer) {
                is HostPath.Refused -> return FolderResult.Problem(hostAnswer.problem, ubuntuPath)
                HostPath.Absent -> null
                is HostPath.Found -> hostAnswer.path
            }
        if (host == null && !virtualParent) {
            if (ubuntuPath == MANAGED_PROJECTS_DIRECTORY) return FolderResult.Ok(emptyList())
            return FolderResult.Problem(FolderListProblem.Missing, ubuntuPath)
        }
        val entries = ArrayList<FolderEntry>()
        if (host != null) {
            val children =
                when (val listed = fs.children(host)) {
                    is FolderResult.Problem -> return listed
                    is FolderResult.Ok -> listed.value
                }
            for (child in children) {
                if (child.type != FsType.Directory) continue // Files and links are not shown.
                val name = child.name
                if (name.isEmpty() || name.startsWith(".")) continue
                if (ubuntuPath == "/" && name in boundAtRoot) continue
                if (external != null && ubuntuPath == "/root" && name == "projects") continue // the binding, not the mountpoint
                entries.add(
                    FolderEntry(
                        name = name,
                        path = if (ubuntuPath == "/") "/$name" else "$ubuntuPath/$name",
                        isGit = hasGit("$host/$name"),
                    ),
                )
            }
        }
        if (external != null && ubuntuPath == "/root") {
            entries.add(FolderEntry(name = "projects", path = MANAGED_PROJECTS_DIRECTORY, isGit = hasGit(external)))
        } else if (virtualParent && ubuntuPath == "/" && entries.none { it.name == "root" }) {
            entries.add(FolderEntry(name = "root", path = "/root"))
        }
        entries.sortWith(compareBy { it.name.lowercase() })
        return FolderResult.Ok(entries)
    }

    private sealed interface HostPath {
        data class Found(val path: String) : HostPath

        data object Absent : HostPath

        data class Refused(val problem: FolderListProblem) : HostPath
    }

    /** The phone path of [ubuntuPath], checking every folder on the way is a real folder (not a link). */
    private suspend fun hostPath(rootfs: String, ubuntuPath: String): HostPath {
        var host = rootfs
        val segments = listOf("") + ubuntuPath.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            if (segment.isNotEmpty()) host = "$host/$segment"
            when (fs.type(host)) {
                FsType.NotFound -> return HostPath.Absent
                FsType.Link -> return HostPath.Refused(FolderListProblem.Linked)
                FsType.File -> return HostPath.Refused(FolderListProblem.Missing)
                FsType.Directory -> Unit
            }
        }
        return HostPath.Found(host)
    }

    private suspend fun hasGit(host: String): Boolean = fs.type("$host/.git") != FsType.NotFound

    public companion object {
        /** Folders bound from Android when Ubuntu runs: empty in the files. */
        private val boundAtRoot = setOf("dev", "proc", "sys")

        private fun inProjects(path: String): Boolean =
            path == MANAGED_PROJECTS_DIRECTORY || path.startsWith("$MANAGED_PROJECTS_DIRECTORY/")

        /** [path] as a plain absolute Ubuntu path, or null when it is relative, holds a NUL or climbs with `..`. */
        public fun normalize(path: String): String? {
            val value = path.trim()
            if (!value.startsWith("/") || value.contains('\u0000')) return null
            val segments = ArrayList<String>()
            for (segment in value.split('/')) {
                if (segment.isEmpty() || segment == ".") continue
                if (segment == "..") return null
                segments.add(segment)
            }
            return "/" + segments.joinToString("/")
        }

        /** The folder [path] is in, or null at `/`. */
        public fun parentOf(path: String): String? {
            val value = normalize(path) ?: return null
            if (value == "/") return null
            val cut = value.lastIndexOf('/')
            return if (cut <= 0) "/" else value.substring(0, cut)
        }
    }
}

/** Asks Ubuntu itself for the projects folder's names (the `listProjectsScript` of the built-in Linux). */
public fun interface ProjectFolderNames {
    public suspend fun list(): Outcome<List<String>>
}

/**
 * The folders of OpenCode inside the app, for the folder browser (port of `BuiltinFolders`): read from Ubuntu's files.
 * Should the app not find those files, Ubuntu is asked for the one folder it keeps a script for, the projects folder;
 * any other folder then says it cannot be shown (a path can still be entered).
 */
public class BuiltinFolders(private val rootfs: BuiltinRootfsFolders, private val throughUbuntu: ProjectFolderNames) {
    public suspend fun list(path: String): FolderResult<List<FolderEntry>> {
        val listed = rootfs.list(path)
        if (listed !is FolderResult.Problem || listed.problem != FolderListProblem.NotInstalled) return listed
        if (BuiltinRootfsFolders.normalize(path) != MANAGED_PROJECTS_DIRECTORY) {
            return FolderResult.Problem(
                FolderListProblem.Failed,
                "Ubuntu’s files were not found; only $MANAGED_PROJECTS_DIRECTORY can be listed.",
            )
        }
        return when (val names = throughUbuntu.list()) {
            is Outcome.Failed -> FolderResult.Problem(FolderListProblem.Failed)
            is Outcome.Ok ->
                FolderResult.Ok(
                    names.value.map { FolderEntry(name = it, path = "$MANAGED_PROJECTS_DIRECTORY/${it.trim()}") },
                )
        }
    }
}
