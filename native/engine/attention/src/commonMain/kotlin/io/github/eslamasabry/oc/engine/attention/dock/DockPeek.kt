package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One line of a peek card. */
public data class DockPeekRow(val fromPerson: Boolean, val text: String)

/** What a peek card shows for a conversation without switching to it. */
public sealed interface DockPeek {
    /** The newest lines, oldest first (at most [DockPeeks.MAX_ROWS]). */
    public data class Lines(val rows: ImmutableList<DockPeekRow>) : DockPeek

    /** Nothing is cached for this conversation, and the dock reads nothing to find out. */
    public data object Unavailable : DockPeek
}

/**
 * The line the home list already keeps for a conversation (its preview), for conversations the dock has heard nothing
 * from since the app started. Implemented where the home list's cache can be reached.
 */
public fun interface DockPeekFallback {
    public fun lastLine(ref: ConversationRef): String?
}

/**
 * The last few lines of the conversations the dock hears about, taken from the same event stream the dock already
 * follows (user and agent text parts as they arrive). Memory only, bounded, no read of any kind: a conversation that was
 * quiet since the app started falls back to the home list's saved line, and failing that is [DockPeek.Unavailable].
 * A reply that streams shows its text when the part completes, not word by word.
 */
public class DockPeeks(private val fallback: DockPeekFallback = DockPeekFallback { null }) {
    private data class Row(val partId: String, val fromPerson: Boolean, val text: String)

    private val rows = MutableStateFlow<Map<ConversationRef, List<Row>>>(emptyMap())
    private val roles = LinkedHashMap<String, Boolean>()

    /** Call from the one dispatcher the dock runs on. */
    public fun feed(profile: ProfileId, event: GatewayEvent) {
        when (event) {
            is GatewayEvent.MessageUpserted -> {
                roles.remove(event.message.id)
                roles[event.message.id] = event.message.role == Role.User
                while (roles.size > MAX_ROLES) roles.remove(roles.keys.first())
            }
            is GatewayEvent.PartUpserted -> {
                val part = event.part
                if (part.type != PartType.Text || part.synthetic) return
                val text = oneLine(part.text) ?: return
                val ref = ConversationRef(profile, io.github.eslamasabry.oc.core.common.ids.SessionId(part.sessionId))
                val row = Row(part.id, roles[part.messageId] ?: false, text)
                val current = rows.value
                val mine = current[ref].orEmpty()
                val next = if (mine.any { it.partId == row.partId }) mine.map { if (it.partId == row.partId) row else it } else (mine + row).takeLast(MAX_ROWS)
                // Newest conversation last, so the oldest is the one forgotten.
                val map = LinkedHashMap(current)
                map.remove(ref)
                map[ref] = next
                while (map.size > MAX_CONVERSATIONS) map.remove(map.keys.first())
                rows.value = map
            }
            is GatewayEvent.SessionDeleted -> forget(ConversationRef(profile, event.sessionId))
            else -> Unit
        }
    }

    public fun forget(ref: ConversationRef) {
        if (ref in rows.value) rows.value = rows.value - ref
    }

    public fun forgetProfile(profile: ProfileId) {
        rows.value = rows.value.filterKeys { it.profileId != profile }
    }

    /** The peek for [ref]; changes as lines arrive. Starts with what is cached right now. */
    public fun peek(ref: ConversationRef): Flow<DockPeek> =
        rows.map { all ->
            val mine = all[ref].orEmpty()
            when {
                mine.isNotEmpty() -> DockPeek.Lines(mine.map { DockPeekRow(it.fromPerson, it.text) }.toImmutableList())
                else -> fallback.lastLine(ref)?.let { DockPeek.Lines(listOf(DockPeekRow(false, it)).toImmutableList()) } ?: DockPeek.Unavailable
            }
        }.distinctUntilChanged()

    public companion object {
        public const val MAX_ROWS: Int = 4
        public const val MAX_CONVERSATIONS: Int = 64
        public const val MAX_LINE: Int = 200
        private const val MAX_ROLES: Int = 256

        private val lineLead = Regex("^\\s{0,3}(?:#{1,6}\\s+|>\\s?|[-*+]\\s+(?:\\[[ xX]]\\s+)?|```\\S*)")
        private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
        private val strong = Regex("(\\*\\*|__|~~)(?=\\S)(.+?)(?<=\\S)\\1")
        private val code = Regex("`+([^`]*)`+")

        /**
         * Words without the marks a reply is written with, on one line, cut at [MAX_LINE]. Mirrors the home list's rule
         * (`HomePreviewRules.plain`), which lives in a feature module the engine may not use. Null when nothing is left.
         */
        public fun oneLine(text: String): String? {
            val plain =
                text.lineSequence().joinToString("\n") { it.replace(lineLead, "") }
                    .replace(link, "$1").replace(strong, "$2").replace(code, "$1").replace("**", "")
            val folded = plain.replace(Regex("\\s+"), " ").trim()
            if (folded.isEmpty()) return null
            return if (folded.length <= MAX_LINE) folded else folded.substring(0, MAX_LINE - 1).trimEnd() + "…"
        }
    }
}
