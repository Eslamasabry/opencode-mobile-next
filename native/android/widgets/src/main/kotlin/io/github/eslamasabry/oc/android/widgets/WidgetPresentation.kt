package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.widget.WidgetText

/**
 * Launcher display fields only. Routing, tool input and request metadata never enter the result.
 */
public class WidgetPresentation(context: Context, redactor: Redactor) {
    private val app = context.applicationContext
    private val text = WidgetText(redactor)

    public fun title(value: String?): String =
        text.sessionTitle(value, NativeStrings.get(app, R.string.widget_v2_new))

    public fun project(value: String): String = text.project(value)

    /**
     * Callers supply assistant prose only; reasoning and Markdown are removed before publication.
     */
    public fun preview(value: String): String = text.preview(value)

    /** Only ordinary assistant text: user prompts, synthetic parts and tool output are excluded. */
    public fun assistantPreview(messages: List<MessageWithParts>): String {
        for (message in messages.asReversed()) {
            if (message.info.role != Role.Assistant) continue
            val value =
                preview(
                    message.parts
                        .filter { it.type == PartType.Text && !it.synthetic }
                        .joinToString(" ") { it.text }
                )
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    /**
     * The observed call may supply its reviewed target. Without it a folder pattern remains a
     * folder, and a shell pattern remains one command. Never read arbitrary request metadata.
     */
    public fun ask(
        permission: String,
        patterns: List<String>,
        tool: String? = null,
        target: String? = null,
    ): Map<String, String> {
        val toolName = tool?.trim()?.lowercase().orEmpty()
        val kind =
            when (permission) {
                "bash",
                "shell" -> "run"
                "edit",
                "write",
                "multiedit",
                "patch" -> "write"
                "read" -> "read"
                "external_directory" ->
                    when (toolName) {
                        "write",
                        "edit",
                        "multiedit",
                        "patch",
                        "apply_patch" -> "write"
                        "read",
                        "glob",
                        "grep",
                        "list",
                        "ls" -> "read"
                        "bash",
                        "shell" -> "run"
                        else -> "other"
                    }
                else -> "other"
            }
        val question =
            when {
                permission == "external_directory" ->
                    when (kind) {
                        "write" -> R.string.widget_ask_outside_write
                        "read" -> R.string.widget_ask_outside_read
                        "run" -> R.string.widget_ask_outside_run
                        else -> R.string.widget_ask_directory
                    }
                kind == "run" -> R.string.widget_ask_command
                kind == "write" -> R.string.widget_ask_edit
                kind == "read" -> R.string.widget_ask_read
                else -> R.string.widget_ask_permission
            }
        val result =
            mutableMapOf("kind" to kind, "q" to text.title(NativeStrings.get(app, question)))
        val first = patterns.firstOrNull()?.trim().orEmpty()
        val seen = target?.trim().orEmpty()
        val folder = first.replace(Regex("""[\\/]?\*+$"""), "")
        if (kind == "run") {
            val command = seen.ifEmpty { if (permission == "external_directory") "" else first }
            if (command.isNotEmpty()) result["file"] = text.title(command)
            if (permission == "external_directory" && folder.isNotEmpty())
                result["dir"] = text.path(folder)
        } else if (seen.isEmpty()) {
            if (folder.isNotEmpty()) result["dir"] = text.path(folder)
        } else {
            val isPath = '/' in seen
            val cut = if (isPath) seen.lastIndexOf('/') else -1
            result["file"] = text.title(if (isPath) seen.substring(cut + 1) else seen)
            if (isPath) result["path"] = text.path(seen)
            if (cut > 0) result["dir"] = text.path(seen.substring(0, cut))
        }
        return result
    }
}
