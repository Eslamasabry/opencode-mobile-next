package io.github.eslamasabry.oc.ui.kitchat.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M-009 (S10 motion audit): the reveal never trails the network by more than 250 ms, so a reply's words and its footer
 * (STANDARD, 250 ms) finish together. The old drain (backlog / 250 ms, re-taken every frame) only decayed toward the
 * end and laid out a long backlog for over a second after the last words arrived.
 */
class RevealDeadlineTest {
    private val frame = 16_000_000L // the test clock's frame
    private val text = (1..400).joinToString(" ") { "word$it" } // 3 491 chars

    /** Drives [state] one 16 ms frame per call, as the block's frame clock does. */
    private class Clock(val state: RevealState) {
        var now = 1_000_000_000L
        var last = 0L
        var carry = 0f

        fun frame(text: CharSequence, motion: KitMotionLevel = KitMotionLevel.Full) {
            carry = state.advance(now, last, carry, text, motion)
            last = now
            now += 16_000_000L
        }
    }

    @Test
    fun `M-009 a long backlog is all laid out within 250 ms of arriving`() {
        val clock = Clock(RevealState(0, null))
        val arrived = clock.now
        clock.frame(text)
        while (clock.now - arrived < 125_000_000L) clock.frame(text)
        val half = clock.state.shown
        assertTrue(half in 1 until text.length, "still revealing half-way ($half of ${text.length}): no burst")
        while (clock.now - arrived <= 250_000_000L + frame) clock.frame(text)
        assertEquals(text.length, clock.state.shown, "every word laid out 250 ms after it arrived")
    }

    @Test
    fun `M-009 when the stream stops the words finish 250 ms after the last ones arrived`() {
        val clock = Clock(RevealState(0, null))
        var received = 0
        // A fast stream: 120 characters a frame (7 500 a second) for about 400 ms.
        while (received < text.length) {
            received = (received + 120).coerceAtMost(text.length)
            clock.frame(text.take(received))
        }
        val lastArrival = clock.now - frame
        while (clock.now - lastArrival <= 250_000_000L + frame) clock.frame(text)
        assertEquals(text.length, clock.state.shown)
    }

    @Test
    fun `M-009 a short reply still reveals at the base pace, not all at once`() {
        val clock = Clock(RevealState(0, null))
        val short = "Done."
        clock.frame(short)
        clock.frame(short)
        assertTrue(clock.state.shown <= short.length)
        repeat(20) { clock.frame(short) }
        assertEquals(short.length, clock.state.shown)
    }

    @Test
    fun `M-009 with motion off the words are there with each publish`() {
        val clock = Clock(RevealState(0, null))
        clock.frame(text, KitMotionLevel.Off)
        assertEquals(text.length, clock.state.shown)
    }
}

/**
 * C-028: a reply's start never hides behind its live tail. In the reversed transcript the newest row composes first,
 * so when several block rows arrive at once the earlier block was chained behind the live tail and stayed empty until
 * the reply ended.
 */
class RevealOrderTest {
    @Test
    fun `C-028 a block chained behind a still-growing block reveals within 250 ms plus the drain`() {
        val registry = RevealRegistry()
        // The newest (live tail) composes first; the earlier block of the same part is created after it.
        val tail = registry.stateFor("b:p:40#0", "b:p", live = true, length = 0)
        val head = registry.stateFor("b:p:0#0", "b:p", live = true, length = 0)
        val headText = "The start of the reply, right under the prompt."
        var tailText = "in the middle."
        var now = 1_000_000_000L
        var last = 0L
        var tailCarry = 0f
        var headCarry = 0f
        val start = now
        while (now - start <= 520_000_000L) {
            tailText += " more" // the tail keeps streaming, so it is never done
            tailCarry = tail.advance(now, last, tailCarry, tailText, KitMotionLevel.Full)
            headCarry = head.advance(now, last, headCarry, headText, KitMotionLevel.Full)
            last = now
            now += 16_000_000L
        }
        assertTrue(tail.shown > 0)
        assertEquals(headText.length, head.shown, "the reply's start is all there within 0.5 s")
    }

    @Test
    fun `C-028 a block still waits its turn while the block before it finishes quickly`() {
        val registry = RevealRegistry()
        val first = registry.stateFor("a", "g", live = true, length = 0)
        val second = registry.stateFor("b", "g", live = true, length = 0)
        second.advance(1_000_000_000L, 0L, 0f, "second words", KitMotionLevel.Full)
        assertEquals(0, second.shown, "words appear in order")
        first.advance(1_000_000_000L, 0L, 0f, "", KitMotionLevel.Full)
    }
}
