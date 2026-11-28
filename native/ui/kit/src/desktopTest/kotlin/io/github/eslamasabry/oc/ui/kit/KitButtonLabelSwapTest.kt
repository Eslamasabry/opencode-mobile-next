package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A KitButton whose action opts in with `swapLabels` cross-fades its words at Full and swaps them instantly at Off; a
 * button that did not opt in changes its words at once, as every caller had before.
 */
@OptIn(ExperimentalTestApi::class)
class KitButtonLabelSwapTest {
    private val width = 412
    private val height = 120

    @Composable
    private fun Scene(motion: MotionLevel, label: String, optIn: Boolean = true) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KitTheme(
                KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = motion),
                FakeKitPlatform(),
                "en",
                stillFrame = true,
            ) {
                InWindow(width, height) {
                    Box(
                        Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KitButton(
                            action = KitAction(label, {}, swapLabels = if (optIn) SWAP_LABELS else emptyList()),
                            modifier = Modifier.fillMaxWidth(),
                            role = KitButtonRole.Secondary,
                        )
                    }
                }
            }
        }
    }

    private fun changedPixels(a: PixelMap, b: PixelMap): Int {
        var diff = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pa = a[x, y]
                val pb = b[x, y]
                if (abs(pa.red - pb.red) + abs(pa.green - pb.green) + abs(pa.blue - pb.blue) > PIXEL_EPS) diff++
            }
        }
        return diff
    }

    @Test
    fun `B-KIT-011 KitButton cross-fades its label at Full while semantics switches immediately`() =
        runDesktopComposeUiTest(width, height) {
            var label by mutableStateOf("Not now")
            setContent { Scene(MotionLevel.Full, label) }
            waitForIdle()
            val before = onRoot().captureToImage().toPixelMap()

            mainClock.autoAdvance = false
            label = "Keep these versions"
            repeat(MID_SWAP_FRAMES) { mainClock.advanceTimeByFrame() }
            val mid = onRoot().captureToImage().toPixelMap()
            onNodeWithText("Keep these versions").assertExists()
            onNodeWithText("Not now").assertDoesNotExist()

            mainClock.autoAdvance = true
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            val settled = onRoot().captureToImage().toPixelMap()

            assertTrue(changedPixels(before, mid) > 0, "expected mid-swap frame to differ from before")
            assertTrue(
                changedPixels(mid, settled) > 0,
                "expected mid-swap frame to differ from settled (hard cut instead of cross-fade)",
            )
        }

    @Test
    fun `B-KIT-011 KitButton swaps its label instantly under Off`() =
        runDesktopComposeUiTest(width, height) {
            var label by mutableStateOf("Not now")
            setContent { Scene(MotionLevel.Off, label) }
            waitForIdle()

            mainClock.autoAdvance = false
            label = "Keep these versions"
            mainClock.advanceTimeByFrame()
            val afterOne = onRoot().captureToImage().toPixelMap()

            mainClock.autoAdvance = true
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            val settled = onRoot().captureToImage().toPixelMap()

            assertEquals(0, changedPixels(afterOne, settled), "expected instant label swap under MotionLevel.Off")
        }

    @Test
    fun `B-KIT-011 a button that did not opt in changes its words at once at Full`() =
        runDesktopComposeUiTest(width, height) {
            var label by mutableStateOf("Not now")
            setContent { Scene(MotionLevel.Full, label, optIn = false) }
            waitForIdle()

            mainClock.autoAdvance = false
            label = "Keep these versions"
            mainClock.advanceTimeByFrame()
            val afterOne = onRoot().captureToImage().toPixelMap()
            onNodeWithText("Keep these versions").assertExists()
            onNodeWithText("Not now").assertDoesNotExist()

            mainClock.autoAdvance = true
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            val settled = onRoot().captureToImage().toPixelMap()

            assertEquals(
                0,
                changedPixels(afterOne, settled),
                "expected a plain cut: a button without swapLabels must not cross-fade its words",
            )
        }

    private companion object {
        const val MID_SWAP_FRAMES = 4
        const val SETTLE_MS = 400L
        const val PIXEL_EPS = 0.02f
        val SWAP_LABELS = listOf("Not now", "Keep these versions")
    }
}
