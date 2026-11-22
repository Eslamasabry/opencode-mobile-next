package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.Allow
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/**
 * NEW-a/NEW-b (a send never flashes a failure): the server's copy of the second prompt arrives before its RunStarted,
 * and in those frames the new turn must read as taken, not ended - no "No reply came back" card, no leftover "Stopped"
 * line. A run that truly ends with no reply still shows the card, and keeps it. A live "instructions.updated" notice
 * reads "Instructions updated", as the refetch does, never "Server message".
 */
@OptIn(ExperimentalTestApi::class)
class SendNoFalseFailureTest {
    private val sid = Ref.sessionId.raw

    private val rowArrives = "a row arrives and pushes the content: intended"

    private val twoPrompts =
        setOf(
            Allow.jump("kit-prompt-bubble", rowArrives),
            Allow.jump("second ask", rowArrives),
            Allow.jump("first ask", rowArrives),
            Allow.first("second ask", rowArrives),
            Allow.first("first ask", rowArrives),
        )

    private val noticeAndPrompt =
        setOf(
            Allow.jump("kit-prompt-bubble", rowArrives),
            Allow.jump("second ask", rowArrives),
            Allow.first("second ask", rowArrives),
            Allow.jump("Instructions updated", rowArrives),
            Allow.first("Instructions updated", rowArrives),
        )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private fun firstTurn(): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(900, null)),
                listOf(
                    Part("m1-t", "m1", sid, PartType.Text, "text", text = "first ask", ended = true),
                ).toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo(
                    "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                    time = MsgTime(1_000, 2_000), finish = "stop",
                ),
                listOf(
                    Part("m2-t", "m2", sid, PartType.Text, "text", text = "Hi!", ended = true),
                ).toImmutableList(),
            ),
        )

    /** A chat whose first run ended at 3_000; the second prompt's server copy arrived at 5_000, before its run. */
    private fun endedChat(outcome: RunOutcome): TurnFeedKeeper =
        TurnFeedKeeper(sid).run {
            event(GatewayEvent.RunStarted(SessionId(sid)), 1_000)
            history(firstTurn(), newest = true)
            event(GatewayEvent.RunEnded(SessionId(sid), outcome), 3_000)
            event(
                GatewayEvent.MessageUpserted(MessageInfo("m3", sid, Role.User, time = MsgTime(5_000, null))),
                5_000,
            )
            event(
                GatewayEvent.PartUpserted(
                    Part("m3-t", "m3", sid, PartType.Text, "text", text = "second ask", ended = true)
                ),
                5_000,
            )
            this
        }

    private fun present(k: TurnFeedKeeper): TurnFactsUi = TurnFactsPresenter(FixedClock).present(k.feed.value, 6_000)

    private fun screen(vararg rows: TranscriptRow): ChatUi =
        ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = persistentListOf(*rows))

    private val sends = { screen(prompt("m3", "second ask"), prompt("m1", "first ask")) }

    private fun ComposeUiTest.show(state: ChatUi, factsNow: () -> TurnFactsUi) {
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = "en",
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    ChatScreen(state, actions, composer, facts = factsNow())
                }
            }
        }
    }

    private fun ComposeUiTest.sixFrames(checks: () -> Unit) {
        repeat(6) {
            mainClock.advanceTimeByFrame()
            checks()
        }
    }

    private fun ComposeUiTest.noFailureCard() {
        onAllNodesWithTag("chat-no-reply", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("No reply came back", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `B-CHAT-320 a second send shows no failure card before its run starts`() = kitUiTest(allow = twoPrompts) {
        val k = endedChat(RunOutcome.Idle)
        var facts by mutableStateOf(present(k))
        show(sends()) { facts }
        waitForIdle()
        mainClock.autoAdvance = false
        sixFrames { noFailureCard() }
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 5_100)
        facts = present(k)
        sixFrames { noFailureCard() }
        mainClock.autoAdvance = true
        waitForIdle()
        noFailureCard()
    }

    @Test
    fun `B-CHAT-321 a run that ends with no reply shows the card and keeps it`() = kitUiTest(allow = twoPrompts) {
        val k = endedChat(RunOutcome.Idle)
        var facts by mutableStateOf(present(k))
        show(sends()) { facts }
        waitForIdle()
        mainClock.autoAdvance = false
        k.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Idle), 9_000)
        facts = present(k)
        sixFrames {
            onAllNodesWithTag("chat-no-reply", useUnmergedTree = true).assertCountEquals(1)
            onAllNodesWithText("No reply came back", useUnmergedTree = true).assertCountEquals(1)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onAllNodesWithTag("chat-no-reply", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `B-CHAT-322 a send after a stopped run shows no Stopped line before its run starts`() =
        kitUiTest(allow = twoPrompts) {
        val k = endedChat(RunOutcome.Interrupted(InterruptReason.User))
        var facts by mutableStateOf(present(k))
        show(sends()) { facts }
        waitForIdle()
        mainClock.autoAdvance = false
        sixFrames {
            onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(0)
            noFailureCard()
        }
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 5_100)
        facts = present(k)
        sixFrames {
            onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(0)
            noFailureCard()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `B-CHAT-323 the live instructions notice reads Instructions updated, never Server message`() =
        kitUiTest(allow = noticeAndPrompt) {
        val key = "notice:msg_x/notice-msg_x"
        val part =
            Part(
                "notice-msg_x", "msg_x", sid, PartType.Notice, "v2:notice",
                noticeMetadata = persistentMapOf("kind" to "instructions.updated"),
            )
        var facts by mutableStateOf(
            TurnFactsUi(reading = TranscriptReadingUi(details = mapOf(key to TranscriptDetailUi(key, part))))
        )
        show(screen(NoticeRow(key, "m3", NoticeKind.InstructionsUpdated), prompt("m3", "second ask"))) { facts }
        waitForIdle()
        mainClock.autoAdvance = false
        sixFrames {
            onAllNodesWithText("Server message", useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithText("Instructions updated", useUnmergedTree = true).assertCountEquals(1)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onAllNodesWithText("Instructions updated", useUnmergedTree = true).assertCountEquals(1)
    }
}
