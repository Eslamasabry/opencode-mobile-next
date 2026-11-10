package io.github.eslamasabry.oc.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val GLASS = "io.github.eslamasabry.opencode_mobile.proof.GlassProofActivity"

/**
 * Smoothness (docs/native/qa/smoothness-2026-11-09): frame timing of one self-scrolling kit page in each look, release
 * build (R8). The same frames on every build, so two builds compare on one device.
 */
@RunWith(AndroidJUnit4::class)
class GlassBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun scroll(look: String, scene: String, liquid: String = "") =
        rule.measureRepeated(
            packageName = PKG_NATIVE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = null,
            iterations = 3,
            setupBlock = {
                // Test Lab devices run with animations off; the owner's phone does not.
                device.executeShellCommand("settings put global animator_duration_scale 1")
                device.executeShellCommand("settings put global transition_animation_scale 1")
                device.executeShellCommand("settings put global window_animation_scale 1")
                killProcess()
                startActivityAndWait(
                    Intent().setClassName(PKG_NATIVE, GLASS).putExtra("look", look).putExtra("scene", scene).putExtra("liquid", liquid),
                )
                check(device.wait(Until.hasObject(By.text("Ready")), 30_000)) { "not ready" }
                Thread.sleep(1_500) // let the page settle (first frames, the bar's liquid face)
            },
        ) {
            device.findObject(By.text("Start")).click()
            check(device.wait(Until.hasObject(By.text("Done")), 60_000)) { "not done" }
        }

    @Test fun listSolid() = scroll("solid", "list")

    @Test fun listGlass() = scroll("glass", "list")

    @Test fun listGlassAurora() = scroll("glass-aurora", "list")

    @Test fun listGlassDrift() = scroll("glass-drift", "list")

    @Test fun chatSolid() = scroll("solid", "chat")

    @Test fun chatGlass() = scroll("glass", "chat")

    @Test fun chatGlassAurora() = scroll("glass-aurora", "chat")

    /** The owner's report (2026-11-09): a reply's ⋯ menu opening over the chat in Glass. */
    @Test fun menuGlassAurora() = scroll("glass-aurora", "menu")

    @Test fun menuGlass() = scroll("glass", "menu")

    @Test fun menuSolid() = scroll("solid", "menu")

    /** Smoothness round 2: a page pushed over the chat and popped (two framed pages, each with its own ground, at once). */
    @Test fun pushGlassAurora() = scroll("glass-aurora", "push")

    @Test fun pushGlass() = scroll("glass", "push")

    /** The same paths with liquid panes at full resolution (the library's own behaviour): fix 1's before, same build. */
    @Test fun chatGlassAuroraLiquidFull() = scroll("glass-aurora", "chat", liquid = "full")

    @Test fun chatGlassLiquidFull() = scroll("glass", "chat", liquid = "full")

    @Test fun menuGlassLiquidFull() = scroll("glass", "menu", liquid = "full")

    @Test fun menuGlassAuroraLiquidFull() = scroll("glass-aurora", "menu", liquid = "full")

    /**
     * Joined glass (docs/designs/liquid-merge-one-body.md section 5): the chat scrolling with the jump pill joined to the
     * message box (a steady cost while the reader is scrolled up), and the bar's menu joined to the bar, opened and
     * closed eight times. Pass: the median run's share of frames more than one vsync late rises by at most 1 point and
     * frame overrun P90 by at most 0.5 ms against chatGlass / menuGlass on the commit before the join.
     */
    @Test fun joinPillGlass() = scroll("glass", "join-pill")

    @Test fun joinPillGlassAurora() = scroll("glass-aurora", "join-pill")

    @Test fun joinMenuGlass() = scroll("glass", "join-menu")

    @Test fun joinMenuGlassAurora() = scroll("glass-aurora", "join-menu")
}

private const val PKG_NATIVE = "io.github.eslamasabry.opencode_mobile.native"
