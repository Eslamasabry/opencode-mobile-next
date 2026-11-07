package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.InflaterInputStream

/**
 * File-side repair of proot object links in built-in Linux projects (`/root/projects`). A link is restored only from a
 * verified copy inside the projects folder; a link that points outside it (for example into another app's storage) is
 * reported missing and left untouched.
 */
internal class BuiltinGitObjectRepair(
    private val projects: File,
    private val current: () -> Boolean,
    private val verifyRepository: suspend (String) -> Boolean,
) {
    private data class ObjectLink(
        val link: File,
        val target: String,
        val relative: String,
        val local: File?,
    )

    private val root = projects.toPath().toAbsolutePath().normalize()

    suspend fun repair(directory: String): GitLinkRepairReport {
        if (!projectPath(directory)) return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
        require(current())
        val (repository, folder) =
            repositoryOf(File(projects, directory.removePrefix(PROJECTS)))
                ?: return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
        val missing = mutableListOf<String>()
        var restored = 0
        for (link in objectLinks(folder)) {
            require(current())
            val source = link.local
            if (source != null && verify(link.relative, source) && replace(link, source)) restored++
            else missing += link.relative
        }
        require(current())
        val guestRepository =
            PROJECTS + root.relativize(repository.toPath().toAbsolutePath().normalize()).toString()
        val valid = verifyRepository(guestRepository)
        return GitLinkRepairReport(
            when {
                missing.isNotEmpty() || !valid -> GitLinkRepairResult.MissingObjects
                restored == 0 -> GitLinkRepairResult.Healthy
                else -> GitLinkRepairResult.Repaired
            },
            restored,
            missing.sorted(),
        )
    }

    /** The nearest repository at or above [start] and its git folder; null when a link or a file is in the way. */
    private fun repositoryOf(start: File): Pair<File, File>? {
        var repository = start
        while (repository.toPath().toAbsolutePath().normalize() != root) {
            if (Files.isSymbolicLink(repository.toPath())) return null
            if (safeDirectory(repository)) {
                gitFolder(repository)?.let { return repository to it }
            } else if (Files.exists(repository.toPath(), NOFOLLOW_LINKS)) return null
            repository = repository.parentFile ?: return null
        }
        return null
    }

    /** Every symbolic link under the git folder's `objects/`, bounded in depth, entries and links. */
    private fun objectLinks(folder: File): List<ObjectLink> {
        val links = mutableListOf<ObjectLink>()
        var visited = 0
        fun walk(dir: File, relative: String) {
            require(current())
            check(relative.count { it == '/' } < 64)
            check(safeDirectory(dir))
            for (entry in requireNotNull(dir.listFiles())) {
                check(++visited <= 100_000)
                val name = if (relative.isEmpty()) entry.name else "$relative/${entry.name}"
                if (Files.isSymbolicLink(entry.toPath())) {
                    if (!entry.name.startsWith(".l2s.") && "/$name".contains("/objects/"))
                        links += classify(entry, name)
                } else if (Files.isDirectory(entry.toPath(), NOFOLLOW_LINKS)) walk(entry, name)
                check(links.size <= 20_000)
            }
        }
        walk(folder, "")
        return links
    }

    private fun safeDirectory(file: File): Boolean {
        val path = file.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(root)) return false
        var at = root
        if (!Files.isDirectory(at, NOFOLLOW_LINKS) || Files.isSymbolicLink(at)) return false
        for (part in root.relativize(path)) {
            at = at.resolve(part)
            if (!Files.isDirectory(at, NOFOLLOW_LINKS) || Files.isSymbolicLink(at)) return false
        }
        return true
    }

    private fun named(parent: File, path: String): File? {
        val found =
            if (path.startsWith(PROJECTS)) File(projects, path.removePrefix(PROJECTS))
            else File(parent, path)
        val normalized = found.toPath().toAbsolutePath().normalize().toFile()
        return normalized.takeIf { safeDirectory(it) }
    }

    private fun gitFolder(repository: File): File? {
        val dot = File(repository, ".git")
        if (safeDirectory(dot)) return dot
        if (!Files.isRegularFile(dot.toPath(), NOFOLLOW_LINKS) || dot.length() > 4096) return null
        val text =
            dot.readText()
                .lineSequence()
                .firstOrNull { it.startsWith("gitdir:") }
                ?.removePrefix("gitdir:")
                ?.trim() ?: return null
        val folder = named(repository, text) ?: return null
        val common = File(folder, "commondir")
        return if (Files.isRegularFile(common.toPath(), NOFOLLOW_LINKS) && common.length() <= 4096)
            named(folder, common.readText().trim())
        else folder
    }

    private fun classify(link: File, relative: String): ObjectLink {
        val first = Files.readSymbolicLink(link.toPath()).toString()
        var at = link
        repeat(8) {
            if (!safeDirectory(at.parentFile!!))
                return ObjectLink(link, first, relative, null)
            val target = Files.readSymbolicLink(at.toPath()).toString()
            var next = if (target.startsWith('/')) File(target) else File(at.parentFile, target)
            if (!Files.exists(next.toPath(), NOFOLLOW_LINKS))
                next = File(at.parentFile, File(target).name)
            if (!safeDirectory(next.parentFile!!))
                return ObjectLink(link, first, relative, null)
            if (Files.isSymbolicLink(next.toPath())) at = next
            else
                return ObjectLink(
                    link,
                    first,
                    relative,
                    next.takeIf { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) },
                )
        }
        return ObjectLink(link, first, relative, null)
    }

    private fun verify(relative: String, file: File): Boolean =
        try {
            if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) || file.length() > MAX_OBJECT) false
            else {
                val loose = LOOSE.find(relative)
                val packed = PACKED.find(relative)
                when {
                    loose != null -> verifyLoose(file, loose.groupValues[1] + loose.groupValues[2])
                    packed != null -> verifyPacked(file, if (packed.groupValues[1].length == 40) 20 else 32)
                    else -> false
                }
            }
        } catch (_: Exception) {
            false
        }

    private fun replace(link: ObjectLink, source: File): Boolean =
        try {
            require(current())
            check(unchanged(link))
            val temporary = File(link.link.parentFile, ".oc-repair-${UUID.randomUUID()}")
            try {
                copyNew(source, temporary)
                require(current())
                check(verify(link.relative, temporary))
                check(unchanged(link))
                Files.move(temporary.toPath(), link.link.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                true
            } finally {
                temporary.delete()
            }
        } catch (_: Exception) {
            false
        }

    /** The link is still the one classified, in a folder still inside the projects. */
    private fun unchanged(link: ObjectLink): Boolean =
        safeDirectory(link.link.parentFile!!) &&
            Files.isSymbolicLink(link.link.toPath()) &&
            Files.readSymbolicLink(link.link.toPath()).toString() == link.target
}

