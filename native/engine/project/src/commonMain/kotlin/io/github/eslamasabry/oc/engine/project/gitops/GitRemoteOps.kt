package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

public enum class GitOpsAction { Remotes, AddRemote, Clone, Push }

public enum class GitOpsNote { RemoteAdded, Cloned, Pushed, PushedAndTracking, UpToDate }

public data class GitOpsResult(
    val action: GitOpsAction,
    val note: GitOpsNote? = null,
    val failure: GitOpsFailure? = null,
    /** The remote branch a push went to (`origin/main`). */
    val target: String? = null,
    /** The folder a clone made. */
    val clonedTo: String? = null,
) {
    val ok: Boolean get() = failure == null
    val problem: GitOpsProblem? get() = failure?.problem
}

/**
 * Remotes, clone and push for one repository over any [GitOpsShell] (the remote/clone/push half of
 * lib/domain/git/git_repository.dart). Every name and address is checked before a shell runs. [credentials] exists only
 * where the app can write privately into the project's environment (built-in Ubuntu); elsewhere a token is never used.
 */
public class GitRemoteOps(
    private val shell: GitOpsShell,
    /** The project folder as [shell] sees it. */
    public val directory: String,
    private val credentials: GitCredentialDrop? = null,
    private val readTimeoutMs: Long = 45_000,
    private val networkTimeoutMs: Long = 180_000,
) {
    /** True where a GitHub token may be handed to the drop. */
    public val canUseGitHubToken: Boolean get() = credentials != null

    /** Each remote with its address, user info removed. */
    public suspend fun remotes(): Outcome<List<GitRemoteEntry>> =
        when (val run = run(GitOpsScript(directory, listOf(GitOpsStep("remotes", GIT + listOf("config", "--get-regexp", "^remote\\..*\\.url$")))), readTimeoutMs)) {
            is Run.Failed -> Outcome.Failed(Failure.ProjectGit())
            is Run.Done -> Outcome.Ok(parseGitRemotes(run.envelope["remotes"]?.stdout.orEmpty()))
        }

    /** `git remote add <name> <url>`, both checked first; an address with a password is refused before anything runs. */
    public suspend fun addRemote(name: String, url: String): GitOpsResult {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (!isValidGitRemoteName(cleanName)) return fail(GitOpsAction.AddRemote, GitOpsProblem.InvalidRemoteName)
        gitRemoteAddressProblem(cleanUrl)?.let { return fail(GitOpsAction.AddRemote, it) }
        val envelope =
            when (val run = run(GitOpsScript(directory, listOf(GitOpsStep("run", GIT + listOf("remote", "add", cleanName, cleanUrl)))), readTimeoutMs)) {
                is Run.Failed -> return GitOpsResult(GitOpsAction.AddRemote, failure = run.failure)
                is Run.Done -> run.envelope
            }
        val step = envelope["run"]!!
        return if (step.ok) GitOpsResult(GitOpsAction.AddRemote, GitOpsNote.RemoteAdded) else stepFailure(GitOpsAction.AddRemote, step, emptyList(), false)
    }

    /** Clones [url] into a new folder [folderName] beside this project (in its parent folder). */
    public suspend fun clone(url: String, folderName: String): GitOpsResult {
        val cleanUrl = url.trim()
        gitRemoteAddressProblem(cleanUrl)?.let { return fail(GitOpsAction.Clone, it) }
        val folder = folderName.trim()
        if (!isValidGitFolderName(folder)) return fail(GitOpsAction.Clone, GitOpsProblem.InvalidFolderName)
        val script = GitOpsScript(gitParentFolder(directory), listOf(GitOpsStep("clone", GIT + listOf("clone", "--", cleanUrl, folder))))
        val envelope =
            when (val run = run(script, networkTimeoutMs)) {
                is Run.Failed -> return GitOpsResult(GitOpsAction.Clone, failure = run.failure)
                is Run.Done -> run.envelope
            }
        val step = envelope["clone"]!!
        if (!step.ok) return stepFailure(GitOpsAction.Clone, step, emptyList(), false)
        return GitOpsResult(GitOpsAction.Clone, GitOpsNote.Cloned, clonedTo = gitCloneTarget(directory, folder))
    }

    /**
     * `git push`, or `git push --set-upstream <remote> <branch>` the first time (`origin`, else the only remote). With
     * [gitHubToken] and a drop, the token goes to a private one-command file the script names; without a drop it is
     * never used.
     */
    public suspend fun push(gitHubToken: String? = null): GitOpsResult =
        withToken(GitOpsAction.Push, gitHubToken) { auth ->
            val plan =
                when (
                    val run =
                        run(
                            GitOpsScript(
                                directory,
                                listOf(
                                    GitOpsStep("head", GIT + listOf("symbolic-ref", "--short", "-q", "HEAD"), stopOnFailure = true),
                                    GitOpsStep("upstream", GIT + listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")),
                                    GitOpsStep("remotes", GIT + listOf("remote")),
                                ),
                            ),
                            readTimeoutMs,
                        )
                ) {
                    is Run.Failed -> return@withToken GitOpsResult(GitOpsAction.Push, failure = run.failure)
                    is Run.Done -> run.envelope
                }
            val head = plan["head"]!!
            if (!head.ok) {
                return@withToken GitOpsResult(
                    GitOpsAction.Push,
                    failure = GitOpsFailure(classifyGitOpsError(head.stderr) ?: GitOpsProblem.DetachedHead, scrubGitOpsText(head.stderr.trim(), auth.secrets)),
                )
            }
            val branch = head.stdout.trim()
            val hasUpstream = plan["upstream"]?.ok ?: false
            val args: List<String>
            val target: String
            if (hasUpstream) {
                target = plan["upstream"]?.stdout?.trim().orEmpty()
                args = listOf("push")
            } else {
                val remotes = plan["remotes"]?.stdout.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                val remote = if ("origin" in remotes) "origin" else remotes.singleOrNull()
                    ?: return@withToken fail(GitOpsAction.Push, GitOpsProblem.NoRemote)
                args = listOf("push", "--set-upstream", remote, branch)
                target = "$remote/$branch"
            }
            val script =
                GitOpsScript(
                    directory,
                    listOf(GitOpsStep("push", GIT + auth.options + args)),
                    environment = auth.environment,
                    removeOnExit = auth.removeOnExit,
                )
            val envelope =
                when (val run = run(script, networkTimeoutMs)) {
                    is Run.Failed -> return@withToken GitOpsResult(GitOpsAction.Push, failure = run.failure)
                    is Run.Done -> run.envelope
                }
            val push = envelope["push"]!!
            if (!push.ok) return@withToken stepFailure(GitOpsAction.Push, push, auth.secrets, auth.usesToken)
            val text = "${push.stdout}\n${push.stderr}".lowercase()
            val note =
                when {
                    "everything up-to-date" in text -> GitOpsNote.UpToDate
                    hasUpstream -> GitOpsNote.Pushed
                    else -> GitOpsNote.PushedAndTracking
                }
            GitOpsResult(GitOpsAction.Push, note, target = target)
        }

    // ---------------------------------------------------------------------------------------------------- helpers

    private class Auth(
        val options: List<String> = emptyList(),
        val environment: Map<String, String> = emptyMap(),
        val removeOnExit: List<String> = emptyList(),
        val secrets: List<String> = emptyList(),
    ) {
        val usesToken: Boolean get() = secrets.isNotEmpty()
    }

    /** Runs [body] with a token placed for one command when one is given and this environment may receive it. */
    private suspend fun withToken(action: GitOpsAction, token: String?, body: suspend (Auth) -> GitOpsResult): GitOpsResult {
        val drop = credentials
        if (token.isNullOrEmpty() || drop == null) return body(Auth())
        val file =
            when (val placed = drop.place(token)) {
                // The file could not be made private: do not push with it.
                is Outcome.Failed -> return fail(action, GitOpsProblem.EnvironmentUnavailable)
                is Outcome.Ok -> placed.value
            }
        try {
            return body(
                Auth(
                    options = gitHubCredentialOptions(),
                    environment = mapOf(GIT_TOKEN_FILE_VARIABLE to file.shellPath),
                    removeOnExit = listOf(file.shellPath),
                    secrets = listOf(token),
                ),
            )
        } finally {
            drop.remove(file)
        }
    }

    private fun fail(action: GitOpsAction, problem: GitOpsProblem): GitOpsResult = GitOpsResult(action, failure = GitOpsFailure(problem))

    private fun stepFailure(action: GitOpsAction, step: GitOpsStepResult, secrets: List<String>, usedToken: Boolean): GitOpsResult {
        val details = scrubGitOpsText("${step.stderr}\n${step.stdout}".trim(), secrets)
        return GitOpsResult(action, failure = GitOpsFailure(classifyGitOpsError(details, usedToken) ?: GitOpsProblem.Unknown, details))
    }

    private sealed interface Run {
        class Done(val envelope: GitOpsEnvelope) : Run

        class Failed(val failure: GitOpsFailure) : Run
    }

    private suspend fun run(script: GitOpsScript, timeoutMs: Long): Run {
        val text =
            try {
                script.render()
            } catch (e: IllegalArgumentException) {
                return Run.Failed(GitOpsFailure(GitOpsProblem.Unknown))
            }
        val output =
            when (val reply = shell.run(text, timeoutMs)) {
                is GitOpsReply.Failed ->
                    return Run.Failed(
                        GitOpsFailure(
                            when (reply.failure) {
                                GitOpsShellFailure.Unavailable -> GitOpsProblem.EnvironmentUnavailable
                                GitOpsShellFailure.Unsupported -> GitOpsProblem.ServerCannotRunCommands
                                GitOpsShellFailure.TimedOut -> GitOpsProblem.TimedOut
                            },
                            reply.details,
                        ),
                    )
                is GitOpsReply.Output -> reply.text
            }
        val envelope = GitOpsEnvelope.parse(output)
        return when (envelope.problem) {
            null -> Run.Done(envelope)
            GitOpsEnvelopeProblem.FolderMissing -> Run.Failed(GitOpsFailure(GitOpsProblem.FolderMissing))
            GitOpsEnvelopeProblem.GitMissing -> Run.Failed(GitOpsFailure(GitOpsProblem.GitMissing))
            GitOpsEnvelopeProblem.NoTemp -> Run.Failed(GitOpsFailure(GitOpsProblem.EnvironmentUnavailable))
            GitOpsEnvelopeProblem.Truncated -> Run.Failed(GitOpsFailure(GitOpsProblem.OutputTooLarge))
        }
    }

    private companion object {
        val GIT = listOf("git", "--no-pager")
    }
}
