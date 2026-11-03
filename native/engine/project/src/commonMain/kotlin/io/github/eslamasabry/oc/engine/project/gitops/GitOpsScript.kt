package io.github.eslamasabry.oc.engine.project.gitops

import kotlin.io.encoding.Base64

/*
 * One Git action as one `sh` script and the envelope its answer comes back in (port of lib/domain/git/git_script.dart
 * and git_shell.dart; the same marker protocol :engine:git speaks, copied so this engine does not depend on it):
 *
 *   @@OCG begin / @@OCG out <step> <b64> / @@OCG err <step> <b64> / @@OCG exit <step> <code> / @@OCG end
 */

/** Runs one POSIX `sh` script where the project lives and returns what it printed. */
public fun interface GitOpsShell {
    public suspend fun run(script: String, timeoutMs: Long): GitOpsReply
}

public sealed interface GitOpsReply {
    public data class Output(val text: String) : GitOpsReply {
        override fun toString(): String = "Output(${text.length} chars)"
    }

    /** The bridge could not run the script at all. [details] is the bridge's own description, never server text. */
    public data class Failed(val failure: GitOpsShellFailure, val details: String = "") : GitOpsReply
}

public enum class GitOpsShellFailure { Unavailable, Unsupported, TimedOut }

/** [value] as one single-quoted `sh` word: `it's` becomes `'it'\''s'`. A NUL byte is refused. */
public fun gitShQuote(value: String): String {
    require('\u0000' !in value) { "value contains a NUL byte" }
    return "'" + value.replace("'", "'\\''") + "'"
}

public fun gitShCommand(argv: List<String>): String = argv.joinToString(" ") { gitShQuote(it) }

public data class GitOpsStep(val name: String, val argv: List<String>, val stopOnFailure: Boolean = false) {
    override fun toString(): String = "GitOpsStep($name)"
}

/** No prompts, no pager, no colour, English messages, no optional index locks. */
public val GitOpsBaseEnvironment: Map<String, String> =
    linkedMapOf(
        "GIT_TERMINAL_PROMPT" to "0",
        "GIT_PAGER" to "cat",
        "PAGER" to "cat",
        "LC_ALL" to "C",
        "LANG" to "C",
        "GIT_OPTIONAL_LOCKS" to "0",
    )

public data class GitOpsScript(
    val directory: String,
    val steps: List<GitOpsStep>,
    /** Extra variables exported for every step (values are quoted). Only paths, never secrets. */
    val environment: Map<String, String> = emptyMap(),
    /** Files the script deletes when it exits, however it exits. */
    val removeOnExit: List<String> = emptyList(),
) {
    public fun render(): String {
        val b = StringBuilder()
        b.append("printf '@@OCG begin\\n'\n")
        b.append("cd -- ").append(gitShQuote(directory)).append(" 2>/dev/null || { printf '@@OCG nodir\\n'; exit 0; }\n")
        b.append("command -v git >/dev/null 2>&1 || { printf '@@OCG nogit\\n'; exit 0; }\n")
        b.append("ocg_t=\$(mktemp -d 2>/dev/null) || { printf '@@OCG notemp\\n'; exit 0; }\n")
        val cleanup = (listOf("rm -rf \"\$ocg_t\"") + removeOnExit.map { "rm -f -- " + gitShQuote(it) }).joinToString("; ")
        b.append("trap ").append(gitShQuote(cleanup)).append(" EXIT\n")
        for ((key, value) in LinkedHashMap(GitOpsBaseEnvironment).apply { putAll(environment) }) {
            require(ENV_NAME.matches(key)) { "bad environment name" }
            b.append(key).append('=').append(gitShQuote(value)).append("; export ").append(key).append('\n')
        }
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
            val line = "ocg_s ${step.name} ${gitShCommand(step.argv)}"
            b.append(if (step.stopOnFailure) "$line || { printf '@@OCG end\\n'; exit 0; }" else line).append('\n')
        }
        b.append("printf '@@OCG end\\n'\n")
        return b.toString()
    }

    override fun toString(): String = "GitOpsScript(steps=${steps.map { it.name }})"

    private companion object {
        val STEP_NAME = Regex("^[A-Za-z0-9-]+$")
        val ENV_NAME = Regex("^[A-Z_][A-Z0-9_]*$")
    }
}

public data class GitOpsStepResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0

    override fun toString(): String = "GitOpsStepResult(exit=$exitCode)"
}

public enum class GitOpsEnvelopeProblem { FolderMissing, GitMissing, NoTemp, Truncated }

public data class GitOpsEnvelope(val steps: Map<String, GitOpsStepResult> = emptyMap(), val problem: GitOpsEnvelopeProblem? = null) {
    public operator fun get(name: String): GitOpsStepResult? = steps[name]

    public companion object {
        private val MARKER = Regex("^@@OCG (\\S+)(?: (\\S+))?(?: (\\S*))?\\s*$")

        public fun parse(output: String): GitOpsEnvelope {
            var begun = false
            var ended = false
            val outs = HashMap<String, String>()
            val errs = HashMap<String, String>()
            val exits = LinkedHashMap<String, Int>()
            for (raw in output.split('\n')) {
                val line = raw.trimEnd()
                val at = line.indexOf("@@OCG ")
                if (at < 0) continue
                val m = MARKER.find(line.substring(at)) ?: continue
                val name = m.groups[2]?.value
                val value = m.groups[3]?.value ?: ""
                when (m.groupValues[1]) {
                    "begin" -> begun = true
                    "end" -> ended = true
                    "nodir" -> return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.FolderMissing)
                    "nogit" -> return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.GitMissing)
                    "notemp" -> return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.NoTemp)
                    "out" -> if (name != null) outs[name] = value
                    "err" -> if (name != null) errs[name] = value
                    "exit" -> if (name != null) exits[name] = value.toIntOrNull() ?: -1
                }
            }
            if (!begun || !ended) return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.Truncated)
            val steps = LinkedHashMap<String, GitOpsStepResult>()
            for ((name, code) in exits) {
                val out = outs[name] ?: return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.Truncated)
                val err = errs[name] ?: return GitOpsEnvelope(problem = GitOpsEnvelopeProblem.Truncated)
                steps[name] = GitOpsStepResult(code, decode(out), decode(err))
            }
            return GitOpsEnvelope(steps)
        }

        private fun decode(value: String): String =
            if (value.isEmpty()) "" else try {
                Base64.decode(value).decodeToString()
            } catch (e: IllegalArgumentException) {
                ""
            }
    }
}
