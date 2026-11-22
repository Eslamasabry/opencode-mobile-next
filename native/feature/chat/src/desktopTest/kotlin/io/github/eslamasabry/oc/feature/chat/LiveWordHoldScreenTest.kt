package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * B-CHAT-320..323 (j03 E08, j05k X6): a reply's text part can end before the run does, or before the next part
 * begins, and for a few frames no reply block is live. The live line said Thinking there and Writing again once
 * the next part began. The rule pinned here: Writing gives way to Thinking only after two seconds without writing.
 */
@OptIn(ExperimentalTestApi::class)
class LiveWordHoldScreenTest {
    private val ask = prompt("t", "Tell a story")
    private val part1 = block("t", "m", "p1", 0, "Once upon a time", live = true)
    private val part1Ended = block("t", "m", "p1", 0, "Once upon a time", live = false)
    private val part2 = block("t", "m", "p2", 0, "Then", live = true)

    /**
     * The screen while a turn runs, as the test moves it. A move shows on the next frame the clock gives; a failure
     * names the frame it was seen on.
     */
    private inner class Drive(val test: ComposeUiTest, start: ChatUi) {
        var ui by mutableStateOf(start)
            private set
        var facts by mutableStateOf(TurnFactsUi(nowMs = 60_000, runStartedAtMs = 0))
            private set
        var frame = 0
            private set

        /** What the last frame's live line said; "" while no live line is drawn. */
        var last = ""
            private set

        /** The rows, newest first. */
        fun rows(first: TranscriptRow, vararg older: TranscriptRow) {
            ui = ui.copy(rows = persistentListOf(first, *older))
        }

        /** The run ends: the live line goes. */
        fun end() {
            ui = ui.copy(live = null)
        }

        /** The turn's clock moves to [nowMs]. */
        fun tick(nowMs: Long) {
            facts = facts.copy(nowMs = nowMs)
        }

        /** One frame on the test clock. */
        fun next() {
            test.mainClock.advanceTimeByFrame()
            frame++
            last = test.line()
        }

        /** [n] frames, each saying Writing and never Thinking. */
        fun writing(n: Int) = repeat(n) {
            next()
            assertTrue("Writing" in last, "frame $frame: the live line says \"$last\", not Writing")
            assertFalse("Thinking" in last, "frame $frame: the live line says \"$last\"")
        }

        /** [n] frames, none saying Thinking. */
        fun neverThinking(n: Int) = repeat(n) {
            next()
            assertFalse("Thinking" in last, "frame $frame: the live line says \"$last\"")
        }

        /** One frame, its live line saying [word]. */
        fun says(word: String) {
            next()
            assertTrue(word in last, "frame $frame: the live line says \"$last\", not $word")
        }

        /** [n] frames, each saying Thinking. */
        fun thinking(n: Int) = repeat(n) { says("Thinking") }
    }

    /** Every Text and ContentDescription of the live line's node and all its children; "" while none is drawn. */
    private fun ComposeUiTest.line(): String {
        val nodes = onAllNodesWithTag("chat-live-line", useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.joinToString(" ") { said(it) }
    }

    private fun said(node: SemanticsNode): String {
        val own =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return (own + node.children.map { said(it) }).filter { it.isNotEmpty() }.joinToString(" ")
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Full),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    /**
     * The chat at en, 360 dp, text 1, full motion, with a turn running on [rows] (newest first). The clock is the
     * test's: [drive] moves the screen and runs it one frame at a time.
     */
    private fun screen(vararg rows: TranscriptRow, drive: suspend KitUiTest.(Drive) -> Unit) {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            kitUiTest(360, 800) {
                val scene = ChatScene()
                val start =
                    scene.live().copy(status = null, rows = persistentListOf(*rows), live = LiveTurnUi(false))
                val s = Drive(this, start)
                setContent { Root { scene.Screen(s.ui, facts = s.facts, projectName = null) } }
                waitForIdle()
                mainClock.autoAdvance = false
                drive(s)
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `B-CHAT-320 the live word stays Writing through a pause between parts`() =
        screen(part1, ask) { s ->
            s.writing(3)
            s.rows(s.part1Ended, s.ask)
            s.writing(18)
            s.rows(s.part2, s.part1Ended, s.ask)
            s.writing(6)
        }

    @Test
    fun `B-CHAT-321 a reply that ends leaves with Writing never Thinking`() =
        screen(part1, ask) { s ->
            s.writing(3)
            s.rows(s.part1Ended, s.ask)
            s.writing(18)
            s.end()
            s.neverThinking(6)
            assertEquals("", s.last, "the live line left with the run")
        }

    @Test
    fun `B-CHAT-322 a real switch to thinking shows once the pause lasted`() =
        screen(part1, ask) { s ->
            s.writing(3)
            s.rows(s.part1Ended, s.ask)
            s.writing(3)
            s.tick(61_000)
            s.writing(3)
            s.tick(62_000)
            repeat(2) { s.next() }
            s.says("Thinking")
        }

    @Test
    fun `B-CHAT-323 writing shows at once after thinking`() =
        screen(ask) { s ->
            s.thinking(3)
            s.rows(s.part1, s.ask)
            s.says("Writing")
        }
}
