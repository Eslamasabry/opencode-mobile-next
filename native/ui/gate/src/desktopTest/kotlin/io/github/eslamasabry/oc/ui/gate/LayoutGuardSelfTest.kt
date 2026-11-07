package io.github.eslamasabry.oc.ui.gate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitList
import io.github.eslamasabry.oc.ui.kit.foundation.kitMayTruncate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E02-S13: each layout rule fires on a scene that breaks it and stays quiet on the same scene done right
 * (native/docs/kit/LAYOUT-RULES.md). Plain foundation parts, so the rule, not a kit part, is under test.
 */
class LayoutGuardSelfTest {
    private val long = "Words that are much too long to fit on one short line of this box"

    private fun findings(window: Window = Window.Phone, frame: Frame = Frame.Screen, content: @Composable () -> Unit) =
        LayoutGuard.audit(LayoutGuard.Scene(frame, content = content), listOf(window))

    private fun rules(window: Window = Window.Phone, frame: Frame = Frame.Screen, content: @Composable () -> Unit) =
        findings(window, frame, content).map { it.rule }

    private fun tap(modifier: Modifier = Modifier, role: Role? = null) =
        modifier.clickable(role = role, onClick = {})

    @Test
    fun `E02-S13 words on the window edge break the gutter and words inside it do not`() {
        assertTrue("edge-text" in rules { BasicText("Flush") })
        assertTrue("edge-text" !in rules { BasicText("Inside", Modifier.padding(horizontal = 16.dp)) })
    }

    @Test
    fun `E02-S13 an action flush at x 0 breaks the edge rule unless its kit part bleeds`() {
        assertTrue("edge-tap" in rules { Box(tap(Modifier.size(48.dp))) })
        assertTrue("edge-tap" !in rules { Box(tap(Modifier.padding(start = 16.dp).size(48.dp))) })
        val bleed = rules {
            Box(tap(Modifier.fillMaxWidth().height(56.dp).kitEdge(KitEdge.Bleed))) {
                BasicText("Row title", Modifier.padding(horizontal = 16.dp))
            }
        }
        assertTrue("edge-tap" !in bleed && "edge-text" !in bleed, bleed.toString())
    }

    @Test
    fun `E02-S13 chrome owns its insets so nothing inside it is held to the gutter`() {
        val found = rules {
            Box(Modifier.kitEdge(KitEdge.Chrome)) { Box(tap(Modifier.size(56.dp))) { BasicText("Rail") } }
        }
        assertTrue("edge-tap" !in found && "edge-text" !in found, found.toString())
    }

    @Test
    fun `B-KIT-066 a tap target under 48 dp is reported`() {
        assertTrue("small-target" in rules { Box(tap(Modifier.padding(16.dp).size(32.dp))) })
        assertTrue("small-target" !in rules { Box(tap(Modifier.padding(16.dp).size(48.dp))) })
    }

