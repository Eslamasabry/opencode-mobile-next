package io.github.eslamasabry.oc.engine.project.repair

import io.github.eslamasabry.oc.core.common.dart.DartJson

/*
 * Repairing a project whose Git objects are links to files Git can't reach (port of lib/builtin/git_link_repair.dart).
 * Ubuntu under proot with --link2symlink stores every hard link as a link to an absolute path. Such links break when
 * the project folder moves and, for projects brought over from Termux's Ubuntu, point into Termux's private storage.
 * This file holds the pure file work over the [RepairFileSystem] port: the scan, the checks and the replacement.
 */

/** What one path is, without following links. */
public enum class RepairEntryKind { Folder, File, Link }

/** The app's side of the files. Every call is a plain, local filesystem operation; failures are values. */
public interface RepairFileSystem {
    /** What [path] is, without following a link; null when nothing is there. */
    public fun kind(path: String): RepairEntryKind?

    /** The names in folder [path] (links not followed), or null when it can't be read. */
    public fun list(path: String): List<String>?

    /** What link [path] says (its first hop), or null when it is not a readable link. */
    public fun readLink(path: String): String?

    public fun readText(path: String): String?

    public fun size(path: String): Long?

    public fun readBytes(path: String): ByteArray?

    public fun copy(from: String, to: String): Boolean

    public fun rename(from: String, to: String): Boolean

    /** Deletes a file or link (not followed). */
    public fun delete(path: String): Boolean

    public fun deleteTree(path: String): Boolean

    public fun createFolders(path: String): Boolean
}

/** Inflates a zlib stream (a loose Git object), or null when it is not one. Platform code binds java.util.zip. */
public fun interface ZlibInflater {
    public fun inflate(bytes: ByteArray): ByteArray?
}

/** Where the bytes behind one object link are. */
public enum class GitLinkPlace {
    /** On the app's side: the link resolves, or its file sits next to it. */
    Here,

    /** In Termux's Ubuntu (proot-distro's `.l2s` store). */
    Termux,

    /** Nowhere the app can reach. */
    Missing,
}

/** One link standing in for a Git object file. */
public data class GitObjectLink(
    /** The link, on the app's side. */
    val path: String,
    /** Its path inside the repository's Git folder (`objects/ab/cdef…`). */
    val relative: String,
    /** What the link says (the first hop). */
    val target: String,
    val place: GitLinkPlace,
    /** [GitLinkPlace.Here]: the file holding the bytes. [GitLinkPlace.Termux]: the path in Termux. */
    val file: String? = null,
)

public data class GitLinkScan(val links: List<GitObjectLink>) {
    public fun of(place: GitLinkPlace): List<GitObjectLink> = links.filter { it.place == place }
}

public class GitObjectLinks(private val fs: RepairFileSystem, private val inflater: ZlibInflater) {
    /**
     * The Git folder of the repository at [repository] (its `.git`, or the folder a `.git` file names), or null when it
     * has none. [host] turns a path the `.git` file names (written inside Ubuntu) into the app's side.
     */
    public fun gitFolder(repository: String, host: (String) -> String = { it }): String? {
        val dotGit = "$repository/.git"
        when (fs.kind(dotGit)) {
            RepairEntryKind.Folder -> return dotGit
            RepairEntryKind.File -> Unit
            else -> return null
        }
        val line = fs.readText(dotGit)?.lineSequence()?.firstOrNull { it.startsWith("gitdir:") } ?: return null
        val named = line.removePrefix("gitdir:").trim()
        val folder = if (named.startsWith("/")) host(named) else "$repository/$named"
        // A worktree's objects live in the main repository's folder.
        val common = fs.readText("$folder/commondir")?.trim() ?: return folder
        return if (common.startsWith("/")) host(common) else "$folder/$common"
    }

    /** Every link under an `objects` folder of [gitFolder] (proot's own `.l2s.` files left out), with where its bytes are. */
    public fun scan(gitFolder: String): GitLinkScan {
        val links = ArrayList<GitObjectLink>()
        fun walk(folder: String, relative: String) {
            val children = fs.list(folder) ?: return
            for (name in children) {
                val path = "$folder/$name"
                val inside = if (relative.isEmpty()) name else "$relative/$name"
                when (fs.kind(path)) {
                    RepairEntryKind.Folder -> walk(path, inside)
                    RepairEntryKind.Link ->
                        if (!name.startsWith(L2S_PREFIX) && "/$inside".contains("/objects/")) links += classify(path, inside)
                    else -> Unit
                }
            }
        }
        walk(gitFolder, "")
        return GitLinkScan(links.sortedBy { it.relative })
    }

