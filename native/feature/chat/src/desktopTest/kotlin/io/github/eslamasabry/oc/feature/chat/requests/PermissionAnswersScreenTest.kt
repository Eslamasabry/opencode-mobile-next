package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AlwaysConfirmUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ReviewedRequestAction
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2026-11-13 #3 ("scope and duration cannot be read confidently"): one pair of answers everywhere ("Allow
 * once" / "Deny"), the Always scope in words and only where Always is offered, a note that says it travels with Deny,
 * and one copy action named for what it copies.
 */
@OptIn(ExperimentalTestApi::class)
class PermissionAnswersScreenTest {
    private fun reads(words: String) =
        SemanticsMatcher("reads \"$words\"") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                words in it.text.filterNot { c -> c in "⁦⁧⁨⁩‎‏‪‫‬" }
            }
        }

    private val inSheet = hasAnyAncestor(hasTestTag("chat-permission-details"))

    private fun card(permission: String, patterns: List<String>, always: Boolean, metadata: Map<String, String> = emptyMap()): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId("p1"), SessionId("ses_1"), permission, patterns.toImmutableList(),
                JsonObject(metadata.mapValues { JsonPrimitive(it.value) }), persistentListOf("*"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request),
            canAlwaysAllow = always,
            captured = PendingRequest.Permission(request),
        )
    }

    private object NoActions : ChatRequestActions {
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

    @Composable
    private fun Slot(state: RequestSlotUi, actions: ChatRequestActions = NoActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitChatEnvironment(Redactor()).Provide { KitSheetHost { ChatRequestSlot(state, actions, nowMs = null) } }
        }
    }

    /** Counts the answers that reach the model, whichever surface sent them. */
    private class Counting : ChatRequestActions by NoActions {
        val allowed = mutableListOf<RequestId>()

        override fun performReviewed(card: RequestCardUi, action: ReviewedRequestAction) {
            if (action == ReviewedRequestAction.AllowOnce) allowed += card.id
        }
    }

    @Test
    fun `B-CHAT-REV3-001 the card answers with Allow once and Deny`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false))) }
        onNodeWithText("Allow once").assertIsDisplayed()
        onNodeWithText("Deny").assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-REV3-008 the Details sheet answers with its own Allow once, which allows and closes it`() =
        runComposeUiTest {
            val actions = Counting()
            setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false)), actions) }
            onAllNodesWithText("Details").onFirst().performClick()
            waitForIdle()
            onNodeWithTag("chat-permission-details", useUnmergedTree = true).assertExists()
            // A modal sheet may hide the card under it from the semantics; the sheet's pinned answer is the last one.
            onAllNodesWithText("Allow once", useUnmergedTree = true).onLast().performClick()
            waitForIdle()
            assertEquals(listOf(RequestId("p1")), actions.allowed, "the sheet's Allow once answers")
            onNodeWithTag("chat-permission-details", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `B-CHAT-REV3-009 no answer anywhere says a bare Allow, on the card or in Details`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false))) }
        assertEquals(0, onAllNodesWithText("Allow", useUnmergedTree = true).fetchSemanticsNodes().size, "card")
        onAllNodesWithText("Details").onFirst().performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithText("Allow", useUnmergedTree = true).fetchSemanticsNodes().size, "Details")
    }

    @Test
    fun `B-CHAT-REV3-002 with no Always offered the sheet does not talk about Always nor show a bare pattern`() =
        runComposeUiTest {
            setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false))) }
            onAllNodesWithText("Details").onFirst().performClick()
            waitForIdle()
            assertTrue(
                onAllNodes(reads("Always allow") or hasContentDescription("Always allow", substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty(),
                "nothing in the sheet talks about an Always it does not offer",
            )
            assertTrue(
                onAllNodes((hasText("*") or hasContentDescription("*", substring = true)) and inSheet, useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty(),
                "no bare star",
            )
        }

    @Test
    fun `B-CHAT-REV3-003 where Always is offered the sheet says its scope in words and keeps the pattern under it`() =
        runComposeUiTest {
            setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = true))) }
            onAllNodesWithText("Details").onFirst().performClick()
            waitForIdle()
            onNode(reads("Always allow would also cover any file change."), useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-CHAT-REV3-004 the confirm says the scope in words, never a bare star`() = runComposeUiTest {
        val edit = card("edit", listOf("e9.txt"), always = true)
        setContent {
            Slot(RequestSlotUi(cards = persistentListOf(edit), confirmAlways = AlwaysConfirmUi(edit.id, edit.ask.always, edit.captured)))
        }
        onNode(reads("From now on, any file change runs without asking you"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-CHAT-REV3-005 the note says it is sent only with Deny`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false))) }
        onAllNodesWithText("Details").onFirst().performClick()
        waitForIdle()
        onNode(reads("Sent only if you choose Deny."), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-CHAT-REV3-006 a command's sheet has one copy, named Copy command, and no copy row for the facts`() =
        runComposeUiTest {
            val run = card("bash", listOf("npm test"), always = false, metadata = mapOf("command" to "npm test", "cwd" to "/w"))
            setContent { Slot(RequestSlotUi(run)) }
            onAllNodesWithText("Details").onFirst().performClick()
            waitForIdle()
            fun named(words: String) =
                onAllNodes((hasText(words, substring = true) or hasContentDescription(words, substring = true)) and inSheet, useUnmergedTree = true)
                    .fetchSemanticsNodes()
            assertTrue(named("Copy command").isNotEmpty(), "the command block copies the command")
            assertTrue(named("Copy what it asks to run").isEmpty(), "no second, vaguer copy row")
            assertTrue(named("Copy tool").isEmpty() && named("Copy runs in").isEmpty(), "no copy mark per fact")
            assertTrue(onAllNodes(hasTestTag("kit-details-copy-all"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `B-CHAT-REV3-007 the waiting card says it needs a decision once`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(card("edit", listOf("e9.txt"), always = false))) }
        val cues =
            onAllNodes(reads("Needs your decision") or hasContentDescription("Needs your decision", substring = true))
                .fetchSemanticsNodes()
        assertEquals(1, cues.size, "one waiting cue for one card: $cues")
    }

    @Test
    fun `B-CHAT-REV3-010 the Sending row sits on the page's edge, not the window's`() = runComposeUiTest {
        val sending = card("edit", listOf("e9.txt"), always = false).copy(state = CardState.Sending(AnswerWords.Allowed))
        setContent { Slot(RequestSlotUi(sending)) }
        val words = onNode(reads("Allowed"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(words.left >= 16.dp, "the answer's words start on the page's 16 dp edge: ${words.left}")
    }

    @Test
    fun `B-CHAT-REV3-011 a Deny with a note quotes the person's words`() = runComposeUiTest {
        val denied =
            card("edit", listOf("e9.txt"), always = false).copy(state = CardState.Sending(AnswerWords.RejectedWith("use b.txt")))
        setContent { Slot(RequestSlotUi(denied)) }
        onNode(reads("Denied, asked to do this instead: \u201cuse b.txt\u201d"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RC1-CARD-002 an edit names its file in the question as a creation does`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(card("edit", listOf("rc1.txt"), always = false))) }
        onNode(reads("Edit rc1.txt?"), useUnmergedTree = true).assertExists()
        assertTrue(onAllNodes(reads("Edit this file?"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `B-CHAT-REV3-012 a card retired by Stop never says the run stopped while it may still be stopping`() =
        runComposeUiTest {
            val retired = card("edit", listOf("e9.txt"), always = false).copy(state = CardState.NoLongerNeeded)
            setContent { Slot(RequestSlotUi(retired)) }
            onNode(reads("The run is ending, so nothing was sent."), useUnmergedTree = true).assertExists()
            assertTrue(onAllNodes(reads("The run stopped"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
}
