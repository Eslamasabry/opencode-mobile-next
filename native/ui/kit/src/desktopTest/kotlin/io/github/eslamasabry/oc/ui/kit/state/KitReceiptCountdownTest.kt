package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The held receipt (LAMP-CRAFT §4 Stop and Allow once, "Open kit work" `KitReceipt` with `countdownMs`): the
 * "Allowed · Undo" / "Stopping ‹title›… · Undo" pill drains over the 3 s hold, Undo cancels it, the end is called once,
 * TalkBack hears how long Undo lasts, and a receipt without a hold is unchanged.
 */
@OptIn(ExperimentalTestApi::class)
class KitReceiptCountdownTest {
    private class Scene {
        var shown by mutableStateOf(true)
        var ends = 0
        var undos = 0
    }

    private fun ComposeUiTest.host(scene: Scene, label: String = "Allowed", motion: MotionLevel = MotionLevel.Full, style: Style = Style.Solid, hold: Int? = MotionTokens.HELD_ANSWER) {
        setContent {
            KitTheme(KitAppearance(motion = motion, style = style), FakeKitPlatform(), "en") {
                Box(Modifier.size(360.dp, 200.dp)) {
                    if (scene.shown)
                        KitReceipt(
                            KitReceiptState.Confirmed,
                            label = label,
                            onUndo = {
                                scene.undos++
                                scene.shown = false
                            },
                            countdownMs = hold,
                            onHoldEnd = { scene.ends++ },
                        )
                }
            }
        }
    }

    @Test
    fun `B-LAMP-K1-040 the hold ends once after 3 s, and only then`() {
        for (motion in listOf(MotionLevel.Full, MotionLevel.Off)) {
            runDesktopComposeUiTest(400, 240) {
                mainClock.autoAdvance = false
                val scene = Scene()
                host(scene, motion = motion)
                mainClock.advanceTimeBy(2_900)
                assertEquals(0, scene.ends, "$motion: still holding at 2.9 s")
                mainClock.advanceTimeBy(300)
                assertEquals(1, scene.ends, "$motion: the hold ended")
                mainClock.advanceTimeBy(5_000)
                assertEquals(1, scene.ends, "$motion: once")
                onNodeWithTag(RECEIPT_HOLD_TAG, useUnmergedTree = true).assertExists()
            }
        }
    }

    @Test
    fun `B-LAMP-K1-041 Undo cancels the hold`() =
        runDesktopComposeUiTest(400, 240) {
            val scene = Scene()
            host(scene, label = "Stopping “Bank API migration”…")
            mainClock.advanceTimeBy(1_000)
            onNodeWithText("Undo").performClick()
            mainClock.advanceTimeBy(5_000)
            assertEquals(1, scene.undos)
            assertEquals(0, scene.ends, "an undone hold never ends")
        }

    @Test
    fun `B-LAMP-K1-042 TalkBack hears the words and how long Undo lasts`() =
        runDesktopComposeUiTest(400, 240) {
            val scene = Scene()
            host(scene, label = "Stopping “Bank API migration”…")
            waitForIdle()
            onNode(hasContentDescription("Stopping “Bank API migration”…. Undo within", substring = true), useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-LAMP-K1-043 a receipt without a hold keeps its plain look and words`() =
        runDesktopComposeUiTest(400, 240) {
            val scene = Scene()
            host(scene, hold = null)
            waitForIdle()
            onNodeWithTag(RECEIPT_HOLD_TAG, useUnmergedTree = true).assertDoesNotExist()
            onNode(hasContentDescription("Allowed"), useUnmergedTree = true).assertExists()
            mainClock.advanceTimeBy(5_000)
            assertEquals(0, scene.ends)
        }

    @Test
    fun `B-LAMP-K1-044 the held pill is drawn in every style`() {
        for (style in listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)) {
            runDesktopComposeUiTest(400, 240) {
                val scene = Scene()
                host(scene, style = style)
                waitForIdle()
                onNodeWithTag(RECEIPT_HOLD_TAG, useUnmergedTree = true).assertExists()
                onNodeWithText("Undo").assertExists()
            }
        }
    }
}
