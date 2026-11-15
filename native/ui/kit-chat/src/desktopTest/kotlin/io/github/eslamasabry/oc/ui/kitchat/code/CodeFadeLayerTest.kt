package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * c2-performance (2026-11-15): every code block on screen drew its lines in an offscreen layer of its own for its
 * sideways edge fade, also when nothing was cleared (the lines fit, no corner kept), so a conversation with code paid a
 * second GPU fill of each block on every frame it moved. The layer is there only while a fade clears something
 * (CodeOverflow is the one sideways scroller of every code block, chunk and command box).
 */
@OptIn(ExperimentalTestApi::class)
class CodeFadeLayerTest {
    private fun offscreen(sources: List<String>): Int {
        var n = -1
        runDesktopComposeUiTest(400, 600) {
            mainClock.autoAdvance = false
            val meter = FrameMeter.attach(this)
            setContent {
                ChatTestTheme {
                    Column(Modifier.width(300.dp)) {
                        sources.forEach {
                            Box { CodeOverflow(Color(0xFF202124), Color.Gray) { BasicText(it, softWrap = false) } }
                        }
                    }
                }
            }
            meter.settle()
            n = meter.offscreenLayers().first
            meter.close()
        }
        return n
    }

    @Test
    fun `PERF-CODE-1 code blocks whose lines fit composite nothing offscreen`() {
        assertEquals(0, offscreen(listOf("ok", "val a = 1", "done")), "three short blocks")
    }

    @Test
    fun `PERF-CODE-2 a block whose lines run past its edge keeps its fade's layer`() {
        assertEquals(1, offscreen(listOf("ok", "W".repeat(120))), "one wide block among short ones")
    }
}
