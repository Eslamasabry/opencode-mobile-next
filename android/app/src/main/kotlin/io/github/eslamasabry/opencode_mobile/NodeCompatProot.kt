package io.github.eslamasabry.opencode_mobile

import java.io.File

/**
 * Issue #117: the Node.js step found that this phone runs Node only in the
 * setup runner's compatibility mode (proot without seccomp acceleration,
 * glibc without rseq). It records that inside Ubuntu as a second line
 * `proot-compat` in /etc/oc-setup/node-compat (lib/builtin/setup/node_compat.dart).
 * Every later proot (the OpenCode server, agents, the terminal, scripts)
 * then starts the same way, so the server runs the way setup did.
 */
internal object NodeCompatProot {
    /** The state file, relative to the Ubuntu root. */
    const val STATE_PATH = "etc/oc-setup/node-compat"

    private const val MARKER = "proot-compat"

    /** Bigger than any real state file (a mode name and the marker). */
    private const val MAX_BYTES = 256L

    /** Whether the state file's [text] asks for compatibility mode. */
    fun required(text: String?): Boolean =
        text != null && text.lineSequence().drop(1).any { it.trim() == MARKER }

    /** Reads [rootfs]'s state file; a missing, unreadable or odd file asks for nothing. */
    fun required(rootfs: File): Boolean = try {
        val file = File(rootfs, STATE_PATH)
        file.isFile && file.length() <= MAX_BYTES && required(file.readText())
    } catch (_: Exception) {
        false
    }
}
