package io.github.eslamasabry.oc.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowHost
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-530: Crashlytics 159e2535 — IllegalArgumentException in subcompose when measuring a lazy list during the
 * page-slide lookahead pass inside `SharedTransitionLayout`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class ChatLookaheadCrashTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private class Host(
        override val sharedTransitionScope: SharedTransitionScope,
        override val animatedVisibilityScope: AnimatedVisibilityScope,
        private val isOpening: () -> Boolean,
    ) : KitGrowHost {
        override val transformEnabled: Boolean = true
        override val opening: Boolean
            get() = isOpening()
    }

    private fun turns(count: Int): TranscriptState =
        stateOf(*(1..count).flatMap { turn(it, answer = "Answer number $it with a few words") }.toTypedArray())

    private fun KitUiTest.stepFrames(count: Int) {
        repeat(count) { mainClock.advanceTimeByFrame() }
    }

    private fun lookaheadProbe(onLookahead: () -> Unit): Modifier =
        Modifier.layout { measurable, constraints ->
            if (isLookingAhead) onLookahead()
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Full),
            localeTag = "en",
            content = content,
        )
    }

    @Composable
    private fun GrowShell(
        open: Boolean,
        widthDp: Int,
        heightDp: Int,
        onLookahead: () -> Unit,
        pageContent: @Composable () -> Unit,
    ) {
        Themed {
            SharedTransitionLayout(Modifier.size(widthDp.dp, heightDp.dp)) {
                val shared = this
                AnimatedContent(targetState = open, label = "grow-open") { isOpen ->
                    val host = remember(shared, this) { Host(shared, this) { open } }
                    CompositionLocalProvider(LocalKitGrowHost provides host) {
                        if (!isOpen) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item(key = "row-1") {
                                    KitRow("Row 1", "preview 1", onClick = {}, growKey = GROW_KEY)
                                }
                            }
                        } else {
                            KitGrowPage(growKey = GROW_KEY) {
                                Box(Modifier.size(widthDp.dp, heightDp.dp).then(lookaheadProbe(onLookahead))) {
                                    pageContent()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TranscriptPage(state: TranscriptState) {
        val list = rememberTranscriptListState()
        ChatTranscript(state, Words, list, rememberRevealRegistry(), KitMotionLevel.Full)
    }

    @Test
    fun `B-530 a chat opened and closed twenty times mid-transition never throws`() =
        kitUiTest(VIEW_SIZE, VIEW_SIZE) {
            mainClock.autoAdvance = false
            val rows60 = turns(60)
            var open by mutableStateOf(false)
            var lookahead = 0
            setContent {
                GrowShell(open, PORTRAIT_W, PORTRAIT_H, onLookahead = { lookahead++ }) {
                    TranscriptPage(rows60)
                }
            }
            stepFrames(2)
            repeat(20) {
                open = true
                stepFrames(2)
                open = false
                stepFrames(2)
            }
            open = true
            stepFrames(SETTLE_FRAMES)
            println("B-530 twenty-flips lookahead=$lookahead")
            assertTrue(lookahead > 0, "expected lookahead passes > 0, got $lookahead")
            onNodeWithText("Answer number 60 with a few words").assertExists()
        }

    @Test
    fun `B-530 turning the window mid-transition never throws`() =
        kitUiTest(VIEW_SIZE, VIEW_SIZE) {
            mainClock.autoAdvance = false
            val rows60 = turns(60)
            var open by mutableStateOf(false)
            var w by mutableIntStateOf(PORTRAIT_W)
            var h by mutableIntStateOf(PORTRAIT_H)
            var lookahead = 0
            setContent {
                GrowShell(open, w, h, onLookahead = { lookahead++ }) {
                    TranscriptPage(rows60)
                }
            }
            stepFrames(2)
            open = true
            stepFrames(2)
            w = PORTRAIT_H
            h = PORTRAIT_W
            stepFrames(2)
            w = PORTRAIT_W
            h = PORTRAIT_H
            stepFrames(SETTLE_FRAMES)
            println("B-530 rotate lookahead=$lookahead")
            assertTrue(lookahead > 0, "expected lookahead passes > 0, got $lookahead")
        }

    @Test
    fun `B-530 rows arriving while the chat opens never throw`() =
        kitUiTest(VIEW_SIZE, VIEW_SIZE) {
            mainClock.autoAdvance = false
            var open by mutableStateOf(false)
            var rows by mutableStateOf(turns(10))
            var lookahead = 0
            setContent {
                GrowShell(open, PORTRAIT_W, PORTRAIT_H, onLookahead = { lookahead++ }) {
                    TranscriptPage(rows)
                }
            }
            stepFrames(2)
            open = true
            for (count in 11..25) {
                stepFrames(2)
                rows = turns(count)
            }
            stepFrames(SETTLE_FRAMES)
            println("B-530 arrivals lookahead=$lookahead")
            assertTrue(lookahead > 0, "expected lookahead passes > 0, got $lookahead")
            onNodeWithText("Answer number 25 with a few words").assertExists()
        }

    @Test
    fun `B-530 model sheet pages change under the lookahead pass without throwing`() =
        kitUiTest(VIEW_SIZE, VIEW_SIZE) {
            mainClock.autoAdvance = false
            val first = KitModelOption("anthropic", "claude-5", "Claude 5", providerName = "Anthropic")
            val duplicate = KitModelOption("anthropic", "claude-5", "Claude 5 Fast", providerName = "Anthropic")
            val other = KitModelOption("zen", "glm-5.3", "GLM-5.3", providerName = "Zen")
            val options = listOf(first, duplicate, other)
            var open by mutableStateOf(false)
            var favorites by mutableStateOf<List<KitModelOption>>(emptyList())
            var lookahead = 0
            setContent {
                GrowShell(open, PORTRAIT_W, PORTRAIT_H, onLookahead = { lookahead++ }) {
                    KitSheetHost {
                        KitModelSheet(
                            visible = true,
                            onDismissRequest = {},
                            options = options,
                            current = first.key,
                            onChoose = {},
                            favorites = favorites,
                        )
                    }
                }
            }
            stepFrames(2)
            open = true
            stepFrames(40)
            favorites = listOf(other)
            stepFrames(2)
            onNodeWithTag("kit-tab-pages").performTouchInput { swipeLeft() }
            stepFrames(SETTLE_FRAMES)
            println("B-530 model-sheet lookahead=$lookahead")
            assertTrue(lookahead > 0, "expected lookahead passes > 0, got $lookahead")
            assertEquals(2, onAllNodesWithTag("chat-model-choice").fetchSemanticsNodes().size)
        }

    private companion object {
        const val GROW_KEY = "p/s"
        const val VIEW_SIZE = 900
        const val PORTRAIT_W = 412
        const val PORTRAIT_H = 860
        const val SETTLE_FRAMES = 90
    }
}
