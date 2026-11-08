package io.github.eslamasabry.opencode_mobile

import java.io.File

/**
 * Which files [LaunchShareChannel] may hand to another app through the
 * `.localfiles` FileProvider: a regular file inside the phone's shared storage
 * (the person's own folders, where the built-in Linux and Termux save), judged
 * after symlinks and `..` are resolved. Never the app's private storage (the
 * built-in Linux root with the server password file is there), never another
 * app's `Android/` folders. Dart repeats this in `canonicalPhoneFile`
 * (lib/domain/phone_file.dart): keep both in step.
 */
internal object LocalFileGate {
    fun allowed(sharedRoot: File, path: String?): File? {
        if (path.isNullOrEmpty()) return null
        val root = canonicalOrNull(sharedRoot)
        val file = canonicalOrNull(File(path))
        return if (root != null && file != null && isShared(root, file)) file else null
    }

    private fun canonicalOrNull(file: File): File? =
        try { file.canonicalFile } catch (_: java.io.IOException) { null }

    private fun isShared(root: File, file: File): Boolean {
        val inside = file.isFile && file.toPath().startsWith(root.toPath()) &&
            file.toPath() != root.toPath()
        return inside && root.toPath().relativize(file.toPath()).getName(0).toString() != "Android"
    }
}
