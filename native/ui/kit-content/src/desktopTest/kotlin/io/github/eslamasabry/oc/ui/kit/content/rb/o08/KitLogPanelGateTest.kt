package io.github.eslamasabry.oc.ui.kit.content.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogBuffer
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogEnd
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLevel
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLine
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanelFold
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogSize
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_log_panel_golden_test.dart: the panel in a Scaffold's SafeArea with a 16 dp
 * padding, aligned top-centre (or filling it), with the Dart test's deterministic setup output.
 */
@OptIn(ExperimentalTestApi::class)
class KitLogPanelGateTest {
    /** Deterministic setup output with one warning and one error line (kit_log_panel_golden_test.dart:26-57). */
    private fun setupLines(extra: Int = 0): KitLogBuffer = KitLogBuffer().apply {
        appendText("[oc] Checking the Linux environment\n[oc] Ubuntu 24.04 found\n[oc] Installing opencode 2.0.10\n")
        add(KitLogLine("npm warn deprecated inflight@1.0.6", KitLogLevel.Warning))
        appendText("added 212 packages in 14s\n[oc] Starting the server on :4097\n")
        add(KitLogLine("listen EADDRINUSE: address in use", KitLogLevel.Error))
        appendText("[oc] Retrying on :4098\n[oc] Server is ready\n")
        for (i in 0 until extra) add(KitLogLine("[oc] step ${i + 1} of $extra done"))
    }

    private fun scene(fill: Boolean = false, panel: @Composable () -> Unit): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = if (fill) Alignment.TopStart else Alignment.TopCenter) { panel() }
    }

    private fun shot(name: String, fill: Boolean = false, act: (ComposeUiTest.() -> Unit)? = null, panel: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, Frame.Screen, act = act, content = scene(fill, panel))
    }

    @Test fun empty() = shot("kit_log_panel_empty") { KitLogPanel(remember { KitLogBuffer() }, live = true) }

    @Test fun live() = shot("kit_log_panel_live") { KitLogPanel(remember { setupLines() }, live = true) }

    @Test fun quiet() = shot("kit_log_panel_quiet", act = { mainClock.advanceTimeBy(8_000) }) { KitLogPanel(remember { setupLines() }, live = true) }

    @Test fun ended_failed() = shot("kit_log_panel_ended_failed") {
        KitLogPanel(remember { setupLines() }, ended = KitLogEnd(exitCode = 1, failed = true, reason = "The server could not start."))
    }

    @Test fun read_failed() = shot("kit_log_panel_read_failed", act = { mainClock.advanceTimeBy(2_000) }) {
        KitLogPanel(remember { setupLines() }, live = true, onRefresh = { error("gone") })
    }

    @Test fun scrolled_up() {
        var buffer: KitLogBuffer? = null // each render (light, dark) gets its own buffer
        shot("kit_log_panel_scrolled_up", act = {
            // Up by eight whole lines (8 × 20 dp) from the end: line 19 at the top.
            onNodeWithTag("kit-log-lines").performScrollToIndex(19)
            waitForIdle()
            for (i in 0 until 12) buffer!!.add(KitLogLine("[oc] new step ${i + 1}"))
        }) { KitLogPanel(remember { setupLines(extra = 30).also { buffer = it } }, live = true, wrap = false) }
    }

    @Test fun failed_report() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_log_panel_failed_report$size") {
                KitLogPanel(remember { setupLines() }, ended = KitLogEnd(exitCode = 1, failed = true), headerAction = KitAction("Copy failure report", {}, icon = KitIcons.copy))
            }
        }
    }

    @Test fun dropped() = shot("kit_log_panel_dropped") {
        val buffer = remember {
            KitLogBuffer(capacity = 10).apply { for (i in 0 until 1250) add(KitLogLine("[oc] indexed file ${i + 1}")) }
        }
        KitLogPanel(buffer, ended = KitLogEnd(exitCode = 0))
    }

    @Test fun fold_open() = shot("kit_log_panel_fold_open", act = { onNodeWithText("Show output").performSemanticsAction(SemanticsActions.OnClick) }) {
        Box(Modifier.verticalScroll(rememberScrollState())) { KitLogPanelFold(remember { setupLines() }, live = true) }
    }

    @Test fun live_wide() = shot("kit_log_panel_live_1280x800", fill = true) {
        KitLogPanel(remember { setupLines(extra = 10) }, live = true, size = KitLogSize.Fill)
    }

    @Test fun live_text2() {
        for (size in listOf("", "_1280x800")) shot("kit_log_panel_live_text2$size") { KitLogPanel(remember { setupLines() }, live = true) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_log_panel",
            caps = mapOf("KitLogPanel" to "list"),
            content = { KitLogPanel(remember { setupLines() }, live = true) },
        )
    }
}
