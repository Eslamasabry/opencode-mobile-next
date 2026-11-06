package io.github.eslamasabry.oc.engine.composer.input

/** A sub-agent the `@` strip can offer: its catalog id and the server's description, if any. */
public data class MentionAgent(val id: String, val description: String? = null)

/**
 * The agents half of the `@` suggestion strip (B-CHAT-088; port of the agent branch of Flutter
 * `_inlineSuggestions` in `lib/ui/screens/chat/composer.dart` and `_insertMentionText` in `chat_mentions.dart`).
 *
 * Only visible sub-agents are mentionable (Flutter `_subagents`: not hidden, mode `subagent`); the caller passes
 * that list. A pick writes the same `@id` token [Mentions.agentMentions] reads back at send time, so the server
 * receives the agent part Flutter sends.
 */
public object AgentSuggestions {
    /** Flutter `KitComposerChips.visibleCount`: the strip shows at most this many rows, agents first. */
    public const val STRIP_ROWS: Int = 5

    /**
     * Agents whose id or description contains [query] (any case; an empty query lists all), id-prefix matches
     * first, then by id. Blank and repeated ids are left out.
     */
    public fun rank(query: String, agents: List<MentionAgent>, limit: Int = STRIP_ROWS): List<MentionAgent> {
        val q = query.lowercase()
        val seen = HashSet<String>()
        return agents
            .filter { it.id.isNotBlank() && it.id.none(Char::isWhitespace) && seen.add(it.id) }
            .filter {
                q.isEmpty() ||
                    it.id.lowercase().contains(q) ||
                    it.description?.lowercase()?.contains(q) == true
            }
            .sortedWith(compareBy<MentionAgent> { if (it.id.lowercase().startsWith(q)) 0 else 1 }.thenBy { it.id })
            .take(limit)
    }

    /** The token a pick writes over the `@word` being typed: `@id` and one space, as Flutter writes it. */
    public fun token(agent: MentionAgent): String = "@${agent.id} "

    /** Files share the strip with agents: Flutter keeps the files that fit after the agent rows. */
    public fun fileRoom(agentRows: Int): Int = (STRIP_ROWS - agentRows).coerceAtLeast(0)
}
