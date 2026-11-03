package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.Deflater

/**
 * Makes a new project folder with its git repository from the app itself,
 * without running anything inside Ubuntu (issue #111, FIX-4).
 *
 * Once AI Team is on, every Ubuntu process runs in the protected sandbox,
 * whose seccomp filter denies chmod on purpose (the boundary proof needs
 * that). `git init` must chmod `.git/config.lock`, so the old shell script
 * failed and no new project could be made. The sandbox stays as it is; the
 * app writes the repository instead, before and after AI Team is on, so both
 * give the same result.
 *
 * The result matches what `git init` plus `git commit --allow-empty -m
 * "First commit"` of the Ubuntu's git 2.43 left: same config, same default
 * branch, one empty first commit, same reflogs and index. The only
 * difference: hooks/ is empty, without git's inert `*.sample` files.
 *
 * Safety: the repository is built in [stagingRoot], a private folder no
 * Ubuntu process can reach, and moved into the projects folder with one
 * rename. Nothing is ever written through a path a sandboxed process could
 * have swapped for a link: the projects folder itself can't be replaced from
 * inside (its parent is not writable there), and rename(2) never follows a
 * link at its target.
 */
object BuiltinNewProject {
    const val PROJECTS_IN_UBUNTU = "/root/projects"
    private const val EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
    private const val MESSAGE = "First commit\n"

    enum class Outcome(val wire: String) { CREATED("created"), EXISTS("exists") }

    /** What the Ubuntu's git config would have given the shell script. */
    data class Defaults(
        val name: String = "AI Team",
        val email: String = "aiteam@localhost",
        val branch: String = "master",
    )

    /**
     * The folder name when [path] is a direct child of /root/projects (the
     * only place this makes projects); null for anything else.
     */
    fun managedName(path: String): String? {
        val prefix = "$PROJECTS_IN_UBUNTU/"
        if (!path.startsWith(prefix)) return null
        val name = path.substring(prefix.length)
        if (name.isEmpty() || name == "." || name == ".." || name.length > 255) return null
        if (name.any { it == '/' || it == '\u0000' || it == '\n' || it == '\r' }) return null
        return name
    }

