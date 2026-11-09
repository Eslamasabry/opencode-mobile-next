package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cache-first (owner rule, 2026-11-09): a conversation's last page as it read, kept so the conversation draws its real
 * rows at once when it opens again (before its server answers, or while it cannot). Only what the transcript draws:
 * the title and the newest messages. [fit] trims it under a byte cap.
 */
@Serializable
public data class KeptTranscript(val title: String? = null, val messages: List<MessageWithParts> = emptyList()) {
    public companion object {
        /** The newest messages kept per conversation: one screen and some scroll-back. */
        public const val MAX_MESSAGES: Int = 60

        /** A tool's searchable text (its output) kept per part; the live read brings the rest back. */
        public const val MAX_TOOL_TEXT: Int = 4_096

        /** A picture sent inline (`data:` address) larger than this is not kept; its row still shows. */
        public const val MAX_INLINE_URL: Int = 16_384

        private val json = Json { encodeDefaults = false }

        /**
         * The newest messages of [messages] that fit in [maxBytes] once encoded, oldest first, with long tool text and
         * large inline pictures trimmed. Null when not even the newest message fits.
         */
        public fun fit(title: String?, messages: List<MessageWithParts>, maxBytes: Int): KeptTranscript? {
            val trimmed =
                messages.takeLast(MAX_MESSAGES).map { m ->
                    m.copy(
                        parts =
                            m.parts.map { p ->
                                var part = p
                                if (p.type == PartType.Tool && p.searchText.length > MAX_TOOL_TEXT)
                                    part = part.copy(searchText = p.searchText.take(MAX_TOOL_TEXT))
                                if ((p.url?.length ?: 0) > MAX_INLINE_URL && p.url!!.startsWith("data:"))
                                    part = part.copy(url = null)
                                part
                            }
                    )
                }
            var from = 0
            while (from < trimmed.size) {
                val kept = KeptTranscript(title, trimmed.subList(from, trimmed.size).toList())
                val size = json.encodeToString(serializer(), kept).encodeToByteArray().size
                if (size <= maxBytes) return kept
                // Drop the oldest quarter (at least one) and try again: a handful of encodes, never one per message.
                from += maxOf(1, (trimmed.size - from) / 4)
            }
            return null
        }
    }
}
