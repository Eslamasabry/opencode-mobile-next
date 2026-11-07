package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitPresenceTest {
    @Composable
    private fun Themed(level: MotionLevel, content: @Composable () -> Unit) {
        KitTheme(appearance = KitAppearance(motion = level), platform = DesktopKitPlatform(), stillFrame = true, content = content)
    }

    @Composable
    private fun Live(live: MutableSet<String>) {
        DisposableEffect(Unit) {
            live += "child"
            onDispose { live -= "child" }
        }
        Box(Modifier.size(100.dp, 20.dp))
    }

    @Test
    fun `B-KIT-023 KitPresence first shown is simply there, hidden first composes nothing`() = runComposeUiTest {
        val live = mutableSetOf<String>()
        var shown by mutableStateOf(false)
        setContent { Themed(MotionLevel.Full) { KitPresence(shown) { Live(live) } } }
        waitForIdle()
        assertTrue(live.isEmpty(), "a hidden presence is not in the tree")
    }

    @Test
    fun `B-KIT-023 KitPresence slot and fade run on one QUICK tween at Full`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var shown by mutableStateOf(false)
        setContent {
            Themed(MotionLevel.Full) {
                Row(Modifier.testTag("row")) { KitPresence(shown, Modifier.testTag("slot")) { Box(Modifier.size(100.dp, 20.dp)) } }
            }
        }
        mainClock.advanceTimeByFrame()
        shown = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MotionTokens.QUICK.toLong() / 2)
        val half = onNodeWithTag("slot").getBoundsInRoot().let { it.right - it.left }
        assertTrue(half > 0.dp && half < 100.dp, "the slot is opening, was $half")
        mainClock.advanceTimeBy(MotionTokens.QUICK.toLong())
        assertEquals(100.dp, onNodeWithTag("slot").getBoundsInRoot().let { it.right - it.left }, "settled on QUICK, not a spring")
    }

    @Test
    fun `B-KIT-023 KitPresence leaves at once under Off`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val live = mutableSetOf<String>()
        var shown by mutableStateOf(true)
        setContent { Themed(MotionLevel.Off) { KitPresence(shown) { Live(live) } } }
        mainClock.advanceTimeByFrame()
        assertEquals(setOf("child"), live)
        shown = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(live.isEmpty())
    }
}
