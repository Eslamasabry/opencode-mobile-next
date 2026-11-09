package io.github.eslamasabry.oc.ui.kitchat.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-089: the stream caret never stands at full strength ahead of the words still fading in. */
class CaretFollowsWordsTest {
    @Test
    fun `B-089 a word that just landed holds the caret back, and it returns as the word settles`() {
        val state = RevealState(0, null)
        assertEquals(1f, state.newestWordVisibility(), "nothing fading: full caret")
        state.runs += FreshRun(0, 5, bornNanos = 1_000L)
        state.frameNanos = 1_000L
        assertEquals(0f, state.newestWordVisibility(), "born this frame: caret hidden")
        state.frameNanos = 1_000L + RevealPace.FADE_NS / 2
        val half = state.newestWordVisibility()
        assertTrue(half > 0f && half < 1f, "mid-fade: caret partly there")
        state.frameNanos = 1_000L + RevealPace.FADE_NS
        assertEquals(1f, state.newestWordVisibility(), "fade done: full caret")
    }

    @Test
    fun `B-089 the caret follows the newest run, not an older one`() {
        val state = RevealState(0, null)
        state.runs += FreshRun(0, 5, bornNanos = 0L)
        state.runs += FreshRun(5, 10, bornNanos = RevealPace.FADE_NS)
        state.frameNanos = RevealPace.FADE_NS
        assertEquals(0f, state.newestWordVisibility())
    }
}
