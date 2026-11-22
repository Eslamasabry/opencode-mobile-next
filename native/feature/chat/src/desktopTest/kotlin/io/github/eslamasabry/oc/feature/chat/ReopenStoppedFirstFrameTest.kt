package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Ruling E17 (journey j16, read 2): a chat reopened after its run was stopped in the notification
 * drew the stale running page for 0.61 s and then jumped to Stopped, elsewhere on the screen. The
 * first frame after every return must show what the world holds now, and once the Stopped note is
 * there it must not move.
 */
@OptIn(ExperimentalTestApi::class)
class ReopenStoppedFirstFrameTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText(if (chrome.busy()) "composer: busy" else "composer: idle", modifier)
            }
        }

    /** The page under test: the world it reads, its two states, and the away/back switch. */
    private class Reopened(
        val world: FakeChatWorld,
        val running: TranscriptState,
        val stopped: TranscriptState,
        val collecting: MutableState<Boolean>,
        val scope: CoroutineScope,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.reopenedChat(): Reopened {
        mainClock.autoAdvance = false
        val world = FakeChatWorld()
        val running =
            stateOf(
                prompt("u1", "ask 1"),
                block("u1", "a1", "p1", 0, "answer so far", live = true),
                run = RunFacts(busy = true),
            )
        val stopped =
            stateOf(
                prompt("u1", "ask 1"),
                block("u1", "a1", "p1", 0, "answer so far"),
                FooterRow("footer:u1", "u1", failed = false, stopped = true),
            )
        world.transcript.value = running
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        // The work dispatcher below is never advanced, so the combine never emits (as the note at the
        // combine says, its first value arrives after the first frame is drawn); WhileSubscribed(0)
        // stands for "the 5 s have passed", so every return from the background starts sharing again.
        val states = p.states(scope, StandardTestDispatcher(), SharingStarted.WhileSubscribed(0))
        val held = mutableStateOf(states.value)
        val collecting = mutableStateOf(true)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    // The screen's own collectAsStateWithLifecycle: while it is away the page stops
                    // collecting, as the background does.
                    LaunchedEffect(collecting.value) {
                        if (collecting.value) states.collect { held.value = it }
                    }
                    ChatScreen(held.value, p, composer)
                }
            }
        }
        return Reopened(world, running, stopped, collecting, scope)
    }

    /** The page goes to the background: the collecting stops and two frames pass. */
    private fun androidx.compose.ui.test.ComposeUiTest.away(chat: Reopened) {
        chat.collecting.value = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun `B-CHAT-420 a chat reopened after its run was stopped draws Stopped from its first frame`() =
        kitUiTest(412, 915) {
            val chat = reopenedChat()
            try {
                // The running page is up before the app goes to the background.
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                onNodeWithText("composer: busy").assertExists()
                onNodeWithTag("chat-live-line", useUnmergedTree = true).assertExists()
                away(chat)
                chat.world.transcript.value = chat.stopped
                chat.collecting.value = true
                repeat(30) {
                    mainClock.advanceTimeByFrame()
                    onAllNodesWithTag("chat-live-line", useUnmergedTree = true).assertCountEquals(0)
                    onAllNodesWithText("composer: busy").assertCountEquals(0)
                    onNodeWithTag("chat-turn-footer-note", useUnmergedTree = true).assertExists()
                }
                // Both ways: a run that went back to running while the page was away is drawn at once.
                away(chat)
                chat.world.transcript.value = chat.running
                chat.collecting.value = true
                repeat(30) {
                    mainClock.advanceTimeByFrame()
                    onNodeWithText("composer: busy").assertExists()
                    onNodeWithTag("chat-live-line", useUnmergedTree = true).assertExists()
                }
            } finally {
                chat.scope.cancel()
            }
        }

    @Test
    fun `B-CHAT-421 the reopened stopped chat keeps its place: the Stopped note never moves`() =
        kitUiTest(412, 915) {
            val chat = reopenedChat()
            try {
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                onNodeWithText("composer: busy").assertExists()
                away(chat)
                chat.world.transcript.value = chat.stopped
                chat.collecting.value = true
                var frame1 = -1f
                repeat(30) { i ->
                    mainClock.advanceTimeByFrame()
                    val top =
                        onNodeWithTag("chat-turn-footer-note", useUnmergedTree = true)
                            .getBoundsInRoot()
                            .top
                            .value
                    if (i == 0) frame1 = top
                    assertTrue(
                        abs(top - frame1) <= 1f,
                        "frame ${i + 1}: the note is at $top dp, frame 1 drew it at $frame1 dp",
                    )
                }
            } finally {
                chat.scope.cancel()
            }
        }
}
