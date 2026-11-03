package io.github.eslamasabry.oc.engine.project.gitops

/*
 * Remote addresses, names and clone folders, checked before any shell runs (port of the remote/clone half of
 * lib/domain/git/git_repository.dart and git_parsers.dart).
 */

/** What went wrong with a remote, clone or push, in categories the page has words for. */
public enum class GitOpsProblem {
    NotARepository, GitMissing, FolderMissing, EnvironmentUnavailable, ServerCannotRunCommands, OutputTooLarge, TimedOut,
    NoUpstream, NoRemote, DetachedHead, PushRejected,
    AuthRequired, GitHubTokenRejected, NoPermission, Network, RemoteNotFound,
    InvalidRemoteName, InvalidRemoteUrl, RemoteAddressHasPassword, RemoteExists, InvalidFolderName, FolderExists,
    Unknown,
}

/** A failed action: the category and Git's own text for Details, secrets already masked. */
public data class GitOpsFailure(val problem: GitOpsProblem, val details: String = "") {
    override fun toString(): String = "GitOpsFailure($problem)"
}

public data class GitRemoteEntry(
    val name: String,
    /** The address without user info, so a credential never reaches the screen; null when it has none. */
    val address: String?,
)

private val SIMPLE_NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9._-]{0,99}$")
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
private val CONTROL = Regex("[\\s\\x00-\\x1f\\x7f]")
private val USER_INFO = Regex("://[^/@\\s]*@")

/** True when [name] can be a remote's name (`origin`). */
public fun isValidGitRemoteName(name: String): Boolean = SIMPLE_NAME.matches(name)

/** True when [name] can be the folder a clone makes beside the project. */
public fun isValidGitFolderName(name: String): Boolean = SIMPLE_NAME.matches(name) && !name.endsWith(".lock")

/**
 * Why [url] cannot be a remote's address, or null when it can. `https`, `http`, `ssh`, `git` and `file` addresses,
 * `user@host:path` and folder paths are accepted; one that carries a password or token is refused.
 */
public fun gitRemoteAddressProblem(url: String): GitOpsProblem? {
    val value = url.trim()
    if (value.isEmpty() || value.startsWith("-") || CONTROL.containsMatchIn(value)) return GitOpsProblem.InvalidRemoteUrl
    val scheme = SCHEME.find(value)
    if (scheme == null) {
        // `ext::sh -c …` and other transport helpers run programs.
        return if ("::" in value) GitOpsProblem.InvalidRemoteUrl else null
    }
    val name = scheme.groupValues[1].lowercase()
    if (name !in setOf("https", "http", "ssh", "git", "file")) return GitOpsProblem.InvalidRemoteUrl
    val authority = value.substring(scheme.value.length).substringBefore('/').substringBefore('?').substringBefore('#')
    val userInfo = if ('@' in authority) authority.substringBeforeLast('@') else ""
    val host = authority.substringAfterLast('@').substringBefore(':').removePrefix("[").substringBefore(']')
    if (name != "file" && host.isEmpty()) return GitOpsProblem.InvalidRemoteUrl
    if (':' in userInfo && name in setOf("https", "http", "git")) return GitOpsProblem.RemoteAddressHasPassword
    return null
}

/** The folder name a clone of [url] would get (`https://example.invalid/o/app.git` → `app`); empty when none fits. */
public fun suggestGitCloneFolder(url: String): String {
    val value = url.trim().trimEnd('/')
    val cut = value.indexOfLast { it == '/' || it == ':' }
    val name = (if (cut < 0) value else value.substring(cut + 1)).removeSuffix(".git")
    return if (isValidGitFolderName(name)) name else ""
}

/** The folder holding [path]: where a clone beside the project lands. */
public fun gitParentFolder(path: String): String {
    val value = path.trimEnd('/').ifEmpty { "/" }
    val cut = value.lastIndexOf('/')
    return when {
        cut < 0 -> "."
        cut == 0 -> "/"
        else -> value.substring(0, cut)
    }
}

public fun gitCloneTarget(directory: String, folder: String): String {
    val parent = gitParentFolder(directory)
    return if (parent.endsWith("/")) parent + folder else "$parent/$folder"
}

/** [url] without the user info of a `scheme://user:secret@host` address. An scp-style `git@host:path` keeps it. */
public fun stripGitUserInfo(url: String): String = USER_INFO.replaceFirst(url, "://")

/** `git config --get-regexp '^remote\..*\.url$'` lines, each address without user info. */
public fun parseGitRemotes(output: String): List<GitRemoteEntry> {
    val remotes = LinkedHashMap<String, GitRemoteEntry>()
    for (line in output.split('\n')) {
        val space = line.indexOf(' ')
        if (space < 0) continue
        val key = line.substring(0, space)
        val url = line.substring(space + 1).trim()
        if (!key.startsWith("remote.") || !key.endsWith(".url")) continue
        val name = key.substring("remote.".length, key.length - ".url".length)
        if (name.isEmpty() || name in remotes) continue
        remotes[name] = GitRemoteEntry(name, if (url.isEmpty()) null else stripGitUserInfo(url))
    }
    return remotes.values.toList()
}

/** The category for [stderr] of a failed remote, clone or push command, or null when nothing matches. */
public fun classifyGitOpsError(stderr: String, usedGitHubToken: Boolean = false): GitOpsProblem? {
    val text = stderr.lowercase()
    fun has(s: String) = s in text
    return when {
        has("not a git repository") -> GitOpsProblem.NotARepository
        has("[rejected]") || has("updates were rejected") || (has("failed to push some refs") && has("fetch first")) ->
            GitOpsProblem.PushRejected
        has("no upstream configured") || has("has no upstream branch") -> GitOpsProblem.NoUpstream
        has("no configured push destination") ||
            (has("does not appear to be a git repository") && !has("repository not found")) -> GitOpsProblem.NoRemote
        has("repository not found") || has("the requested url returned error: 404") -> GitOpsProblem.RemoteNotFound
        (has("permission to") && has("denied")) || has("the requested url returned error: 403") -> GitOpsProblem.NoPermission
        has("authentication failed") || has("invalid username or password") || has("the requested url returned error: 401") ||
            has("could not read username") || has("could not read password") || has("terminal prompts disabled") ||
            has("permission denied (publickey") || has("host key verification failed") ->
            if (usedGitHubToken && !has("permission denied (publickey") && !has("host key verification failed")) {
                GitOpsProblem.GitHubTokenRejected
            } else {
                GitOpsProblem.AuthRequired
            }
        has("could not resolve host") || has("connection refused") || has("unable to access") ||
            has("could not read from remote repository") -> GitOpsProblem.Network
        has("remote") && has("already exists") -> GitOpsProblem.RemoteExists
        has("destination path") && has("already exists") -> GitOpsProblem.FolderExists
        else -> null
    }
}

/** [text] with every one of [secrets] masked, and remote addresses with user info masked, for Details. */
public fun scrubGitOpsText(text: String, secrets: Iterable<String>): String {
    var out = text
    for (secret in secrets) if (secret.isNotEmpty()) out = out.replace(secret, "•••")
    return Regex("://[^/\\s@]+@").replace(out, "://•••@")
}
