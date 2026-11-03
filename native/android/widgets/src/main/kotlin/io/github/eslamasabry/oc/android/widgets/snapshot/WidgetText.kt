package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor

/**
 * Launcher-safe words (port of the text rules in Flutter `lib/background/widget_snapshot.dart`). Every title,
 * folder, path and excerpt that reaches the home screen goes through here: secrets masked by the app's [Redactor],
 * control characters and bidirectional overrides blanked, one line, bounded by code points.
 */
public class WidgetText(private val redactor: Redactor) {
    /** Flutter `safeWidgetTitle`: at most 80 code points. */
    public fun title(value: String): String = safe(value, TITLE_LIMIT)

    /** Flutter `safeWidgetPath`: a file path or folder, with room for a full path. */
    public fun path(value: String): String = safe(value, PATH_LIMIT)

    /** Flutter `safeWidgetProject`: only a display name, even when a path is given; a generic folder shows nothing. */
    public fun project(value: String): String {
        val last = value.split(PATH_SEPARATORS).lastOrNull { it.isNotEmpty() } ?: return ""
        val name = title(last)
        return if (name.lowercase() in GENERIC_PROJECTS) "" else name
    }

    /** Flutter `safeWidgetPreview`: no inline reasoning, no raw Markdown, at most 240 code points. */
    public fun preview(value: String): String = safe(WidgetMarkdown.strip(WidgetMarkdown.withoutReasoning(value)), PREVIEW_LIMIT)

    /**
     * Flutter `safeWidgetAssistantPreview`: the latest assistant prose only. Never user prompts, synthetic text,
     * reasoning, tool output or message errors.
     */
    public fun assistantPreview(messages: List<MessageWithParts>): String {
        for (message in messages.asReversed()) {
            if (message.info.role != Role.Assistant) continue
            val joined = message.parts.filter { it.type == PartType.Text && !it.synthetic }.joinToString(" ") { it.text }
            val shown = preview(joined)
            if (shown.isNotEmpty()) return shown
        }
        return ""
    }

    /**
     * Flutter `widgetAskFor`: what a permission request says on the home screen. [kind] is run, write, read or other;
     * a command is never split into path parts, and a request that names only a pattern gives a folder, never an
     * invented file. [target] is the file or command of the call the request belongs to, when it was seen.
     */
    public fun askFor(
        permission: String,
        patterns: List<String>,
        words: WidgetSnapshotWords,
        tool: String? = null,
        target: String? = null,
    ): Map<String, String> {
        val toolName = tool?.trim()?.lowercase().orEmpty()
        val kind =
            when (permission) {
                "bash", "shell" -> "run"
                "edit", "write", "multiedit", "patch" -> "write"
                "read" -> "read"
                "external_directory" ->
                    when (toolName) {
                        "write", "edit", "multiedit", "patch", "apply_patch" -> "write"
                        "read", "glob", "grep", "list", "ls" -> "read"
                        "bash", "shell" -> "run"
                        else -> "other"
                    }
                else -> "other"
            }
        val question =
            when {
                permission == "external_directory" && kind == "write" -> words.askOutsideWrite
                permission == "external_directory" && kind == "read" -> words.askOutsideRead
                permission == "external_directory" && kind == "run" -> words.askOutsideRun
                permission == "external_directory" -> words.askDirectory
                permission == "bash" || permission == "shell" -> words.askCommand
                permission == "read" -> words.askRead
                permission in setOf("edit", "write", "multiedit", "patch") -> words.askEdit
                else -> words.usePermission(permission)
            }
        val first = patterns.firstOrNull()?.trim().orEmpty()
        val seen = target?.trim().orEmpty()
        val base = linkedMapOf("kind" to kind, "q" to title(question))
        if (kind == "run") {
            // Without a seen call the request's own pattern is the command. It is text, not a path.
            val command = seen.ifEmpty { if (permission == "external_directory") "" else first }
            // An outside-the-project ask names the folder it reaches into in its pattern.
            val folder = if (permission == "external_directory") first.replace(TRAILING_GLOB, "") else ""
            if (command.isNotEmpty()) base["file"] = title(command)
            if (folder.isNotEmpty()) base["dir"] = path(folder)
            return base
        }
        if (seen.isEmpty()) {
            val folder = first.replace(TRAILING_GLOB, "")
            if (folder.isNotEmpty()) base["dir"] = path(folder)
            return base
        }
        val isPath = ' ' !in seen && '/' in seen
        val cut = if (isPath) seen.lastIndexOf('/') else -1
        base["file"] = title(if (isPath) seen.substring(cut + 1) else seen)
        if (isPath) base["path"] = path(seen)
        if (isPath && cut > 0) base["dir"] = path(seen.substring(0, cut))
        return base
    }