    @Test
    fun `E02-S13 an ellipsis needs a kit part that allows it and a silent cut is always reported`() {
        val box = Modifier.padding(16.dp).width(120.dp)
        val title = box.kitMayTruncate("title")
        assertTrue("ellipsis" in rules { BasicText(long, box, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        assertTrue("ellipsis" !in rules { BasicText(long, title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        assertTrue("clipped" in rules { BasicText(long, box, maxLines = 1, overflow = TextOverflow.Clip) })
        assertTrue("ellipsis" !in rules { BasicText(long, box) }) // wraps: nothing lost
    }

    @Test
    fun `E02-S13 a headline tight against a field is reported and a spacing step clears it`() {
        @Composable
        fun scene(gap: Int) = Column(Modifier.padding(16.dp)) {
            BasicText("Headline")
            Spacer(Modifier.height(gap.dp))
            Box(tap(Modifier.fillMaxWidth().height(48.dp)))
        }
        assertTrue("tight" in rules { scene(0) })
        assertTrue("tight" !in rules { scene(12) })
    }

    @Test
    fun `B-KIT-066 two stacked controls off the 4 dp scale are reported`() {
        @Composable
        fun scene(gap: Int) = Column(Modifier.padding(16.dp)) {
            Box(tap(Modifier.size(200.dp, 48.dp)))
            Spacer(Modifier.height(gap.dp))
            Box(tap(Modifier.size(200.dp, 48.dp)))
        }
        assertTrue("off-scale" in rules { scene(10) })
        assertTrue("off-scale" !in rules { scene(8) })
    }

    @Test
    fun `E02-S13 short words that fit are never reported cut off`() {
        val found = rules {
            Column(Modifier.padding(16.dp)) {
                KitText("Done", kind = KitTextKind.Frame)
                KitButton("Done", onClick = {})
            }
        }
        assertTrue("clipped" !in found, found.toString())
    }

    @Test
    fun `E02-S13 a part drawn inside a control is not an overlap but a stacked one is`() {
        val nested = rules {
            Box(tap(Modifier.padding(16.dp).size(300.dp, 56.dp))) {
                Box(tap(Modifier.align(Alignment.CenterEnd).size(48.dp)))
            }
        }
        assertTrue("overlap" !in nested, nested.toString())
        val beside = rules {
            Box(Modifier.padding(16.dp).size(300.dp, 80.dp)) {
                BasicText("Filter", Modifier.fillMaxWidth().align(Alignment.CenterStart))
                Box(tap(Modifier.align(Alignment.CenterEnd).size(48.dp)))
            }
        }
        assertTrue("overlap" !in beside, beside.toString())
        val stacked = rules {
            Column(Modifier.padding(16.dp)) {
                Box(tap(Modifier.size(200.dp, 48.dp)))
                Box(tap(Modifier.offset(y = (-8).dp).size(200.dp, 48.dp)))
            }
        }
        assertTrue("overlap" in stacked, stacked.toString())
    }

    @Test
    fun `B-KIT-066 a gap wider than two spacing steps is page layout not a spacing step`() {
        val found = rules {
            Column(Modifier.padding(16.dp)) {
                Box(tap(Modifier.size(200.dp, 48.dp)))
                Spacer(Modifier.height(62.dp))
                Box(tap(Modifier.size(200.dp, 48.dp)))
            }
        }
        assertTrue("off-scale" !in found, found.toString())
    }

    @Test
    fun `E02-S13 rows of one list whose titles start on different edges are reported`() {
        @Composable
        fun row(start: Int, title: String) =
            Box(tap(Modifier.fillMaxWidth().height(56.dp).kitEdge(KitEdge.Bleed))) {
                BasicText(title, Modifier.padding(start = start.dp, end = 16.dp))
            }
        val found = findings { Column(Modifier.kitList("test")) { row(16, "One"); row(16, "Two"); row(56, "Three") } }
        val edges = found.filter { it.rule == "title-edge" }
        assertEquals(listOf("Three"), edges.map { it.subject })
        val aligned = rules { Column(Modifier.kitList("test")) { row(16, "One"); row(16, "Two"); row(16, "Three") } }
        assertTrue("title-edge" !in aligned)
    }

    @Test
    fun `E02-S13 a button wider than the control cap is reported on a tablet`() {
        val scene: @Composable () -> Unit = {
            Box(tap(Modifier.padding(16.dp).fillMaxWidth().height(48.dp), Role.Button))
        }
        assertTrue("wide-control" in rules(Window.Tablet, content = scene))
        assertTrue("wide-control" !in rules(Window.Phone, content = scene))
    }

    @Test
    fun `E02-S13 a lone part is not held to the page gutter`() {
        assertTrue("edge-text" !in rules(frame = Frame.Part) { BasicText("Part") })
    }

    @Test
    fun `E02-S13 findings are keyed without coordinates so the baseline survives a move`() {
        val f = LayoutGuard.Finding(Window.Phone, "edge-text", "Flush", "x 0.0..40.0")
        assertEquals("phone\tedge-text\tFlush", f.key)
    }
}
