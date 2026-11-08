package io.github.eslamasabry.oc.android.platform

import java.io.File
import java.io.IOException

/**
 * Which files may be handed to another app (Android's chooser or its package installer): a regular file inside the
 * phone's shared storage (the person's own folders, where the built-in Linux and Termux save), judged after symlinks
 * and `..` are resolved. Never the app's private storage (the built-in Linux root with the server password file is
 * there), never the storage root itself, never another app's `Android/` folders. Twin of Flutter's `LocalFileGate`
 * (`LocalFileGate.kt`, commit 39e15ae77) and `canonicalPhoneFile` (`lib/domain/phone_file.dart`).
 */
public object LocalFileGate {
    /** The canonical file when [path] may be handed over, else null. [sharedRoot] is the primary shared storage. */
    public fun allowed(sharedRoot: File, path: String?): File? {
        if (path.isNullOrEmpty()) return null
        val root = try { sharedRoot.canonicalFile } catch (_: IOException) { return null }
        val file = try { File(path).canonicalFile } catch (_: IOException) { return null }
        if (!file.isFile) return null
        val inside = file.toPath().startsWith(root.toPath()) && file.toPath() != root.toPath()
        if (!inside) return null
        val first = root.toPath().relativize(file.toPath()).getName(0).toString()
        if (first == "Android") return null
        return file
    }
}