    /** Follows [link] hop by hop. A hop whose absolute target is gone is looked for under the same name next to the link. */
    private fun classify(link: String, relative: String): GitObjectLink {
        var first = ""
        var current = link
        for (hop in 0 until 8) {
            val target = fs.readLink(current) ?: break
            if (hop == 0) first = target
            val folder = current.substringBeforeLast('/')
            var next = if (target.startsWith("/")) target else "$folder/$target"
            var kind = fs.kind(next)
            if (kind == null) {
                if (inTermux(next)) {
                    val place = if (TERMUX_STORE.matches(next)) GitLinkPlace.Termux else GitLinkPlace.Missing
                    return GitObjectLink(link, relative, first, place, next)
                }
                val beside = "$folder/${target.substringAfterLast('/')}"
                if (beside != current) {
                    next = beside
                    kind = fs.kind(next)
                }
            }
            when (kind) {
                RepairEntryKind.Link -> current = next
                RepairEntryKind.File -> return GitObjectLink(link, relative, first, GitLinkPlace.Here, next)
                else -> break
            }
        }
        return GitObjectLink(link, relative, first, GitLinkPlace.Missing)
    }

    /**
     * Whether [file] holds what an object file at [relative] must hold: a loose object whose inflated bytes hash to its
     * name, or a pack, index or reverse index whose trailing checksum matches its contents. Anything else is refused.
     */
    public fun verify(relative: String, file: String): Boolean {
        LOOSE.find(relative)?.let { m ->
            val id = m.groupValues[1] + m.groupValues[2]
            val size = fs.size(file) ?: return false
            if (size > MAX_LOOSE_BYTES) return false
            val inflated = fs.readBytes(file)?.let(inflater::inflate) ?: return false
            val digest = if (id.length == 40) Sha1.hex(inflated) else DartJson.sha256Hex(inflated)
            return digest == id
        }
        PACK.find(relative)?.let { m ->
            val size = if (m.groupValues[1].length == 40) 20 else 32
            val bytes = fs.readBytes(file) ?: return false
            if (bytes.size <= size) return false
            val body = bytes.size - size
            val trailer = bytes.copyOfRange(body, bytes.size).toHex()
            val digest = if (size == 20) Sha1.digest(bytes, body).toHex() else DartJson.sha256Hex(bytes.copyOfRange(0, body))
            return digest == trailer
        }
        return false
    }

    /** Puts a plain file holding [bytes] where the link at [link] is; with [move], [bytes] is renamed into place. */
    public fun replace(link: String, bytes: String, move: Boolean = false): Boolean {
        val folder = link.substringBeforeLast('/')
        val temporary = "$folder/.oc-repair-${link.substringAfterLast('/')}.tmp"
        val placed = if (move) fs.rename(bytes, temporary) else fs.copy(bytes, temporary)
        if (!placed) return false
        if (!fs.delete(link)) {
            fs.delete(temporary)
            return false
        }
        return fs.rename(temporary, link)
    }

    public companion object {
        /** proot's name for the files behind its links. */
        public const val L2S_PREFIX: String = ".l2s."

        internal const val MAX_LOOSE_BYTES: Long = 256L * 1024 * 1024

        /** A file in a Termux proot-distro container's `.l2s` store: the only Termux paths the repair asks for. */
        public val TERMUX_STORE: Regex =
            Regex(
                "^(?!.*/\\.\\.(?:/|$))" +
                    "/data/(?:data|user/0)/com\\.termux/files/usr/var/lib/proot-distro/" +
                    "(?:containers/[A-Za-z0-9._-]+/rootfs|installed-rootfs/[A-Za-z0-9._-]+)" +
                    "/\\.l2s/\\.l2s\\.[A-Za-z0-9._-]+$",
            )

        private val LOOSE = Regex("(?:^|/)objects/([0-9a-f]{2})/([0-9a-f]{38}|[0-9a-f]{62})$")
        private val PACK = Regex("(?:^|/)objects/pack/pack-([0-9a-f]{40}|[0-9a-f]{64})\\.(?:pack|idx|rev)$")

        private fun inTermux(path: String): Boolean =
            path.startsWith("/data/data/com.termux/") || path.startsWith("/data/user/0/com.termux/")
    }
}
