package io.github.eslamasabry.oc.engine.git

import kotlin.io.encoding.Base64

/*
 * One Git action as one `sh` script, and the envelope its answer comes back in (port of
 * lib/domain/git/git_script.dart). Every bridge returns plain text, some merge stdout with stderr, so each step's
 * stdout and stderr go to files and come back base64-encoded on marker lines:
 *
 *   @@OCG begin
 *   @@OCG out status <base64 stdout>
 *   @@OCG err status <base64 stderr>
 *   @@OCG exit status 0
 *   @@OCG end
 *
 * Anything else (a login banner) is ignored. A missing `begin` or an `exit` without its `out` means a cut-off answer.
 */

/** One command of a [GitScript]: an argv list, every word quoted when the script is written. */
public data class GitStep(
    /** Letters, digits and `-` only; the marker key. */
    val name: String,
    val argv: List<String>,
    /** A failure ends the script here (later steps do not run). */
    val stopOnFailure: Boolean = false,
    /** Extra information: an answer without this step's result is still a whole answer. */
    val optional: Boolean = false,
) {
    /** Never prints the argv: a commit message or a path is the person's text. */
    override fun toString(): String = "GitStep($name)"
}

/**
 * The environment every Git command runs with: no prompts, no pager, no colour, English messages, no optional index
 * locks (agents may be working in the same repository).
 */
public val GitBaseEnvironment: Map<String, String> =
    linkedMapOf(
        "GIT_TERMINAL_PROMPT" to "0",
        "GIT_PAGER" to "cat",
        "PAGER" to "cat",
        "LC_ALL" to "C",
        "LANG" to "C",
        "GIT_OPTIONAL_LOCKS" to "0",
    )

public data class GitScript(
    /** Where the project lives, as the shell sees it. */
    val directory: String,
    val steps: List<GitStep>,
    /** Extra variables exported for every step (values are quoted). */
    val environment: Map<String, String> = emptyMap(),
    /** Files the script deletes when it exits, however it exits. */
    val removeOnExit: List<String> = emptyList(),
) {
    /** The script text. Never contains a secret: callers pass only paths. Throws for a bad step or variable name. */
    public fun render(): String {
        val b = StringBuilder()
        b.append("printf '@@OCG begin\\n'\n")
        b.append("cd -- ").append(shQuote(directory)).append(" 2>/dev/null || { printf '@@OCG nodir\\n'; exit 0; }\n")
        b.append("command -v git >/dev/null 2>&1 || { printf '@@OCG nogit\\n'; exit 0; }\n")
        b.append("ocg_t=\$(mktemp -d 2>/dev/null) || { printf '@@OCG notemp\\n'; exit 0; }\n")
        val cleanup = (listOf("rm -rf \"\$ocg_t\"") + removeOnExit.map { "rm -f -- " + shQuote(it) }).joinToString("; ")
        b.append("trap ").append(shQuote(cleanup)).append(" EXIT\n")
        val env = LinkedHashMap(GitBaseEnvironment).apply { putAll(environment) }
        for ((key, value) in env) {
            require(ENV_NAME.matches(key)) { "bad environment name" }
            b.append(key).append('=').append(shQuote(value)).append("; export ").append(key).append('\n')
        }
        // ocg_s NAME CMD...: run CMD, then print its stdout, stderr and status.
        b.append("ocg_s() {\n")
        b.append("  ocg_n=\$1; shift\n")
        b.append("  \"\$@\" >\"\$ocg_t/o\" 2>\"\$ocg_t/e\"; ocg_c=\$?\n")
        b.append("  printf '@@OCG out %s ' \"\$ocg_n\"; base64 <\"\$ocg_t/o\" | tr -d '\\n'; printf '\\n'\n")
        b.append("  printf '@@OCG err %s ' \"\$ocg_n\"; base64 <\"\$ocg_t/e\" | tr -d '\\n'; printf '\\n'\n")
        b.append("  printf '@@OCG exit %s %s\\n' \"\$ocg_n\" \"\$ocg_c\"\n")
        b.append("  return \$ocg_c\n")
        b.append("}\n")
        for (step in steps) {
            require(STEP_NAME.matches(step.name)) { "bad step name" }
            val line = "ocg_s ${step.name} ${shCommand(step.argv)}"
            b.append(if (step.stopOnFailure) "$line || { printf '@@OCG end\\n'; exit 0; }" else line).append('\n')
        }
        b.append("printf '@@OCG end\\n'\n")
        return b.toString()
    }

    override fun toString(): String = "GitScript(steps=${steps.map { it.name }})"

    private companion object {
        val STEP_NAME = Regex("^[A-Za-z0-9-]+$")
        val ENV_NAME = Regex("^[A-Z_][A-Z0-9_]*$")
    }
}

/** What one step printed and how it ended. */
public data class GitStepResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0

    override fun toString(): String = "GitStepResult(exit=$exitCode)"
}

/** Why the envelope itself could not be read. */
public enum class GitEnvelopeProblem { FolderMissing, GitMissing, NoTemp, Truncated }

/** A decoded answer. */
public data class GitEnvelope(
    val steps: Map<String, GitStepResult> = emptyMap(),
    val problem: GitEnvelopeProblem? = null,
) {
    public operator fun get(name: String): GitStepResult? = steps[name]

    public companion object {
        private val MARKER = Regex("^@@OCG (\\S+)(?: (\\S+))?(?: (\\S*))?\\s*$")

        public fun parse(output: String): GitEnvelope {
            var begun = false
            var ended = false
            val outs = HashMap<String, String>()
            val errs = HashMap<String, String>()
            val exits = LinkedHashMap<String, Int>()
            for (raw in output.split('\n')) {
                val line = raw.trimEnd()
                val at = line.indexOf("@@OCG ")
                if (at < 0) continue
                val match = MARKER.find(line.substring(at)) ?: continue
                val kind = match.groupValues[1]
                val name = match.groups[2]?.value
                val value = match.groups[3]?.value ?: ""
                when {
                    kind == "begin" -> begun = true
                    kind == "end" -> ended = true
                    kind == "nodir" -> return GitEnvelope(problem = GitEnvelopeProblem.FolderMissing)
                    kind == "nogit" -> return GitEnvelope(problem = GitEnvelopeProblem.GitMissing)
                    kind == "notemp" -> return GitEnvelope(problem = GitEnvelopeProblem.NoTemp)
                    kind == "out" && name != null -> outs[name] = value
                    kind == "err" && name != null -> errs[name] = value
                    kind == "exit" && name != null -> exits[name] = value.toIntOrNull() ?: -1
                }
            }
            if (!begun || !ended) return GitEnvelope(problem = GitEnvelopeProblem.Truncated)
            val steps = LinkedHashMap<String, GitStepResult>()
            for ((name, code) in exits) {
                val out = outs[name]
                val err = errs[name]
                if (out == null || err == null) return GitEnvelope(problem = GitEnvelopeProblem.Truncated)
                steps[name] = GitStepResult(code, decode(out), decode(err))
            }
            return GitEnvelope(steps)
        }

        private fun decode(value: String): String {
            if (value.isEmpty()) return ""
            return try {
                Base64.decode(value).decodeToString()
            } catch (e: IllegalArgumentException) {
                ""
            }
        }
    }
}
