package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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

/**
 * Issue 147 / F-016 and F-023 on the real conversation page at the real phone's size (360 x 780 dp), with the real
 * floating message box (its model row included): a waiting decision's buttons are always whole above the box, and a
 * message sent while it waits never takes it out of view.
 */
@OptIn(ExperimentalTestApi::class)
class WaitingDecisionVisibleTest {
    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val command =
        "chmod +x /root/projects/habittracker/server.py && python3 -c \"\n" +
            (1..60).joinToString("\n") { "import subprocess, time, urllib.request  # line $it of the script" } + "\n\""

    private fun card(inSlot: Boolean = false): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId("r1"), SessionId(denySid), "bash", persistentListOf(command), JsonObject(emptyMap()),
                persistentListOf("*"), PermissionTool(MessageId("m2"), if (inSlot) "call-elsewhere" else "call-1"), true, null,
            )
        return RequestCardUi.Permission(
            id = request.id, waitingSinceMs = 0, state = CardState.Waiting, ask = PermissionAsk.of(request),
            canAlwaysAllow = false, captured = PendingRequest.Permission(request),
        )
    }

    private fun run(queued: Boolean, inSlot: Boolean = false, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(360, 780) {
            val facts = TurnFactsPresenter(FixedClock).present(runningEditFeed(), 58_000, waitingForYou = true)
            val scene = ChatScene()
            var withQueued by mutableStateOf(false)
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        // Older conversation above the turn, so the page can be read back away from the decision.
                        val older = (1..12).map { prompt("old$it", "An earlier message, number $it, that fills the page " + "x".repeat(60)) }
                        val rows0 =
                            if (withQueued)
                                persistentListOf(prompt("m3", (1..40).joinToString("\n") { "Also tell me the preview URL, part $it" }), WorkLineRow("work:m1", "m1", calls = 1, failed = 0, state = WorkState.Running), prompt("m1", "edit it"))
                            else persistentListOf(WorkLineRow("work:m1", "m1", calls = 1, failed = 0, state = WorkState.Running), prompt("m1", "edit it"))
                        val rows = (rows0 + older).toImmutableList()
                        val state = ChatUi.loading(Ref).copy(title = "Habit tracker", phase = ChatPhase.Live, rows = rows, live = LiveTurnUi(stopping = false))
                        ChatScreen(
                            state, scene.actions, scene.host, facts = facts,
                            requests = RequestSlotUi(card(inSlot)), requestActions = NoRequestActions,
                        )
                    }
                }
            }
            waitForIdle()
            if (queued) {
                runOnIdle { withQueued = true }
                waitForIdle()
            }
            check()
        }

    private fun androidx.compose.ui.test.ComposeUiTest.assertDecisionWhole(inside: String? = null) {
        val box = onNodeWithTag("chat-composer-pane").getBoundsInRoot()
        for (word in listOf("Allow once", "Deny")) {
            val found = onAllNodesWithText(word).fetchSemanticsNodes()
            assertTrue(
                found.isNotEmpty(),
                "$word is on the page; prompt=${onAllNodesWithText("Also tell me", substring = true).fetchSemanticsNodes().size} work=${onAllNodes(androidx.compose.ui.test.hasTestTag("chat-work-line")).fetchSemanticsNodes().size} waiting=${onAllNodesWithText("Waiting for you", substring = true).fetchSemanticsNodes().size} inplace=${onAllNodes(androidx.compose.ui.test.hasTestTag("chat-request-in-place")).fetchSemanticsNodes().size}",
            )
            val b = onAllNodesWithText(word)[0].getBoundsInRoot()
            assertTrue(b.bottom <= box.top, "$word ends at ${b.bottom}, the message box starts at ${box.top}: the box covers it")
            assertTrue(b.top >= 0.dp, "$word starts at ${b.top}: it is above the page")
            if (inside != null) {
                val range = onNodeWithTag(inside).fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
                assertTrue(range == null || range.maxValue() <= 0.5f, "the holder scrolls (${range?.maxValue?.invoke()} px): its last line, the answers, is past its edge")
                val frame = onNodeWithTag(inside).getBoundsInRoot()
                assertTrue(b.bottom <= frame.bottom + 0.5.dp, "$word ends at ${b.bottom}, past the bottom of its holder at ${frame.bottom}: cut off")
            }
        }
    }

    @Test
    fun `F-016 a waiting permission for a long command in the request slot shows Allow once and Deny whole`() =
        run(queued = false, inSlot = true) { assertDecisionWhole(inside = "kit-request-stack") }

    @Test
    fun `F-016 the same card in place under its step shows Allow once and Deny whole above the message box`() =
        run(queued = false) { assertDecisionWhole() }

    @Test
    fun `F-023 a tap in the message box while a decision waits takes the page back to it`() =
        run(queued = true) {
            // The person reads back to the start of the conversation: the decision is out of view.
            repeat(4) { onNode(androidx.compose.ui.test.hasScrollAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo)).performTouchInput { swipeDown() } }
            waitForIdle()
            assertTrue(
                onAllNodesWithText("Allow once").fetchSemanticsNodes().isEmpty(),
                "the page was scrolled away from the decision",
            )
            onNodeWithTag("chat-composer-field").performClick()
            waitForIdle()
            assertDecisionWhole()
        }

    // B-459 (268569150a): a sent or queued message goes to the END, as any send does; the card stays above it. A message
    // taller than the page therefore shows the message, not the card; the box tap (the test above) brings the page back.
    @Test
    fun `F-023 a long message sent while a decision waits goes to the end and a tap in the box brings the card back`() =
        run(queued = true) {
            assertTrue(
                onAllNodesWithText("Also tell me", substring = true).fetchSemanticsNodes().isNotEmpty(),
                "the sent message is in view at the end of the page",
            )
            onNodeWithTag("chat-composer-field").performClick()
            waitForIdle()
            assertDecisionWhole()
        }
}

private val denySid get() = Ref.sessionId.raw
private object NoRequestActions : io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions {
    override fun shown(id: RequestId) = Unit
    override fun allowOnce(id: RequestId) = Unit
    override fun reject(id: RequestId) = Unit
    override fun askAlwaysAllow(id: RequestId) = Unit
    override fun confirmAlwaysAllow() = Unit
    override fun cancelAlwaysAllow() = Unit
    override fun keepAsking(id: RequestId) = Unit
    override fun choose(id: RequestId, option: String) = Unit
    override fun openOther(id: RequestId) = Unit
    override fun editOther(id: RequestId, text: String) = Unit
    override fun sendOther(id: RequestId) = Unit
    override fun openSheet(id: RequestId) = Unit
    override fun closeSheet(id: RequestId) = Unit
    override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
    override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
    override fun sendSheet(id: RequestId) = Unit
    override fun undo(id: RequestId) = Unit
    override fun retry(id: RequestId) = Unit
}
