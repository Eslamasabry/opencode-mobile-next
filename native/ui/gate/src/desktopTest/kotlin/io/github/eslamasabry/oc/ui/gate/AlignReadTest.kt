package io.github.eslamasabry.oc.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JVM layer reads real kit parts: a KitIcon squeezed by Modifier.size (sweep 2, P-101: cut status icons) and an
 * icon off its label's line are reported from a render; the same parts used right are not.
 */
class AlignReadTest {
    private fun align(screen: Boolean = false, content: @Composable () -> Unit): AlignGuard.Result {
        var result: AlignGuard.Result? = null
        val frame = if (screen) Frame.Screen else Frame.Part
        LayoutGuard.audit(LayoutGuard.Scene(frame, content = content), listOf(Window.Phone)) { roots, window ->
            if (result == null) result = AlignGuard.inspect(roots, window.w, window.h, screen)
        }
        return result!!
    }

    private fun rules(screen: Boolean = false, content: @Composable () -> Unit) =
        align(screen, content).findings.map { it.rule }

    @Test
    fun `P-101 a KitIcon squeezed by Modifier size is reported and one at a kit size is not`() {
        assertTrue("icon-squeezed" in rules { KitIcon(KitIcons.check, Modifier.size(16.dp)) })
        val right = rules { KitIcon(KitIcons.check, size = KitIconSize.Small) }
        assertTrue("icon-squeezed" !in right && "icon-size" !in right, right.toString())
    }

    @Test
    fun `B-GATE-201 a word turned half round by a layer is read without an inverted box`() {
        // Before the fix the turned node's far corner lay before its origin and AlignRead threw
        // "Cannot coerce value to an empty range" (every opened KitStepTimeline: its chevron turns 180°).
        val result = align { KitText("Turned", Modifier.padding(16.dp).graphicsLayer { rotationZ = 180f }) }
        assertTrue(result.findings.none { it.rule == "crash" }, result.findings.toString())
    }

    @Test
    fun `an icon beside its label is centred on the label's line`() {
        val off = rules {
            Row(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
                KitIcon(KitIcons.check, size = KitIconSize.Small)
                Column { KitText("Label"); KitText("A second block under it") }
            }
        }
        assertTrue("icon-label" in off, off.toString())
        val centred = rules {
            Row(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                KitIcon(KitIcons.check, size = KitIconSize.Small)
                KitText("Label")
            }
        }
        assertTrue("icon-label" !in centred, centred.toString())
    }

    @Test
    fun `a screen's words at 32 dp are off the page columns and at 16 dp are on them`() {
        val off = align(screen = true) {
            KitText("Double margin", Modifier.padding(horizontal = 32.dp, vertical = 64.dp))
        }
        assertTrue(off.findings.any { it.rule == "start-edge" }, off.findings.toString())
        assertEquals(setOf(32f), off.edges.keys)
        val on = align(screen = true) { KitText("Page edge", Modifier.padding(horizontal = 16.dp, vertical = 64.dp)) }
        assertTrue(on.findings.none { it.rule == "start-edge" }, on.findings.toString())
    }
}
