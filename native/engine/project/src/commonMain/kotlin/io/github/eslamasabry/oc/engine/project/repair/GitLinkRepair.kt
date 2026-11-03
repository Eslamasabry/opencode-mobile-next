package io.github.eslamasabry.oc.engine.project.repair

import io.github.eslamasabry.oc.core.common.Outcome

/** How the repair is going, for its progress line. */
public enum class GitRepairStage { Looking, Restoring, AskingTermux, Checking }

public data class GitRepairProgress(val stage: GitRepairStage, val done: Int = 0, val total: Int = 0)

/** Why Termux could not hand over the files. */
public enum class GitRepairTermux {
    /** Termux is not on this phone (any more): its files went with it. */
    NotInstalled,

    /** The app may not run commands in Termux yet. */
    NotAllowed,

    /** Termux did not answer. */
    NoAnswer,
}

public enum class GitRepairResult {
    /** Every link is a file again and Git can read the project. */
    Repaired,

    /** Nothing was missing and Git can read the project. */
    Healthy,

    /** Some files are nowhere on this phone, or Git still refuses. */
    CannotRepair,

    /** The files are in Termux, which could not hand them over now. */
    Termux,

    /** The folder is not a Git project the repair can reach. */
    NotRepository,
}

public data class GitRepairOutcome(
    val result: GitRepairResult,
    /** Links now plain files. */
    val restored: Int = 0,
    /** Object files still missing, as paths inside the Git folder. */
    val missing: List<String> = emptyList(),
    val termux: GitRepairTermux? = null,
    /** Technical text for Details only (counts and Git-folder paths; never file contents). */
    val details: String = "",
)

/** Git's own verdict on a repository. */
public data class GitCheck(val ok: Boolean, val output: String = "")

/** Files fetched from Termux. */
public interface TermuxGitFiles {
    /** Null when Termux can be asked; else why not. */
    public suspend fun available(): GitRepairTermux?

    /** Index into [paths] -> the received file on the app's side (inside [into]). A failure means Termux did not answer. */
    public suspend fun fetch(paths: List<String>, into: String, progress: (Int) -> Unit): Outcome<Map<Int, String>>
}

