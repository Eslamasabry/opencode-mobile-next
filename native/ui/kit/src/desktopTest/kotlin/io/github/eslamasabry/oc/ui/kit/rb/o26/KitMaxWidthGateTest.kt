package io.github.eslamasabry.oc.ui.kit.rb.o26

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * part-kit_max_width (lib/ui/kit/kit_max_width.dart at 8302de537) has no golden: the matrix renders a capped reading
 * block in every style, mode, scale and direction, and the layout cases pin `_RenderKitMaxWidth` rule by rule.
 */
@OptIn(ExperimentalTestApi::class)
class KitMaxWidthGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_max_width") {
            Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading)) {
                KitText("Prose, cards, notices and forms hold the reading measure.", role = TypeRole.Body)
            }
        }
    }

    private fun ComposeUiTest.place(slot: Int, rtl: Boolean = false, content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Box(Modifier.requiredWidth(slot.dp).testTag("slot")) { content() } // wider than the 1024 dp test window
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.bounds(tag: String): Pair<Float, Int> {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val slot = onNodeWithTag("slot", useUnmergedTree = true).fetchSemanticsNode()
        return (node.positionInRoot.x - slot.positionInRoot.x) to node.size.width
    }

    @Test fun `a wide slot holds a page part to its cap in the middle`() = runComposeUiTest {
        place(1600) { Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading).testTag("part")) { Box(Modifier.fillMaxWidth().height(8.dp)) } }
        assertEquals(440f to 720, bounds("part"))
    }

    @Test fun `a slot no wider than the cap lays the child out untouched`() = runComposeUiTest {
        place(300) { Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading).testTag("part")) { Box(Modifier.fillMaxWidth().height(8.dp)) } }
        assertEquals(0f to 300, bounds("part"))
    }

    @Test fun `an inline part keeps the start edge and mirrors in RTL`() = runComposeUiTest {
        place(1600, rtl = true) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Chip)) { Box(Modifier.fillMaxWidth().height(8.dp).testTag("chip")) }
            }
        }
        assertEquals(1280f to 320, bounds("chip"))
    }

    @Test fun `an end-aligned value keeps the end edge`() = runComposeUiTest {
        place(1000) {
            Box(Modifier.fillMaxWidth().kitMaxWidth(400.dp, CapAlign.End)) { Box(Modifier.fillMaxWidth().height(8.dp).testTag("value")) }
        }
        assertEquals(600f to 400, bounds("value"))
    }

    @Test fun `intrinsic widths are capped so an IntrinsicSize parent stays inside the cap`() = runComposeUiTest {
        place(1600) {
            Box(Modifier.width(IntrinsicSize.Max).testTag("outer")) {
                Box(Modifier.kitMaxWidth(CapRole.Chip)) { Box(Modifier.width(500.dp).height(8.dp)) }
            }
        }
        assertEquals(320, bounds("outer").second)
    }

    @Test fun `roles without a cap add nothing`() = runComposeUiTest {
        place(1600) { Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Fill).testTag("part")) { Box(Modifier.fillMaxWidth().height(8.dp)) } }
        assertEquals(0f to 1600, bounds("part"))
    }

    @Test fun `the sliver padding is half the spare room on each side and never negative`() {
        assertEquals(140.dp, KitMaxWidth.sidePadding(1000.dp, 720.dp))
        assertEquals(0.dp, KitMaxWidth.sidePadding(500.dp, 720.dp))
        assertEquals(130.dp, KitMaxWidth.sidePadding(1220.dp, CapRole.List))
        assertEquals(0.dp, KitMaxWidth.sidePadding(5000.dp, CapRole.Fill))
    }
}
