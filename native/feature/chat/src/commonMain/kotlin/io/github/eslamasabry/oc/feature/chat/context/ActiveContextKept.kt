package io.github.eslamasabry.oc.feature.chat.context

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.context.ActiveContextMessage
import io.github.eslamasabry.oc.engine.agents.context.ContextContent
import io.github.eslamasabry.oc.engine.agents.context.ContextContentKind
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the inspector opens on the messages it last listed, then the live read
 * replaces them in place. Bound by the app to its last-known store; without a binding it loads as before.
 */
public interface ActiveContextLastKnown {
    public fun peek(ref: ConversationRef): KeptActiveContext?

    public suspend fun read(ref: ConversationRef): KeptActiveContext?

    public suspend fun keep(ref: ConversationRef, kept: KeptActiveContext)
}

/**
 * The message list as the person saw it: id, type and the start of each prose part. Tool input and output text are
 * never kept (they may carry secrets), and every text is cut, so the whole entry stays well under its shelf cap.
 */
@Serializable
public data class KeptActiveContext(val messages: List<Message> = emptyList()) {
    @Serializable
    public data class Part(val kind: ContextContentKind, val text: String = "", val name: String? = null)

    @Serializable
    public data class Message(val id: String, val type: String, val parts: List<Part> = emptyList())

    public fun toMessages(): List<ActiveContextMessage> =
        messages.map { m -> ActiveContextMessage(m.id, m.type, m.parts.map { ContextContent(it.kind, it.text, it.name) }) }

    public companion object {
        public const val MAX_MESSAGES: Int = 60
        public const val MAX_PARTS: Int = 4
        public const val MAX_TEXT: Int = 400

        private val PROSE = setOf(ContextContentKind.Text, ContextContentKind.Reasoning, ContextContentKind.Notice)

        public fun of(messages: List<ActiveContextMessage>): KeptActiveContext =
            KeptActiveContext(
                messages.take(MAX_MESSAGES).map { m ->
                    Message(
                        m.id.take(120),
                        m.type.take(60),
                        m.content.take(MAX_PARTS).map { p ->
                            Part(p.kind, if (p.kind in PROSE) p.text.take(MAX_TEXT) else "", p.name?.take(60))
                        },
                    )
                }
            )
    }
}
