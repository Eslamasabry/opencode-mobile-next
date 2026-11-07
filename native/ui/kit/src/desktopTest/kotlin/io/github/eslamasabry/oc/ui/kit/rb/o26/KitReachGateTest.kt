package io.github.eslamasabry.oc.ui.kit.rb.o26

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitReach
import io.github.eslamasabry.oc.ui.kit.frame.KitReachExtend
import io.github.eslamasabry.oc.ui.kit.frame.kitReach
import io.github.eslamasabry.oc.ui.kit.frame.kitReachExtend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * part-kit_reach (lib/ui/kit/kit_reach.dart at 8302de537) has no golden: the matrix renders a reaching block in every
 * style, mode, scale and direction (KitReach is a wrapper, no cap), and the cases pin the scope and `_RenderKitReach`.
 */
@OptIn(ExperimentalTestApi::class)
class KitReachGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_reach", caps = mapOf("KitReach" to "wrapper")) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitReachExtend(16.dp) { KitText("Rows run out to the sheet's edges.", role = TypeRole.Body) }
            }
        }
    }

    private fun ComposeUiTest.place(slot: Int, content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.width(slot.dp).testTag("slot")) { content() }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.bounds(tag: String): Pair<Float, Int> {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val slot = onNodeWithTag("slot", useUnmergedTree = true).fetchSemanticsNode()
        return (node.positionInRoot.x - slot.positionInRoot.x) to node.size.width
    }

    @Test fun `the reach is zero outside any scope and the scope's value inside`() = runComposeUiTest {
        val seen = mutableListOf<Dp>()
        setContent {
            seen += kitReach()
            KitReach(24.dp) { seen += kitReach() }
        }
        waitForIdle()
        assertEquals(listOf(0.dp, 24.dp), seen.distinct())
    }

    @Test fun `a part that reaches out sets the reach back to zero for what it holds`() = runComposeUiTest {
        var inside: Dp? = null
        setContent { KitReach(24.dp) { KitReachExtend(24.dp) { inside = kitReach() } } }
        waitForIdle()
        assertEquals(0.dp, inside)
    }

    @Test fun `zero reach returns the content as it is`() = runComposeUiTest {
        var inside: Dp? = null
        place(300) { KitReach(12.dp) { KitReachExtend(0.dp) { inside = kitReach(); Box(Modifier.fillMaxWidth().height(8.dp).testTag("child")) } } }
        assertEquals(12.dp, inside)
        assertEquals(0f to 300, bounds("child"))
    }

    @Test fun `a negative reach is refused`() {
        assertFailsWith<IllegalArgumentException> { runComposeUiTest { setContent { KitReach((-1).dp) {} }; waitForIdle() } }
    }

    @Test fun `the child is laid out wider and drawn out past the box which keeps its width`() = runComposeUiTest {
        place(300) {
            Box(Modifier.testTag("box").kitReachExtend(16.dp)) { Box(Modifier.fillMaxWidth().height(8.dp).testTag("child")) }
        }
        assertEquals(0f to 300, bounds("box"))
        assertEquals(-16f to 332, bounds("child"))
    }

    @Test fun `intrinsic widths shrink by the reach on both sides`() = runComposeUiTest {
        place(1000) {
            Box(Modifier.width(IntrinsicSize.Max).testTag("outer")) {
                Box(Modifier.kitReachExtend(16.dp)) { Box(Modifier.width(200.dp).height(8.dp)) }
            }
        }
        assertEquals(168, bounds("outer").second)
    }

    @Test fun `a press in the reach lands on the child`() = runComposeUiTest {
        var taps = 0
        place(300) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.kitReachExtend(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("row").clickable { taps++ })
                }
            }
        }
        // The row's first 16 dp lie in the reach, outside the reaching box itself.
        onNodeWithTag("row", useUnmergedTree = true).performTouchInput { click(Offset(4f, 24f)) }
        assertEquals(1, taps)
        assertEquals(0f to 300, bounds("row"))
    }
}
