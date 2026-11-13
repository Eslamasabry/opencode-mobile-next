package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The edge light's 11 dp task mark (LAMP-CRAFT §1 `MarkSize.Mini`): its size, its word, a drawn check, a still dashed ring. */
@OptIn(ExperimentalTestApi::class)
class KitTaskMarkMiniTest {
    @Test
    fun `B-LAMP-K1-050 every state draws an 11 dp mark that carries its word, in dark and light`() {
        for (dark in listOf(true, false)) for (state in KitTaskState.entries) {
            runDesktopComposeUiTest(120, 120) {
                setContent {
                    KitTheme(KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light), FakeKitPlatform(), "en") {
                        Box(Modifier.size(60.dp)) {
                            KitTaskMark(state, Modifier.testTag("mark"), label = "word $state", size = KitMarkSize.Mini, progress = 0.5f)
                        }
                    }
                }
                waitForIdle()
                val b = onNodeWithTag("mark", useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(abs((b.right - b.left).value - 11f) < 0.5f && abs((b.bottom - b.top).value - 11f) < 0.5f, "$state: 11 x 11, was $b")
                onNodeWithContentDescription("word $state").assertExists()
            }
        }
    }

    @Test
    fun `B-LAMP-K1-051 a finished mark's check draws itself over CHECK_DRAW, and a dashed ring never moves`() =
        runDesktopComposeUiTest(120, 120) {
            mainClock.autoAdvance = false
            var state by mutableStateOf(KitTaskState.Working)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                    Box(Modifier.size(60.dp)) { KitTaskMark(state, size = KitMarkSize.Mini, progress = null) }
                }
            }
            mainClock.advanceTimeBy(500)
            val still = onRoot().captureToImage().toPixelMap().buffer.copyOf()
            mainClock.advanceTimeBy(2_000)
            assertContentEquals(still, onRoot().captureToImage().toPixelMap().buffer, "the dashed ring of unknown progress is still")
            state = KitTaskState.Done
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(80)
            val drawing = onRoot().captureToImage().toPixelMap().buffer.copyOf()
            mainClock.advanceTimeBy(500)
            val drawn = onRoot().captureToImage().toPixelMap().buffer
            assertFalse(drawing.contentEquals(drawn), "the check is still drawing 80 ms in")
        }
}
