@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.gate.flicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The planted cases: a tiny composable per rule that does the fault on purpose (a line that blinks, a message drawn
 * twice, words that leave and come back, a row that jumps, a first frame laid out without the box's room, a step that
 * recomposes on every frame) is caught by name, and its well-behaved twin is not.
 */
class FlickerRecorderTest {
    private val strict = KitUiOptions(mode = FlickerMode.Fail)

    /** What the recorder failed the run with (nothing when it passed). */
    private fun found(
        options: KitUiOptions = strict,
        allow: Set<Allow> = emptySet(),
        content: @Composable () -> Unit,
    ): List<Finding> =
        try {
            kitUiTest(W, H, allow, options) {
                setContent(content)
                waitForIdle()
            }
            emptyList()
        } catch (failure: FlickerFailure) {
            failure.findings
        }

    private fun List<Finding>.of(rule: FlickerRule, label: String): List<Finding> =
        filter { it.rule == rule && label in it.label }

    @Test
    fun `B-FLICKER-001 a line shown for two frames is a blink, a line shown for twelve is not`() {
        val blink = found { Planted(shown = 5 until 7) { BasicText("Flash") } }
        assertTrue(blink.of(FlickerRule.BLINK, "Flash").isNotEmpty(), "the two-frame line was not caught: $blink")
        val steady = found { Planted(shown = 5 until 17) { BasicText("Flash") } }
        assertTrue(steady.isEmpty(), "a line shown for twelve frames was reported: $steady")
    }

    @Test
    fun `B-FLICKER-002 the same message drawn twice for a moment is a double, a second message that stays is not`() {
        val double = found { Column { BasicText("hello d8"); Planted(shown = 5 until 7) { BasicText("hello d8") } } }
        assertTrue(double.of(FlickerRule.DOUBLE, "hello d8").isNotEmpty(), "the double bubble was not caught: $double")
        val stays = found { Column { BasicText("hello d8"); Planted(shown = 5..LAST) { BasicText("hello d8") } } }
        assertTrue(stays.none { it.rule == FlickerRule.DOUBLE }, "two lasting copies were reported as a double: $stays")
    }

    @Test
    fun `B-FLICKER-003 words that leave and come back are an A-B-A, words that change once are not`() {
        val retry = found { Words { f -> if (f in 5 until 10) "Thinking · 7 s" else "Retrying 2 in 0:01" } }
        assertTrue(retry.of(FlickerRule.ABA, "Retrying").isNotEmpty(), "the retry line's return was missed: $retry")
        val once = found { Words { f -> if (f >= 5) "Thinking · 7 s" else "Retrying 2 in 0:01" } }
        assertTrue(once.isEmpty(), "words that changed once were reported: $once")
    }

    @Test
    fun `B-FLICKER-004 a row that moves away and comes back is an A-B-A of its place`() {
        val bounce = found { Planted(shown = 0..LAST) { f -> PlacedRow(if (f in 5 until 10) 40 else 0) } }
        assertTrue(bounce.of(FlickerRule.ABA, "Row").isNotEmpty(), "the row's trip and return was missed: $bounce")
    }

    @Test
    fun `B-FLICKER-005 a row that jumps 100 dp in one frame is a jump, a row that glides 30 dp a frame is not`() {
        val jump = found { Planted(shown = 0..LAST) { f -> PlacedRow(if (f >= 5) 100 else 0) } }
        assertTrue(jump.of(FlickerRule.JUMP, "Row").isNotEmpty(), "the jump was missed: $jump")
        val glide = found { Planted(shown = 0..LAST) { f -> PlacedRow(minOf(f, 10) * 30) } }
        assertTrue(glide.none { it.rule == FlickerRule.JUMP }, "a steady glide was reported as a jump: $glide")
    }

    @Test
    fun `B-FLICKER-006 a first frame laid out without the box's room differs from the settled page`() {
        val late = found { Room(known = false) }
        assertTrue(late.of(FlickerRule.FIRST, "Newest line").isNotEmpty(), "the missing room was missed: $late")
        val known = found { Room(known = true) }
        assertTrue(known.isEmpty(), "a page that knew its room on the first frame was reported: $known")
    }

