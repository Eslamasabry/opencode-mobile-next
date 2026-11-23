package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-KIT-036, where a running step's travelling light starts: always at the top of its lap, and at the bottom of the
 * node the rail lit last — the step that just finished — never at a sentence dot or a row folded in between, and
 * never below the running node. Reads the rail's own record ([StepRail.lit]) instead of pixels.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepLightPathTest {
    private fun step(
        id: String,
        kind: KitToolKind = KitToolKind.Read,
        title: String = "Read",
        subject: String? = "app.js",
        status: KitToolStatus = KitToolStatus.Done,
    ) = KitToolView(id, kind, title, subject, null, status, "0.2s", null, null, "Done")

    private fun KitUiTest.host(
        motion: MotionLevel = MotionLevel.Full,
        rtl: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = motion),
                    platform = ChatFakePlatform(),
                    localeTag = if (rtl) "ar" else "en",
                    stillFrame = false,
                ) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        Box(Modifier.size(412.dp, 700.dp).background(Color.Black)) {
                            Box(Modifier.padding(horizontal = 16.dp)) { content() }
                        }
                    }
                }
            }
        }
    }

    /** Every node's box, dp from the list's own top edge. */
    private fun KitUiTest.nodesFromListTop(): List<DpRect> {
        val top = onNodeWithTag(STEP_LIST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val all = onAllNodesWithTag("kit-step-node", useUnmergedTree = true)
        return all.fetchSemanticsNodes().indices.map { all[it].getUnclippedBoundsInRoot() }
            .sortedBy { it.top.value }
            .map { DpRect(it.left, it.top - top, it.right, it.bottom - top) }
    }

    /** The running (bottom-most) node's latest recorded frame. */
    private fun frameOf(rail: StepRail): StepRail.LitFrame? {
        val key = rail.spans.entries.maxByOrNull { it.value.start }?.key ?: return null
        return rail.lit[key]
    }

    /** fromY lies in [node]: from its centre to its box bottom, a dp of tolerance each way. */
    private fun inNode(what: String, fromY: Float, node: DpRect) {
        val centre = (node.top.value + node.bottom.value) / 2f
        val ok = centre - 1f <= fromY && fromY <= node.bottom.value + 1f
        assertTrue(ok, "$what: fromY $fromY, node ${node.top.value}..${node.bottom.value}")
    }

    @Test
    fun `B-KIT-036 the first step's light starts at the list's top and comes down first`() =
        kitUiTest(412, 700) {
            mainClock.autoAdvance = false
            val rail = arrayOfNulls<StepRail>(1)
            host {
                KitStepList {
                    Box { rail[0] = LocalKitStepRail.current }
                    KitStepItem { KitToolRow(step("a", status = KitToolStatus.Running), opened = false, onToggle = {}) }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val f = frameOf(rail[0]!!) ?: error("the running step recorded no frame")
            assertEquals(0f, f.fromY, 1f, "the light starts at the list's top edge")
            assertTrue(f.phase < .1f, "the lap opens at its start, not at ${f.phase}")
        }

    @Test
    fun `B-KIT-036 the next step's light starts at the step that just finished`() {
        for (rtl in listOf(false, true)) {
            for (scale in listOf(1f, 1.3f)) {
                kitUiTest(412, 700) {
                    mainClock.autoAdvance = false
                    val what = "rtl=$rtl scale=$scale"
                    val rail = arrayOfNulls<StepRail>(1)
                    var stage by mutableStateOf(0)
                    host(rtl = rtl, fontScale = scale) {
                        KitStepList {
                            Box { rail[0] = LocalKitStepRail.current }
                            KitStepItem {
                                KitToolRow(
                                    step("a", status = if (stage == 0) KitToolStatus.Running else KitToolStatus.Done),
                                    opened = false,
                                    onToggle = {},
                                )
                            }
                            if (stage == 1) {
                                KitStepSentence { KitText("A sentence between the two steps") }
                                KitStepItem {
                                    KitToolRow(
                                        step("b", KitToolKind.Edit, "Edited", "a.ts", KitToolStatus.Running),
                                        opened = false,
                                        onToggle = {},
                                    )
                                }
                            }
                        }
                    }
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    stage = 1
                    mainClock.advanceTimeBy(400)
                    val nodes = nodesFromListTop()
                    assertEquals(2, nodes.size, "$what: A and B carry a node; the sentence dot does not")
                    val f = frameOf(rail[0]!!) ?: error("$what: the running step recorded no frame")
                    inNode("$what: the light starts at the step that just finished, not the dot", f.fromY, nodes[0])
                }
            }
        }
    }

    @Test
    fun `B-KIT-036 a step inserted above the finished one does not move the start`() =
        kitUiTest(412, 700) {
            mainClock.autoAdvance = false
            val rail = arrayOfNulls<StepRail>(1)
            var stage by mutableStateOf(0)
            host {
                KitStepList {
                    Box { rail[0] = LocalKitStepRail.current }
                    if (stage == 1) KitStepItem { KitToolRow(step("x"), opened = false, onToggle = {}) }
                    KitStepItem {
                        KitToolRow(
                            step("a", status = if (stage == 0) KitToolStatus.Running else KitToolStatus.Done),
                            opened = false,
                            onToggle = {},
                        )
                    }
                    if (stage == 1) {
                        KitStepItem {
                            KitToolRow(step("b", status = KitToolStatus.Running), opened = false, onToggle = {})
                        }
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            stage = 1
            mainClock.advanceTimeBy(400)
            val nodes = nodesFromListTop()
            assertEquals(3, nodes.size, "X, A and B on the rail")
            val f = frameOf(rail[0]!!) ?: error("the running step recorded no frame")
            inNode("the start stays at the finished step, not the row inserted above it", f.fromY, nodes[1])
        }

    @Test
    fun `B-KIT-036 with the finished step folded away the light starts at the fold row`() =
        kitUiTest(412, 700) {
            mainClock.autoAdvance = false
            val rail = arrayOfNulls<StepRail>(1)
            var stage by mutableStateOf(0)
            host {
                KitStepList(
                    fold =
                        if (stage == 1) {
                            KitStepFoldLine("Show 1 earlier step", null, open = false, onToggle = {})
                        } else {
                            null
                        },
                ) {
                    Box { rail[0] = LocalKitStepRail.current }
                    KitStepItem(visible = stage == 0) {
                        KitToolRow(
                            step("a", status = if (stage == 0) KitToolStatus.Running else KitToolStatus.Done),
                            opened = false,
                            onToggle = {},
                        )
                    }
                    if (stage == 1) {
                        KitStepItem {
                            KitToolRow(step("b", status = KitToolStatus.Running), opened = false, onToggle = {})
                        }
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            stage = 1
            mainClock.advanceTimeBy(600)
            val nodes = nodesFromListTop()
            assertEquals(2, nodes.size, "the fold row and B on the rail")
            val f = frameOf(rail[0]!!) ?: error("the running step recorded no frame")
            inNode("the light starts at the fold row once the finished step folded away", f.fromY, nodes[0])
        }

    @Test
    fun `B-KIT-036 a rebuilt list starts its light at the step above, from the top`() =
        kitUiTest(412, 700) {
            mainClock.autoAdvance = false
            val rail = arrayOfNulls<StepRail>(1)
            var shown by mutableStateOf(true)
            host {
                if (shown) {
                    KitStepList {
                        Box { rail[0] = LocalKitStepRail.current }
                        KitStepItem { KitToolRow(step("a"), opened = false, onToggle = {}) }
                        KitStepItem {
                            KitToolRow(step("b", status = KitToolStatus.Running), opened = false, onToggle = {})
                        }
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            shown = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            shown = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            val nodes = nodesFromListTop()
            assertEquals(2, nodes.size, "A and B on the rebuilt rail")
            val f = frameOf(rail[0]!!) ?: error("the running step recorded no frame")
            inNode("the rebuilt list starts at the step above", f.fromY, nodes[0])
            assertTrue(f.phase < .1f, "from the top of the lap, not at ${f.phase}")
        }

    @Test
    fun `B-KIT-036 reduced motion draws no travelling light, only the running mark`() =
        kitUiTest(412, 700) {
            mainClock.autoAdvance = false
            val rail = arrayOfNulls<StepRail>(1)
            host(motion = MotionLevel.Off) {
                KitStepList {
                    Box { rail[0] = LocalKitStepRail.current }
                    KitStepItem { KitToolRow(step("a"), opened = false, onToggle = {}) }
                    KitStepItem { KitToolRow(step("b", status = KitToolStatus.Running), opened = false, onToggle = {}) }
                }
            }
            mainClock.advanceTimeBy(500)
            assertTrue(rail[0]!!.lit.isEmpty(), "no travelling light is recorded under reduced motion")
            assertEquals(2, nodesFromListTop().size, "both steps still draw their node")
        }
}
