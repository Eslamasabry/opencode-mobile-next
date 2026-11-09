package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCard
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCardMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Motion review 2026-11-09, item 9: one animation per change, read part-way on the paused clock. */
@OptIn(ExperimentalTestApi::class)
class OneClockMotionTest {
    private val red = Color(0xFFFF0000)

    @Test
    fun `MOT-ONE-1 a Stop arriving with its strip fades once, as the strip does`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var running by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    Column(Modifier.width(360.dp)) {
                        KitComposerStripReveal(stop = if (running) ({ Spacer(Modifier.size(80.dp, 40.dp).background(red).testTag("mine")) }) else null)
                        // The reference: the same unfold and one fade on the same pace, nothing nested.
                        val specs = KitMotion.specs
                        AnimatedVisibility(
                            visible = running,
                            enter = expandVertically(specs.enter()) + fadeIn(specs.enter()),
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.size(80.dp, 40.dp).background(red).testTag("ref"))
                            }
                        }
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            running = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            val img = onRoot().captureToImage().toAwtImage()
            fun red(tag: String): Int {
                val b = onNodeWithTag(tag).getBoundsInRoot()
                val x = ((b.left + b.right).value / 2).toInt()
                val y = (b.top.value + 2).toInt()
                return (img.getRGB(x, y) shr 16) and 0xFF
            }
            val mine = red("mine")
            val ref = red("ref")
            assertTrue(abs(mine - ref) <= 3, "the Stop is as strong as one fade makes it (mine $mine, one fade $ref)")
        }

    @Test
    fun `MOT-ONE-2 an agent card does not animate its own size when its body changes`() =
        runComposeUiTest {
            var notice by mutableStateOf<String?>(null)
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    KitAgentCard(eyebrow = "Claude Code", title = "Done", mode = KitAgentCardMode.Report, notice = notice)
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            notice = "Saved to the project folder, with a longer line that wraps onto a second one."
            repeat(2) { mainClock.advanceTimeByFrame() }
            val early = onNodeWithTag("kit-agent-card").getBoundsInRoot().height
            mainClock.autoAdvance = true
            waitForIdle()
            val settled = onNodeWithTag("kit-agent-card").getBoundsInRoot().height
            assertEquals(settled, early, "the card is at its final size at once; only a part the person opens animates")
        }
}
