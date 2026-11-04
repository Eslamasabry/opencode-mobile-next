package io.github.eslamasabry.oc.engine.agents.context

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One inspector part's kind (lib/domain/active_context.dart `ContextContentKind`). */
public enum class ContextContentKind { Text, Reasoning, ToolInput, ToolOutput, File, Notice, Pruned, Truncated }

/**
 * One text-only part. [text] is never a binary body, an attachment address or metadata; [name] is a label (tool name,
 * MIME type, status). Pruned, truncated and unknown parts carry empty [text]: the UI words them.
 */
public data class ContextContent(val kind: ContextContentKind, val text: String, val name: String? = null)

/** One message of the server's active context, as the explicit inspector shows it. */
public class ActiveContextMessage(
    public val id: String,
    public val type: String,
    content: List<ContextContent>,
) {
    public val content: ImmutableList<ContextContent> = content.toImmutableList()

    /** Search over id, type and each part's text and label (Dart `matches`; the query is compared lower-cased). */
    public fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return id.lowercase().contains(q) || type.lowercase().contains(q) ||
            content.any { it.text.lowercase().contains(q) || it.name?.lowercase()?.contains(q) == true }
    }

    /**
     * A 180-unit preview around the first match, 60 units of lead-in (Dart `previewFor`). It never starts on a low
     * surrogate or ends between a surrogate pair, so an emoji is never split; whitespace runs fold to one space and an
     * ellipsis marks each cut end.
     */
    public fun previewFor(query: String): String {
        val q = query.lowercase()
        val candidates = content.map { it.text }.filter { it.isNotEmpty() }
        val text = candidates.firstOrNull { q.isNotEmpty() && it.lowercase().contains(q) } ?: candidates.firstOrNull() ?: ""
        val match = if (q.isEmpty()) 0 else text.lowercase().indexOf(q)
        var start = if (match > 60) match - 60 else 0
        if (start > 0 && text[start].isLowSurrogate()) start--
        var end = (start + 180).coerceIn(0, text.length)
        if (end < text.length && text[end].isLowSurrogate()) end--
        val head = if (start > 0) "…" else ""
        val tail = if (end < text.length) "…" else ""
        return head + text.substring(start, end).replace(WHITESPACE, " ") + tail
    }

    override fun equals(other: Any?): Boolean =
        other is ActiveContextMessage && other.id == id && other.type == type && other.content == content

    override fun hashCode(): Int = (id.hashCode() * 31 + type.hashCode()) * 31 + content.hashCode()

    override fun toString(): String = "ActiveContextMessage($id, $type, ${content.size} parts)" // no text in logs

    private companion object {
        /** Dart `\s` (Unicode white space), not only ASCII. */
        val WHITESPACE = Regex("[\\s\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")
    }
}
