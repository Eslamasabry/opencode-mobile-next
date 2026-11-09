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

    private fun scroll(look: String, scene: String) =
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
                startActivityAndWait(Intent().setClassName(PKG_NATIVE, GLASS).putExtra("look", look).putExtra("scene", scene))
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
}

private const val PKG_NATIVE = "io.github.eslamasabry.opencode_mobile.native"
