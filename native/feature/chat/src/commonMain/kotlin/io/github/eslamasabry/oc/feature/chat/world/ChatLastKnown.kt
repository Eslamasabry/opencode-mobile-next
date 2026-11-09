package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts

/**
 * Cache-first (owner rule, 2026-11-09): a conversation opened again draws its real transcript at once from the page
 * it last read, then the live read replaces it in place. There is no offline or loading variant of the chat: while
 * the server is away the kept rows stay and the status line says so. The app binds this to its last-known store
 * (per profile and server, size-capped, cleared with the profile); without a binding a conversation opens as before.
 */
public interface ChatLastKnown {
    /** The kept page already in memory (no I/O), for the first frame. */
    public fun peek(ref: ConversationRef): KeptTranscript?

    /** The kept page, read from storage when not in memory. Null when none, or when it came from another server. */
    public suspend fun read(ref: ConversationRef): KeptTranscript?

    /** Keeps the newest of [messages] (oldest first) and the title, trimmed to the store's cap. */
    public suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>)

    /** A transcript with no server behind it, that draws a kept page with the live transcript's rows. */
    public fun detached(ref: ConversationRef): TranscriptHandle
}
