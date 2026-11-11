package io.github.eslamasabry.oc.feature.team.presentation

/** What a step did, for its words (Dart `AgentStepKind`). */
public enum class FleetStepKind { Command, Test, Read, Edit, Search, Other }

/** One tool call of the transcript (Dart `AgentStep`): the tool, the command it carried and the output that followed. */
public data class FleetStep(val tool: String, val command: String, val output: String, val kind: FleetStepKind)

/** A run of the transcript: what the agent said, or what it did (Dart `AgentTranscriptBlock`). */
public sealed interface FleetBlock {
    /** Text the agent wrote between tool calls. */
    public data class Prose(val text: String) : FleetBlock

    /** Consecutive tool calls with nothing said in between. */
    public data class Steps(val steps: List<FleetStep>) : FleetBlock
}

/**
 * Reads an agent's output the way Flutter's agent page does (`parseAgentTranscript`, lib/orchestration/models/
 * agent_output.dart): the `[tool: ...]` markers split it into prose and step groups. A bare tool name marker names the
 * tool for the command markers after it; a command marker repeated with the same text is the same call captured again
 * with more output, so only the last body counts; the text after a call's output is split into the output and the prose
 * that followed it.
 */
public object FleetTranscript {
    /** Prose is streamed one token per line: a trailing run of at least this many token-like lines is what was said next. */
    private const val PROSE_MIN_LINES = 4

    private val marker = Regex("^\\[tool: ([\\s\\S]*?)\\]$", RegexOption.MULTILINE)
    private val toolName = Regex("^[a-z][a-z0-9_.-]{0,23}$")
    private val testCommand =
        Regex(
            "(^|[\\s;&|(])(pytest|unittest|jest|vitest|mocha|rspec|phpunit|flutter\\s+test|dart\\s+test|npm\\s+test|yarn\\s+test|" +
                "pnpm\\s+test|go\\s+test|cargo\\s+test|bun\\s+test|make\\s+test)(\$|[\\s;&|)])",
        )

    public fun parse(transcript: String): List<FleetBlock> {
        val blocks = ArrayList<FleetBlock>()
        var group = ArrayList<FleetStep>()

        fun closeGroup() {
            if (group.isNotEmpty()) {
                blocks += FleetBlock.Steps(group.toList())
                group = ArrayList()
            }
        }

        fun prose(text: String) {
            val joined = joinProse(text)
            if (joined.isEmpty()) return
            closeGroup()
            blocks += FleetBlock.Prose(joined)
        }

        val matches = marker.findAll(transcript).toList()
        if (matches.isEmpty()) {
            prose(transcript)
            return blocks
        }
        prose(transcript.substring(0, matches.first().range.first))
        var tool = "tool"
        for ((i, match) in matches.withIndex()) {
            val text = match.groupValues[1].trim()
            val bodyStart = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else transcript.length
            val body = transcript.substring(bodyStart, end)
            if (toolName.matches(text)) {
                tool = text
                prose(body)
                continue
            }
            val (output, said) = splitOutput(body)
            if (group.isNotEmpty() && group.last().command == text) {
                group[group.lastIndex] = group.last().copy(output = output)
            } else {
                group += FleetStep(tool, text, output, kindOf(tool, text))
            }
            if (said.isNotEmpty()) prose(said)
        }
        closeGroup()
        return blocks
    }

    /** The newest tool call, what the agent does now or did last. */
    public fun lastStep(blocks: List<FleetBlock>): FleetStep? = blocks.asReversed().firstNotNullOfOrNull { (it as? FleetBlock.Steps)?.steps?.lastOrNull() }

    /** The command's first line, for Technical details. */
    public fun commandLine(step: FleetStep?): String? = step?.command?.trim()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun kindOf(tool: String, command: String): FleetStepKind =
        when (tool) {
            "read", "view", "cat" -> FleetStepKind.Read
            "edit", "write", "patch", "apply_patch", "multiedit" -> FleetStepKind.Edit
            "glob", "grep", "search", "list", "ls" -> FleetStepKind.Search
            "bash", "shell", "sh", "exec", "tool" -> if (testCommand.containsMatchIn(command)) FleetStepKind.Test else FleetStepKind.Command
            else -> FleetStepKind.Other
        }

    /** Splits a call's body into its output and the streamed prose after it: the trailing lines that look like tokens. */
    private fun splitOutput(body: String): Pair<String, String> {
        val lines = body.split('\n')
        var cut = lines.size
        while (cut > 0 && tokenLike(lines[cut - 1])) cut--
        val trailing = lines.subList(cut, lines.size).filter { it.isNotEmpty() }
        if (trailing.size < PROSE_MIN_LINES) return body.trim() to ""
        // Streamed words carry their leading space; a listing of short names does not, and stays output.
        val spaced = trailing.count { it.startsWith(" ") }
        if (spaced * 3 < trailing.size) return body.trim() to ""
        return lines.subList(0, cut).joinToString("\n").trim() to lines.subList(cut, lines.size).joinToString("\n")
    }

    private fun tokenLike(line: String): Boolean =
        line.isEmpty() ||
            line.startsWith(" ") ||
            (line.length <= 20 && !line.contains(' ') && !line.contains('/') && !(line.contains('.') && line.length > 1))

    /** Streamed tokens joined back into sentences; a real paragraph (lines with spaces inside) keeps its line breaks. */
    private fun joinProse(text: String): String {
        val out = StringBuilder()
        for (line in text.split('\n')) {
            if (line.isEmpty()) continue
            if (tokenLike(line)) {
                out.append(line)
            } else {
                if (out.isNotEmpty()) out.append('\n')
                out.append(line.trim())
            }
        }
        return out.toString().trim()
    }
}
