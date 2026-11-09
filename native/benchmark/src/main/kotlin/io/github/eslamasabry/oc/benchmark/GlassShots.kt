package io.github.eslamasabry.oc.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

private const val SHOT_ACTIVITY = "io.github.eslamasabry.opencode_mobile.native/io.github.eslamasabry.opencode_mobile.proof.GlassProofActivity"

/**
 * Smoothness round 2 (docs/native/qa/smoothness-2026-11-09): still screenshots of the proof page, so two builds (or the
 * two settings of one build) are compared by eye at the same scale on the same device. Each lands in
 * `additionalTestOutputDir` as `<name>.png`.
 */
@RunWith(AndroidJUnit4::class)
class GlassShots {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shot(name: String, look: String, scene: String, extras: String = "", start: Boolean = false) {
        device.executeShellCommand("settings put global animator_duration_scale 1")
        device.executeShellCommand("am force-stop io.github.eslamasabry.opencode_mobile.native")
        device.executeShellCommand("am start -W -n $SHOT_ACTIVITY --es look $look --es scene $scene $extras")
        check(device.wait(Until.hasObject(By.text("Ready")), 30_000)) { "not ready" }
        Thread.sleep(2_000)
        if (start) {
            device.findObject(By.text("Start")).click()
            check(device.wait(Until.hasObject(By.text("Done")), 30_000)) { "not done" }
            Thread.sleep(500)
        }
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: "/sdcard/Download"
        val file = File(dir, "$name.png")
        check(device.takeScreenshot(file)) { "no screenshot $file" }
    }

    @Test fun chatGlass() = shot("chat-glass", "glass", "chat")

    @Test fun chatGlassFull() = shot("chat-glass-liquid-full", "glass", "chat", "--es liquid full")

    @Test fun chatGlassDrift() = shot("chat-glass-drift", "glass-drift", "chat")

    @Test fun chatGlassDriftFull() = shot("chat-glass-drift-liquid-full", "glass-drift", "chat", "--es liquid full")

    @Test fun menuGlass() = shot("menu-glass", "glass", "menu", "--ez hold true", start = true)

    @Test fun menuGlassFull() = shot("menu-glass-liquid-full", "glass", "menu", "--ez hold true --es liquid full", start = true)

    @Test fun menuSolid() = shot("menu-solid", "solid", "menu", "--ez hold true", start = true)
}
