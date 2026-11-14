package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
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
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Review #12 (C-087, C-020, C-032): while a permission card waited, the transcript's tail said "Waiting for you" on the
 * work line, "Waiting for you…" on an amber live line, and the card "Needs your decision"; a stop on its way added
 * "Stopping" beside the waiting line. The tail states the run's state once: the card speaks for a pending decision,
 * the live line for a run that works or stops, and the work line only says how much work there is.
 */
@OptIn(ExperimentalTestApi::class)
class R12OneRunStateTest {
    private val sid = Ref.sessionId.raw

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

    /** A turn whose write is asking for permission (the tool runs and waits on the person's answer). */
    private fun asking(): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
                listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "create a file e9.txt with one line", ended = true)).toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(1_100, null)),
                listOf(
                        Part(
                            "t1", "m2", sid, PartType.Tool, "tool", callId = "c1", toolName = "write",
                            tool = ToolState(ToolStatus.Running, input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/e9.txt"))), startedAtMs = 1_200),
                        ),
                    )
                    .toImmutableList(),
            ),
        )

    private fun facts(waitingForYou: Boolean, stoppedAfter: Boolean = false): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 1_000)
        k.history(asking(), newest = true)
        if (stoppedAfter) k.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Interrupted(InterruptReason.User)), 3_000)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 4_000, waitingForYou = waitingForYou)
    }

    private fun screen(facts: TurnFactsUi, live: LiveTurnUi?, footer: Boolean = false): ChatUi {
        val work = WorkLineRow("work:m1", "m1", calls = 1, failed = 0, state = WorkState.Done)
        val ask = prompt("m1", "create a file e9.txt with one line")
        val rows = if (footer) persistentListOf(footer("m1"), work, ask) else persistentListOf(work, ask)
        return ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows, live = live)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: ChatUi, facts: TurnFactsUi) {
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { ChatScreen(state, actions, composer, facts = facts) }
            }
        }
    }

    @Test
    // D1 "steps only" (owner 2026-11-14): was "the card says it, the transcript adds no waiting line of its own". The
    // step that waits now says so on its own row (d1-waiting: the ask is answered right under it); the promise kept is
    // that it is said once: no live line, no second waiting line of the transcript's own.
    fun `R12 waiting for the decision - the waiting step says it once, the transcript adds no waiting line of its own`() = runComposeUiTest {
        val facts = facts(waitingForYou = true)
        assertEquals(WorkLineState.WaitingForYou, facts.work["m1"]?.state)
        show(screen(facts, LiveTurnUi(stopping = false)), facts)
        onAllNodesWithText("Waiting for you", substring = true, useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithTag("chat-live-line", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `R12 stopping while the card still waits - the tail says Stopping once and nothing waits`() = runComposeUiTest {
        val facts = facts(waitingForYou = true)
        show(screen(facts, LiveTurnUi(stopping = true)), facts)
        onAllNodesWithText("Stopping", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithText("Waiting for you", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `R12 stopped - the footer says Stopped once, no Stopping and no waiting`() = runComposeUiTest {
        val facts = facts(waitingForYou = false, stoppedAfter = true)
        show(screen(facts, live = null, footer = true), facts)
        onAllNodesWithText("Stopping", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Waiting for you", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `R12 working - after the decision is sent the live line says what the turn does, never waiting`() = runComposeUiTest {
        val facts = facts(waitingForYou = false)
        assertEquals(WorkLineState.Running, facts.work["m1"]?.state)
        show(screen(facts, LiveTurnUi(stopping = false)), facts)
        onAllNodesWithText("Waiting for you", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
