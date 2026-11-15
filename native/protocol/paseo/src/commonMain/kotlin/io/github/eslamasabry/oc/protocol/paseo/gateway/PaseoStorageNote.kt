package io.github.eslamasabry.oc.protocol.paseo.gateway

/**
 * Issue 147: what an agent started in a folder of the phone's shared storage is told, so it works with that storage
 * instead of fighting it. Paseo appends a creation-time `systemPrompt` to the agent's own instructions (Claude Code's
 * preset, Codex's developer instructions, OpenCode's system prompt) from 0.15.3; an older helper is sent nothing extra.
 * Agent-facing text, not shown to the person (their words are in New conversation's help).
 */
internal object PaseoStorageNote {
    const val NOTE: String =
        "This project folder is in Android's shared storage (a FUSE file system that behaves like a memory card). " +
            "Symbolic links cannot be created there, files there cannot be executed directly, and chmod has no " +
            "effect. Work with it: install npm packages with `npm install --no-bin-links` (and run package tools " +
            "with `npx` or `node node_modules/<package>/<bin>`), run scripts through their interpreter " +
            "(`sh script.sh`, `node script.js`, `python3 script.py`) instead of `./script.sh`, and keep anything that " +
            "needs links or executable files, such as a Python virtual environment, outside this folder (for " +
            "example under the home folder). Git here is set with core.fileMode=false and core.symlinks=false."

    /** The first helper whose create request carries `systemPrompt` and whose agents append it. */
    private val FIRST = listOf(0, 15, 3)

    /** The note for an agent created in [cwd] by a helper of [version]; null when none is to be sent. */
    fun forFolder(cwd: String?, version: String?): String? {
        if (cwd == null || !(cwd.startsWith("/storage/") || cwd.startsWith("/sdcard/"))) return null
        val parts = version?.trim()?.split('.')?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.size != 3) return null
        for (i in 0..2) if (parts[i] != FIRST[i]) return if (parts[i] > FIRST[i]) NOTE else null
        return NOTE
    }
}
