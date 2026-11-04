package io.github.eslamasabry.oc.engine.attention.notify

/**
 * Words that may reach the notification shade, kept safe (port of `notification_text.dart`, B-SEC-105/106). The shade
 * (not the lock screen, which gets a count-only public version) shows a command or the first line of a reply; keys,
 * tokens and passwords are masked, and raw errors never become copy.
 */
public object NotificationText {
    public const val MASK: String = "•••"
    public const val MAX_LENGTH: Int = 140

    private val SECRET_PATTERNS: List<Pair<Regex, (MatchResult) -> String>> =
        listOf(
            // Authorization headers and bearer tokens.
            Regex("""\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{6,}""", RegexOption.IGNORE_CASE) to { m -> "${m.groupValues[1]} $MASK" },
            // key=value and key: value pairs that name a secret: the name stays.
            Regex(
                """\b((?:api[_-]?key|access[_-]?key|secret|token|passwd|password|pwd|authorization|auth)[A-Za-z0-9_-]*)\s*[=:]\s*("[^"]*"|'[^']*'|\S+)""",
                RegexOption.IGNORE_CASE,
            ) to { m -> "${m.groupValues[1]}=$MASK" },
            // Flags that carry a secret.
            Regex("""(--?(?:password|passwd|token|secret|api-key|apikey|key|auth))[ =]+("[^"]*"|\S+)""", RegexOption.IGNORE_CASE) to
                { m -> "${m.groupValues[1]} $MASK" },
            // user:password@host
            Regex("""(?<=://)[^/\s:@]+:[^/\s@]+(?=@)""") to { _ -> MASK },
            // Well-known token shapes.
            Regex("""\b(?:sk|pk|rk)-[A-Za-z0-9_-]{8,}""") to { _ -> MASK },
            Regex("""\bgh[pousr]_[A-Za-z0-9]{16,}""") to { _ -> MASK },
            Regex("""\bxox[abprs]-[A-Za-z0-9-]{10,}""") to { _ -> MASK },
            Regex("""\bAKIA[0-9A-Z]{16}\b""") to { _ -> MASK },
            Regex("""\bAIza[0-9A-Za-z_-]{20,}""") to { _ -> MASK },
            // Any long unbroken token-looking run.
            Regex("""[A-Za-z0-9+/_=-]{32,}""") to { _ -> MASK },
        )
    private val WHITESPACE = Regex("""\s+""")
    private val REPLY_PREFIX = Regex("""^[#>*\-\s`]+""")
    private val ASSIGNMENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*=""")
    private val SUB_COMMAND = Regex("""^[a-z][a-z0-9:_-]*$""")
    private val PATH_SEPARATORS = Regex("""[\\/]+""")

    /** One short line of [input] with secrets masked, at most [maxLength] long. */
    public fun redact(input: String, maxLength: Int = MAX_LENGTH): String {
        var text = input.replace(WHITESPACE, " ").trim()
        for ((pattern, replace) in SECRET_PATTERNS) text = pattern.replace(text, replace)
        if (text.length > maxLength) text = text.substring(0, maxLength - 1).trimEnd() + "…"
        return text
    }

    /** One transcript message as notification copy sees it. */
    public data class ReplyMessage(val role: String, val parts: List<ReplyPart>)

    public data class ReplyPart(val type: String, val text: String, val synthetic: Boolean = false)

    /** The first line of the newest assistant reply, redacted; empty when none. */
    public fun firstReplyLine(messages: List<ReplyMessage>, maxLength: Int = MAX_LENGTH): String {
        for (message in messages.asReversed()) {
            if (message.role != "assistant") continue
            for (part in message.parts) {
                if (part.type != "text" || part.synthetic) continue
                val lines = part.text.split('\n').map { it.replaceFirst(REPLY_PREFIX, "").trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) continue
                var chosen = lines.first()
                if (chosen.endsWith(':')) {
                    // A line that introduces a list is read with its first item, or loses the colon alone.
                    chosen = if (lines.size > 1) "$chosen ${lines[1]}" else chosen.dropLast(1).trimEnd()
                }
                return redact(chosen, maxLength)
            }
        }
        return ""
    }

    /** A plain reason token for a stopped chat, never the server's own text (matches native `alertReasonText`). */
    public fun stoppedReasonToken(errorName: String?): String {
        val trimmed = errorName?.trim()
        if (trimmed.isNullOrEmpty()) return "other"
        val key = trimmed.lowercase().replace(Regex("[^a-z]"), "")
        return when {
            key.contains("contextoverflow") || key.contains("contextlength") -> "context"
            key.contains("outputlength") || key == "length" -> "length"
            key.contains("providerauth") || key.contains("needsauth") || key.contains("unauthorized") || key.contains("authentication") -> "auth"
            key.contains("contentfilter") -> "filter"
            key.contains("abort") -> "other"
            key.contains("modelnotfound") -> "model"
            else -> "other"
        }
    }

    /** One question as notification buttons see it. */
    public data class QuestionPrompt(val multiple: Boolean, val choices: List<String>)

    /** One single-choice prompt with three or fewer short, distinct choices becomes buttons; else none. */
    public fun optionsOf(prompts: List<QuestionPrompt>): List<String> {
        if (prompts.size != 1) return emptyList()
        val prompt = prompts.single()
        if (prompt.multiple) return emptyList()
        val labels = prompt.choices.map { it.trim() }
        if (labels.isEmpty() || labels.size > 3) return emptyList()
        if (labels.any { it.isEmpty() || it.length > 24 }) return emptyList()
        if (labels.toSet().size != labels.size) return emptyList()
        return labels
    }

    /** A permission request as the shade sees it: its kind, metadata strings and patterns (resources). */
    public data class PermissionAsk(
        val permission: String,
        val metadata: Map<String, String> = emptyMap(),
        val patterns: List<String> = emptyList(),
    ) {
        /** The shell command awaiting approval: `metadata.command`, else the first pattern of a bash/shell ask. */
        val commandPreview: String?
            get() = firstString(listOf("command", "cmd")) ?: if (permission.lowercase() in COMMAND_PERMISSIONS) firstPattern() else null

        /** The file the ask concerns: metadata first, else the first pattern of a file-scoped permission. */
        val filePath: String?
            get() = firstString(FILE_KEYS) ?: if (permission.lowercase() in FILE_PERMISSIONS) firstPattern() else null

        private fun firstString(keys: List<String>): String? = keys.firstNotNullOfOrNull { k -> metadata[k]?.takeIf { it.isNotBlank() } }

        private fun firstPattern(): String? = patterns.firstOrNull { it.isNotBlank() && it != "*" }

        private companion object {
            val COMMAND_PERMISSIONS = setOf("bash", "shell")
            val FILE_KEYS = listOf("filePath", "filepath", "file_path", "path", "file")
            val FILE_PERMISSIONS = setOf("edit", "write", "read", "multiedit", "patch", "external_directory")
        }
    }

    /** An action token (`run`, `edit`, `read`, `folder`) and its short masked target; both empty with nothing to show. */
    public data class AskTitle(val action: String, val target: String)

    public fun permissionAskOf(ask: PermissionAsk): AskTitle {
        val command = ask.commandPreview
        if (command != null && command.isNotBlank()) return AskTitle("run", commandHead(command))
        val path = ask.filePath
        if (path != null && path.isNotBlank()) {
            val action =
                when (ask.permission.lowercase()) {
                    "read" -> "read"
                    "external_directory" -> "folder"
                    else -> "edit"
                }
            return AskTitle(action, lastSegment(path))
        }
        return AskTitle("", "")
    }

    /** A command's program and, when it has one, its sub-command ("npm install", "git push", "pytest"). Masked; ≤ 40. */
    public fun commandHead(command: String): String {
        val words = redact(command, maxLength = 400).split(' ').filter { it.isNotEmpty() }.toMutableList()
        // Leading NAME=value assignments only set the environment.
        while (words.isNotEmpty() && ASSIGNMENT.containsMatchIn(words.first())) words.removeAt(0)
        if (words.isEmpty()) return ""
        val program = lastSegment(words.first())
        val head = if (words.size > 1 && SUB_COMMAND.matches(words[1])) "$program ${words[1]}" else program
        return redact(head, maxLength = 40)
    }

    /** The notification subtitle "server · project"; either part optional, empty when both are missing. */
    public fun subtitle(server: String?, project: String?): String =
        listOfNotNull(server, project).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

    private fun lastSegment(path: String): String {
        val parts = path.trim().split(PATH_SEPARATORS).filter { it.isNotEmpty() }
        return redact(if (parts.isEmpty()) path.trim() else parts.last(), maxLength = 40)
    }
}

/**
 * What a permission alert shows on the shade (port of the permission branch of `connection/alerts.dart`, B-SEC-105).
 * [shadeOnly] is always true: the lock screen gets a count-only public version. [askAction]/[askTarget] build
 * "Run npm run?"; both empty means the alert cannot be allowed from the shade, only opened.
 */
public data class PermissionShadeCopy(
    val askAction: String,
    val askTarget: String,
    val detail: String,
    val subtext: String,
    val chatTitle: String?,
    val shadeOnly: Boolean = true,
) {
    public companion object {
        public fun of(ask: NotificationText.PermissionAsk, server: String?, project: String?, chatTitle: String?): PermissionShadeCopy {
            val title = NotificationText.permissionAskOf(ask)
            return PermissionShadeCopy(
                askAction = title.action,
                askTarget = title.target,
                detail = NotificationText.redact(ask.commandPreview ?: ask.filePath ?: ""),
                subtext = NotificationText.subtitle(server, project),
                chatTitle = chatTitle?.let { NotificationText.redact(it) }?.ifEmpty { null },
            )
        }
    }
}
