package io.github.eslamasabry.oc.engine.composer.input

import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment

/**
 * `@` mentions (plan 09 §10.7; ports of `resolveFileMentions` in `lib/domain/models/models_requests.dart` and
 * `_promptAgentMentions` in `chat_send.dart`). Offsets are UTF-16 code units, the same as the OpenCode wire.
 */
public object Mentions {
    private val BEFORE = Regex("[\\s(\\[{\"']")
    private val AFTER = Regex("[\\s.,!?;:)}\\]\"']")
    private val WINDOWS = Regex("^[A-Za-z]:[\\\\/]")

    private fun boundaryBefore(text: String, start: Int): Boolean = start == 0 || BEFORE.matches(text[start - 1].toString())

    private fun boundaryAfter(text: String, end: Int): Boolean = end == text.length || AFTER.matches(text[end].toString())

    /** A project file picked from the `@` list: `text/plain`, a `file:` URL, tied to the `@path` text. */
    public fun projectFile(directory: String, relativePath: String): PromptAttachment {
        val base = directory.removeSuffix("/")
        return PromptAttachment(
            mime = "text/plain",
            filename = relativePath.split('/').last(),
            url = fileUrl("$base/$relativePath"),
            mentionPath = relativePath,
        )
    }

    /** The text a file mention answers to. */
    public fun mentionText(attachment: PromptAttachment): String = "@${attachment.mentionPath}"

    /**
     * Keeps each file mention whose `@path` is still in [text] (with its range; the first unclaimed match at word
     * boundaries) and drops the ones the person deleted; other attachments pass through (B-CHAT-087).
     */
    public fun resolveFileMentions(text: String, attachments: List<PromptAttachment>): List<PromptAttachment> {
        val out = mutableListOf<PromptAttachment>()
        val claimed = HashSet<Int>()
        for (attachment in attachments) {
            if (attachment.mentionPath == null) {
                out += attachment
                continue
            }
            val value = mentionText(attachment)
            var from = 0
            while (from <= text.length - value.length) {
                val start = text.indexOf(value, from)
                if (start < 0) break
                val end = start + value.length
                if (start !in claimed && boundaryBefore(text, start) && boundaryAfter(text, end)) {
                    claimed += start
                    out += attachment.copy(mentionStart = start, mentionEnd = end)
                    break
                }
                from = start + 1
            }
        }
        return out
    }

    /** Sub-agent mentions in [text] for the visible sub-agent ids [agents]; longest names first, by position. */
    public fun agentMentions(text: String, agents: Collection<String>): List<PromptAgentMention> {
        val names = agents.filter { it.isNotEmpty() }.toSet().sortedByDescending { it.length }
        val mentions = mutableListOf<PromptAgentMention>()
        for (name in names) {
            val value = "@$name"
            var offset = 0
            while (offset < text.length) {
                val start = text.indexOf(value, offset)
                if (start < 0) break
                val end = start + value.length
                if (boundaryBefore(text, start) && boundaryAfter(text, end)) {
                    mentions += PromptAgentMention(name = name, value = value, start = start, end = end)
                }
                offset = end
            }
        }
        return mentions.sortedBy { it.start }
    }

    /** Dart `Uri.file(path, windows: …).toString()` for the paths the app builds. */
    internal fun fileUrl(path: String): String {
        val windows = WINDOWS.containsMatchIn(path) || path.startsWith("\\\\")
        if (windows) {
            val forward = path.replace('\\', '/')
            return if (forward.startsWith("//")) "file:" + encodePath(forward) else "file:///" + encodePath(forward)
        }
        return "file://" + encodePath(path)
    }

    private const val PATH_SAFE = "-._~!$&'()*+,;=:@/"

    private fun encodePath(path: String): String =
        buildString {
            for (b in path.encodeToByteArray()) {
                val c = (b.toInt() and 0xff)
                val ch = c.toChar()
                if (c < 0x80 && (ch.isLetterOrDigit() || ch in PATH_SAFE)) {
                    append(ch)
                } else {
                    append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
                }
            }
        }
}
