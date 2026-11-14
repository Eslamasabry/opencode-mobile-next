package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owner 2026-11-13 ("How many stopped we will show"), drawn: a turn the person stopped showed "Stopped" three times
 * (the work line "1 step · Stopped", the footer's note, and a closing line under it). With the work line folded, the
 * word shows once, in the footer; the work line says how much work happened, "1 step". Live and reopened alike.
 */
@OptIn(ExperimentalTestApi::class)
class StoppedOnceScreenTest {
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

    private val ask =
        MessageWithParts(
            MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
            listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "write a long story about rivers", ended = true))
                .toImmutableList(),
        )

    private val step =
        Part(
            "t-read", "m2", sid, PartType.Tool, "tool", callId = "c-read", toolName = "read",
            tool = ToolState(
                ToolStatus.Error,
                input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/rivers.txt"))),
                output = "Tool execution aborted",
                startedAtMs = 2_000,
                completedAtMs = 3_000,
            ),
        )

    private fun reply(vararg parts: Part) =
        MessageWithParts(
            MessageInfo(
                "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                time = MsgTime(1_100, 3_100), errorText = "The operation was aborted.", errorKind = MessageErrorKind.Aborted,
            ),
            parts.toList().toImmutableList(),
        )

    private fun facts(page: List<MessageWithParts>, live: Boolean) =
        TurnFeedKeeper(sid).let { keeper ->
            keeper.history(page, newest = true)
            if (live)
                keeper.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Interrupted(InterruptReason.User)), 4_000)
            else keeper.idle()
            TurnFactsPresenter(FixedClock).present(keeper.feed.value, 5_000)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.show(rows: List<TranscriptRow>, page: List<MessageWithParts>, live: Boolean) {
        val facts = facts(page, live)
        val state = ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows.toImmutableList())
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { ChatScreen(state, actions, composer, facts = facts) }
            }
        }
    }

    /** The list's rows for the stopped turn, newest first: footer, work line, prompt. */
    private val stepRows =
        persistentListOf<TranscriptRow>(
            FooterRow("footer:m1", "m1", failed = false, stopped = true),
            WorkLineRow("work:m1", "m1", 1, 0, WorkState.Stopped),
            prompt("m1", "write a long story about rivers"),
        )

    @Test
    // D1: was "... and its work line says 1 step"; the turn's work is its step, in view, and "Stopped" is said once.
    fun `a live stopped turn with one step says Stopped once and shows its step`() = runComposeUiTest {
        show(stepRows, listOf(ask, reply(step)), live = true)
        onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithTag("chat-step-t-read", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("1 step · Stopped", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the same turn reopened draws the same lines`() = runComposeUiTest {
        show(stepRows, listOf(ask, reply(step)), live = false)
        onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithTag("chat-step-t-read", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("1 step · Stopped", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `words that streamed before Stop stay, and one Stopped follows them`() = runComposeUiTest {
        val words = Part("x2", "m2", sid, PartType.Text, "text", text = "The Nile flows north.")
        val rows =
            persistentListOf<TranscriptRow>(
                FooterRow("footer:m1", "m1", failed = false, stopped = true),
                block("m1", "m2", "x2", 0, "The Nile flows north."),
                prompt("m1", "write a long story about rivers"),
            )
        show(rows, listOf(ask, reply(words)), live = true)
        onAllNodesWithText("Stopped", useUnmergedTree = true).assertCountEquals(1)
        val text = onNodeWithText("The Nile flows north.", useUnmergedTree = true).getBoundsInRoot()
        val stopped = onNodeWithText("Stopped", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(stopped.top >= text.bottom, "Stopped at ${stopped.top}, the words end at ${text.bottom}")
    }
}
