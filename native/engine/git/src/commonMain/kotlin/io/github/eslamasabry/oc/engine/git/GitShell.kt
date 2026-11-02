package io.github.eslamasabry.oc.engine.git

/**
 * Runs one POSIX `sh` script where the project lives and returns what it printed (port of
 * lib/domain/git/git_shell.dart). The Git page never talks to Git directly: it writes one script per action and hands
 * it to whichever command bridge reaches the project. Every path, branch name and message reaches that script
 * through [shQuote], never as bare text.
 *
 * Implementations: [ServerGitShell] (an OpenCode 2 server's `POST /api/shell`). The phone runtime joins when the
 * Linux runtime is wired into the app.
 */
public fun interface GitShell {
    public suspend fun run(script: String, timeoutMs: Long): GitShellReply
}

/** What a bridge answered: the script's output, or why it could not run at all. */
public sealed interface GitShellReply {
    public data class Output(val text: String) : GitShellReply {
        override fun toString(): String = "Output(${text.length} chars)"
    }

    /** [details] is the bridge's own description, for Details only; it never carries server text. */
    public data class Failed(val failure: GitShellFailure, val details: String = "") : GitShellReply
}

/** Why a bridge could not run the script at all (not a Git failure). */
public enum class GitShellFailure {
    /** The bridge is not there or not ready (the server connection closed, the request failed). */
    Unavailable,

    /** The server has no command endpoint (404 on `POST /api/shell`). */
    Unsupported,

    /** The script did not finish in time. */
    TimedOut,
}

/** Thrown only for a NUL byte, which no shell word can carry. The value is never echoed. */
public class GitQuoteException : IllegalArgumentException("value contains a NUL byte") {
    override fun toString(): String = "GitQuoteException"
}

/**
 * [value] as one single-quoted `sh` word: `it's` becomes `'it'\''s'`. Single quotes stop every expansion (`$`,
 * backticks, `\`, globbing), so the only character needing care is the quote itself. A NUL byte is refused.
 */
public fun shQuote(value: String): String {
    if ('\u0000' in value) throw GitQuoteException()
    return "'" + value.replace("'", "'\\''") + "'"
}

/** [argv] as one `sh` command line, every word quoted. */
public fun shCommand(argv: List<String>): String = argv.joinToString(" ") { shQuote(it) }
