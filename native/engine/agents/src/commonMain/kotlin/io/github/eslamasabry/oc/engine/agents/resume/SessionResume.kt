package io.github.eslamasabry.oc.engine.agents.resume

/**
 * The copyable terminal command that resumes a session on the computer hosting the server (Flutter
 * `lib/domain/session_handoff.dart` `SessionResumeCommand`). OpenCode sessions are location-scoped,
 * so the command changes into the session's directory first. Verified flags: `opencode2 --session,
 * -s string Session ID to continue`.
 */
public enum class SessionResumeCli {
    OpenCode2
}

/** Why no resume command could be built; the UI maps each reason to its own words. */
public enum class SessionResumeUnavailable {
    /** The server reported no project directory, so there is nowhere meaningful to `cd`. */
    MissingDirectory,

    /** The directory holds control characters and cannot be placed in a shell command. */
    UnsafeDirectory,

    /** The session id is not a plain identifier (could read as a flag or shell text). */
    InvalidSessionId,

    /**
     * A managed OpenCode 2 workspace (`wrk_…`): its directory belongs to the workspace host, so a
     * `cd` would mislead.
     */
    ManagedWorkspace,
}

/** The resume command for one session, or the reason there is none. */
public data class SessionResumeCommand(
    /** The full POSIX-quoted shell command, or null when [unavailable] is set. */
    public val command: String?,
    public val unavailable: SessionResumeUnavailable?,
    public val cli: SessionResumeCli,
) {
    public val available: Boolean
        get() = command != null

    /** The binary the person will type. */
    public val binary: String
        get() = binaryFor(cli)

    public companion object {
        private val safeIdentifier = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val control = Regex("[\\x00-\\x1f\\x7f]")

        public fun binaryFor(cli: SessionResumeCli): String =
            when (cli) {
                SessionResumeCli.OpenCode2 -> "opencode2"
            }

        /**
         * Builds `cd '<directory>' && <binary> --session '<id>'`. A non-empty [workspaceId]
         * (managed workspace) makes it unavailable rather than wrong; a git worktree session is an
         * ordinary directory and resumes inside it.
         */
        public fun build(
            cli: SessionResumeCli,
            sessionId: String,
            directory: String?,
            workspaceId: String? = null,
        ): SessionResumeCommand {
            fun none(reason: SessionResumeUnavailable) = SessionResumeCommand(null, reason, cli)
            if (!workspaceId.isNullOrEmpty()) return none(SessionResumeUnavailable.ManagedWorkspace)
            if (!isSafeIdentifier(sessionId)) return none(SessionResumeUnavailable.InvalidSessionId)
            if (directory != null && control.containsMatchIn(directory))
                return none(SessionResumeUnavailable.UnsafeDirectory)
            val dir = directory?.trim().orEmpty()
            if (dir.isEmpty()) return none(SessionResumeUnavailable.MissingDirectory)
            if (control.containsMatchIn(dir)) return none(SessionResumeUnavailable.UnsafeDirectory)
            return SessionResumeCommand(
                "cd ${shellQuote(dir)} && ${binaryFor(cli)} --session ${shellQuote(sessionId)}",
                null,
                cli,
            )
        }

        /** Single-quotes [value] for a POSIX shell; a literal `'` becomes `'\''`. */
        public fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /**
         * ASCII letters, digits, `_`, `-`, `.`; starts with a letter or digit (never a flag); at
         * most 128 chars.
         */
        public fun isSafeIdentifier(value: String): Boolean = safeIdentifier.matches(value)
    }
}
