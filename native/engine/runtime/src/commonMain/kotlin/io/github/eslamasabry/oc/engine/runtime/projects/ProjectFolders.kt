package io.github.eslamasabry.oc.engine.runtime.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/** What a path on the phone is, never following a link. */
public enum class NodeKind { Missing, Directory, Link, Other }

/**
 * The phone's own filesystem, read-only, as the folder browser needs it. The Android binding implements it with
 * `Files.readAttributes(…, NOFOLLOW_LINKS)` and a directory listing; commonTest uses an in-memory tree.
 */
public interface PhoneFiles {
    /** The kind of [path] without following a link; [NodeKind.Missing] when nothing is there. */
    public suspend fun kind(path: String): NodeKind

    /**
     * The names directly inside the folder [path], of any kind. Fails with `Failure.Folder(Missing)` (ENOENT),
     * `Failure.Folder(Denied)` (EPERM, EACCES) or `Failure.Unknown` (anything else), the OS text in Details only.
     */
    public suspend fun names(path: String): Outcome<List<String>>
}

/** Ubuntu runs the projects through `/root/projects`; the fallback through Ubuntu lists only those names. */
public fun interface ProjectNames {
    /** BuiltinProjectFolders.list (the `listProjectsScript` run inside Ubuntu). */
    public suspend fun list(): Outcome<List<String>>
}

/** One folder in a listing: its name, its full path inside Ubuntu, and whether it is a git repository. */
public data class FolderEntry(val name: String, val path: String, val isGit: Boolean = false)

/**
 * Where Ubuntu and the retained projects are on the phone (port of `BuiltinRootfsFolders.locateRootfs` and
 * `locateProjectStorage`). Mirrors BuiltinLinux.kt: `home = File(context.filesDir, "linux")`, `rootfs = File(home,
 * "ubuntu")`, installed when `File(home, "ubuntu.ready")` exists; projects live at `<files>/projects`.
 */
public class PhoneFolderLocations(private val files: PhoneFiles, private val filesDir: String?) {
    /** Ubuntu's root folder, or null when it is not installed (or this platform has no built-in Linux). */
    public suspend fun rootfs(): String? {
        val home = "${filesDir ?: return null}/$LINUX_HOME"
        if (files.kind("$home/$READY_MARK") == NodeKind.Missing) return null
        val rootfs = "$home/$ROOTFS"
        return if (files.kind(rootfs) == NodeKind.Directory) rootfs else null
    }

    /** Android's retained project storage, whether or not Ubuntu is installed. */
    public fun projectStorage(): String? = filesDir?.let { "$it/$PROJECTS" }

    public companion object {
        public const val LINUX_HOME: String = "linux"
        public const val ROOTFS: String = "ubuntu"
        public const val READY_MARK: String = "ubuntu.ready"
        public const val PROJECTS: String = "projects"
    }
}

/**
 * The built-in project browser (port of lib/builtin/builtin_folders.dart; B-LINUX-016, B-LINUX-017). Lists folders
 * inside the app's own Ubuntu straight from its files on the phone, without running anything in Ubuntu and without
 * OpenCode: it works while the server is stopped. Projects bound from `<files>/projects` at `/root/projects` win over
 * the legacy rootfs folder, with no duplicate row, and stay browsable after Ubuntu is removed. Both places are
 * resolved again on every listing, so a migration or removal while the browser is open is followed.
 *
 * Links are never followed (an absolute target means a path inside Ubuntu); hidden names and files are left out;
 * `/dev`, `/proc` and `/sys` are runtime bindings and are left out of `/`. Without Ubuntu's files only the projects
 * folder lists, through [throughUbuntu], and its rows claim no git mark they did not check.
 */
