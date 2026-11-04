package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

// Port of the measuring half of lib/builtin/project_export.dart (ProjectStorageScanner, ProjectExportPolicy,
// AppStorageLayout): what Android's "Clear storage" would delete, measured from the files. Links and special files are
// never followed; proot's link2symlink links to `.l2s.` files inside the app's folder are the one exception (Git objects).

/** What a path is, without following a link. */
public enum class StorageEntryKind { File, Directory, Link, Other, Missing }

/** The file system as the scanner reads it. Synchronous: run the scan off the main thread. */
public interface StorageTree {
    public fun kind(path: String): StorageEntryKind

    /** Child names, or null when the folder cannot be read (counted as nothing, never followed). */
    public fun list(path: String): List<String>?

    /** A regular file's logical size; null when unreadable. */
    public fun size(path: String): Long?

    /** A link's own target text; null when [path] is not a readable link. */
    public fun readLink(path: String): String?

    /** The canonical path with every link resolved; null when it cannot be resolved. */
    public fun realPath(path: String): String?
}

/** One file going into an export: its name inside the zip and where it is. */
public data class ProjectExportEntry(val name: String, val source: String, val bytes: Long, val private: Boolean = false)

/** A project folder (or a loose file) under the projects folder. */
public data class ProjectFacts(val name: String, val bytes: Long, val files: Int, val privateFiles: Int)

/** What "Clear storage" would delete. */
public data class AppStorageFacts(
    val projects: List<ProjectFacts>,
    val serverInstalled: Boolean,
    /** Ubuntu and OpenCode, projects not counted. */
    val serverBytes: Long,
    /** OpenCode's sign-ins, settings and conversations inside Ubuntu. */
    val privateDataBytes: Long,
    val savedServers: Int = 0,
) {
    val projectBytes: Long get() = projects.sumOf { it.bytes }
    val privateFiles: Int get() = projects.sumOf { it.privateFiles }

    public fun withSavedServers(count: Int): AppStorageFacts = copy(savedServers = count)

    public companion object {
        public val Empty: AppStorageFacts = AppStorageFacts(emptyList(), false, 0, 0)
    }
}

/** Which files are credentials. Deliberately broad: a false positive only moves a file into the private export. */
public object ProjectExportPolicy {
    public val privateNames: Set<String> =
        setOf(
            "auth.json", ".gitconfig", ".git-credentials", ".netrc", "_netrc", ".npmrc", ".pypirc", ".pgpass",
            ".dockercfg", "credentials", "credentials.json", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
        )
    public val privateFolders: Set<String> = setOf(".ssh", ".gnupg", ".aws", ".docker")
    public val privateExtensions: Set<String> = setOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore")
    private val envTemplates = setOf(".env.example", ".env.sample", ".env.template", ".env.dist")

    /** Whether [relative] (a `/` path inside projects) is private. Git config is always private. */
    public fun isPrivate(relative: String): Boolean {
        val parts = relative.lowercase().split('/')
        val name = parts.last()
        if (parts.any { it in privateFolders }) return true
        if (name in privateNames) return true
        if (name == ".env" || (name.startsWith(".env.") && name !in envTemplates)) return true
        val dot = name.lastIndexOf('.')
        if (dot > 0 && name.substring(dot) in privateExtensions) return true
        if (".git" in parts && (name == "config" || name == "config.worktree")) return true
        return false
    }

    /** OpenCode's data inside Ubuntu (relative to its /root), exported only privately. */
    public val privateDataRoots: List<String> =
        listOf(".local/share/opencode", ".config/opencode", ".oc-opencode2/config", ".oc-opencode2/data", ".oc-migration-exports")

    /** Regenerable parts of those roots that are left out even then. */
    public val privateDataSkipped: Set<String> = setOf("bin", "log")
}

/** The places under the app's files folder. */
public class AppStorageLayout(public val filesDir: String) {
    public val projects: String get() = "$filesDir/projects"
    public val linux: String get() = "$filesDir/linux"
    public val rootfs: String get() = "$filesDir/linux/ubuntu"
    public val root: String get() = "$rootfs/root"
    public val legacyProjects: String get() = "$root/projects"
    public val ready: String get() = "$filesDir/linux/ubuntu.ready"
}

/** A plan could not be made faithfully (strict mode): an unreadable folder or two roots with one project name. */
private class StoragePlanProblem(message: String) : Exception(message)

/** Walks the files without following links or reading contents. */
public class ProjectStorageScanner(private val tree: StorageTree) {
    /** Measures each project, the server and the saved private data. Nothing installed measures as nothing. */
    public fun scan(filesDir: String): AppStorageFacts {
        val layout = AppStorageLayout(filesDir)
        val projects = mutableListOf<ProjectFacts>()
        for ((name, path) in projectTops(layout, strict = false)) {
            var bytes = 0L
            var files = 0
            var privateFiles = 0
            walk(path, name, gitLinksUnder = filesDir) { relative, _, size ->
                bytes += size
                files++
                if (ProjectExportPolicy.isPrivate(relative)) privateFiles++
            }
            projects += ProjectFacts(name, bytes, files, privateFiles)
        }
        projects.sortBy { it.name.lowercase() }
        val installed = tree.kind(layout.ready) == StorageEntryKind.File
        var linuxBytes = 0L
        walk(layout.linux, "linux") { _, _, size -> linuxBytes += size }
        var legacyBytes = 0L
        walk(layout.legacyProjects, "p") { _, _, size -> legacyBytes += size }
        val privateBytes = privateData(layout, strict = false).sumOf { it.bytes }
        return AppStorageFacts(
            projects = projects,
            serverInstalled = installed,
            serverBytes = (linuxBytes - legacyBytes).coerceIn(0, linuxBytes.coerceAtLeast(0)),
            privateDataBytes = privateBytes,
        )
    }

