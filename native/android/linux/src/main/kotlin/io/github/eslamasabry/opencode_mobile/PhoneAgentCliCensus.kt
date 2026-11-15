package io.github.eslamasabry.opencode_mobile

/**
 * signin-truth: which running programs are a Claude Code CLI, read from their argument lists only (never their
 * environment, files or output). Several CLIs of one sign-in may refresh it at once (a conversation, the sign-in
 * check, a sign-in terminal), and only Claude's own refresh lock guards that.
 */
internal object PhoneAgentCliCensus {
    /** The CLI's program or package as it is started: `claude`, `/usr/local/bin/claude`, `/opt/oc-claude/…`, `claude-code/cli.js`. */
    private val CLAUDE = Regex("""(?:^|[/\s])(?:claude|oc-claude|claude-code)(?:$|[/\s.])""")

    fun namesClaude(argv: List<String>): Boolean = argv.any { CLAUDE.containsMatchIn(it) }

    /**
     * Whether a Claude Code CLI is among [argvs] (one per running task of an agent tree; null when it could not be read).
     * Evidence that one runs: an unreadable task proves nothing either way.
     */
    fun claudeAmong(argvs: List<List<String>?>): Boolean = argvs.any { it != null && namesClaude(it) }

    /** A `/proc/<pid>/cmdline` as its arguments. */
    fun argv(cmdline: ByteArray): List<String> =
        cmdline.toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
}
