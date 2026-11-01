package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files

/**
 * Git in the built-in Ubuntu puts new objects in place by renaming them.
 *
 * Git's default is a hard link followed by a delete. The app's proot runs
 * with --link2symlink, which turns every hard link into a symlink to an
 * absolute host path next to the original file. Such a repository works
 * until its folder moves: renaming the project, an agent's `mv`, or an
 * older install moving /root/projects out of Ubuntu each left every object
 * link pointing at the old path, and Git then said `bad object HEAD`.
 * `core.createObject = rename` keeps objects as plain files from the start.
 *
 * Written to /etc/gitconfig (the system level, so the root user and the
 * agent user both read it) only when nobody has set createObject there.
 */
object BuiltinGitConfig {
    internal const val SECTION =
        "\n# Added by OpenCode Mobile: proot turns Git's hard links into links to an\n" +
            "# absolute path, which break when a project folder moves.\n" +
            "[core]\n\tcreateObject = rename\n"

    private val alreadySet = Regex("(?im)^\\s*createobject\\s*=")

    /** True when the file was written; false when it already said, or can't. */
    fun preferRenames(rootfs: File): Boolean {
        val config = target(rootfs) ?: return false
        val existing = if (config.isFile) config.readText() else ""
        return !alreadySet.containsMatchIn(existing) && replaceWith(config, existing + SECTION)
    }

    /** /etc/gitconfig of [rootfs], or null when it (or /etc) is a link or something odd. */
    private fun target(rootfs: File): File? {
        val etc = File(rootfs, "etc")
        if (!etc.isDirectory || Files.isSymbolicLink(etc.toPath())) return null
        val config = File(etc, "gitconfig")
        val unusable = Files.isSymbolicLink(config.toPath()) || (config.exists() && !config.isFile)
        return config.takeUnless { unusable }
    }

    private fun replaceWith(config: File, text: String): Boolean {
        val temporary = File(config.parentFile, ".gitconfig.oc-tmp")
        temporary.writeText(text)
        val renamed = temporary.renameTo(config)
        if (!renamed) temporary.delete()
        return renamed
    }
}
