package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-CHAT-ROOM: the room between the chat's newest row and the message box. The composer pane's own top space plus the
 * list's room under its newest row make the kit's one step (Space.S4, 16 dp) between the newest row's bottom and the
 * composer's top, at text 1.0 and 1.3, with or without the live line; the real message box keeps at least that; and no
 * step row is clipped or overlapped on the settled frames before a step arrives and after the fold has finished.
 */
@OptIn(ExperimentalTestApi::class)
class ChatBottomRoomTest {
    private val settledFrames = 5
    private val settleFrameLimit = 120
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(motion: MotionLevel = MotionLevel.Off, content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = motion),
            localeTag = "en",
            stillFrame = motion == MotionLevel.Off,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    private fun ComposeUiTest.composerTop(): Float = boundsOfTag("chat-composer-pane").top + Space.S2

    private fun ComposeUiTest.newestRowBounds(): Rect {
        val live = onAllNodesWithTag("chat-live-line", useUnmergedTree = true).fetchSemanticsNodes()
        val tag = if (live.isNotEmpty()) "chat-live-line" else "chat-work-line"
        return boundsOfTag(tag)
    }

    private fun ComposeUiTest.newestRowBottom(): Float = newestRowBounds().bottom

    private fun ComposeUiTest.room(): Float = composerTop() - newestRowBottom()

    private fun ComposeUiTest.rowRects(): List<Rect> = stepRows().map { it.boundsInRoot } + newestRowBounds()

    private fun ComposeUiTest.stepRows(): List<SemanticsNode> =
        onAllNodes(
            SemanticsMatcher("a step row") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("chat-step-") == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

    private fun ComposeUiTest.assertStepsWhole(where: String) {
        val rows = stepRows()
        for (row in rows) {
            val drawn = row.boundsInRoot.height
            if (drawn > 0.6f)
                assertEquals(
                    row.size.height.toFloat(), drawn, 0.6f,
                    "$where: the step row at top ${row.boundsInRoot.top} is clipped: " +
                        "drawn $drawn dp of its ${row.size.height} dp",
                )
        }
        val sorted = rows.sortedBy { it.boundsInRoot.top }
        for (i in 1 until sorted.size)
            assertTrue(
                sorted[i].boundsInRoot.top >= sorted[i - 1].boundsInRoot.bottom - 0.6f,
                "$where: step rows overlap: ${sorted.joinToString { it.boundsInRoot.toString() }}",
            )
    }

    private fun ComposeUiTest.assertRoom(where: String) {
        val gap = room()
        assertEquals(
            Space.S4.toFloat(), gap, 0.6f,
            "$where: composer top - newest row bottom = $gap dp, not ${Space.S4} dp",
        )
    }

    private fun ComposeUiTest.settleUntilStill(where: String) {
        var before = rowRects()
        var still = 0
        var frames = 0
        while (still < settledFrames && frames < settleFrameLimit) {
            mainClock.advanceTimeByFrame()
            frames++
            val now = rowRects()
            still = if (now == before) still + 1 else 0
            before = now
        }
        assertTrue(
            still == settledFrames,
            "$where: the step rows and the newest row still moved after $frames frames: $before",
        )
    }

    @Test
    fun `B-CHAT-ROOM-001 the live line rests 16 dp above the message box at text 1_0`() =
        kitUiTest(360, 780) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                Root {
                    ChatScreen(
                        StepsCraftScene.runningState(), actions, composer,
                        facts = StepsCraftScene.runningFacts(),
                    )
                }
            }
            waitForIdle()
            assertRoom("text 1.0, live line")
        }

    @Test
    fun `B-CHAT-ROOM-002 the live line rests 16 dp above the message box at text 1_3`() =
        kitUiTest(360, 780) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    Root {
                        ChatScreen(
                            StepsCraftScene.runningState(), actions, composer,
                            facts = StepsCraftScene.runningFacts(),
                        )
                    }
                }
            }
            waitForIdle()
            assertRoom("text 1.3, live line")
        }

    @Test
    fun `B-CHAT-ROOM-003 the last step rests 16 dp above the box when no live line shows`() {
        for (scale in listOf(1f, 1.3f)) {
            kitUiTest(360, 780) {
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                        Root {
                            ChatScreen(
                                StepsCraftScene.runningState().copy(live = null), actions, composer,
                                facts = StepsCraftScene.runningFacts(),
                            )
                        }
                    }
                }
                waitForIdle()
                assertRoom("text $scale, no live line")
            }
        }
    }

    @Test
    fun `B-CHAT-ROOM-004 the real message box keeps 16 dp under the newest row`() =
        kitUiTest(360, 780) {
            val scene = ChatScene()
            setContent {
                Root { ChatScreen(scene.live(), scene.actions, scene.host, facts = scene.facts()) }
            }
            waitForIdle()
            val boxTop = boundsOfTag("kit-composer-box").top
            val rowBottom = newestRowBottom()
            assertTrue(
                boxTop - rowBottom >= Space.S4 - 0.6f,
                "kit-composer-box top $boxTop - newest row bottom $rowBottom = ${boxTop - rowBottom} dp, " +
                    "under ${Space.S4 - 0.6f} dp",
            )
        }

    @Test
    fun `B-CHAT-ROOM-005 no step row is clipped or overlapped at rest or while a step arrives`() =
        kitUiTest(360, 780) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts = StepsCraftScene.runningFacts(arrived)
                    ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            assertRoom("at rest, before the step arrives")
            assertStepsWhole("at rest, before the step arrives")
            arrived = true
            repeat(40) { mainClock.advanceTimeByFrame() }
            mainClock.advanceTimeBy(900)
            settleUntilStill("after the step arrived")
            for (frame in 1..settledFrames) {
                mainClock.advanceTimeByFrame()
                assertRoom("at rest, settled frame $frame after the step arrived")
                assertStepsWhole("at rest, settled frame $frame after the step arrived")
            }
        }
}
