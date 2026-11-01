package io.github.eslamasabry.opencode_mobile

import java.io.File

/**
 * Names the Android groups the app's processes belong to (inet 3003,
 * everybody 9997, the app's cache and shared groups…) in the guest's
 * /etc/group. Ubuntu's login profile runs `groups`, which otherwise prints
 * "cannot find name for group ID 3003" once per group before the prompt.
 * The group ids are per install, so this runs on every guest launch.
 */
object AndroidGroupNames {
    /** The supplementary group ids listed on a `Groups:` line of /proc/<pid>/status. */
    fun parseGroups(status: String): List<Int> = status.lineSequence()
        .firstOrNull { it.startsWith("Groups:") }
        ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))
        ?.mapNotNull { it.toIntOrNull() }
        .orEmpty()

    /**
     * [groupFile] with a `aid_<id>` line for every id in [gids] that has no
     * line yet, or null when nothing is missing. Idempotent; keeps a last line
     * that lacks its newline from swallowing the new ones.
     */
    fun merged(groupFile: String, gids: List<Int>): String? {
        val existing = groupFile.lineSequence().mapNotNull { it.split(':').getOrNull(2)?.toIntOrNull() }.toSet()
        val missing = gids.filter { it !in existing }.distinct()
        if (missing.isEmpty()) return null
        val lead = if (groupFile.isEmpty() || groupFile.endsWith("\n")) "" else "\n"
        return groupFile + lead + missing.joinToString("") { "aid_$it:x:$it:\n" }
    }

    /** Best effort: a failure here must never stop a shell from starting. */
    fun apply(rootfs: File, statusFile: File = File("/proc/self/status")) {
        try {
            val group = File(rootfs, "etc/group")
            if (!group.isFile) return
            val updated = merged(group.readText(), parseGroups(statusFile.readText())) ?: return
            val staged = File(group.parentFile, "group.oc-new")
            staged.writeText(updated)
            if (!staged.renameTo(group)) {
                staged.delete()
                group.writeText(updated)
            }
        } catch (_: Exception) {
        }
    }
}
