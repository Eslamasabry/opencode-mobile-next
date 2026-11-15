package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitRowHotPathTest {
    private fun stepsFromTitleToRow(titleNode: SemanticsNode, rowNode: SemanticsNode): Int {
        var current: LayoutInfo? = titleNode.layoutInfo
        val target = rowNode.layoutInfo
        var steps = 0
        while (current != null && current !== target) {
            current = current.parentInfo
            steps++
        }
        assertTrue(current === target, "title must be a descendant of kit-row")
        return steps
    }

    @Test
    fun `B-KIT-ROW-HOT plain and large-scale rows skip subcomposition while normal value rows still measure`() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        Column(Modifier.width(320.dp)) {
                            Box(Modifier.testTag("plain-wrap")) {
                                KitRow("Plain row", supporting = "Details")
                            }
                            Box(Modifier.testTag("inline-wrap")) {
                                KitRow("Connection", trailing = KitRowTrailing.Value("Home"))
                            }
                            Box(Modifier.testTag("long-wrap")) {
                                KitRow(
                                    "Connection",
                                    trailing = KitRowTrailing.Value("A very long connection name that moves below"),
                                )
                            }
                            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2.0f)) {
                                Box(Modifier.testTag("large-wrap")) {
                                    KitRow("Scale", trailing = KitRowTrailing.Value("1"))
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()

            fun boundsIn(wrapTag: String, partTag: String) =
                onNode(
                    hasTestTag(partTag) and hasAnyAncestor(hasTestTag(wrapTag)),
                    useUnmergedTree = true,
                ).getBoundsInRoot()

            fun stepsIn(wrapTag: String): Int {
                val rowNode = onNode(
                    hasTestTag("kit-row") and hasAnyAncestor(hasTestTag(wrapTag)),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                val titleNode = onNode(
                    hasTestTag("kit-row-title") and hasAnyAncestor(hasTestTag(wrapTag)),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                return stepsFromTitleToRow(titleNode, rowNode)
            }

            // Without BoxWithConstraints: title -> titleRow -> Column -> middleRow -> kit-row (4 hops).
            // With BoxWithConstraints: an extra SubcomposeLayout sits between middleRow and kit-row (5 hops).
            assertEquals(4, stepsIn("plain-wrap"), "plain row must not subcompose with BoxWithConstraints")
            assertEquals(
                4,
                stepsIn("large-wrap"),
                "large-scale row with value must not subcompose with BoxWithConstraints",
            )
            assertEquals(5, stepsIn("inline-wrap"), "normal-scale row with value measures against available width")

            val inlineTitle = boundsIn("inline-wrap", "kit-row-title")
            val inlineValue = boundsIn("inline-wrap", "kit-row-value")
            assertTrue(inlineValue.top < inlineTitle.bottom, "short value stays inline: $inlineTitle $inlineValue")

            val longTitle = boundsIn("long-wrap", "kit-row-title")
            val longValue = boundsIn("long-wrap", "kit-row-value")
            assertTrue(longValue.top >= longTitle.bottom, "long value moves below title: $longTitle $longValue")

            val largeTitle = boundsIn("large-wrap", "kit-row-title")
            val largeValue = boundsIn("large-wrap", "kit-row-value")
            assertTrue(
                largeValue.top >= largeTitle.bottom,
                "large-scale value always moves below title: $largeTitle $largeValue",
            )
        }
}
