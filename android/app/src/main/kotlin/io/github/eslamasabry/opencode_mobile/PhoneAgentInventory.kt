package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Read-only presence of catalog-owned installation files, never account data. */
internal object PhoneAgentInventory {
    private val agents = mapOf(
        "codex" to "codex", "gemini" to "gemini", "qwen" to "qwen",
        "goose" to "goose", "omp" to "omp-acp", "fx" to "fx",
    )

    private const val MAX_TEMPORARY_LAUNCHERS = 256

    /** Null means an unsafe/unreadable path, not evidence of an absent payload. */
    fun present(rootfs: File, executable: String): Boolean? {
        val agent = agents[executable] ?: return false
        return try { probe(rootfs, executable, agent) } catch (_: Exception) { null }
    }

    private fun probe(rootfs: File, executable: String, agent: String): Boolean? {
        // Resolve only the Android-owned parent alias, never guest links.
        val root = File(rootfs.parentFile.canonicalFile, rootfs.name).toPath()
        val home = root.resolve("home/oc")
        val parent = home.resolve(".local/share/oc-agents")
        val bin = home.resolve(".local/bin")
        val ancestors = listOf(root, root.resolve("home"), home,
            home.resolve(".local"), home.resolve(".local/share"), parent, bin)
        val unsafe = ancestors.any {
            Files.exists(it, NOFOLLOW_LINKS) && !Files.isDirectory(it, NOFOLLOW_LINKS)
        }
        val existing = if (unsafe) null else
            listOf(parent.resolve(agent), parent.resolve(".lock-$executable"))
                .firstOrNull { Files.exists(it, NOFOLLOW_LINKS) }
        return when {
            unsafe -> null
            existing != null -> if (Files.isDirectory(existing, NOFOLLOW_LINKS)) true else null
            else -> launcherPresent(bin, agent, executable)
        }
    }

    private fun launcherPresent(bin: Path, agent: String, executable: String): Boolean? = when {
        authoredLink(bin.resolve(executable), agent) -> true
        !Files.isDirectory(bin, NOFOLLOW_LINKS) -> false
        else -> temporaryLauncherPresent(bin, agent, executable)
    }

    // Only installer-authored decimal-PID temporary launcher names.
    private fun temporaryLauncherPresent(bin: Path, agent: String, executable: String): Boolean? =
        Files.newDirectoryStream(bin, "$executable.new.*").use { entries ->
            val seen = entries.asSequence().take(MAX_TEMPORARY_LAUNCHERS + 1).toList()
            val found = seen.take(MAX_TEMPORARY_LAUNCHERS).any { path ->
                path.fileName.toString().removePrefix("$executable.new.").matches(Regex("[0-9]+")) &&
                    authoredLink(path, agent)
            }
            if (found) true else if (seen.size > MAX_TEMPORARY_LAUNCHERS) null else false
        }

    private fun authoredLink(path: Path, agent: String): Boolean = Files.isSymbolicLink(path) &&
        Regex("^/home/oc/\\.local/share/oc-agents/$agent/[0-9A-Za-z][0-9A-Za-z._+-]{0,79}/launch$")
            .matches(Files.readSymbolicLink(path).toString())
}
