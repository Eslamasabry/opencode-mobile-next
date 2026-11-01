package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerGlow
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStop
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStripReveal
import io.github.eslamasabry.oc.ui.kitchat.composer.KitGlowChoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopAndSendPill
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopState
import io.github.eslamasabry.oc.ui.kitchat.turn.KitLiveSweep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S3-COMPMOTION key frames (plan 08 §5.2, §5.3, §9.4) under Robolectric native graphics, 412 × 915 dp at 420 dpi.
 * Loops draw their still frame (`stillFrame`: the Classic highlight at phase 0.57, centred on the top edge; the live
 * sweep at mid-pass); transitions are captured at fixed times on the paused test clock. Record:
 * `:ui:kit-chat:recordRoborazziAndroidHostTest`; verify: `:ui:kit-chat:verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class ComposerMotionGoldensTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        level: MotionLevel = MotionLevel.Full,
        dark: Boolean = true,
        saver: Boolean = false,
        stillFrame: Boolean = true,
        localeTag: String = "en",
        content: @Composable () -> Unit,
    ) {
        val mode = if (saver) AppearanceMode.Saver else if (dark) AppearanceMode.Dark else AppearanceMode.Light
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = mode, motion = level),
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                localeTag = localeTag,
                stillFrame = stillFrame,
            ) {
                Box(Modifier.testTag(SCENE).background(LocalKitColors.current.ground).padding(24.dp)) { content() }
            }
        }
    }

    private fun capture(name: String) = compose.onNodeWithTag(SCENE).captureRoboImage("src/screenshots/ComposerMotion/$name.png")

    @Composable
    private fun Pill() {
        val colors = LocalKitColors.current
        Box(
            Modifier.size(320.dp, 56.dp).background(colors.surface2, RoundedCornerShape(26.dp)).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) { KitText("Message", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame) }
    }

    private fun glow(choice: KitGlowChoice, level: MotionLevel, dark: Boolean, saver: Boolean = false, name: String) {
        show(level = level, dark = dark, saver = saver) { KitComposerGlow(active = true, choice = choice) { Pill() } }
        compose.waitForIdle()
        capture(name)
    }

    @Test fun glowClassicDark() = glow(KitGlowChoice.Classic, MotionLevel.Full, dark = true, name = "glow_classic_dark_phase057")

    @Test fun glowClassicLight() = glow(KitGlowChoice.Classic, MotionLevel.Full, dark = false, name = "glow_classic_light_phase057")

    @Test fun glowSoftDark() = glow(KitGlowChoice.Soft, MotionLevel.Full, dark = true, name = "glow_soft_dark_phase008")

    @Test fun glowSoftLight() = glow(KitGlowChoice.Soft, MotionLevel.Full, dark = false, name = "glow_soft_light_phase008")

    @Test fun glowCalm() = glow(KitGlowChoice.Classic, MotionLevel.Calm, dark = true, name = "glow_calm")

    @Test fun glowSaverHairline() = glow(KitGlowChoice.Classic, MotionLevel.Full, dark = true, saver = true, name = "glow_saver_hairline")

    private fun stripFrames(level: MotionLevel, withChips: Boolean, times: List<Long>, name: (Long) -> String) {
        var running by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        show(level = level) {
            Box(Modifier.width(360.dp)) {
                KitComposerStripReveal(
                    stop = if (running) ({ KitComposerStop("Stop", "Stop Claude Code", onStop = {}) }) else null,
                    chips = if (withChips) ({ KitChip("Sonnet 5.5") }) else null,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        running = true
        compose.mainClock.advanceTimeByFrame() // the frame that starts the transition
        var at = 0L
        for (t in times) {
            compose.mainClock.advanceTimeBy(t - at)
            at = t
            capture(name(t))
        }
    }

    @Test fun stopChipArriveFull() = stripFrames(MotionLevel.Full, true, listOf(0L, 75L, 150L)) { "stop_chip_arrive_${it}ms_full" }

    @Test fun stopChipArriveCalm() = stripFrames(MotionLevel.Calm, true, listOf(0L, 75L, 150L)) { "stop_chip_arrive_${it}ms_calm" }

    @Test fun stopStripReveal() = stripFrames(MotionLevel.Full, false, listOf(0L, 125L, 250L)) { "stop_strip_reveal_${it}ms" }

    @Test
    fun stopChipStuck() {
        show { KitComposerStop("Stop", "Stop Claude Code", onStop = {}, state = KitStopState.Stuck) }
        compose.waitForIdle()
        capture("stop_chip_stuck_amber")
    }

    @Test
    fun stopChipWorking() {
        show { KitComposerStop("Stop", "Stop Claude Code", onStop = {}, state = KitStopState.Working, workingReason = "Stopping") }
        compose.waitForIdle()
        capture("stop_chip_working")
    }

    @Test
    fun stopAndSendPillLtr() {
        show { KitStopAndSendPill("Stop and send", onClick = {}) }
        compose.waitForIdle()
        capture("stop_and_send_pill_ltr")
    }

    @Test
    fun stopAndSendPillRtl() {
        show(localeTag = "ar") { KitStopAndSendPill("إيقاف وإرسال", onClick = {}) }
        compose.waitForIdle()
        capture("stop_and_send_pill_rtl")
    }

    private fun sweep(localeTag: String, words: String, name: String) {
        show(localeTag = localeTag) {
            KitLiveSweep(working = true) { KitText(words, role = TypeRole.Body, kind = KitTextKind.Frame) }
        }
        compose.waitForIdle()
        capture(name)
    }

    @Test fun liveSweepLtr() = sweep("en", "Working · reading 3 files", "live_sweep_mid_ltr")

    @Test fun liveSweepRtl() = sweep("ar", "يعمل · يقرأ ٣ ملفات", "live_sweep_mid_rtl")

    private companion object {
        const val SCENE = "scene"
    }
}
