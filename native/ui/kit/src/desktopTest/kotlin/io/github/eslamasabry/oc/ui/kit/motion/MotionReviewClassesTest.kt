package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Motion review 2026-11-09 (docs/native/qa/motion-review-2026-11-09): one test per kit class it fixed, each read
 * mid-animation on the paused clock. Each also writes its frames to build/motion-review-frames for the review's
 * before and after strips; the asserts are the behaviour.
 */
@OptIn(ExperimentalTestApi::class)
class MotionReviewClassesTest {
    private val out = File(System.getProperty("user.dir"), "build/motion-review-frames").apply { mkdirs() }

    private fun ComposeUiTest.capture(name: String) =
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))

    private fun ComposeUiTest.frames(name: String, block: () -> Unit) {
        mainClock.autoAdvance = false
        block()
        var t = 0L
        for (at in FRAME_TIMES) {
            mainClock.advanceTimeBy(at - t)
            t = at
            capture("${name}_${at}ms")
        }
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Composable
    private fun Sheets(open: Boolean, height: KitSheetHeight, rtl: Boolean = false, level: MotionLevel = MotionLevel.Full) {
        KitTheme(KitAppearance(motion = level), FakeKitPlatform(), if (rtl) "ar" else "en") {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                KitSheetHost(Modifier.fillMaxSize()) {
                    KitSheet(visible = open, onDismissRequest = {}, title = "Files", height = height) { KitText(BODY) }
                }
            }
        }
    }

    @Test
    fun `MOT-RTL-1 in Arabic the end sheet comes in from its own (left) edge`() =
        runDesktopComposeUiTest(WIDE_W, WIDE_H) {
            var open by mutableStateOf(true)
            setContent { Sheets(open, KitSheetHeight.Full, rtl = true) }
            waitForIdle()
            val rest = onNodeWithText(BODY).getBoundsInRoot().left
            open = false
            waitForIdle()
            frames("end_sheet_rtl") { open = true }
            // Re-open and read one frame mid-way: the panel is still short of its place on the start side.
            open = false
            waitForIdle()
            mainClock.autoAdvance = false
            open = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            val mid = onNodeWithText(BODY).getBoundsInRoot().left
            assertTrue(mid < rest, "mid-open the panel comes from the left edge ($mid vs rest $rest)")
        }

    @Test
    fun `MOT-MODAL-1 a centred sheet fades and rises, never grows from a smaller size`() =
        runDesktopComposeUiTest(WIDE_W, WIDE_H) {
            var open by mutableStateOf(true)
            setContent { Sheets(open, KitSheetHeight.Content) }
            waitForIdle()
            val rest = onNodeWithText(BODY).getBoundsInRoot()
            open = false
            waitForIdle()
            frames("centred_sheet") { open = true }
            open = false
            waitForIdle()
            mainClock.autoAdvance = false
            open = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            val mid = onNodeWithText(BODY).getBoundsInRoot()
            assertEquals(rest.right - rest.left, mid.right - mid.left, "MOT-2: the panel keeps its size while it arrives")
        }

    @Test
    fun `MOT-CALM-1 under Calm the undo bar fades in place, nothing travels`() =
        runDesktopComposeUiTest(PHONE_W, PHONE_H) {
            var offer by mutableStateOf<KitUndoOffer?>(OFFER)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Calm), FakeKitPlatform(), "en") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        KitUndoBar(offer, onUndo = {}, onTimeout = {})
                    }
                }
            }
            waitForIdle()
            val rest = onNodeWithText(OFFER.message).getBoundsInRoot().top
            offer = null
            waitForIdle()
            frames("undo_calm") { offer = OFFER }
            offer = null
            waitForIdle()
            mainClock.autoAdvance = false
            offer = OFFER
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(rest, onNodeWithText(OFFER.message).getBoundsInRoot().top, "Calm: the bar does not rise")
        }

    private companion object {
        const val WIDE_W = 1000
        const val WIDE_H = 700
        const val PHONE_W = 412
        const val PHONE_H = 860
        const val BODY = "Sheet body words"
        val OFFER = KitUndoOffer("u1", "Conversation archived")
        val FRAME_TIMES = listOf(16L, 48L, 80L, 112L, 160L, 208L, 256L, 320L)
    }
}
