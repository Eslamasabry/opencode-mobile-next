package io.github.eslamasabry.oc.feature.agents.hub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * AG5 (sweep-b 2026-11-11): a conversation record as a person reads it. The record itself is a wire artefact written
 * for the agent that receives it (fixed English headings, JSON-quoted labels, the source conversation's id, fenced
 * message text, compact tool lines); the page shows only the conversation's title and what was said, as Markdown,
 * and keeps the whole record, exactly as it will be copied, under Details.
 *
 * [you] and [agent] are the words for who spoke ("You", "Agent").
 */
public data class HandoffReading(val title: String?, val body: String) {
    public companion object {
        public fun of(markdown: String, you: String, agent: String): HandoffReading {
            val lines = markdown.lines()
            val title =
                lines
                    .firstOrNull { it.startsWith(TITLE) }
                    ?.removePrefix(TITLE)
                    ?.let(::unquote)
                    ?.takeIf { it.isNotBlank() }
            val start = lines.indexOfFirst { it == CONVERSATION }
            if (start < 0) return HandoffReading(title, "")
            val out = StringBuilder()
            var fence: String? = null
            var skipBlock = false
            var skipTurn = false
            for (line in lines.drop(start + 1)) {
                val open = fence
                if (open != null) {
                    if (line == open) fence = null
                    else if (!skipBlock && !skipTurn) out.appendLine(line)
                    continue
                }
                if (FENCE.matches(line)) {
                    fence = line
                    continue
                }
                skipBlock = false
                when {
                    line == "### User" -> {
                        skipTurn = false
                        out.appendLine().appendLine("#### $you")
                    }
                    line == "### Assistant" -> {
                        skipTurn = false
                        out.appendLine().appendLine("#### $agent")
                    }
                    // Notices from the source and other roles carry no words a person said.
                    line.startsWith("### ") -> skipTurn = true
                    // The error that ended a message is technical: the record keeps it, the reading does not.
                    line == ERROR -> skipBlock = true
                    line.startsWith("- Tool ") ||
                        line.startsWith("Attachment contents excluded:") ||
                        line.startsWith("Older messages omitted") ||
                        line == COMPACTION -> Unit
                    skipTurn -> Unit
                    else -> out.appendLine(line)
                }
            }
            return HandoffReading(title, out.toString().trim())
        }

        private fun unquote(value: String): String =
            runCatching { (Json.parseToJsonElement(value.trim()) as? JsonPrimitive)?.contentOrNull }.getOrNull()
                ?: value.trim().removeSurrounding("\"")

        private const val TITLE = "Title: "
        private const val CONVERSATION = "## Conversation"
        private const val ERROR = "Message ended with an error:"
        private const val COMPACTION = "The source reported a conversation compaction event."
        private val FENCE = Regex("^`{3,}$")
    }
}
