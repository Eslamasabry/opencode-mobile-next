package io.github.eslamasabry.oc.engine.project.terminal

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TYPED = "the quick brown fox"

class TerminalInputQueueTest {
    @Test
    fun `B-LINUX-223 queued keystrokes leave as one ordered write none repeated`() = runTest {
        val sent = mutableListOf<String>()
        val queue = TerminalInputQueue(this, sent::add)
        for (char in TYPED) queue.write(char.toString())
        assertTrue(sent.isEmpty(), "nothing leaves before the flush")
        runCurrent()
        assertEquals(listOf(TYPED), sent)
        assertFalse(queue.hasPending)

        queue.write("a")
        queue.write("b")
        queue.flush()
        assertEquals(listOf(TYPED, "ab"), sent)
        queue.flush()
        assertEquals(listOf(TYPED, "ab"), sent, "an empty flush sends nothing")
        runCurrent()
        assertEquals(listOf(TYPED, "ab"), sent, "the scheduled flush finds nothing left")
    }

    @Test
    fun `B-LINUX-223 a channel that goes away drops what was never sent`() = runTest {
        val sent = mutableListOf<String>()
        val queue = TerminalInputQueue(this, sent::add)
        queue.write("lost")
        queue.discard()
        queue.flush()
        assertTrue(sent.isEmpty())
        queue.close()
        queue.write("after close")
        queue.flush()
        runCurrent()
        assertTrue(sent.isEmpty())
    }
}
