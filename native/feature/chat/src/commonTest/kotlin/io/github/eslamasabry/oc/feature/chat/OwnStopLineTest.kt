package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.LinkRetryFacts
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnStopLineTest {
    @Test
    fun `B-CHAT-307 the person's own stop carries no retry countdown even while a retry is scheduled`() {
        val line =
            LinkRetryFacts(
                retryAtMs = 106_000,
                nowMs = 100_000,
                onThisPhone = true,
                serverWait = ChatServerWait.StoppedByPerson,
            ).line("c", ChatStatusKind.Offline)
        assertNull(line.retryInSeconds)
        assertEquals(ChatServerWait.StoppedByPerson, line.serverWait)
    }

    @Test
    fun `B-CHAT-310 a server that stopped by itself keeps its retry countdown`() {
        val line =
            LinkRetryFacts(retryAtMs = 106_000, nowMs = 100_000, onThisPhone = true).line("c", ChatStatusKind.Offline)
        assertEquals(6, line.retryInSeconds)
        assertNull(line.serverWait)
    }
}
