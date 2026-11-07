package io.github.eslamasabry.oc.ui.kit.content.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogBuffer
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogEnd
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLevel
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLine
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_log_panel_golden_test.dart, with the Dart test's exact inputs and framing. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitLogPanelGateTest {
    /** Deterministic setup output with one warning and one error line (Dart `_setupLines`). */
    private fun setupLines(extra: Int = 0): KitLogBuffer =
        KitLogBuffer().apply {
            appendText("[oc] Checking the Linux environment\n[oc] Ubuntu 24.04 found\n[oc] Installing opencode 2.0.10\n")
            add(KitLogLine("npm warn deprecated inflight@1.0.6", KitLogLevel.Warning))
            appendText("added 212 packages in 14s\n[oc] Starting the server on :4097\n")
            add(KitLogLine("listen EADDRINUSE: address in use", KitLogLevel.Error))
            appendText("[oc] Retrying on :4098\n[oc] Server is ready\n")
            for (i in 0 until extra) add(KitLogLine("[oc] step ${i + 1} of $extra done"))
        }

    /**
     * The Dart `_shot` frame: the panel 16 dp in, at the top centre (or filling the body). The part frame already
     * holds the 16 dp above, so the scene adds the sides (and, for a filled panel, the body's height: the window less
     * 16 dp above and below). A lone part, so the gate reads it with the part rules, not a screen's page columns.
     */
    private fun frame(fill: Boolean = false, height: Int = 0, panel: @Composable () -> Unit): @Composable () -> Unit = {
        // The body is the window less 16 dp on each side (wider than the part frame's 720 dp on a wide window).
        val window = LocalKitWindow.current
        val body = Modifier.requiredWidth((window.widthDp - 32).dp)
        val sized = if (fill) body.height((window.heightDp - 32).dp) else body
        Box(sized, contentAlignment = if (fill) Alignment.TopStart else Alignment.TopCenter) { panel() }
    }

    private fun shot(name: String, fill: Boolean = false, height: Int = 0, act: (ComposeUiTest.() -> Unit)? = null, panel: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Part, act = act, content = frame(fill, height, panel))
    }

    private val live: @Composable () -> Unit = { KitLogPanel(remember { setupLines() }, live = true) }

    private val failedReport: @Composable () -> Unit = {
        KitLogPanel(
            remember { setupLines() },
            ended = KitLogEnd(exitCode = 1, failed = true),
            headerAction = KitAction("Copy failure report", onClick = {}, icon = KitIcons.copy),
        )
    }

    @Test fun kit_log_panel_empty() = shot("kit_log_panel_empty") { KitLogPanel(remember { KitLogBuffer() }, live = true) }

    @Test fun kit_log_panel_live() = shot("kit_log_panel_live", panel = live)

    @Test fun kit_log_panel_quiet() = shot("kit_log_panel_quiet", act = { mainClock.advanceTimeBy(8_000) }, panel = live)

    @Test fun kit_log_panel_ended_failed() = shot("kit_log_panel_ended_failed") {
        KitLogPanel(remember { setupLines() }, ended = KitLogEnd(exitCode = 1, failed = true, reason = "The server could not start."))
    }

    @Test fun kit_log_panel_read_failed() = shot("kit_log_panel_read_failed", act = { mainClock.advanceTimeBy(1_000) }) {
        KitLogPanel(remember { setupLines() }, live = true, onRefresh = { false })
    }

    private var scrolled: KitLogBuffer? = null

    @Test fun kit_log_panel_scrolled_up() = shot(
        "kit_log_panel_scrolled_up",
        act = {
            // Up by whole lines (8 × 20 dp), then twelve new lines arrive.
            onNodeWithTag("kit-log-lines").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -160f) }
            waitForIdle()
            runOnIdle { for (i in 0 until 12) scrolled!!.add(KitLogLine("[oc] new step ${i + 1}")) }
        },
    ) {
        KitLogPanel(remember { setupLines(extra = 30).also { scrolled = it } }, live = true, wrap = false)
    }

    @Test fun kit_log_panel_failed_report() = shot("kit_log_panel_failed_report", panel = failedReport)

    @Test fun kit_log_panel_failed_report_1280x800() = shot("kit_log_panel_failed_report_1280x800", panel = failedReport)

    @Test fun kit_log_panel_dropped() = shot("kit_log_panel_dropped") {
        val buffer = remember {
            KitLogBuffer(capacity = 10).apply { for (i in 0 until 1250) add(KitLogLine("[oc] indexed file ${i + 1}")) }
        }
        KitLogPanel(buffer, ended = KitLogEnd(exitCode = 0))
    }

    @Test fun kit_log_panel_live_1280x800() = shot("kit_log_panel_live_1280x800", fill = true, height = 800) {
        KitLogPanel(remember { setupLines(extra = 10) }, live = true, size = KitLogSize.Fill)
    }

    @Test fun kit_log_panel_live_text2() = shot("kit_log_panel_live_text2", panel = live)

    @Test fun kit_log_panel_live_text2_1280x800() = shot("kit_log_panel_live_text2_1280x800", panel = live)

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_log_panel", caps = mapOf("KitLogPanel" to "list"), content = live)
    }
}
