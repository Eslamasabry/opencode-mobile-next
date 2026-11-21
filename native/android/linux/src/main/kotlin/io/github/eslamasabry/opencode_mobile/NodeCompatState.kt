package io.github.eslamasabry.opencode_mobile

import java.io.File

/**
 * The Node.js safe mode the Node install saved inside Ubuntu (issues #117/#119; twin of the Flutter fix/117-adapt
 * `node_compat.dart`). The install writes `/etc/oc-setup/node-compat` (the mode, and `proot-compat` on a second line
 * when Node only ran with the proot in compatibility mode) and `/etc/oc-setup/node-compat.env`. The shell text below is
 * the same as `NodeCompat.SERVER_ENV` in :engine:linuxsetup and `NodeCompatScripts.SERVER_ENV` in :engine:runtime; this
 * module depends on neither, so it keeps its own copy.
 */
internal object NodeCompatState {
    /** The state file's path inside the root filesystem. */
    const val STATE_PATH = "etc/oc-setup/node-compat"
    private const val PROOT_COMPAT = "proot-compat"
    private const val DEFAULT_MODE = "none"
    private const val MAX_STATE_BYTES = 256L

    /** Exports the saved safe mode before the server starts, so Node.js the server starts keeps it. */
    const val SERVER_ENV: String =
        $$"""if [ -r /etc/oc-setup/node-compat.env ]; then
  while IFS='=' read -r oc_compat_key oc_compat_value; do
    case "$oc_compat_key" in
      OPENSSL_armcap|UV_THREADPOOL_SIZE) export "$oc_compat_key=$oc_compat_value" ;;
    esac
  done < /etc/oc-setup/node-compat.env
fi
"""

    /** Whether [text] (the state file) says the server proot must run in compatibility mode. */
    fun prootCompat(text: String?): Boolean =
        text?.lineSequence()?.drop(1)?.any { it.trim() == PROOT_COMPAT } == true

    /** [prootCompat] for the state file under [rootfs]; false when it is missing, a link, too big or unreadable. */
    fun prootCompat(rootfs: File): Boolean {
        val file = File(rootfs, STATE_PATH)
        return try {
            val readable = file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                file.length() <= MAX_STATE_BYTES
            readable && prootCompat(file.readText())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Persists `proot-compat` in [STATE_PATH] under [rootfs], preserving any existing Node safe mode on line 1.
     * Refuses symlinks so a guest link cannot redirect a host write outside the rootfs.
     */
    fun writeProotCompat(rootfs: File): Boolean =
        try {
            val file = safeStateFile(rootfs, createDir = true)
            file != null && writeAtomically(file, "${existingMode(file)}\n$PROOT_COMPAT\n")
        } catch (_: java.io.IOException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private fun writeAtomically(file: File, content: String): Boolean {
        val tmp = File(file.parentFile, "node-compat.new")
        if (isLink(tmp)) return false
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
        return true
    }

    /** Removes a `proot-compat` marker from [STATE_PATH] when compat mode also failed. */
    fun clearProotCompat(rootfs: File) {
        try {
            val file = safeStateFile(rootfs, createDir = false)
            if (file != null && file.isFile && file.length() <= MAX_STATE_BYTES) {
                val mode = existingMode(file)
                if (mode == DEFAULT_MODE) file.delete() else file.writeText("$mode\n")
            }
        } catch (_: java.io.IOException) {
            // Best-effort cleanup after a failed compat probe.
        } catch (_: SecurityException) {
            // Best-effort cleanup after a failed compat probe.
        }
    }

    private fun safeStateFile(rootfs: File, createDir: Boolean): File? {
        val etc = File(rootfs, "etc")
        val dir = File(etc, "oc-setup")
        val file = File(dir, "node-compat")
        val parentsSafe =
            rootfs.isDirectory &&
                !isLink(rootfs) &&
                !isLink(etc) &&
                !isLink(dir) &&
                (dir.exists() || (createDir && dir.mkdirs())) &&
                dir.isDirectory &&
                !isLink(dir)
        val fileSafe = !isLink(file) && (!file.exists() || file.isFile)
        return file.takeIf { parentsSafe && fileSafe }
    }

    private fun isLink(file: File): Boolean = java.nio.file.Files.isSymbolicLink(file.toPath())

    private fun existingMode(file: File): String {
        val first =
            if (file.isFile && file.length() <= MAX_STATE_BYTES) {
                file.readText().lineSequence().firstOrNull()?.trim().orEmpty()
            } else {
                ""
            }
        return if (first.isNotEmpty() && first != PROOT_COMPAT) first else DEFAULT_MODE
    }
}
