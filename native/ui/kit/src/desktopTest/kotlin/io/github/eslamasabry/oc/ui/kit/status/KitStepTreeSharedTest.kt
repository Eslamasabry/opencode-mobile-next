package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.lamp.K2
import io.github.eslamasabry.oc.ui.kit.lamp.k2Host
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D1 "steps only" (owner 2026-11-14): the step tree is ONE kit part for the edge light's panel and a conversation's
 * steps. The conversation hangs a group's children close to its rail ([KitStepTree]'s indent), opens a group by
 * itself only when it chooses to (opensItself), says a stopped child's own word, and draws every tree's lines in the
 * same ink per style ([rememberStepTreeInk]).
 */
@OptIn(ExperimentalTestApi::class)
class KitStepTreeSharedTest {
    private val children =
        listOf(
            KitTreeStep("a", "Find every file that reads rivers", KitTreeStepState.Done, seconds = 12),
            KitTreeStep("b", "Compare the old copies", KitTreeStepState.Working),
        )

    @Test
    fun `D1-TREE-001 children hang at the indent the caller gives, mirrored in Arabic`() {
        for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(412, 600) {
                k2Host(rtl = rtl) {
                    KitStepTree(KitStepTreeKind.Parallel, children, K2.steps, Modifier.width(412.dp), indent = 4, opensItself = true) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            KitText("2 sub-agents", Modifier.stepTreeRoot(), role = TypeRole.Label)
                        }
                    }
                }
                waitForIdle()
                val marks = onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true)
                assertEquals(2, marks.fetchSemanticsNodes().size, "both children shown")
                for (i in 0 until 2) {
                    val r = marks[i].getUnclippedBoundsInRoot()
                    if (!rtl) assertTrue(abs(r.left.value - 4f) < 0.6f, "ltr: child $i mark at 4 dp, was ${r.left}")
                    else assertTrue(abs(r.right.value - (412f - 4f)) < 0.6f, "rtl: child $i mark 4 dp from the right, was ${r.right}")
                }
            }
        }
    }

    @Test
    fun `D1-TREE-002 a caller's opensItself false keeps a working group folded, true opens a finished one`() {
        runDesktopComposeUiTest(412, 600) {
            k2Host {
                KitStepTree(KitStepTreeKind.Parallel, children, K2.steps, Modifier.width(412.dp), opensItself = false) {
                    KitText("2 sub-agents", Modifier.stepTreeRoot(), role = TypeRole.Label)
                }
            }
            waitForIdle()
            assertEquals(0, onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true).fetchSemanticsNodes().size, "working, yet folded: the caller said so")
        }
        runDesktopComposeUiTest(412, 600) {
            k2Host {
                KitStepTree(
                    KitStepTreeKind.Parallel,
                    children.map { it.copy(state = KitTreeStepState.Done) },
                    K2.steps,
                    Modifier.width(412.dp),
                    opensItself = true,
                ) {
                    KitText("2 sub-agents", Modifier.stepTreeRoot(), role = TypeRole.Label)
                }
            }
            waitForIdle()
            assertEquals(2, onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true).fetchSemanticsNodes().size, "all done, yet open: the caller said so")
        }
    }

    @Test
    fun `D1-TREE-003 a child's own word replaces the state's word`() =
        runDesktopComposeUiTest(412, 600) {
            k2Host {
                KitStepTree(
                    KitStepTreeKind.Parallel,
                    listOf(KitTreeStep("s", "Compare the old copies", KitTreeStepState.NotStarted, word = "Stopped")),
                    K2.steps,
                    Modifier.width(412.dp),
                    opensItself = true,
                ) {
                    KitText("1 sub-agent", Modifier.stepTreeRoot(), role = TypeRole.Label)
                }
            }
            waitForIdle()
            onNodeWithText("Stopped").assertExists()
            onNodeWithText("Not started").assertDoesNotExist()
        }

    @Test
    fun `D1-TREE-004 every tree draws in one ink per style - 1_5 dp quiet guide, Brutal 2 dp of ink, Paper a 1 dp rule`() {
        for (style in K2.styles) for (dark in listOf(true, false)) {
            var ink: StepTreeInk? = null
            var text1 = androidx.compose.ui.graphics.Color.Unspecified
            var accent = androidx.compose.ui.graphics.Color.Unspecified
            runDesktopComposeUiTest(200, 200) {
                k2Host(style = style, dark = dark) {
                    ink = rememberStepTreeInk()
                    text1 = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.text1
                    accent = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.accent
                }
                waitForIdle()
            }
            val got = ink!!
            val what = "$style ${if (dark) "dark" else "light"}"
            val width =
                when (style) {
                    Style.Brutal -> 2f
                    Style.Paper -> 1f
                    else -> 1.5f
                }
            assertEquals(width, got.width.value, 0.01f, "$what: line width")
            val inked = style == Style.Brutal || style == Style.Paper
            assertEquals(if (inked) text1.alpha else text1.alpha * 0.22f, got.guide.alpha, 0.01f, "$what: the guide's ink")
            assertEquals(if (style == Style.Paper) text1 else accent, got.accent, "$what: progress is ink in Paper, accent elsewhere")
        }
    }
}