/** The repair of one project in the built-in Ubuntu. */
public class GitLinkRepair(
    /** The app's side of `/root/projects`. */
    private val projects: String,
    /** Where fetched files wait (same disk as [projects]). */
    private val staging: String,
    private val fs: RepairFileSystem,
    inflater: ZlibInflater,
    private val termux: TermuxGitFiles,
    /** Runs Git's check on a repository, given its path inside Ubuntu. */
    private val check: suspend (String) -> GitCheck,
    /** Names each staging job folder uniquely (a clock reading). */
    private val jobName: () -> String,
) {
    private val links = GitObjectLinks(fs, inflater)

    public suspend fun repair(directory: String, progress: (GitRepairProgress) -> Unit = {}): GitRepairOutcome {
        fun say(stage: GitRepairStage, done: Int = 0, total: Int = 0) = progress(GitRepairProgress(stage, done, total))
        if (!reaches(directory)) return GitRepairOutcome(GitRepairResult.NotRepository)
        say(GitRepairStage.Looking)
        val (repository, gitFolder) = locate(directory) ?: return GitRepairOutcome(GitRepairResult.NotRepository)
        val scan = links.scan(gitFolder)
        val ubuntuRepository = UBUNTU_PROJECTS + repository.substring(projects.length)
        var restored = 0
        val missing = scan.of(GitLinkPlace.Missing).map { it.relative }.toMutableList()
        val notes =
            mutableListOf(
                "Links found: ${scan.links.size} (here ${scan.of(GitLinkPlace.Here).size}, " +
                    "Termux ${scan.of(GitLinkPlace.Termux).size}, missing ${missing.size})",
            )

        val here = scan.of(GitLinkPlace.Here)
        here.forEachIndexed { index, link ->
            say(GitRepairStage.Restoring, index, here.size)
            if (links.replace(link.path, link.file!!)) restored++ else missing += link.relative
        }

        val inTermux = scan.of(GitLinkPlace.Termux)
        var termuxProblem: GitRepairTermux? = null
        if (inTermux.isNotEmpty()) {
            termuxProblem = termux.available()
            if (termuxProblem == GitRepairTermux.NotInstalled) {
                missing += inTermux.map { it.relative }
                notes += "Termux is not installed; its files are gone."
                termuxProblem = null
            } else if (termuxProblem == null) {
                say(GitRepairStage.AskingTermux, 0, inTermux.size)
                val job = "$staging/${jobName()}"
                try {
                    when (val received = termux.fetch(inTermux.map { it.file!! }, job) { say(GitRepairStage.AskingTermux, it, inTermux.size) }) {
                        is Outcome.Ok ->
                            inTermux.forEachIndexed { index, link ->
                                val file = received.value[index]
                                if (file != null && links.verify(link.relative, file) && links.replace(link.path, file, move = true)) {
                                    restored++
                                } else {
                                    missing += link.relative
                                }
                            }
                        is Outcome.Failed -> {
                            termuxProblem = GitRepairTermux.NoAnswer
                            // The kind only: a message could carry the script's text.
                            notes += "Termux: ${received.failure::class.simpleName}"
                        }
                    }
                } finally {
                    if (fs.kind(job) != null) fs.deleteTree(job)
                }
            }
        }

        termuxProblem?.let {
            return GitRepairOutcome(GitRepairResult.Termux, restored = restored, termux = it, details = notes.joinToString("\n"))
        }

        say(GitRepairStage.Checking)
        val verdict = check(ubuntuRepository)
        val details =
            buildList {
                addAll(notes)
                if (missing.isNotEmpty()) add("Missing (${missing.size}):\n${missing.take(20).joinToString("\n")}")
                if (verdict.output.isNotBlank()) add("git: ${verdict.output.trim()}")
            }.joinToString("\n")
        if (missing.isNotEmpty() || !verdict.ok) {
            return GitRepairOutcome(GitRepairResult.CannotRepair, restored = restored, missing = missing, details = details)
        }
        return GitRepairOutcome(
            if (restored == 0) GitRepairResult.Healthy else GitRepairResult.Repaired,
            restored = restored,
            details = details,
        )
    }

    /** The repository holding [directory]: the nearest folder at or above it, inside the projects folder, with a Git folder. */
    private fun locate(directory: String): Pair<String, String>? {
        var current = projects + directory.substring(UBUNTU_PROJECTS.length)
        while (current.length > projects.length) {
            if (fs.kind(current) == RepairEntryKind.Folder) {
                val git =
                    links.gitFolder(current) { path ->
                        if (path.startsWith("$UBUNTU_PROJECTS/")) projects + path.substring(UBUNTU_PROJECTS.length) else path
                    }
                if (git != null) return current to git
            }
            current = current.substringBeforeLast('/')
        }
        return null
    }

    public companion object {
        public const val UBUNTU_PROJECTS: String = "/root/projects"

        /** Whether [directory] (a path inside Ubuntu) is a project the repair can reach. */
        public fun reaches(directory: String): Boolean {
            val parts = directory.split('/')
            return directory.startsWith("$UBUNTU_PROJECTS/") && parts.size > 3 &&
                parts.drop(3).none { it.isEmpty() || it == ".." }
        }
    }
}

/**
 * The Termux side of the fetch (port of BridgeTermuxGitFiles): the lines it may upload and the script that uploads
 * them over an authenticated loopback socket. Only paths in a proot-distro `.l2s` store ever leave Termux.
 */
public object TermuxGitFetchScript {
    public const val BATCH: Int = 150

    /** `index|path` lines for [paths] from [start] (one batch), store paths only. */
    public fun lines(paths: List<String>, start: Int): List<String> =
        (start until minOf(start + BATCH, paths.size)).filter { GitObjectLinks.TERMUX_STORE.matches(paths[it]) }.map { "$it|${paths[it]}" }

    /** The Termux side: each line is `index|path`; a path outside a store, or not a readable file, is skipped. */
    public fun script(port: Int, token: String, lines: List<String>): String =
        """
        |# oc-git-repair
        |export LC_ALL=C
        |while IFS='|' read -r index path; do
        |  case "${'$'}path" in
        |    /data/data/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*|/data/user/0/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*) ;;
        |    *) continue ;;
        |  esac
        |  case "${'$'}path" in *..*/*|*/..*) continue ;; esac
        |  [ -f "${'$'}path" ] && [ -r "${'$'}path" ] || continue
        |  curl --silent --fail --max-time 120 --connect-timeout 3 -T "${'$'}path" \
        |    -H 'Authorization: Bearer $token' "http://127.0.0.1:$port/f/${'$'}index" >/dev/null 2>&1 || true
        |done <<'OC_GIT_REPAIR_LIST'
        |${lines.joinToString("\n")}
        |OC_GIT_REPAIR_LIST
        |printf 'done\n'
        |""".trimMargin()
}