    /** Every file an export writes. A plain export leaves out every private file; [includePrivate] adds them and OpenCode's data. */
    public fun plan(filesDir: String, includePrivate: Boolean = false): Outcome<List<ProjectExportEntry>> =
        try {
            planOrThrow(filesDir, includePrivate).ok()
        } catch (_: StoragePlanProblem) {
            Failure.StorageRefused().failed()
        }

    private fun planOrThrow(filesDir: String, includePrivate: Boolean): List<ProjectExportEntry> {
        val layout = AppStorageLayout(filesDir)
        val entries = mutableListOf<ProjectExportEntry>()
        for ((top, path) in projectTops(layout, strict = true)) {
            walk(path, "projects/$top", gitLinksUnder = filesDir, strict = true) { name, source, size ->
                val private = ProjectExportPolicy.isPrivate(name.removePrefix("projects/"))
                if (private && !includePrivate) return@walk
                entries += ProjectExportEntry(name, source, size, private)
            }
        }
        if (includePrivate) entries += privateData(layout, strict = true)
        return entries
    }

    private fun privateData(layout: AppStorageLayout, strict: Boolean): List<ProjectExportEntry> {
        val entries = mutableListOf<ProjectExportEntry>()
        for (root in ProjectExportPolicy.privateDataRoots) {
            walk("${layout.root}/$root", "opencode/$root", skip = ProjectExportPolicy.privateDataSkipped, strict = strict) {
                name, path, size ->
                entries += ProjectExportEntry(name, path, size, private = true)
            }
        }
        return entries
    }

    private fun projectTops(layout: AppStorageLayout, strict: Boolean): List<Pair<String, String>> {
        val seen = mutableSetOf<String>()
        val tops = mutableListOf<Pair<String, String>>()
        for (folder in listOf(layout.projects, layout.legacyProjects)) {
            if (tree.kind(folder) != StorageEntryKind.Directory) continue
            val children = tree.list(folder) ?: if (strict) throw StoragePlanProblem("unreadable projects") else continue
            for (name in children) {
                val path = "$folder/$name"
                val kind = tree.kind(path)
                if (kind != StorageEntryKind.Directory && kind != StorageEntryKind.File) continue
                if (!seen.add(name)) {
                    if (strict) throw StoragePlanProblem("conflicting project roots")
                    continue
                }
                tops += name to path
            }
        }
        return tops
    }

    private fun walk(
        path: String,
        prefix: String,
        skip: Set<String> = emptySet(),
        strict: Boolean = false,
        gitLinksUnder: String? = null,
        visit: (name: String, path: String, bytes: Long) -> Unit,
    ) {
        when (tree.kind(path)) {
            StorageEntryKind.File -> visit(prefix, path, size(path, strict))
            StorageEntryKind.Link -> {
                if (gitLinksUnder == null) return
                val bytes = linkedObjectFile(path, gitLinksUnder) ?: return
                visit(prefix, bytes, size(bytes, strict))
            }
            StorageEntryKind.Directory -> {
                val children = tree.list(path) ?: if (strict) throw StoragePlanProblem("unreadable folder") else return
                for (name in children) {
                    if (name in skip) continue
                    if (gitLinksUnder != null && name.startsWith(L2S_PREFIX)) continue
                    walk("$path/$name", "$prefix/$name", strict = strict, gitLinksUnder = gitLinksUnder, visit = visit)
                }
            }
            else -> Unit
        }
    }

    /** The regular `.l2s.` file a link2symlink link leads to inside [root]; null for any other link. */
    public fun linkedObjectFile(link: String, root: String): String? {
        val top = tree.realPath(root) ?: return null
        var current = link
        repeat(8) {
            val target = tree.readLink(current) ?: return null
            val name = target.substringAfterLast('/')
            if (!name.startsWith(L2S_PREFIX)) return null
            val folder = current.substringBeforeLast('/')
            var next = if (target.startsWith("/")) target else "$folder/$target"
            if (tree.kind(next) == StorageEntryKind.Missing) next = "$folder/$name"
            when (tree.kind(next)) {
                StorageEntryKind.Link -> current = next
                StorageEntryKind.File -> {
                    val real = tree.realPath(next) ?: return null
                    return if (real.startsWith("$top/")) real else null
                }
                else -> return null
            }
        }
        return null
    }

    private fun size(path: String, strict: Boolean): Long =
        tree.size(path) ?: if (strict) throw StoragePlanProblem("unreadable file") else 0L

    public companion object {
        public const val L2S_PREFIX: String = ".l2s."
    }
}
