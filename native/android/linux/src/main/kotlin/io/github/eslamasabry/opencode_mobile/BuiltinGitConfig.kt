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

    internal const val SHARED_BEGIN = "# Begin OpenCode Mobile: folders chosen in the phone's storage\n"
    internal const val SHARED_END = "# End OpenCode Mobile: folders chosen in the phone's storage\n"

    /**
     * Issue 147: Git works in the folders the person chose in the phone's storage, for root and the agent user alike.
     * Android's storage reports its own owner for every file, which need not be the Linux identity working there, and
     * Git then refuses the repository as of "dubious ownership". Each folder (and its `/sdcard` spelling) is listed as
     * `safe.directory` in one block the app owns in /etc/gitconfig, rewritten when the folders change and removed when
     * none is left. True when the file was written.
     *
     * Git matches `safe.directory` by a repository's own top folder, so a repository one level inside a chosen folder
     * (a folder of projects chosen as a whole) is listed too, by the names [repositoriesInside] finds (the owner's
     * decision for W3: one level, nothing deeper). They are found again at each start of the phone's Linux.
     */
    fun trustSharedFolders(
        rootfs: File,
        folders: List<String>,
        repositoriesInside: (String) -> List<String> = ::repositoriesOnPhone,
    ): Boolean {
        val config = target(rootfs) ?: return false
        val existing = if (config.isFile) config.readText() else ""
        val start = existing.indexOf(SHARED_BEGIN)
        val end = existing.indexOf(SHARED_END)
        val outside =
            if (start >= 0 && end > start) existing.removeRange(start, end + SHARED_END.length) else existing
        val spellings =
            folders
                .mapNotNull { SharedStorageBinds.canonicalRoot(it) }
                .flatMap { root -> listOf(root) + nested(root, repositoriesInside) }
                .mapNotNull { SharedStorageBinds.canonicalRoot(it) }
                .flatMap { root ->
                    listOf(root) +
                        if (root.startsWith("$PRIMARY/")) listOf("/sdcard" + root.removePrefix(PRIMARY))
                        else emptyList()
                }
                .filter { path -> path.none { it == '\n' || it == '"' || it == '\\' } }
                .distinct()
                .sorted()
        val block =
            if (spellings.isEmpty()) ""
            // Quoted: a folder name may hold '#' or ';', which start a comment in Git's config.
            else SHARED_BEGIN + "[safe]\n" + spellings.joinToString("") { "\tdirectory = \"$it\"\n" } + SHARED_END
        val separated = if (block.isEmpty() || outside.isEmpty() || outside.endsWith("\n")) outside else outside + "\n"
        val wanted = separated + block
        return wanted != existing && replaceWith(config, wanted)
    }

    private const val PRIMARY = "/storage/emulated/0"

    /** The repositories one level inside [root] that [find] names, as paths; plain names only. */
    private fun nested(root: String, find: (String) -> List<String>): List<String> =
        try {
            find(root)
                .filter { name -> name.isNotEmpty() && name != "." && name != ".." && '/' !in name }
                .map { "$root/$it" }
        } catch (_: Exception) {
            emptyList()
        }

    /** The folders one level inside [root] holding a Git repository (a `.git` folder or file), as the app sees them. */
    internal fun repositoriesOnPhone(root: String): List<String> =
        File(root).listFiles { child -> child.isDirectory && File(child, ".git").exists() }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

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