/** A guest path under `/root/projects/` with no empty, `.` or `..` part. */
private fun projectPath(directory: String): Boolean {
    if (!directory.startsWith(PROJECTS)) return false
    return directory.split('/').drop(3).none { it.isEmpty() || it == ".." || it == "." }
}

/** A loose object inflates to bytes whose SHA-1 or SHA-256 is its name. */
private fun verifyLoose(file: File, id: String): Boolean {
    val digest = MessageDigest.getInstance(if (id.length == 40) "SHA-1" else "SHA-256")
    var count = 0L
    InflaterInputStream(Files.newInputStream(file.toPath(), NOFOLLOW_LINKS)).use { stream ->
        val buffer = ByteArray(65_536)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            count += n
            check(count <= 512L * 1024 * 1024)
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == id
}

/** A pack, index or reverse index ends with the checksum of everything before it. */
private fun verifyPacked(file: File, size: Int): Boolean {
    val digest = MessageDigest.getInstance(if (size == 20) "SHA-1" else "SHA-256")
    var remaining = file.length() - size
    check(remaining > 0)
    val tail = ByteArray(size)
    Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(65_536)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            check(n > 0)
            digest.update(buffer, 0, n)
            remaining -= n
        }
        var position = 0
        while (position < size) {
            val n = input.read(tail, position, size - position)
            check(n > 0)
            position += n
        }
    }
    return digest.digest().contentEquals(tail)
}

private fun copyNew(source: File, target: File) {
    Files.newInputStream(source.toPath(), NOFOLLOW_LINKS).use { input ->
        Files.newOutputStream(target.toPath(), java.nio.file.StandardOpenOption.CREATE_NEW).use {
            input.copyTo(it)
        }
    }
}

private const val PROJECTS = "/root/projects/"
private const val MAX_OBJECT = 2L * 1024 * 1024 * 1024
private val LOOSE = Regex("(?:^|/)objects/([0-9a-f]{2})/([0-9a-f]{38}|[0-9a-f]{62})$")
private val PACKED = Regex("(?:^|/)objects/pack/pack-([0-9a-f]{40}|[0-9a-f]{64})\\.(?:pack|idx|rev)$")
