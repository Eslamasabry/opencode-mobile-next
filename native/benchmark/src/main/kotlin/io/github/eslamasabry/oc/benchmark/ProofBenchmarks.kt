package io.github.eslamasabry.oc.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PKG = "io.github.eslamasabry.opencode_mobile.native"
private const val PROOF = "io.github.eslamasabry.opencode_mobile.proof.StreamProofActivity"

/** Slice-0 P3: frame timing while the transcript streams, release build (R8), emulator numbers are indicative only. */
@RunWith(AndroidJUnit4::class)
class StreamBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun stream(scenario: String, timeoutMs: Long) =
        rule.measureRepeated(
            packageName = PKG,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = null, // COLD would kill the app after setupBlock; the stream is measured in a running app
            iterations = 3,
            setupBlock = {
                killProcess()
                startActivityAndWait(Intent().setClassName(PKG, PROOF).putExtra("scenario", scenario))
                check(device.wait(Until.hasObject(By.text("Ready")), 120_000)) { "not ready" }
            },
        ) {
            device.findObject(By.text("Start")).click()
            check(device.wait(Until.hasObject(By.text("Done")), timeoutMs)) { "not done" }
        }

    /** The recorded 5,004-part OC2 stream: history loaded, then its last 20 s streamed live. */
    @Test fun history5000() = stream("history5000", 90_000)

    /** The 6,000-word reply with the open 400-line fence (the JVM over-budget case). */
    @Test fun stream6000() = stream("stream6000", 240_000)
}

/** Start-up: release cold start of the real launcher activity, without and with the baseline profile. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun startup(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = PKG,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) { startActivityAndWait() }

    @Test fun startupNone() = startup(CompilationMode.None())

    @Test fun startupBaselineProfile() = startup(CompilationMode.Partial(androidx.benchmark.macro.BaselineProfileMode.Require))
}

/**
 * Generates the baseline profile and the startup profile (dex layout): cold start to the first frame, then a short walk
 * through what any install shows: scroll the home, open the first few things on screen and come back. It needs no server
 * and no saved account (the walk adapts to what is on screen: the welcome on a fresh install, the conversations on a
 * connected one). Regenerate with `tool/qa/device-bench.sh profile <out>` then `tool/qa/apply-profile.sh <out>`.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = androidx.benchmark.macro.junit4.BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PKG, includeInStartupProfile = true, maxIterations = 4, stableIterations = 2) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            walk()
        }

    /** The screen changes under UiAutomator while it works; a stale node only means "look again". */
    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.walk() {
        // Fling the first scrollable (home list, welcome pages), both ways.
        repeat(2) { n ->
            safely {
                device.findObject(By.scrollable(true))?.fling(
                    if (n == 0) androidx.test.uiautomator.Direction.DOWN else androidx.test.uiautomator.Direction.UP
                )
            }
            device.waitForIdle()
        }
        // Open up to four distinct clickable things and come back; stay inside the app.
        val seen = HashSet<String>()
        repeat(4) {
            safely {
                val target =
                    device.findObjects(By.clickable(true).pkg(PKG)).firstOrNull { o ->
                        val key = (o.contentDescription ?: o.text ?: "") + "@" + o.visibleBounds.toShortString()
                        o.visibleBounds.height() > 0 && seen.add(key)
                    }
                target?.click()
            }
            device.waitForIdle()
            safely { device.findObject(By.scrollable(true))?.fling(androidx.test.uiautomator.Direction.DOWN) }
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
            if (device.currentPackageName != PKG) startActivityAndWait()
        }
    }
}
