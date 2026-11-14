package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Move… (LAMP-CRAFT §5): six spots on a physical phone figure, a tap moves the real light at once with Select; the
 * light at Low sits 196 dp above the bottom, a height change glides, a side change fades across; both directions.
 */
@OptIn(ExperimentalTestApi::class)
class KitLampPlacePickerTest {
    @Test
    fun `B-LAMP-K2-050 six spots - a tap picks the physical side at once with Select, in both directions`() {
        for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(412, 800) {
                val platform = FakeKitPlatform()
                var place by mutableStateOf(KitLampPlace())
                val picks = mutableListOf<KitLampPlace>()
                k2Host(rtl = rtl, platform = platform) {
                    KitLampPlacePicker(place, K2.place, { picks += it; place = it }, {})
                }
                waitForIdle()
                assertEquals(6, onAllNodesWithTag(LAMP_PLACE_SPOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
                // The light starts Low on the reading end: the right edge in English, the left in Arabic.
                onNodeWithText(if (rtl) "Left · Low" else "Right · Low").assertExists()
                val leftHigh = onNodeWithContentDescription("Left, High", useUnmergedTree = true)
                val rightHigh = onNodeWithContentDescription("Right, High", useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(leftHigh.getUnclippedBoundsInRoot().left < rightHigh.left, "${if (rtl) "rtl" else "ltr"}: the figure's left is the screen's left")
                leftHigh.performClick()
                assertEquals(listOf(KitLampPlace(if (rtl) KitLampSide.End else KitLampSide.Start, KitLampHeight.High)), picks)
                assertEquals(listOf(KitHaptic.Select), platform.haptics)
                waitForIdle()
                onNodeWithText("Left · High").assertExists()
            }
        }
    }

    @Test
    fun `B-LAMP-K2-052 Move as a modal - a tap on the dimmed page closes it, a tap on the picker does not`() =
        runDesktopComposeUiTest(412, 800) {
            var shown by mutableStateOf(true)
            var done = 0
            k2Host {
                KitLampPlaceLayer(shown, KitLampPlace(), K2.place, {}, { done++; shown = false })
            }
            waitForIdle()
            onNodeWithText("Right · Low").performClick()
            waitForIdle()
            assertEquals(0, done, "a tap on the picker stays in the picker")
            onNodeWithTag(LAMP_PLACE_SCRIM_TAG).performTouchInput { click(androidx.compose.ui.geometry.Offset(10f, 10f)) }
            waitForIdle()
            assertEquals(1, done, "a tap on the dimmed page closes it")
            onNodeWithTag(LAMP_PLACE_SCRIM_TAG).assertDoesNotExist()
        }

    @Test
    fun `B-LAMP-K2-051 the real light - Low 196 dp above the bottom, a height change glides, a side change fades across`() =
        runDesktopComposeUiTest(400, 800) {
            mainClock.autoAdvance = false
            var place by mutableStateOf(KitLampPlace())
            k2Host(widthDp = 400, heightDp = 800) {
                KitLampAtPlace(place) { Box(Modifier.size(24.dp, 56.dp).testTag("light")) }
            }
            mainClock.advanceTimeBy(100)
            val low = onNodeWithTag("light").getUnclippedBoundsInRoot()
            assertTrue(abs(low.bottom.value - (800f - 196f)) < 0.6f, "Low: 196 dp above the bottom, was ${low.bottom}")
            assertTrue(abs(low.right.value - 400f) < 0.6f, "on the right edge")
            place = place.copy(height = KitLampHeight.High)
            mainClock.advanceTimeBy(64)
            val mid = onNodeWithTag("light").getUnclippedBoundsInRoot()
            mainClock.advanceTimeBy(600)
            val high = onNodeWithTag("light").getUnclippedBoundsInRoot()
            val highCentre = (high.top.value + high.bottom.value) / 2f
            assertTrue(abs(highCentre - (800f - 800f * 0.64f)) < 0.6f, "High: its centre at 64 % from the bottom, was $highCentre")
            assertTrue(mid.top < low.top && mid.top > high.top, "it glides along the edge")
            place = place.copy(side = KitLampSide.Start)
            mainClock.advanceTimeBy(600)
            val left = onNodeWithTag("light").getUnclippedBoundsInRoot()
            assertTrue(abs(left.left.value) < 0.6f, "it stands on the other edge, at the same height")
            assertTrue(abs(left.top.value - high.top.value) < 0.6f, "the height is kept across sides")
        }
}