public class ProjectFolders(
    private val files: PhoneFiles,
    private val locate: suspend () -> String?,
    private val locateProjects: suspend () -> String? = { null },
    private val throughUbuntu: ProjectNames? = null,
) {
    /** The folders directly inside the Ubuntu [path], sorted by name ignoring case. */
    public suspend fun list(path: String): Outcome<List<FolderEntry>> {
        val ubuntuPath = normalize(path) ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val listed = listFromFiles(ubuntuPath)
        val failure = (listed as? Outcome.Failed)?.failure
        if (failure !is Failure.Folder || failure.problem != FolderProblem.NotInstalled || throughUbuntu == null) return listed
        // Ubuntu's files were not found: only the projects folder can be listed, by asking Ubuntu.
        if (ubuntuPath != PROJECTS_DIR) return Failure.Unknown().failed()
        return when (val names = throughUbuntu.list()) {
            is Outcome.Failed -> Failure.Unknown(names.failure.details).failed()
            is Outcome.Ok -> names.value.map { FolderEntry(it.trim(), pathFor(it)) }.ok()
        }
    }

    /** The phone folder that holds Ubuntu's [path], or null when it is missing, a link, or Ubuntu's files are not there. */
    public suspend fun hostDirectory(path: String): String? {
        val ubuntuPath = normalize(path) ?: return null
        val external = locateProjects()?.let { (hostPath(it, "/") as? HostPath.Found)?.path }
        if (external != null && inProjects(ubuntuPath)) {
            return (hostPath(external, ubuntuPath.removePrefix(PROJECTS_DIR)) as? HostPath.Found)?.path
        }
        val rootfs = locate() ?: return null
        return (hostPath(rootfs, ubuntuPath) as? HostPath.Found)?.path
    }

    private suspend fun listFromFiles(ubuntuPath: String): Outcome<List<FolderEntry>> {
        val external = when (val found = locateProjects()?.let { hostPath(it, "/") }) {
            is HostPath.Found -> found.path
            is HostPath.Refused -> return found.failure.failed()
            HostPath.Absent, null -> null
        }
        val rootfs = locate()
        val projects = inProjects(ubuntuPath)
        val virtualParent = external != null && (ubuntuPath == "/" || ubuntuPath == "/root")
        if (rootfs == null && !(external != null && projects) && !virtualParent) {
            return Failure.Folder(FolderProblem.NotInstalled).failed()
        }
        val resolved =
            when {
                external != null && projects -> hostPath(external, ubuntuPath.removePrefix(PROJECTS_DIR))
                rootfs != null -> hostPath(rootfs, ubuntuPath)
                else -> HostPath.Absent
            }
        val host = when (resolved) {
            is HostPath.Found -> resolved.path
            is HostPath.Refused -> return resolved.failure.failed()
            HostPath.Absent -> null
        }
        if (host == null && !virtualParent) {
            // A fresh install before OpenCode first started has no projects folder yet: it lists as empty.
            return if (ubuntuPath == PROJECTS_DIR) emptyList<FolderEntry>().ok() else Failure.Folder(FolderProblem.Missing).failed()
        }
        val entries = mutableListOf<FolderEntry>()
        if (host != null) {
            val names = when (val read = files.names(host)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
            for (name in names) {
                if (name.isEmpty() || name.startsWith('.')) continue
                if (files.kind("$host/$name") != NodeKind.Directory) continue // Files and links are not shown.
                if (ubuntuPath == "/" && name in BOUND_AT_ROOT) continue
                if (external != null && ubuntuPath == "/root" && name == "projects") continue // The binding, not the mountpoint.
                entries += FolderEntry(name, if (ubuntuPath == "/") "/$name" else "$ubuntuPath/$name", hasGit("$host/$name"))
            }
        }
        if (external != null && ubuntuPath == "/root") {
            entries += FolderEntry("projects", PROJECTS_DIR, hasGit(external))
        } else if (virtualParent && ubuntuPath == "/" && entries.none { it.name == "root" }) {
            entries += FolderEntry("root", "/root")
        }
        return entries.sortedBy { it.name.lowercase() }.ok()
    }

    private sealed interface HostPath {
        data class Found(val path: String) : HostPath

        data object Absent : HostPath

        data class Refused(val failure: Failure) : HostPath
    }

    /** The phone path of [ubuntuPath] under [root], checking every folder on the way is a real folder, not a link. */
    private suspend fun hostPath(root: String, ubuntuPath: String): HostPath {
        var host = root
        for (segment in listOf("") + ubuntuPath.split('/').filter { it.isNotEmpty() }) {
            if (segment.isNotEmpty()) host = "$host/$segment"
            when (files.kind(host)) {
                NodeKind.Missing -> return HostPath.Absent
                NodeKind.Link -> return HostPath.Refused(Failure.Folder(FolderProblem.Linked))
                NodeKind.Other -> return HostPath.Refused(Failure.Folder(FolderProblem.Missing))
                NodeKind.Directory -> Unit
            }
        }
        return HostPath.Found(host)
    }

    /** A `.git` of its own, folder or file (a worktree's `.git` is a file). */
    private suspend fun hasGit(host: String): Boolean = files.kind("$host/.git") != NodeKind.Missing

    public companion object {
        /** BuiltinLinux.projectsDir. */
        public const val PROJECTS_DIR: String = "/root/projects"

        /** Folders bound from Android when Ubuntu runs: empty in the files. */
        private val BOUND_AT_ROOT = setOf("dev", "proc", "sys")

        public fun pathFor(name: String): String = "$PROJECTS_DIR/${name.trim()}"

        /** [path] as a plain absolute Ubuntu path, or null when it is relative, holds a NUL or climbs with `..`. */
        public fun normalize(path: String): String? {
            val value = path.trim()
            if (!value.startsWith('/') || value.contains('\u0000')) return null
            val segments = mutableListOf<String>()
            for (segment in value.split('/')) {
                if (segment.isEmpty() || segment == ".") continue
                if (segment == "..") return null
                segments += segment
            }
            return "/" + segments.joinToString("/")
        }

        /** The folder [path] is in, or null at `/`. */
        public fun parentOf(path: String): String? {
            val value = normalize(path)
            if (value == null || value == "/") return null
            val cut = value.lastIndexOf('/')
            return if (cut <= 0) "/" else value.substring(0, cut)
        }

        private fun inProjects(path: String) = path == PROJECTS_DIR || path.startsWith("$PROJECTS_DIR/")
    }
}
