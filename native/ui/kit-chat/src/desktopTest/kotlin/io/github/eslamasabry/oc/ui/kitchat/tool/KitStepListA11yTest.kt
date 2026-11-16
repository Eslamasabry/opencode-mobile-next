package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTag
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTagTone
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-A11Y-STEPS (UX-21, UX-36): what a screen reader meets in a turn's step list.
 *
 * The announcement rule, in one place. A polite live region speaks when its words change, so it belongs only where the
 * person must hear something they cannot be expected to find by swiping: a request that needs them (the approval card's
 * ask), a notice, a reply that failed, a copy receipt, the find bar's count, the turn's one live line (its phase, never
 * its seconds). It does NOT belong on a step row, the fold row, a status tag or streaming reply text: a step list that
 * speaks every row as it arrives, or a reply that speaks every token, makes TalkBack unusable mid-turn. Those parts are
 * silent on purpose; the person reaches them by swiping, and a failure inside folded steps is named by the fold row's
 * state ("1 failed"), which is read with the row.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepListA11yTest {
    private fun step(id: String, status: KitToolStatus = KitToolStatus.Done, label: String = "Done") =
        KitToolView(id, KitToolKind.Read, "Read", "$id.js", null, status, "0.2s", null, null, label)

    private val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Test
    fun `B-A11Y-STEPS appending a step while a sibling row is focused does not move focus`() =
        runComposeUiTest {
            val ids = mutableStateListOf("a")
            var open by mutableStateOf(false)
            setContent {
                ChatTestTheme {
                    KitStepList(
                        fold = KitStepFoldLine("Show 2 earlier steps", "1 failed", open, onToggle = { open = !open }),
                        earlier = { KitToolRow(step("old", KitToolStatus.Failed, "Failed"), false, {}) },
                    ) {
                        KitStepFold("Thought", expanded = false, onToggle = {}, icon = KitIcons.idea) { KitText("words") }
                        for (id in ids) key(id) { KitStepItem { KitToolRow(step(id), opened = false, onToggle = {}) } }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Thought").performSemanticsAction(SemanticsActions.RequestFocus)
            onNodeWithText("Thought").assertIsFocused()
            val before = onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size
            runOnIdle { ids += "b" }
            waitForIdle()
            val after = onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size
            assertEquals(before + 1, after, "the step arrived")
            onNodeWithText("Thought").assertIsFocused()
            // The same with the fold row focused while a step arrives and its count changes in place.
            onNodeWithText("Show 2 earlier steps").performSemanticsAction(SemanticsActions.RequestFocus)
            onNodeWithText("Show 2 earlier steps").assertIsFocused()
            runOnIdle { ids += "c" }
            waitForIdle()
            onNodeWithText("Show 2 earlier steps").assertIsFocused()
        }

    @Test
    fun `B-A11Y-STEPS a failure folded away stays discoverable, named by the fold row and opened by its action`() =
        runComposeUiTest {
            var open by mutableStateOf(false)
            setContent {
                ChatTestTheme {
                    KitStepList(
                        fold = KitStepFoldLine(if (open) "Hide earlier steps" else "Show 2 earlier steps", "1 declined · 1 failed", open, onToggle = { open = !open }),
                        earlier = {
                            KitToolRow(step("e1", KitToolStatus.Failed, "Failed"), false, {})
                            KitToolRow(step("e2", KitToolStatus.Stopped, "Declined"), false, {})
                        },
                    ) {
                        KitToolRow(step("a"), opened = false, onToggle = {})
                    }
                }
            }
            waitForIdle()
            val fold = onNodeWithText("Show 2 earlier steps")
            val node = fold.fetchSemanticsNode()
            assertEquals("1 declined · 1 failed", node.config.getOrNull(SemanticsProperties.StateDescription), "the fold row says what lies inside")
            assertTrue(node.config.contains(SemanticsActions.Expand), "a screen reader can open it without a tap")
            assertEquals(1, onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size, "the folded steps are not in the tree")
            fold.performSemanticsAction(SemanticsActions.Expand)
            waitForIdle()
            assertEquals(3, onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size, "opened, the failed step is there")
        }

    @Test
    fun `B-A11Y-STEPS the step list, its rows and status tags never announce on their own`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitStepList(
                        fold = KitStepFoldLine("Show 2 earlier steps", "1 failed", open = false, onToggle = {}),
                        earlier = { KitToolRow(step("e", KitToolStatus.Failed, "Failed"), false, {}) },
                    ) {
                        KitToolRow(step("a"), opened = false, onToggle = {})
                        KitToolRow(step("b", KitToolStatus.Running, "Working"), opened = false, onToggle = {})
                        KitToolRow(step("c", KitToolStatus.Failed, "Failed"), opened = false, onToggle = {})
                        KitStatusTag("Needs you", KitStatusTagTone.NeedsYou)
                        KitStatusTag("Running", KitStatusTagTone.Running)
                        KitStatusTag("Failed", KitStatusTagTone.Failed)
                        KitStatusTag("Done", KitStatusTagTone.Done)
                    }
                }
            }
            waitForIdle()
            val speaking = onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(emptyList(), speaking.map { it.config }, "no step row, fold row or status tag is a live region")
        }

    @Test
    fun `B-A11Y-STEPS the approval card announces its ask once, politely, and not the waiting seconds`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitPermissionCard(
                        title = "Run this command?", summary = null, subject = null,
                        targets = listOf(KitRequestTarget("flutter test")),
                        allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Reject",
                        onAllowOnce = {}, onAlways = null, onDeny = {},
                        look = KitRequestLook.Question, waitingAge = "3 s", onDetails = {},
                    )
                }
            }
            waitForIdle()
            val speaking = onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(speaking.all { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite }, "polite, never assertive")
            val asks = onAllNodes(liveRegion and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
            assertEquals(1, asks.fetchSemanticsNodes().size, "the ask is announced once")
            val ticking = speaking.filter { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("3 s") } == true }
            assertEquals(emptyList(), ticking.map { it.config }, "the age that counts up is not a live region")
        }

    @Test
    fun `B-A11Y-STEPS a notice announces politely`() =
        runComposeUiTest {
            setContent { ChatTestTheme { KitNotice("The reply could not be finished. Try again.", title = "Reply stopped") } }
            waitForIdle()
            val speaking = onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(speaking.isNotEmpty(), "the notice announces")
            assertTrue(speaking.all { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite }, "polite, never assertive")
        }
}
