package io.github.eslamasabry.oc.android.termux

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

/** File-side repair of proot object links. No command in this class writes to Termux's source. */
class GitObjectRepair(
    private val projects: File,
    private val staging: File,
    private val termux: GitRepairTermuxFiles,
    private val current: () -> Boolean,
    private val verifyRepository: suspend (String) -> Boolean,
) {
    private data class ObjectLink(
        val link: File,
        val target: String,
        val relative: String,
        val local: File?,
        val remote: String?,
    )

    private val root = projects.toPath().toAbsolutePath().normalize()

    suspend fun repair(directory: String): GitLinkRepairReport {
        if (
            !directory.startsWith("/root/projects/") ||
                directory.split('/').drop(3).any { it.isEmpty() || it == ".." || it == "." }
        )
            return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
        require(current())
        var repository = File(projects, directory.removePrefix("/root/projects/"))
        var git: File? = null
        while (repository.toPath().toAbsolutePath().normalize() != root) {
            if (Files.isSymbolicLink(repository.toPath()))
                return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
            if (safeDirectory(repository)) {
                git = gitFolder(repository)
                if (git != null) break
            } else if (Files.exists(repository.toPath(), NOFOLLOW_LINKS))
                return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
            repository = repository.parentFile ?: break
        }
        val folder = git ?: return GitLinkRepairReport(GitLinkRepairResult.NotRepository)
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
        val remote = links.filter { it.remote != null }
        // Permission refusal changes no linked object, including locally recoverable objects.
        when (termux.available()) {
            GitRepairTermuxState.Permission ->
                if (remote.isNotEmpty())
                    return GitLinkRepairReport(GitLinkRepairResult.TermuxPermission)
            GitRepairTermuxState.Unavailable ->
                if (remote.isNotEmpty())
                    return GitLinkRepairReport(GitLinkRepairResult.TermuxUnavailable)
            else -> Unit
        }
        val missing = mutableListOf<String>()
        var restored = 0
        for (link in links.filter { it.remote == null }) {
            require(current())
            val source = link.local
            if (source != null && verify(link.relative, source) && replace(link, source)) restored++
            else missing += link.relative
        }
        val noTermux = termux.available() == GitRepairTermuxState.Missing
        if (remote.isNotEmpty() && noTermux) missing += remote.map { it.relative }
        else if (remote.isNotEmpty()) {
            require(current())
            check(!Files.isSymbolicLink(staging.toPath()))
            Files.createDirectories(staging.toPath())
            val job = File(staging, UUID.randomUUID().toString())
            check(job.mkdir())
            try {
                val fetched = termux.fetch(remote.map { requireNotNull(it.remote) }, job)
                if (fetched == null)
                    return GitLinkRepairReport(
                        GitLinkRepairResult.TermuxUnavailable,
                        restored,
                        missing,
                    )
                for ((index, link) in remote.withIndex()) {
                    require(current())
                    val source = fetched[index]
                    if (source != null && verify(link.relative, source) && replace(link, source))
                        restored++
                    else missing += link.relative
                }
            } finally {
                Files.walkFileTree(
                    job.toPath(),
                    object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        override fun visitFile(
                            file: java.nio.file.Path,
                            attributes: java.nio.file.attribute.BasicFileAttributes,
                        ): java.nio.file.FileVisitResult {
                            Files.deleteIfExists(file)
                            return java.nio.file.FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(
                            dir: java.nio.file.Path,
                            error: java.io.IOException?,
                        ): java.nio.file.FileVisitResult {
                            if (error != null) throw error
                            Files.deleteIfExists(dir)
                            return java.nio.file.FileVisitResult.CONTINUE
                        }
                    },
                )
            }
        }
        require(current())
        val guestRepository =
            "/root/projects/" +
                root.relativize(repository.toPath().toAbsolutePath().normalize()).toString()
        val valid = verifyRepository(guestRepository)
        return GitLinkRepairReport(
            when {
                noTermux && remote.isNotEmpty() -> GitLinkRepairResult.TermuxMissing
                missing.isNotEmpty() || !valid -> GitLinkRepairResult.MissingObjects
                restored == 0 -> GitLinkRepairResult.Healthy
                else -> GitLinkRepairResult.Repaired
            },
            restored,
            missing.sorted(),
        )
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
            if (path.startsWith("/root/projects/"))
                File(projects, path.removePrefix("/root/projects/"))
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
                return ObjectLink(link, first, relative, null, null)
            val target = Files.readSymbolicLink(at.toPath()).toString()
            var next = if (target.startsWith('/')) File(target) else File(at.parentFile, target)
            if (GitRepairTermuxFiles.STORE.matches(next.path))
                return ObjectLink(link, first, relative, null, next.path)
            if (!Files.exists(next.toPath(), NOFOLLOW_LINKS))
                next = File(at.parentFile, File(target).name)
            if (!safeDirectory(next.parentFile!!))
                return ObjectLink(link, first, relative, null, null)
            if (Files.isSymbolicLink(next.toPath())) at = next
            else
                return ObjectLink(
                    link,
                    first,
                    relative,
                    next.takeIf { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) },
                    null,
                )
        }
        return ObjectLink(link, first, relative, null, null)
    }

    private fun verify(relative: String, file: File): Boolean =
        try {
            if (
                !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) ||
                    file.length() > 2L * 1024 * 1024 * 1024
            )
                false
            else {
                val loose =
                    Regex("(?:^|/)objects/([0-9a-f]{2})/([0-9a-f]{38}|[0-9a-f]{62})$")
                        .find(relative)
                if (loose != null) {
                    val id = loose.groupValues[1] + loose.groupValues[2]
                    val digest =
                        MessageDigest.getInstance(if (id.length == 40) "SHA-1" else "SHA-256")
                    var count = 0L
                    InflaterInputStream(Files.newInputStream(file.toPath(), NOFOLLOW_LINKS)).use {
                        stream ->
                        val buffer = ByteArray(65_536)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            count += n
                            check(count <= 512L * 1024 * 1024)
                            digest.update(buffer, 0, n)
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) } == id
                } else {
                    val packed =
                        Regex(
                                "(?:^|/)objects/pack/pack-([0-9a-f]{40}|[0-9a-f]{64})\\.(?:pack|idx|rev)$"
                            )
                            .find(relative)
                    if (packed == null) false
                    else {
                        val size = if (packed.groupValues[1].length == 40) 20 else 32
                        val digest =
                            MessageDigest.getInstance(if (size == 20) "SHA-1" else "SHA-256")
                        var remaining = file.length() - size
                        check(remaining > 0)
                        val tail = ByteArray(size)
                        Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
                            val buffer = ByteArray(65_536)
                            while (remaining > 0) {
                                val n =
                                    input.read(
                                        buffer,
                                        0,
                                        minOf(remaining, buffer.size.toLong()).toInt(),
                                    )
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
                        digest.digest().contentEquals(tail)
                    }
                }
            }
        } catch (_: Exception) {
            false
        }

    private fun replace(link: ObjectLink, source: File): Boolean =
        try {
            require(current())
            check(safeDirectory(link.link.parentFile!!))
            check(
                Files.isSymbolicLink(link.link.toPath()) &&
                    Files.readSymbolicLink(link.link.toPath()).toString() == link.target
            )
            val temporary = File(link.link.parentFile, ".oc-repair-${UUID.randomUUID()}")
            try {
                Files.newInputStream(source.toPath(), NOFOLLOW_LINKS).use { input ->
                    Files.newOutputStream(
                            temporary.toPath(),
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                        )
                        .use { input.copyTo(it) }
                }
                require(current())
                check(verify(link.relative, temporary))
                check(safeDirectory(link.link.parentFile!!))
                check(
                    Files.isSymbolicLink(link.link.toPath()) &&
                        Files.readSymbolicLink(link.link.toPath()).toString() == link.target
                )
                Files.move(temporary.toPath(), link.link.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                true
            } finally {
                temporary.delete()
            }
        } catch (_: Exception) {
            false
        }
}