    /**
     * Makes [name] inside [projects] as a git project with one empty commit,
     * or reports [Outcome.EXISTS] for a folder that is already there.
     */
    fun create(
        projects: File,
        name: String,
        stagingRoot: File,
        defaults: Defaults,
        epochSeconds: Long,
    ): Outcome {
        require(managedName("$PROJECTS_IN_UBUNTU/$name") == name) { "Invalid project name" }
        check(isRealDirectory(projects)) { "The projects folder is missing" }
        val target = File(projects, name)
        if (presentAsFolder(target)) return Outcome.EXISTS
        check(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Not a folder" }

        prepareStaging(stagingRoot)
        val staged = Files.createTempDirectory(stagingRoot.toPath(), "p").toFile()
        try {
            writeRepository(staged, defaults, epochSeconds)
            try {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (raced: Exception) {
                // Something made a folder of that name while the repository
                // was built; it is left alone, like the script did.
                if (presentAsFolder(target)) return Outcome.EXISTS
                throw raced
            }
            return Outcome.CREATED
        } finally {
            if (staged.exists()) staged.deleteRecursively()
        }
    }

    /** The repository: `.git` inside [folder], laid out as git 2.43 makes it. */
    internal fun writeRepository(folder: File, defaults: Defaults, epochSeconds: Long) {
        val git = File(folder, ".git")
        for (dir in listOf("branches", "hooks", "info", "objects/info", "objects/pack", "refs/tags")) {
            check(File(git, dir).mkdirs()) { "Could not make $dir" }
        }
        val branch = defaults.branch
        File(git, "description").writeText(
            "Unnamed repository; edit this file 'description' to name the repository.\n")
        File(git, "info/exclude").writeText(
            "# git ls-files --others --exclude-from=.git/info/exclude\n" +
                "# Lines that start with '#' are comments.\n" +
                "# For a project mostly in C, the following would be a good set of\n" +
                "# exclude patterns (uncomment them if you want to use them):\n" +
                "# *.[oa]\n" +
                "# *~\n")
        File(git, "config").writeText(
            "[core]\n\trepositoryformatversion = 0\n\tfilemode = true\n" +
                "\tbare = false\n\tlogallrefupdates = true\n")
        File(git, "HEAD").writeText("ref: refs/heads/$branch\n")

        writeObject(git, "tree", ByteArray(0)).also { check(it == EMPTY_TREE) }
        val who = "${defaults.name} <${defaults.email}> $epochSeconds +0000"
        val commit = writeObject(git, "commit",
            "tree $EMPTY_TREE\nauthor $who\ncommitter $who\n\n$MESSAGE".toByteArray(Charsets.UTF_8))

        val ref = File(git, "refs/heads/$branch")
        ref.parentFile!!.mkdirs()
        ref.writeText("$commit\n")
        val log = "0000000000000000000000000000000000000000 $commit $who\tcommit (initial): First commit\n"
        File(git, "logs/HEAD").apply { parentFile!!.mkdirs() }.writeText(log)
        File(git, "logs/refs/heads/$branch").apply { parentFile!!.mkdirs() }.writeText(log)
        File(git, "COMMIT_EDITMSG").writeText(MESSAGE)
        File(git, "index").writeBytes(emptyIndex())
    }

    /** An index with no entries and the empty tree cached, as `git commit` leaves it. */
    internal fun emptyIndex(): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("DIRC".toByteArray(Charsets.US_ASCII))
        body.write(int32(2))
        body.write(int32(0))
        val tree = ByteArrayOutputStream().apply {
            write(0)
            write("0 0\n".toByteArray(Charsets.US_ASCII))
            write(unhex(EMPTY_TREE))
        }.toByteArray()
        body.write("TREE".toByteArray(Charsets.US_ASCII))
        body.write(int32(tree.size))
        body.write(tree)
        val bytes = body.toByteArray()
        return bytes + MessageDigest.getInstance("SHA-1").digest(bytes)
    }

    private fun writeObject(git: File, type: String, content: ByteArray): String {
        val raw = "$type ${content.size}\u0000".toByteArray(Charsets.US_ASCII) + content
        val id = hex(MessageDigest.getInstance("SHA-1").digest(raw))
        val deflater = Deflater(Deflater.BEST_SPEED)
        val packed = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        val file = File(git, "objects/${id.substring(0, 2)}/${id.substring(2)}")
        file.parentFile!!.mkdirs()
        file.writeBytes(packed)
        file.setWritable(false, false)
        return id
    }

    /**
     * user.name, user.email and init.defaultBranch from the Ubuntu's
     * /etc/gitconfig, then /root/.gitconfig (which wins), as `git config`
     * read them for the shell script. Values that git would not accept in a
     * commit header or a branch name are ignored. Links are never followed:
     * the Ubuntu folders are writable from the sandbox.
     */
    fun readDefaults(rootfs: File): Defaults {
        var values = Defaults()
        for (relative in listOf("etc/gitconfig", "root/.gitconfig")) {
            val text = readPlainFile(rootfs, relative) ?: continue
            values = applyConfig(values, text)
        }
        return values
    }

    internal fun applyConfig(start: Defaults, text: String): Defaults {
        var values = start
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("[")) {
                section = line.removePrefix("[").substringBefore("]").trim().lowercase()
                continue
            }
            val equals = line.indexOf('=')
            if (equals <= 0) continue
            val key = line.substring(0, equals).trim().lowercase()
            val value = unquote(line.substring(equals + 1).trim())
            values = when ("$section.$key") {
                "user.name" -> if (validIdentity(value)) values.copy(name = value) else values
                "user.email" -> if (validIdentity(value)) values.copy(email = value) else values
                "init.defaultbranch" -> if (validBranch(value)) values.copy(branch = value) else values
                else -> values
            }
        }
        return values
    }

    private fun unquote(value: String): String {
        val bare = if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value.substring(1, value.length - 1)
        } else {
            value.substringBefore(" #").substringBefore(" ;").trim()
        }
        return bare
    }

    internal fun validIdentity(value: String): Boolean =
        value.isNotBlank() && value.length <= 128 &&
            value.none { it == '<' || it == '>' || it == '"' || it == '\\' || it.code < 0x20 || it.code == 0x7f }

    internal fun validBranch(value: String): Boolean =
        value.length in 1..100 &&
            Regex("^[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*)*$").matches(value) &&
            !value.contains("..") && !value.endsWith(".lock") && !value.endsWith(".") &&
            value != "HEAD"

    private fun readPlainFile(rootfs: File, relative: String): String? {
        var current = rootfs
        for (part in relative.split('/')) {
            if (Files.isSymbolicLink(current.toPath())) return null
            current = File(current, part)
        }
        val path = current.toPath()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.size(path) > 64 * 1024) return null
        return try { String(Files.readAllBytes(path), Charsets.UTF_8) } catch (_: Exception) { null }
    }

    private fun prepareStaging(stagingRoot: File) {
        if (Files.isSymbolicLink(stagingRoot.toPath())) Files.delete(stagingRoot.toPath())
        if (!stagingRoot.isDirectory) check(stagingRoot.mkdirs()) { "No staging folder" }
        // A build an earlier run did not finish (the app was killed).
        stagingRoot.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun isRealDirectory(file: File): Boolean =
        Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    /** `[ -d ]` as the shell script asked it: a folder, or a link to one. */
    private fun presentAsFolder(file: File): Boolean = Files.isDirectory(file.toPath())

    private fun int32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String) = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