    /** Flutter `_safeWidgetAsk`: only kind, question, file, path and folder cross into launcher storage. */
    public fun safeAsk(ask: Map<String, Any?>): Map<String, String> {
        fun field(key: String, clean: (String) -> String): String = (ask[key] as? String)?.let(clean).orEmpty()
        val kind = (ask["kind"] as? String)?.takeIf { it in ASK_KINDS } ?: "other"
        return linkedMapOf(
            "kind" to kind,
            "q" to field("q", ::title),
            "file" to field("file", ::title),
            "path" to field("path", ::path),
            "dir" to field("dir", ::path),
        )
    }

    private fun safe(value: String, limit: Int): String {
        val masked = redactor.redact(value)
        val out = StringBuilder(masked.length)
        var i = 0
        while (i < masked.length) {
            val c = masked.codePointAt(i)
            if (unsafe(c)) out.append(' ') else out.appendCodePoint(c)
            i += Character.charCount(c)
        }
        return takeCodePoints(out.toString().replace(WHITESPACE_RUN, " ").trim(), limit)
    }

    public companion object {
        public const val TITLE_LIMIT: Int = 80
        public const val PATH_LIMIT: Int = 160
        public const val PREVIEW_LIMIT: Int = 240

        private val ASK_KINDS = setOf("run", "write", "read")
        private val PATH_SEPARATORS = Regex("[\\\\/]+")
        private val TRAILING_GLOB = Regex("[\\\\/]?\\*+$")
        private val WHITESPACE_RUN = Regex("(?U)\\s+")
        private val GENERIC_PROJECTS = setOf("project", "projects", "workspace", "tmp", "temp", "home", "root", "src")

        /** Control characters and the bidirectional overrides and isolates (which can reorder a launcher title). */
        private fun unsafe(c: Int): Boolean =
            c < 0x20 || c == 0x7f || c == 0x061c || c == 0x200e || c == 0x200f || c in 0x202a..0x202e || c in 0x2066..0x2069

        internal fun takeCodePoints(value: String, limit: Int): String {
            var end = 0
            var count = 0
            while (end < value.length && count < limit) {
                end += Character.charCount(value.codePointAt(end))
                count++
            }
            return value.substring(0, end)
        }
    }
}

/** Flutter `stripWidgetMarkdown` and `_withoutInlineReasoning`. */
public object WidgetMarkdown {
    private val fence = Regex("^\\s*```[^\\n]*$", RegexOption.MULTILINE)
    private val link = Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)")
    private val heading = Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE)
    private val quote = Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE)
    private val listMarker = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+", RegexOption.MULTILINE)
    private val code = Regex("`+([^`]*)`+")
    private val strong = Regex("(\\*\\*|__|~~)(.+?)\\1")
    private val starEmphasis = Regex("(?<![\\w*])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![\\w*])")
    private val underscoreEmphasis = Regex("(?<![\\w_])_(?!\\s)([^_\\n]+?)(?<!\\s)_(?![\\w_])")
    private val reasoningMarker = Regex("<\\s*(/?)\\s*(?:think|thinking)\\s*>", RegexOption.IGNORE_CASE)

    /** Plain words for a launcher line: no backticks, emphasis, heading or list markers; links keep their label. */
    public fun strip(value: String): String =
        value
            .replace(fence, "")
            .replace(link) { it.groupValues[1] }
            .replace(heading, "")
            .replace(quote, "")
            .replace(listMarker, "")
            .replace(code) { it.groupValues[1] }
            .replace(strong) { it.groupValues[2] }
            .replace(starEmphasis) { it.groupValues[1] }
            .replace(underscoreEmphasis) { it.groupValues[1] }
            .replace("`", "")

    /**
     * Some providers put reasoning delimiters inside an ordinary text part. Complete and still-open blocks are hidden
     * before the excerpt is cut; an orphan close may follow cut-off reasoning, so its prefix goes too. Only these
     * explicit delimiters are recognised, never arbitrary HTML or code.
     */
    public fun withoutReasoning(value: String): String {
        val visible = StringBuilder()
        var depth = 0
        var offset = 0
        for (marker in reasoningMarker.findAll(value)) {
            if (depth == 0) visible.append(value, offset, marker.range.first)
            if (marker.groupValues[1].isEmpty()) {
                depth += 1
            } else if (depth > 0) {
                depth -= 1
                if (depth == 0) visible.append(' ')
            } else {
                visible.setLength(0)
            }
            offset = marker.range.last + 1
        }
        if (depth == 0) visible.append(value, offset, value.length)
        return visible.toString()
    }
}
