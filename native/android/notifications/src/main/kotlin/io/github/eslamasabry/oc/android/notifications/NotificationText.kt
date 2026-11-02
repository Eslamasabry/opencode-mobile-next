package io.github.eslamasabry.oc.android.notifications

/**
 * Words that may reach the notification shade, kept safe (plan 16 §1.8, B-SEC-105, B-SEC-106, B-SEC-149).
 *
 * Port of `lib/background/notification_text.dart` (freeze tip). The shade (not the lock screen, which gets a fixed,
 * count-only public version) shows a command or the first line of a reply so the person can decide without opening
 * the app. Nothing secret may ride along: keys, tokens and passwords are masked, and raw errors never become copy.
 */
public object NotificationText {
    public const val MASK: String = "•••"
    public const val MAX_LINE: Int = 140
    public const val MAX_HEAD: Int = 40

    private val whitespace = Regex("(?U)\\s+")

    private val secretPatterns: List<Pair<Regex, (MatchResult) -> String>> =
        listOf(
            // Authorization headers and bearer tokens.
            Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{6,}") to { m -> "${m.groupValues[1]} $MASK" },
            // key=value and key: value pairs that name a secret: the name stays.
            Regex(
                "(?i)\\b((?:[A-Za-z0-9]+[_-])*(?:api[_-]?key|access[_-]?key|secret|token|passwd|password|pwd|authorization|auth)" +
                    "[A-Za-z0-9_-]*)\\s*[=:]\\s*(\"[^\"]*\"|'[^']*'|\\S+)",
            ) to { m -> "${m.groupValues[1]}=$MASK" },
            // Flags that carry a secret.
            Regex("(?i)(--?(?:password|passwd|token|secret|api-key|apikey|key|auth))[ =]+(\"[^\"]*\"|'[^']*'|\\S+)") to
                { m -> "${m.groupValues[1]} $MASK" },
            // user:password@host (the host stays).
            Regex("(?<=://)[^/\\s:@]+:[^/\\s@]+(?=@)") to { _ -> MASK },
            // Well-known token shapes.
            Regex("\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{8,}") to { _ -> MASK },
            Regex("\\bgh[pousr]_[A-Za-z0-9]{16,}") to { _ -> MASK },
            Regex("\\bxox[abprs]-[A-Za-z0-9-]{10,}") to { _ -> MASK },
            Regex("\\bAKIA[0-9A-Z]{16}\\b") to { _ -> MASK },
            Regex("\\bAIza[0-9A-Za-z_-]{20,}") to { _ -> MASK },
            // Any long unbroken token-looking run.
            Regex("[A-Za-z0-9+/_=-]{32,}") to { _ -> MASK },
        )

    /** One short line of [input] with secrets masked, at most [maxLength] long (ellipsised). */
    public fun redact(input: String, maxLength: Int = MAX_LINE): String {
        var text = input.replace(whitespace, " ").trim()
        for ((pattern, replace) in secretPatterns) text = pattern.replace(text, replace)
        if (text.length > maxLength) text = text.substring(0, maxLength - 1).trimEnd() + "…"
        return text
    }

    /**
     * The first line of the newest assistant reply, redacted; empty when none (B-ANDR-048). [replies] are the text
     * parts of assistant messages, newest last; synthetic text is filtered by the caller (it never reaches here).
     * A line that introduces a list is read with its first item, or loses its colon when nothing follows.
     */
    public fun firstReplyLine(replies: List<String>, maxLength: Int = MAX_LINE): String {
        for (text in replies.asReversed()) {
            val lines = text.split('\n').map { it.replaceFirst(Regex("^[#>*\\-\\s`]+"), "").trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            var chosen = lines.first()
            if (chosen.endsWith(':')) {
                chosen = if (lines.size > 1) "$chosen ${lines[1]}" else chosen.dropLast(1).trimEnd()
            }
            return redact(chosen, maxLength)
        }
        return ""
    }

    /**
     * A command's program and, when it has one, its sub-command: "npm install" for `npm install --save left-pad`,
     * "git push" for `git push origin main`, "pytest" for `/usr/bin/pytest -q`. Masked; at most 40 characters.
     */
    public fun commandHead(command: String): String {
        val words = redact(command, maxLength = 400).split(' ').filter { it.isNotEmpty() }.toMutableList()
        // Leading NAME=value assignments only set the environment.
        while (words.isNotEmpty() && Regex("^[A-Za-z_][A-Za-z0-9_]*=").containsMatchIn(words.first())) words.removeAt(0)
        if (words.isEmpty()) return ""
        val program = lastSegment(words.first())
        val head = if (words.size > 1 && Regex("^[a-z][a-z0-9:_-]*$").matches(words[1])) "$program ${words[1]}" else program
        return redact(head, maxLength = MAX_HEAD)
    }

    /**
     * What a permission asks, for a notification title: an [AskAction] and its short, masked target ("npm install",
     * "settings.dart"). Null when the ask names neither a command nor a file; then the alert cannot be allowed from
     * the shade (B-SEC-105).
     */
    public fun permissionAsk(permission: String, commandPreview: String?, filePath: String?): PermissionAsk? {
        if (commandPreview != null && commandPreview.isNotBlank()) {
            return PermissionAsk(AskAction.Run, commandHead(commandPreview))
        }
        if (filePath != null && filePath.isNotBlank()) {
            val action =
                when (permission.lowercase()) {
                    "read" -> AskAction.Read
                    "external_directory" -> AskAction.Folder
                    else -> AskAction.Edit
                }
            return PermissionAsk(action, lastSegment(filePath))
        }
        return null
    }

    /** A plain reason token for a stopped chat; never the server's own text (B-SEC-104). */
    public fun stoppedReason(errorName: String?): StopReason {
        // Same matching as Dart `MessageErrorKind.fromName` (lib/domain/models/models_messages.dart).
        val key = errorName?.trim()?.lowercase()?.replace(Regex("[^a-z]"), "").orEmpty()
        return when {
            key.isEmpty() -> StopReason.Other
            "contextoverflow" in key || "contextlength" in key -> StopReason.Context
            "outputlength" in key || key == "length" -> StopReason.Length
            "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key ->
                StopReason.Auth
            "contentfilter" in key -> StopReason.Filter
            "abort" in key -> StopReason.Other
            "modelnotfound" in key -> StopReason.Model
            else -> StopReason.Other
        }
    }

    /** The notification subtitle: "MacBook · api-server". Either part may be missing; empty when both are. */
    public fun subtitle(server: String?, project: String?): String =
        listOfNotNull(server, project).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

    private fun lastSegment(path: String): String {
        val parts = path.trim().split(Regex("[\\\\/]+")).filter { it.isNotEmpty() }
        return redact(if (parts.isEmpty()) path.trim() else parts.last(), maxLength = MAX_HEAD)
    }
}

public enum class AskAction(public val wire: String) {
    Run("run"), Edit("edit"), Read("read"), Folder("folder");

    public companion object {
        public fun fromWire(value: String): AskAction? = entries.firstOrNull { it.wire == value }
    }
}

public data class PermissionAsk(val action: AskAction, val target: String)

/** Must match `alertReasonText` of the Flutter app; each token has one sentence in `notify_reason_*`. */
public enum class StopReason(public val wire: String) {
    Auth("auth"), Context("context"), Length("length"), Filter("filter"), Model("model"), Other("other");

    public companion object {
        public fun fromWire(value: String?): StopReason = entries.firstOrNull { it.wire == value } ?: Other
    }
}
