package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.wordEndAtOrAfter
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 09 §14.2 RevealDrawOnlyTest (SP-3, first form) on the desktop JVM, headless: a live block revealing for one
 * second re-lays out only when a word lands, never per frame; the clock parks when the reveal is done.
 */
@OptIn(ExperimentalTestApi::class)
class RevealTest {
    private val sentence = (1..60).joinToString(" ") { "word$it" } // 60 words, 473 chars

    @Test
    fun `words snap forward to the end of a word`() {
        val t = "one two  three"
        assertEquals(0, t.wordEndAtOrAfter(0))
        assertEquals(3, t.wordEndAtOrAfter(1))
        assertEquals(3, t.wordEndAtOrAfter(3))
        assertEquals(7, t.wordEndAtOrAfter(5))
        assertEquals(t.length, t.wordEndAtOrAfter(99))
    }

    @Test
    fun `P3 a revealing block re-lays out only when a word lands, and the clock parks when done`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val registry = RevealRegistry()
            var measures = 0
            var text by mutableStateOf(sentence.take(40))
            setContent {
                MaterialTheme {
                    KitReplyBlock(
                        text = text, kind = KitBlockKind.Prose, live = true, streamed = true,
                        revealKey = "b:m/t:0", revealGroup = "b:m/t", registry = registry, motion = KitMotionLevel.Calm,
                        modifier = Modifier.layout { m, c -> measures++; val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } },
                    )
                }
            }
            val state = registry.stateFor("b:m/t:0", "b:m/t", live = true, length = 0)
            text = sentence // the rest arrives in one publish: a backlog
            val m0 = measures
            repeat(60) { mainClock.advanceTimeByFrame() } // about one second at the test clock's 16 ms frames
            val relayouts = measures - m0
            // A 473-char backlog drains in ~250 ms and the last fade ends 250 ms later: ~30 clock frames, then it parks.
            assertTrue(state.frames in 20..60, "the clock ran while revealing (${state.frames} frames)")
            assertTrue(state.landings in 2..60, "words landed ${state.landings} times")
            assertTrue(relayouts <= state.landings + 2, "re-layouts $relayouts vs word landings ${state.landings}")
            assertTrue(relayouts < state.frames / 2, "far fewer re-layouts ($relayouts) than frames (${state.frames})")
            assertEquals(sentence.length, state.shown) // a backlog drains in ~250 ms
            // Let the last fade finish, then the clock parks: no more frames while idle.
            repeat(30) { mainClock.advanceTimeByFrame() }
            val parked = state.frames
            repeat(30) { mainClock.advanceTimeByFrame() }
            assertEquals(parked, state.frames, "no frames scheduled while idle")
            assertEquals(0, registry.activeCount)
        }

    @Test
    fun `with motion off the words appear with each publish and nothing fades`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val registry = RevealRegistry()
            setContent {
                MaterialTheme {
                    KitReplyBlock(sentence, KitBlockKind.Prose, live = true, streamed = true, revealKey = "k", revealGroup = "g",
                        registry = registry, motion = KitMotionLevel.Off)
                }
            }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val state = registry.stateFor("k", "g", live = true, length = 0)
            assertEquals(sentence.length, state.shown)
            assertEquals(0, state.landings)
            assertEquals(0, registry.activeCount)
        }

    @Test
    fun `a loaded block never animates`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val registry = RevealRegistry()
            setContent {
                MaterialTheme {
                    KitReplyBlock(sentence, KitBlockKind.Prose, live = false, streamed = true, revealKey = "k", revealGroup = "g",
                        registry = registry, motion = KitMotionLevel.Full)
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val state = registry.stateFor("k", "g", live = false, length = 0)
            assertEquals(sentence.length, state.shown)
            assertEquals(0, state.frames)
        }
}
