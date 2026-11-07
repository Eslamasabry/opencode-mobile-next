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
}
