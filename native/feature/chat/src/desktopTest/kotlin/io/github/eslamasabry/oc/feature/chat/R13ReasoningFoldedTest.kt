package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * Review #13 (C-022, C-023, C-024, C-029, C-032): before "Hi!" a grey paragraph read `The user just wants "hi".
 * Respond briefly.` That is a reasoning part (the gateway gives it type Reasoning: the transcript draws it from a thought
 * row, never from reply text). Reasoning folded under the turn's work line only when the turn also had a tool step, so a
 * turn whose only work was thinking drew its thought loose in the reply area. Reasoning is work: such a turn gets its
 * one work line, "Thought", folded by default; the thought is inside it.
 */
@OptIn(ExperimentalTestApi::class)
class R13ReasoningFoldedTest {
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

    private val thinking = "The user just wants \"hi\". Respond briefly."

    private fun page(vararg reasoning: String): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
                listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "say hi", ended = true)).toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(1_100, 2_000), finish = "stop"),
                (reasoning.mapIndexed { i, r -> Part("r$i", "m2", sid, PartType.Reasoning, "reasoning", text = r, ended = true) } +
                        Part("x2", "m2", sid, PartType.Text, "text", text = "Hi!", ended = true))
                    .toImmutableList(),
            ),
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(reasoningExpanded: Boolean, vararg reasoning: String) {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page(*reasoning), newest = true)
        keeper.idle()
        val facts = TurnFactsPresenter(FixedClock).present(keeper.feed.value, 20_000)
        val thoughts = reasoning.indices.reversed().map { i -> thought("m1", "m2", "r$i", reasoning[i]) }
        val rows = persistentListOf<TranscriptRow>(footer("m1"), block("m1", "m2", "x2", 0, "Hi!")) + thoughts + prompt("m1", "say hi")
        val state =
            ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows.toPersistentList(), reasoningExpanded = reasoningExpanded)
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { ChatScreen(state, actions, composer, facts = facts) }
            }
        }
    }

    @Test
    fun `R13 a turn whose only work is a thought folds it under one Thought line`() = runComposeUiTest {
        show(reasoningExpanded = false, thinking)
        onAllNodesWithText("Respond briefly", substring = true, useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Thought", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Hi!", useUnmergedTree = true).assertExists()
        // Opened, the thought is there.
        onNodeWithText("Thought").performClick()
        waitForIdle()
        onAllNodesWithText("Respond briefly", substring = true, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    // D1 "steps only" (owner 2026-11-14): was "with Show reasoning on the thought is still inside the fold, and two
    // thoughts share one line". There is no summary line any more: each thought is its own "Thought" step on the turn's
    // rail, opened as the person's Show reasoning says, and never loose in the reply area (always inside the step list).
    fun `R13 with Show reasoning on each thought opens on its own Thought step, never loose in the reply`() = runComposeUiTest {
        show(reasoningExpanded = true, thinking, "Keep it to one word.")
        onAllNodesWithText("Thought", useUnmergedTree = true).assertCountEquals(2)
        val inSteps = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("chat-work-line"))
        onAllNodes(androidx.compose.ui.test.hasText("Respond briefly", substring = true) and inSteps, useUnmergedTree = true).assertCountEquals(1)
        onAllNodes(androidx.compose.ui.test.hasText("Keep it to one word", substring = true) and inSteps, useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithText("Respond briefly", substring = true, useUnmergedTree = true).assertCountEquals(1)
        // Closed by a tap, it is a step again.
        onAllNodesWithText("Thought")[0].performClick()
        waitForIdle()
        onAllNodesWithText("Thought", useUnmergedTree = true).assertCountEquals(2)
    }
}
