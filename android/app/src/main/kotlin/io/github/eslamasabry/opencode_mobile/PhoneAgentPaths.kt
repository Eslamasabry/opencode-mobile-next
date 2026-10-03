package io.github.eslamasabry.opencode_mobile

import java.io.File

/** Resolve the Android-owned anchor once, then reject links in managed paths. */
internal object PhoneAgentPaths {
    fun prepare(root: File, relative: String): File = managed(root, relative, true)
    fun resolve(root: File, relative: String): File = managed(root, relative, false)

    private fun managed(root: File, relative: String, create: Boolean): File {
        val segments = relative.split('/')
        check(segments.isNotEmpty() && segments.all {
            Regex("^[A-Za-z0-9_.-]+$").matches(it) && it != "." && it != ".."
        }) { "The agent host is unavailable." }
        // /data/user/0 is an Android-owned alias of /data/data on real phones.
        // Only this trusted anchor may resolve an alias; agent-controlled
        // descendants must retain their exact identity, including after mkdir.
        var directory = root.canonicalFile
        check(directory.isDirectory) { "The agent host is unavailable." }
        for (segment in segments) {
            directory = File(directory, segment)
            check(directory.canonicalFile == directory.absoluteFile) { "The agent host is unavailable." }
            if (create) {
                check(directory.mkdirs() || directory.isDirectory) { "The agent host is unavailable." }
                check(directory.canonicalFile == directory.absoluteFile && directory.isDirectory) {
                    "The agent host is unavailable."
                }
            }
        }
        return directory
    }
}
