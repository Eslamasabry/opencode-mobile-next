package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The bodies of the three agent block fences (map CHAT-GENUI-20; Flutter `agent_blocks.dart`). Built only for closed
 * fences: choices are rows that insert their text into the composer, a checklist is read-only (done items muted, not
 * struck through), commands are "Run on your computer" lines each with a copy button.
 */
public object AgentBlocks {
    private val choiceMarker = Regex("""^(?:[-*+]|\d+[.)])\s+""")
    private val checklistItem = Regex("""(?:[-*+]\s+)?\[([ xX])\]\s*(${Dart.DOT}*)""")

    public fun parse(type: AgentBlockType, body: String): ImmutableList<AgentItem> =
        when (type) {
            AgentBlockType.Choices -> choices(body)
            AgentBlockType.Checklist -> checklist(body)
            AgentBlockType.Command -> commands(body)
        }

    /** One option per non-empty line, list markers dropped. */
    public fun choices(body: String): ImmutableList<AgentItem> =
        body
            .split('\n')
            .map { choiceMarker.replaceFirst(it.trim(), "") }
            .filter { it.isNotEmpty() }
            .map { AgentItem(it) }
            .toImmutableList()

    /** `[ ]` / `[x]` lines (an optional bullet first); any other non-empty line is an open item. */
    public fun checklist(body: String): ImmutableList<AgentItem> {
        val items = ArrayList<AgentItem>()
        for (raw in body.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = checklistItem.matchEntire(line)
            items +=
                if (m == null) {
                    AgentItem(line, done = false)
                } else {
                    AgentItem(m.groupValues[2].trim(), done = m.groupValues[1].lowercase() == "x")
                }
        }
        return items.toImmutableList()
    }

    /** One command per non-blank line, trailing spaces dropped, leading indent kept. */
    public fun commands(body: String): ImmutableList<AgentItem> =
        body
            .split('\n')
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map { AgentItem(it) }
            .toImmutableList()
}
