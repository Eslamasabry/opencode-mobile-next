package io.github.eslamasabry.oc.ui.kitchat.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class CaretOffsetTest {
    @Test
    fun `the caret waits before a word still fading in, then follows it`() {
        val text = "Hello world"
        val state = RevealState(text.length, null)
        state.runs += FreshRun(6, 11, bornNanos = 1_000L)
        state.frameNanos = 1_000L
        assertEquals(5, state.caretOffset(text))
        state.frameNanos = 1_000L + RevealPace.FADE_NS
        assertEquals(text.length, state.caretOffset(text))
    }

    @Test
    fun `with nothing fading the caret ends the text`() {
        assertEquals(5, RevealState(5, null).caretOffset("Hello"))
    }
}