    @Test
    fun `B-FLICKER-007 a step that recomposes on every frame is over its ceiling, a step that only redraws is not`() {
        val options = KitUiOptions(mode = FlickerMode.Fail, recompositionsPerStep = 3)
        val n = mutableIntStateOf(0)
        val recomposing = steps(options, n) { setContent { BasicText("count ${n.intValue}") } }
        assertTrue(recomposing.any { it.rule == FlickerRule.RECOMPOSE }, "20 recompositions were missed: $recomposing")
        val drawing =
            steps(options, n) {
                setContent { Box(Modifier.size(40.dp).drawBehind { check(n.intValue >= 0) }) { BasicText("still") } }
            }
        assertTrue(drawing.isEmpty(), "a step that only redraws was reported: $drawing")
    }

    @Test
    fun `B-FLICKER-008 expectRecompositions fails a step over its ceiling`() {
        val n = mutableIntStateOf(0)
        kitUiTest(W, H, options = KitUiOptions(mode = FlickerMode.Off)) {
            setContent { BasicText("count ${n.intValue}") }
            waitForIdle()
            assertFailsWith<AssertionError> { expectRecompositions(atMost = 3) { advance(n) } }
            expectRecompositions(atMost = 3) { mainClock.advanceTimeBy(FRAME * 20) }
        }
    }

    @Test
    fun `B-FLICKER-009 intended motion allowed by name with its reason is not a failure`() {
        val allowed =
            found(allow = setOf(Allow.blink("Flash", "the planted flash is the point of this test"))) {
                Planted(shown = 5 until 7) { BasicText("Flash") }
            }
        assertTrue(allowed.isEmpty(), "an allowed blink still failed the test: $allowed")
        assertFailsWith<IllegalArgumentException> { Allow.jump("Row", " ") }
    }

    private fun steps(
        options: KitUiOptions,
        n: androidx.compose.runtime.MutableIntState,
        script: KitUiTest.() -> Unit,
    ): List<Finding> =
        try {
            kitUiTest(W, H, options = options) {
                script()
                waitForIdle()
                advance(n)
                waitForIdle()
            }
            emptyList()
        } catch (failure: FlickerFailure) {
            failure.findings
        }

    /** Twenty frames, a state write on each. */
    private fun KitUiTest.advance(n: androidx.compose.runtime.MutableIntState) {
        repeat(20) {
            n.intValue++
            mainClock.advanceTimeByFrame()
        }
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val LAST = 20
        const val FRAME = 16L
    }
}

/** Counts the frames from the first one drawn, up to [FlickerRecorderTest]'s last planted frame. */
@Composable
private fun frameCount(): Int {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (frame < PLANTED_FRAMES) {
            withFrameNanos { }
            frame++
        }
    }
    return frame
}

private const val PLANTED_FRAMES = 20

/** Shows [content] on the frames in [shown] only, beside a steady line. */
@Composable
private fun Planted(shown: IntRange, content: @Composable (Int) -> Unit) {
    val frame = frameCount()
    Column(Modifier.fillMaxSize()) {
        BasicText("Steady")
        if (frame in shown) content(frame)
    }
}

/** One line whose words follow the frame. */
@Composable
private fun Words(words: (Int) -> String) {
    val frame = frameCount()
    Column(Modifier.fillMaxSize()) {
        BasicText("Steady")
        BasicText(words(frame))
    }
}

/** A row placed [y] dp down. */
@Composable
private fun PlacedRow(y: Int) {
    BasicText("Row", Modifier.offset(y = y.dp))
}

/**
 * F1b in small: the newest line sits above a box whose height the page learns only after its first layout ([known]
 * false), or knows from the start.
 */
@Composable
private fun Room(known: Boolean) {
    var room by remember { mutableIntStateOf(if (known) ROOM else 0) }
    Box(Modifier.fillMaxSize()) {
        BasicText("Newest line", Modifier.padding(top = room.dp))
        val box = Modifier.align(Alignment.BottomCenter).size(200.dp, 48.dp)
        BasicText("Message box", box.onSizeChanged { room = ROOM })
    }
}

private const val ROOM = 200
