package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import kotlinx.serialization.json.JsonPrimitive

/**
 * Issue 147: what an old phone agent conversation hands to the new one that continues it, read from what the page
 * holds (the kept page, or the live one): the person's requests ([asks], oldest first), the files its steps changed
 * ([files]) and the agent's last words ([last]). The new agent cannot see the old conversation, so the person starts
 * the new one with these, and can read and change them before sending.
 */
public data class ContinueBrief(val asks: List<String>, val files: List<String>, val last: String?) {
    /** The words around the facts, from the words module (the UI resolves them; nothing here is copy). */
    public data class Words(
        /** The first line; `%1$s` is the conversation's title. */
        val intro: String,
        /** The first line when the conversation has no title. */
        val introUntitled: String,
        val asked: String,
        val changed: String,
        val stopped: String,
    )

    /** The message the new conversation starts with: plain text the person can edit. */
    public fun text(title: String?, words: Words): String = buildString {
        val name = title?.trim()?.takeIf { it.isNotEmpty() }
        append(if (name != null) words.intro.replace("%1\$s", name) else words.introUntitled)
        if (asks.isNotEmpty()) {
            append("\n\n").append(words.asked)
            asks.forEach { append("\n- ").append(it) }
        }
        if (files.isNotEmpty()) {
            append("\n\n").append(words.changed)
            files.forEach { append("\n- ").append(it) }
        }
        if (last != null) append("\n\n").append(words.stopped).append('\n').append(last)
    }

    public val isEmpty: Boolean get() = asks.isEmpty() && files.isEmpty() && last == null

    public companion object {
        /** The newest requests kept: the latest ones say where the work was going. */
        public const val MAX_ASKS: Int = 5

        /** A request or the last words longer than this are cut (with an ellipsis): the brief stays short to edit. */
        public const val MAX_ASK_CHARS: Int = 300
        public const val MAX_LAST_CHARS: Int = 600
        public const val MAX_FILES: Int = 20

        private val PATH_KEYS = listOf("filePath", "file_path", "path")

        /** The brief of [messages] (oldest first). */
        public fun of(messages: List<MessageWithParts>): ContinueBrief {
            val asks =
                messages
                    .filter { it.info.role == Role.User }
                    .mapNotNull { m -> words(m)?.let { cut(it, MAX_ASK_CHARS) } }
                    .takeLast(MAX_ASKS)
            val files = LinkedHashSet<String>()
            for (m in messages) {
                if (m.info.role != Role.Assistant) continue
                for (p in m.parts) {
                    if (p.type != PartType.Tool) continue
                    val facts = toolFacts(p)
                    if (facts.kind != ToolKind.Edit && facts.kind != ToolKind.Write && facts.kind != ToolKind.Patch) continue
                    // The whole path the step named, else the file name its line shows.
                    val path = PATH_KEYS.firstNotNullOfOrNull { key -> (p.tool?.input?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content }
                    (path ?: facts.subject)?.trim()?.takeIf { it.isNotEmpty() && '\n' !in it }?.let { files += it }
                }
            }
            val last =
                messages.lastOrNull { it.info.role == Role.Assistant && words(it) != null }?.let { cut(words(it)!!, MAX_LAST_CHARS) }
            return ContinueBrief(asks, files.toList().takeLast(MAX_FILES), last)
        }

        /** A message's own words (its text parts, not its thinking), trimmed; null when it has none. */
        private fun words(m: MessageWithParts): String? =
            m.parts
                .filter { it.type == PartType.Text && !it.synthetic }
                .joinToString("\n") { it.text.trim() }
                .trim()
                .takeIf { it.isNotEmpty() }

        private fun cut(text: String, max: Int): String = if (text.length <= max) text else text.take(max).trimEnd() + "…"
    }
}
