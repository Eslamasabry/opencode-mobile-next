package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * One Claude Code sign-in on the phone (docs/qa/claude-phone-auth-2026-10-16).
 *
 * Everything the app starts (the Paseo helper, the sign-in check, the sign-in
 * terminal) runs Claude with `CLAUDE_CONFIG_DIR=/home/oc/.oc-profiles/<id>/claude`.
 * Claude started by hand in the Terminal (`su oc -c claude`, or the `claude`
 * launcher) has no such variable and uses `/home/oc/.claude`, so a sign-in
 * made there was never seen by the app, and the app's never by the Terminal.
 *
 * [prepare] makes `/home/oc/.claude` a link to the profile's folder, so both
 * read and write the same files. A real `/home/oc/.claude` left from before is
 * adopted once: its files are copied into the profile's folder without
 * replacing any (its sign-in only when the profile has none), and the folder
 * itself is kept, renamed `.claude.before-app`. Nothing is deleted.
 *
 * Paths here are the Android side of the Ubuntu tree: [agentHome] is
 * `<rootfs>/home/oc`, [rootHome] is `<rootfs>/root`.
 */
internal object PhoneAgentClaudeHome {
    const val LINK = ".claude"
    const val CREDENTIALS = ".credentials.json"
    const val KEPT = ".claude.before-app"
    private const val MAX_CREDENTIALS = 64 * 1024L
    private val PROFILE = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val OUR_LINK = Regex("^\\.oc-profiles/[A-Za-z0-9_-]{1,80}/claude$")

    /** The link's target, relative so it resolves the same inside and outside PRoot. */
    fun linkTarget(profileId: String): String = ".oc-profiles/$profileId/claude"

    enum class Step { CREDENTIALS_ADOPTED, FILES_ADOPTED, LINKED, RELINKED, FOREIGN_LEFT_ALONE }

    fun prepare(agentHome: File, profileId: String, rootHome: File? = null): Set<Step> {
        require(PROFILE.matches(profileId))
        val steps = mutableSetOf<Step>()
        val home = agentHome.toPath()
        require(Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS))
        val profile = home.resolve(linkTarget(profileId))
        Files.createDirectories(profile)
        require(Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS) &&
            profile.toRealPath() == home.toRealPath().resolve(linkTarget(profileId)))
        privateDirectory(profile)

        val link = home.resolve(LINK)
        val oldDirectory = if (isRealDirectory(link)) link else null
        val sources = listOfNotNull(
            oldDirectory,
            home.resolve("claude").takeIf { isRealDirectory(it) },
            rootHome?.toPath()?.resolve(LINK)?.takeIf { isRealDirectory(it) },
        )
        if (adoptCredentials(profile, sources)) steps.add(Step.CREDENTIALS_ADOPTED)

        steps.addAll(pointLink(home, link, profile, oldDirectory, profileId))
        return steps
    }

    private fun pointLink(home: Path, link: Path, profile: Path, oldDirectory: Path?, profileId: String): List<Step> =
        when {
            Files.isSymbolicLink(link) -> repoint(link, profileId)
            oldDirectory != null -> {
                val adopted = copyMissing(oldDirectory, profile)
                Files.move(oldDirectory, freeName(home))
                Files.createSymbolicLink(link, Paths.get(linkTarget(profileId)))
                listOfNotNull(Step.FILES_ADOPTED.takeIf { adopted }, Step.LINKED)
            }
            Files.exists(link, LinkOption.NOFOLLOW_LINKS) -> listOf(Step.FOREIGN_LEFT_ALONE)
            else -> {
                Files.createSymbolicLink(link, Paths.get(linkTarget(profileId)))
                listOf(Step.LINKED)
            }
        }

    private fun repoint(link: Path, profileId: String): List<Step> {
        val target = Files.readSymbolicLink(link).toString()
        return when {
            target == linkTarget(profileId) -> emptyList()
            !OUR_LINK.matches(target) -> listOf(Step.FOREIGN_LEFT_ALONE)
            else -> {
                // Another phone profile's link: point it at this profile now.
                Files.delete(link)
                Files.createSymbolicLink(link, Paths.get(linkTarget(profileId)))
                listOf(Step.RELINKED)
            }
        }
    }

    /** Before a profile's folder is erased: its link would point at nothing. */
    fun forget(agentHome: File, profileId: String) {
        require(PROFILE.matches(profileId))
        val link = agentHome.toPath().resolve(LINK)
        if (Files.isSymbolicLink(link) && Files.readSymbolicLink(link).toString() == linkTarget(profileId)) {
            Files.delete(link)
        }
    }

    /**
     * Copies the newest sign-in among [sources] into [profile] when it has
     * none. A profile's own sign-in is never replaced.
     */
    private fun adoptCredentials(profile: Path, sources: List<Path>): Boolean {
        val own = profile.resolve(CREDENTIALS)
        val newest = if (Files.exists(own, LinkOption.NOFOLLOW_LINKS)) null else newestCredentials(sources)
        if (newest == null) return false
        val temporary = profile.resolve("$CREDENTIALS.adopting")
        Files.deleteIfExists(temporary)
        Files.copy(newest, temporary, StandardCopyOption.COPY_ATTRIBUTES)
        privateFile(temporary)
        Files.move(temporary, own, StandardCopyOption.ATOMIC_MOVE)
        return true
    }

    private fun newestCredentials(sources: List<Path>): Path? = sources.map { it.resolve(CREDENTIALS) }
        .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
        .filter { Files.size(it) in 1..MAX_CREDENTIALS }
        .maxByOrNull { Files.getLastModifiedTime(it, LinkOption.NOFOLLOW_LINKS).toMillis() }

    /** Copies what [into] lacks; never replaces a file and never follows a link. */
    private fun copyMissing(from: Path, into: Path): Boolean {
        var copied = false
        Files.newDirectoryStream(from).use { entries ->
            for (entry in entries) if (copyEntry(entry, into)) copied = true
        }
        return copied
    }

    private fun copyEntry(entry: Path, into: Path): Boolean {
        val name = entry.fileName.toString()
        val target = into.resolve(name)
        return when {
            name == CREDENTIALS || Files.isSymbolicLink(entry) -> false
            Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) -> copyDirectory(entry, target)
            Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(target, LinkOption.NOFOLLOW_LINKS) -> {
                Files.copy(entry, target, StandardCopyOption.COPY_ATTRIBUTES)
                true
            }
            else -> false
        }
    }

    private fun copyDirectory(entry: Path, target: Path): Boolean {
        val blocked = Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
        if (blocked) return false
        Files.createDirectories(target)
        return copyMissing(entry, target)
    }
}

private fun freeName(home: Path): Path {
    var candidate = home.resolve(PhoneAgentClaudeHome.KEPT)
    var n = 2
    while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
        candidate = home.resolve("${PhoneAgentClaudeHome.KEPT}-${n++}")
    }
    return candidate
}

private fun isRealDirectory(path: Path): Boolean =
    !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)

private fun privateDirectory(path: Path) {
    val file = path.toFile()
    file.setReadable(false, false); file.setWritable(false, false); file.setExecutable(false, false)
    file.setReadable(true, true); file.setWritable(true, true); file.setExecutable(true, true)
}

private fun privateFile(path: Path) {
    val file = path.toFile()
    file.setReadable(false, false); file.setWritable(false, false); file.setExecutable(false, false)
    file.setReadable(true, true); file.setWritable(true, true)
}
