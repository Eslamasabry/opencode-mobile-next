package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.protocol.codex.thread
import io.github.eslamasabry.oc.protocol.codex.turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * B8 (chat "The connection dropped before this reply finished."): the chat reads a reply as cut off only when it has
 * no completion, no finish reason and no error. Codex (app-server 0.153.4) keeps `completedAt: null` for a turn the
 * person interrupted, so its finish reason is what says the reply ended; a failed turn carries its error.
 */
class B8CodexEndsTest {
    @Test
    fun `B8 every ended Codex turn says so on its reply - completion, finish reason or error`() {
        val messages =
            codexMessages(
                thread(
                    turns =
                        listOf(
                            turn("t1", "completed", prefix = "a"),
                            turn("t2", "interrupted", prefix = "b"),
                            turn("t3", "failed", prefix = "c"),
                        )
                )
            )
        val replies = messages.filter { it.info.providerId == "codex" }.associateBy { it.info.id }
        assertNotNull(replies.getValue("a-agent").info.time?.completedMs)
        val interrupted = replies.getValue("b-agent").info
        assertNull(interrupted.time?.completedMs)
        assertEquals("cancelled", interrupted.finish)
        assertNotNull(replies.getValue("c-agent").info.errorText)
    }

    @Test
    fun `B8 a Codex turn still in progress has no end at all, so only the chat's running state holds it`() {
        val reply = codexMessages(thread(turns = listOf(turn(status = "inProgress")), busy = true)).last().info
        assertNull(reply.time?.completedMs)
        assertNull(reply.finish)
        assertNull(reply.errorText)
    }
}
