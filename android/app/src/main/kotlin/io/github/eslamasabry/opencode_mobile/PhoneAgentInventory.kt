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

    /** Null means an unsafe/unreadable path, not evidence of an absent payload. */
    fun present(rootfs: File, executable: String): Boolean? {
        val agent = agents[executable] ?: return false
        return try {
            // Resolve only the Android-owned parent alias, never guest links.
            val root = File(rootfs.parentFile.canonicalFile, rootfs.name).toPath()
            val home = root.resolve("home/oc")
            val parent = home.resolve(".local/share/oc-agents")
            val bin = home.resolve(".local/bin")
            val ancestors = listOf(root, root.resolve("home"), home,
                home.resolve(".local"), home.resolve(".local/share"), parent, bin)
            if (ancestors.any { Files.exists(it, NOFOLLOW_LINKS) &&
                    !Files.isDirectory(it, NOFOLLOW_LINKS) }) return null
            val base = parent.resolve(agent)
            if (Files.exists(base, NOFOLLOW_LINKS)) {
                return if (Files.isDirectory(base, NOFOLLOW_LINKS)) true else null
            }
            val lock = parent.resolve(".lock-$executable")
            if (Files.exists(lock, NOFOLLOW_LINKS)) {
                return if (Files.isDirectory(lock, NOFOLLOW_LINKS)) true else null
            }
            fun authoredLink(path: Path): Boolean = Files.isSymbolicLink(path) &&
                Regex("^/home/oc/\\.local/share/oc-agents/$agent/[0-9A-Za-z][0-9A-Za-z._+-]{0,79}/launch$")
                    .matches(Files.readSymbolicLink(path).toString())
            if (authoredLink(bin.resolve(executable))) return true
            if (!Files.isDirectory(bin, NOFOLLOW_LINKS)) return false
            // Only installer-authored decimal-PID temporary launcher names.
            Files.newDirectoryStream(bin, "$executable.new.*").use { entries ->
                var scanned = 0
                for (path in entries) {
                    if (++scanned > 256) return null
                    if (path.fileName.toString().removePrefix("$executable.new.")
                            .matches(Regex("[0-9]+")) && authoredLink(path)) return true
                }
            }
            false
        } catch (_: Exception) { null }
    }
}
