package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C-001 (parity audit 2026-11-07, Flutter kit_turn.dart:487-597): a chat is grouped into turns: 16 dp under the
 * prompt, 12 between the blocks of a turn, 4 above the footer, 24 under a finished turn and 8 under the newest one.
 * Native rows were all padded 4 + 4, so every block sat 8 dp from the next and turns did not read as groups.
 */
@OptIn(ExperimentalTestApi::class)
class TurnRhythmTest {
    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) < 0.5f, "$what: expected $expected, was $actual")

    @Test
    fun `C-001 gaps by segment match Flutter's gapAfter`() {
        assertEquals(16, kitTurnGapAfter(KitTurnSegment.First, latest = false), "under the prompt: space4")
        assertEquals(16, kitTurnGapAfter(KitTurnSegment.First, latest = true, next = KitTurnNext.Line))
        assertEquals(12, kitTurnGapAfter(KitTurnSegment.Middle, latest = false), "between blocks: space3")
        assertEquals(12, kitTurnGapAfter(KitTurnSegment.Middle, latest = true, next = KitTurnNext.Line))
        assertEquals(4, kitTurnGapAfter(KitTurnSegment.Middle, latest = false, next = KitTurnNext.Footer))
        assertEquals(0, kitTurnGapAfter(KitTurnSegment.Middle, latest = false, next = KitTurnNext.Joined))
        assertEquals(24, kitTurnGapAfter(KitTurnSegment.Last, latest = false), "under a turn: sectionGap")
        assertEquals(24, kitTurnGapAfter(KitTurnSegment.Whole, latest = false))
        assertEquals(8, kitTurnGapAfter(KitTurnSegment.Last, latest = true), "the newest turn: space2")
        assertEquals(8, kitTurnGapAfter(KitTurnSegment.Whole, latest = true))
    }

    private fun ComposeUiTest.twoTurns(registry: RevealRegistry) = setContent {
        ChatTestTheme(dark = true) {
            Column(Modifier.width(400.dp)) {
                KitTurn(KitTurnSegment.First) { KitPromptBubble("Say hello.", Modifier.testTag("p1")) }
                KitTurn(KitTurnSegment.Middle) { block("Hello there.", "b1", registry) }
                KitTurn(KitTurnSegment.Middle, next = KitTurnNext.Footer) { block("How can I help?", "b2", registry) }
                KitTurn(KitTurnSegment.Last) {
                    KitReplyFooter("Copy reply", "More", {}, {}, Modifier.testTag("f1"))
                }
                KitTurn(KitTurnSegment.First, latest = true) { KitPromptBubble("Thanks.", Modifier.testTag("p2")) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun block(text: String, tag: String, registry: RevealRegistry) =
        KitReplyBlock(
            text = text,
            kind = KitBlockKind.Prose,
            live = false,
            streamed = false,
            revealKey = tag,
            revealGroup = tag,
            registry = registry,
            motion = KitMotionLevel.Off,
            modifier = Modifier.testTag(tag),
        )

    @Test
    fun `C-001 rows inside a turn take only the turn's gaps, and turns are 24 dp apart`() = runComposeUiTest {
        twoTurns(RevealRegistry())
        val p1 = onNodeWithTag("p1").getBoundsInRoot()
        val b1 = onNodeWithTag("b1").getBoundsInRoot()
        val b2 = onNodeWithTag("b2").getBoundsInRoot()
        val f1 = onNodeWithTag("f1").getBoundsInRoot()
        val p2 = onNodeWithTag("p2").getBoundsInRoot()
        near(16.dp, b1.top - p1.bottom, "prompt → first block")
        near(12.dp, b2.top - b1.bottom, "block → block")
        near(4.dp, f1.top - b2.bottom, "reply → footer")
        near(24.dp, p2.top - f1.bottom, "turn → next turn")
    }

    @Test
    fun `C-001 parts outside a turn keep their own 4 dp row padding`() = runComposeUiTest {
        val registry = RevealRegistry()
        setContent {
            ChatTestTheme(dark = true) {
                Column(Modifier.width(400.dp)) {
                    block("One.", "a", registry)
                    block("Two.", "b", registry)
                }
            }
        }
        val a = onNodeWithTag("a").getBoundsInRoot()
        val b = onNodeWithTag("b").getBoundsInRoot()
        near(0.dp, b.top - a.bottom, "the rows touch; the 4 + 4 dp lives inside each row")
        assertTrue(a.bottom - a.top > 24.dp, "a one-line row is its 24 dp line plus 4 + 4 padding")
    }
}
