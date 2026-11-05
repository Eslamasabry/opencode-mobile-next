package io.github.eslamasabry.oc.ui.kitchat.rb.o17

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_step_light_golden_test.dart: three tool rows, the step under test in the middle
 * with a finished step above and below it, 12 dp from the window's edge. The Flutter shots draw them inside a
 * KitStepTimeline (order 15), which has no native file yet, so the rows stand alone here.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepLightGateTest {
    private fun view(id: String, kind: KitToolKind, title: String, subject: String?, detail: String?, status: KitToolStatus) =
        KitToolView(id, kind, title, subject, detail, status, null, null, null, "")

    private fun timeline(status: KitToolStatus, first: Boolean = false, last: Boolean = false): @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) { // F: test/goldens/kit/kit_step_light_golden_test.dart:73
            if (!first) Row(view("a", KitToolKind.Read, "Read", "lib/a.dart", null, KitToolStatus.Done))
            Row(view("b", KitToolKind.Shell, "Shell", null, "flutter test", status))
            if (!last) Row(view("c", KitToolKind.Edit, "Edit", "lib/b.dart", null, KitToolStatus.Done))
        }
    }

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() } }

    @Composable
    private fun Row(view: KitToolView) = KitToolRow(view, opened = false, onToggle = {})

    private fun shot(name: String, status: KitToolStatus, motion: MotionLevel = MotionLevel.Full, first: Boolean = false, last: Boolean = false, scale: Float? = null, act: (ComposeUiTest.() -> Unit)? = null) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, wrap = wrap, motion = motion, textScale = scale, act = act, content = timeline(status, first, last))
    }

    private fun lap(name: String, ms: Long) {
        // The gate settles 1,200 ms before the act; the lap is 2,000 ms (KitMotion.stepLap).
        shot(name, KitToolStatus.Running, act = { mainClock.advanceTimeBy(((ms - SETTLE) % LAP + LAP) % LAP) })
    }

    @Test fun running_laps() {
        lap("kit_step_glow_running_t10", 200)
        lap("kit_step_glow_running_t40", 800)
        lap("kit_step_glow_running_t55", 1100)
        lap("kit_step_glow_running_t80", 1600)
    }

    @Test fun still_looks() {
        shot("kit_step_glow_reduced", KitToolStatus.Running, MotionLevel.Off)
        shot("kit_step_glow_queued", KitToolStatus.Pending, MotionLevel.Off)
        shot("kit_step_glow_waiting", KitToolStatus.Pending, MotionLevel.Off)
        shot("kit_step_glow_done", KitToolStatus.Done, MotionLevel.Off)
        shot("kit_step_glow_failed", KitToolStatus.Failed, MotionLevel.Off)
        shot("kit_step_glow_stopped", KitToolStatus.Stopped, MotionLevel.Off)
    }

    @Test fun first_and_last() {
        lap("kit_step_glow_first_t40", 800)
        lap("kit_step_glow_last_t40", 800)
    }

    @Test fun waiting_background_stalled() {
        shot("kit_step_glow_waiting_breathe", KitToolStatus.Pending)
        shot("kit_step_glow_background_t40", KitToolStatus.Running)
        shot("kit_step_glow_stalled", KitToolStatus.Running)
    }

    /** Running for 800 ms, then [to] and [after] ms more, like the Flutter scene's second pumpWidget. */
    private fun change(name: String, to: KitToolStatus, after: Long) {
        var status by mutableStateOf(KitToolStatus.Running)
        FlutterGoldenGate.check(
            name,
            frame = FlutterGoldenGate.Frame.Screen,
            wrap = wrap,
            motion = MotionLevel.Full,
            act = {
                status = to
                mainClock.advanceTimeBy(after)
            },
        ) { timeline(status).invoke() }
    }

    @Test fun changes_of_state() {
        change("kit_step_glow_finish_tick", KitToolStatus.Done, 300)
        change("kit_step_glow_finish_spring", KitToolStatus.Done, 700)
        change("kit_step_glow_fail_flash", KitToolStatus.Failed, 100)
        change("kit_step_glow_stop_fade", KitToolStatus.Stopped, 100)
    }

    @Test fun arabic_text2() {
        shot("kit_step_glow_running_t40_ar_text2", KitToolStatus.Running, scale = 2f)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_light", frame = FlutterGoldenGate.Frame.Screen,
            wrap = wrap, content = timeline(KitToolStatus.Running))
    }

    private companion object {
        const val SETTLE = 1200L // NV: the gate's settle time
        const val LAP = 2000L // NV: KitMotion.stepLap
    }
}
