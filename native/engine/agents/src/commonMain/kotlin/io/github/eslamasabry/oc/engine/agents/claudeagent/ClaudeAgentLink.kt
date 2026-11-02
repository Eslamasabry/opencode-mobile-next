package io.github.eslamasabry.oc.engine.agents.claudeagent

import io.github.eslamasabry.oc.engine.agents.OwnerId

/** What [ClaudeAgentLink.script] found (lib/builtin/claude_agent_link.dart). */
public enum class ClaudeAgentLinkResult {
    /** Launcher and link are in place. */
    Linked,

    /** Launcher in place; `/root/.claude` is someone else's folder (chats start over after five idle minutes). */
    LinkedWithoutResume,

    /** Claude Code is not installed for the agent home. */
    NotInstalled,

    /** `/usr/local/sbin/claude` exists and is not the app's. */
    Blocked,

    /** The script could not run. */
    Failed,
}

/** Runs [ClaudeAgentLink.script] as root in the in-app Ubuntu (`:android:linux`); tests pass a fake. */
public fun interface ClaudeAgentLinkRunner {
    public suspend fun link(owner: OwnerId): ClaudeAgentLinkResult
}

/**
 * Points this phone's OpenCode server at Claude Code for the Claude Agent trial (port of ClaudeAgentLink). Writes, as
 * root, `/usr/local/sbin/claude` (a launcher ahead of the Terminal's on root's PATH: piped, it runs Claude Code with
 * `CLAUDE_CONFIG_DIR` at the shared agent home; typed in a terminal it hands over unchanged) and `/root/.claude`, a
 * link to the same folder. Nothing is copied, read or moved: the sign-in stays where Claude Code keeps it. Idempotent;
 * it never replaces a launcher it did not write. The script text is the Dart one byte for byte.
 */
public object ClaudeAgentLink {
    /** The marker line that makes `/usr/local/sbin/claude` the app's. */
    public const val SHIM_MARKER: String = "# oc-claude-agent-shim"

    /** One run may take this long (the Dart `BuiltinLinux.run` timeout). */
    public const val RUN_TIMEOUT_SECONDS: Int = 60

    private val OWNER = Regex("^[A-Za-z0-9_-]{1,80}$")

    /** The root script for agent home [owner]. Prints one result line. Throws for an id that is not an agent home. */
    public fun script(owner: OwnerId): String {
        require(OWNER.matches(owner.raw)) { "Not an agent home id." }
        val home = "/home/oc/.oc-profiles/${owner.raw}/claude"
        // Dart drops the first line of a triple-quoted string when it is empty; Kotlin keeps it.
        return CLAUDE_AGENT_LINK_TEMPLATE.removePrefix("\n")
            .replace("⟨HOME⟩", home)
            .replace("⟨MARKER⟩", SHIM_MARKER)
            .replace('§', '$')
    }

    /** The result line [script] printed, from the run's whole output. */
    public fun parse(exitCode: Int, output: String): ClaudeAgentLinkResult {
        if (exitCode != 0) return ClaudeAgentLinkResult.Failed
        val line = output.split('\n').map { it.trim() }.lastOrNull { it.startsWith("oc-claude-agent:") }.orEmpty()
        return when (line) {
            "oc-claude-agent:linked" -> ClaudeAgentLinkResult.Linked
            "oc-claude-agent:linked-without-resume" -> ClaudeAgentLinkResult.LinkedWithoutResume
            "oc-claude-agent:not-installed" -> ClaudeAgentLinkResult.NotInstalled
            "oc-claude-agent:blocked" -> ClaudeAgentLinkResult.Blocked
            else -> ClaudeAgentLinkResult.Failed
        }
    }
}
