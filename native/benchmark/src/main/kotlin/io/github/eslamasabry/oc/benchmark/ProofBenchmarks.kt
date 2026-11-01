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

/** Generates the start-up baseline profile (cold start to the first frame of the conversations screen). */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = androidx.benchmark.macro.junit4.BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PKG, includeInStartupProfile = true) { startActivityAndWait() }
}
