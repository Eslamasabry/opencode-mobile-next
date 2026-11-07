@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitToastHost
import io.github.eslamasabry.oc.ui.kit.modal.KitToastState
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.motion.KitDissolve
import io.github.eslamasabry.oc.ui.kit.motion.KitRollingNumber
import io.github.eslamasabry.oc.ui.kit.motion.KitStaggerRow
import io.github.eslamasabry.oc.ui.kit.motion.RollingRules
import io.github.eslamasabry.oc.ui.kit.state.KitStepProgress
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The owner's reference motion (2026-11-07) as kit parts: rolling digits, blur dissolve, step bars that fill, the toast pill and staggered faces. Each test drives the test clock by hand, records its frames to
 * build/motion-frames/<part>/ (16 ms apart, for the review GIFs) and proves the change is spread over frames at Full
 * and final on the next frame with Animations Off.
 */
class OwnerReferenceMotionTest {
    private val out = File(System.getProperty("user.dir"), "build/motion-frames")

    private fun ComposeUiTest.frame(): String {
        val img = onRoot().captureToImage().toAwtImage()
        val bytes = java.io.ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        return MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun ComposeUiTest.save(part: String, i: Int) {
        val dir = File(out, part).apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "%03d.png".format(i)))
    }

    /**
     * Shows [scene] at [level], flips it with [change], then: at Full the frame 48 ms in differs from the settled frame
     * (the change is spread over time) and the frames are recorded; at Off the second frame is already the settled one.
     */
    private fun motion(part: String, level: MotionLevel, change: () -> Unit, settleMs: Long = 2000, scene: @Composable () -> Unit) =
        runDesktopComposeUiTest(360, 240) {
            setContent {
                KitTheme(KitAppearance(mode = AppearanceMode.Dark, motion = level), FakeKitPlatform(), "en") {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(16.dp)) { scene() }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            change()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val early = frame()
            if (level == MotionLevel.Full) save(part, 0)
            var mid = early
            for (i in 1..30) {
                mainClock.advanceTimeBy(16)
                if (level == MotionLevel.Full) save(part, i)
                if (i == 3) mid = frame()
            }
            mainClock.advanceTimeBy(settleMs)
            val settled = frame()
            if (level == MotionLevel.Full) assertNotEquals(settled, mid, "$part: mid-move differs from the end")
            else assertEquals(settled, early, "$part: Off is final on the next frame")
        }

    // 1. Rolling digits
    private var count by mutableStateOf("1,284")

    private fun rolling(level: MotionLevel) =
        motion("rolling-number", level, { count = "1,317" }) { KitRollingNumber(count, role = TypeRole.LargeTitle) }

    @Test fun `REF-1 digits roll with a soft blur`() = rolling(MotionLevel.Full)

    @Test fun `REF-1 Off changes digits at once`() = rolling(MotionLevel.Off)

    @Test
    fun `REF-1 the roll direction follows the value`() {
        assertEquals(1284.0, RollingRules.valueOf("1,284"))
        assertEquals(0.42, RollingRules.valueOf("$0.42"))
        assertEquals(-3.0, RollingRules.valueOf("-3"))
        assertEquals(34.0, RollingRules.valueOf("34%"))
    }

    // 2. Blur dissolve
    private var loaded by mutableStateOf(false)

    private fun dissolve(level: MotionLevel) =
        motion("dissolve", level, { loaded = true }) {
            KitDissolve(loaded, Modifier.fillMaxWidth()) { ready ->
                Column {
                    if (ready) {
                        KitText("Results", role = TypeRole.Title)
                        KitText("Three files match")
                    } else Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Gray))
                }
            }
        }

    @Test fun `REF-2 old content blurs out as new content sharpens in`() = dissolve(MotionLevel.Full)

    @Test fun `REF-2 Off swaps at once`() = dissolve(MotionLevel.Off)

    // 3. Choice rows fill; the button turns solid
    private var picked by mutableStateOf(false)

    private fun choice(level: MotionLevel) =
        motion("choice-fill", level, { picked = true }) {
            Column {
                KitChoiceRow("Work & Projects", selected = picked, onClick = {})
                KitButton("Continue", onClick = {}, enabled = picked, disabledReason = "Pick one")
            }
        }

    @Test fun `REF-3 the chosen row fills and its dot springs in, the button turns solid`() = choice(MotionLevel.Full)

    @Test fun `REF-3 Off fills at once`() = choice(MotionLevel.Off)

    // 4. Step bars
    private var step by mutableStateOf(2)

    private fun steps(level: MotionLevel) =
        motion("step-progress", level, { step = 3 }) { KitStepProgress(steps = 6, done = step, label = "Step $step of 6") }

    @Test fun `REF-4 the next bar fills on a spring`() = steps(MotionLevel.Full)

    @Test fun `REF-4 Off fills at once`() = steps(MotionLevel.Off)

    // 5. Toast pill
    private fun toast(level: MotionLevel) {
        var show: (() -> Unit)? = null
        motion("toast", level, { show?.invoke() }, settleMs = 600) {
            val toasts = remember { KitToastState() }
            show = { toasts.show("Copied", KitIcons.check) }
            KitToastHost(toasts)
        }
    }

    @Test fun `REF-5 the toast drops in from the top`() = toast(MotionLevel.Full)

    @Test fun `REF-5 Off shows the toast at once`() = toast(MotionLevel.Off)

    // 6. Staggered faces
    private var faces by mutableStateOf(listOf<Int>())

    private fun stagger(level: MotionLevel) =
        motion("stagger", level, { faces = listOf(0, 1, 2, 3, 4) }) {
            KitStaggerRow(faces, keyOf = { it }) { i ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF3B82F6 + i * 0x101010)))
            }
        }

    @Test fun `REF-6 faces arrive one after another`() = stagger(MotionLevel.Full)

    @Test fun `REF-6 Off shows every face at once`() = stagger(MotionLevel.Off)
}
