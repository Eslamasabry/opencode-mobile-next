package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** How a background command ended, as a reader cares about it. */
public enum class BackgroundShellOutcome {
    Finished,
    Failed,
    Stopped,
}

/**
 * A background command's result (port of lib/domain/background_shell_result.dart, B-AGENT-033).
 * OpenCode 2 files it into the conversation as a synthetic notice whose text is the raw `<shell id
 * state command>output</shell>` envelope ending "Command exited with code N."; read into parts, it
 * shows as a command and how it ended, never as markup.
 */
public data class BackgroundShellResult(
    val command: String,
    val state: String,
    val output: String,
    val exitCode: Int?,
) {
    /**
     * 143, 130 and 137 are someone stopping it (SIGTERM, Ctrl-C, kill), not the command failing.
     */
    public val outcome: BackgroundShellOutcome
        get() =
            when {
                state == "killed" || state == "cancelled" -> BackgroundShellOutcome.Stopped
                exitCode == 143 || exitCode == 130 || exitCode == 137 ->
                    BackgroundShellOutcome.Stopped
                exitCode == null || exitCode == 0 ->
                    if (state == "error" || state == "failed") BackgroundShellOutcome.Failed
                    else BackgroundShellOutcome.Finished
                else -> BackgroundShellOutcome.Failed
            }

    public companion object {
        private val ENVELOPE = Regex("""^\s*<shell\b([^>]*)>([\s\S]*?)</shell>\s*$""")
        private val ATTRIBUTE = Regex("""([A-Za-z_][\w-]*)="([^"]*)"""")
        private val EXIT = Regex("""\n?\s*Command exited with code (-?\d+)\.?\s*$""")

        /** Only a synthetic `v2:notice` part can carry a shell result. */
        public fun fromPart(part: Part): BackgroundShellResult? {
            if (part.rawType != "v2:notice") return null
            if (part.toolName != "synthetic" && part.noticeMetadata["kind"] != "synthetic")
                return null
            return parse(part.text, fallbackCommand = part.filename)
        }

        public fun parse(text: String, fallbackCommand: String? = null): BackgroundShellResult? {
            val match = ENVELOPE.find(text) ?: return null
            val attributes =
                ATTRIBUTE.findAll(match.groupValues[1]).associate {
                    it.groupValues[1] to unescape(it.groupValues[2])
                }
            var output = match.groupValues[2]
            var exitCode: Int? = null
            EXIT.find(output)?.let { exit ->
                exitCode = exit.groupValues[1].toIntOrNull()
                output = output.substring(0, exit.range.first)
            }
            val command = (attributes["command"] ?: fallbackCommand ?: "").trim()
            if (command.isEmpty()) return null
            return BackgroundShellResult(
                command,
                attributes["state"] ?: "completed",
                output.trim(),
                exitCode,
            )
        }

        private fun unescape(value: String): String =
            value
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
    }
}

/**
 * A display projection only: preserve the server's part identity while presenting its synthetic
 * shell result through the existing shell tool row. The transcript's original message and persisted
 * wire data stay unchanged.
 */
public fun backgroundShellToolPart(part: Part): Part? {
    val result = BackgroundShellResult.fromPart(part) ?: return null
    val status =
        when (result.outcome) {
            BackgroundShellOutcome.Finished -> ToolStatus.Completed
            BackgroundShellOutcome.Failed -> ToolStatus.Error
            BackgroundShellOutcome.Stopped -> ToolStatus.Cancelled
        }
    val metadata = buildMap {
        put("backgroundShellResult", JsonPrimitive(true))
        result.exitCode?.let { put("exit", JsonPrimitive(it)) }
    }
    return part.copy(
        type = PartType.Tool,
        rawType = "tool",
        text = "",
        toolName = "shell",
        tool =
            ToolState(
                status = status,
                input = JsonObject(mapOf("command" to JsonPrimitive(result.command))),
                output = result.output,
                metadata = JsonObject(metadata),
            ),
        synthetic = false,
        ended = true,
    )
}
