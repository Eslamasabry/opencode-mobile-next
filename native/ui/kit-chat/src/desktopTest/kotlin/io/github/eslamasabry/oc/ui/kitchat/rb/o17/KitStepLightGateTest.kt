package io.github.eslamasabry.oc.ui.kitchat.rb.o17

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepTimeline
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_step_light_golden_test.dart: an opened [KitStepTimeline] ("Ran 3 commands", the
 * terminal glyph) with three tool rows on its rail, the step under test in the middle with a finished step above and
 * below it, 12 dp from the window's edge. Until 2026-11-07 the rows stood alone (no line, rail or nodes), which was most
 * of the 12-17 % box the scoreboard measured (SCOREBOARD-2026-11-07 B4).
 *
 * Mismatches (native has no input for them, so the shots render the nearest state):
 * - `stalled: true` (kit_step_timeline.dart `stalled`, the dimmed held light): `kit_step_glow_stalled` renders running;
 * - `KitToolStatus.background` (the calmer 4 s lap): `kit_step_glow_background_t40` renders running at the same time.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepLightGateTest {
    @Composable
    private fun view(id: String, kind: KitToolKind, title: String, subject: String?, detail: String?, status: KitToolStatus) =
        KitToolView(id, kind, title, subject, detail, status, null, null, null, statusWords(status))

    /** The row's status words, Flutter's ARB strings (kitToolWaiting … kitToolStopped) in the shot's locale. */
    @Composable
    private fun statusWords(status: KitToolStatus): String {
        val ar = LocalLayoutDirection.current == LayoutDirection.Rtl
        return when (status) {
            KitToolStatus.Pending -> if (ar) "بانتظار" else "Waiting"
            KitToolStatus.Running -> if (ar) "قيد التشغيل" else "Running"
            KitToolStatus.WaitingForYou -> if (ar) "بانتظارك" else "Waiting for you"
            KitToolStatus.Done -> if (ar) "مكتمل" else "Done"
            KitToolStatus.Failed -> if (ar) "فشل" else "Failed"
            KitToolStatus.Stopped -> if (ar) "متوقف" else "Stopped"
        }
    }


    private fun timeline(status: KitToolStatus, first: Boolean = false, last: Boolean = false): @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) { // F: test/goldens/kit/kit_step_light_golden_test.dart:81
            // F: test/goldens/kit/kit_step_light_golden_test.dart:26-53 the timeline, opened, the step under test in the middle
            KitStepTimeline(label = "Ran 3 commands", icon = KitIcons.terminal, expanded = true, onPressed = {}) {
                if (!first) Row(view("a", KitToolKind.Read, "Read", "lib/a.dart", null, KitToolStatus.Done))
                Row(view("b", KitToolKind.Shell, "Shell", null, "flutter test", status))
                if (!last) Row(view("c", KitToolKind.Edit, "Edit", "lib/b.dart", null, KitToolStatus.Done))
            }
        }
    }

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() } }

    @Composable
    private fun Row(view: KitToolView) = KitToolRow(view, opened = false, onToggle = {})

    private fun shot(name: String, status: KitToolStatus, motion: MotionLevel = MotionLevel.Full, first: Boolean = false, last: Boolean = false, scale: Float? = null, act: (ComposeUiTest.() -> Unit)? = null) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, wrap = wrap, motion = motion, textScale = scale, act = act, content = timeline(status, first, last))
    }

    /**
     * The shot [ms] into a lap of [lap] ms. The gate settles 1,200 ms before the act and again after it, so the act
     * advances the rest of the lap: 2 x 1,200 + act is [ms] modulo the lap. (Until 2026-11-07 only one settle was
     * counted, so every "t40" shot was taken at 0 % of the lap.)
     */
    private fun lap(name: String, ms: Long, status: KitToolStatus = KitToolStatus.Running, lap: Long = LAP, first: Boolean = false, last: Boolean = false, scale: Float? = null) {
        shot(name, status, first = first, last = last, scale = scale, act = { mainClock.advanceTimeBy(((ms - 2 * SETTLE) % lap + lap) % lap) })
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
        shot("kit_step_glow_waiting", KitToolStatus.WaitingForYou, MotionLevel.Off) // F: test/goldens/kit/kit_step_light_golden_test.dart:141
        shot("kit_step_glow_done", KitToolStatus.Done, MotionLevel.Off)
        shot("kit_step_glow_failed", KitToolStatus.Failed, MotionLevel.Off)
        shot("kit_step_glow_stopped", KitToolStatus.Stopped, MotionLevel.Off)
    }

    @Test fun first_and_last() {
        // F: test/goldens/kit/kit_step_light_golden_test.dart:158-172 'first': no step above; 'last': no step below
        lap("kit_step_glow_first_t40", 800, first = true)
        lap("kit_step_glow_last_t40", 800, last = true)
    }

    @Test fun waiting_background_stalled() {
        lap("kit_step_glow_waiting_breathe", 800, KitToolStatus.WaitingForYou, lap = BREATHE) // F: test/goldens/kit/kit_step_light_golden_test.dart:178-183
        lap("kit_step_glow_background_t40", 1600, lap = CALM) // F: test/goldens/kit/kit_step_light_golden_test.dart:192 (Mismatch: no background status)
        lap("kit_step_glow_stalled", 1300) // F: test/goldens/kit/kit_step_light_golden_test.dart:203-208 (Mismatch: no stalled input)
    }

    /**
     * Running, then [to], captured [after] ms after the change, like the Flutter scene's second pumpWidget. The gate
     * captures 1,200 ms after composition and settles 1,200 ms after any act, so the change is made by the scene's own
     * clock at 1,200 - [after] ms (an act would always add 1,200 ms and miss every short transition).
     */
    private fun change(name: String, to: KitToolStatus, after: Long) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, wrap = wrap, motion = MotionLevel.Full) {
            var status by remember { mutableStateOf(KitToolStatus.Running) }
            LaunchedEffect(Unit) {
                delay(SETTLE - after)
                status = to
            }
            timeline(status).invoke()
        }
    }

    @Test fun changes_of_state() {
        change("kit_step_glow_finish_tick", KitToolStatus.Done, 300)
        change("kit_step_glow_finish_spring", KitToolStatus.Done, 700)
        change("kit_step_glow_fail_flash", KitToolStatus.Failed, 100)
        change("kit_step_glow_stop_fade", KitToolStatus.Stopped, 100)
    }

    @Test fun arabic_text2() {
        lap("kit_step_glow_running_t40_ar_text2", 800, scale = 2f) // F: test/goldens/kit/kit_step_light_golden_test.dart:241
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_light", frame = FlutterGoldenGate.Frame.Screen,
            wrap = wrap, content = timeline(KitToolStatus.Running))
    }

    private companion object {
        const val SETTLE = 1200L // NV: the gate's settle time
        const val LAP = 2000L // NV: KitMotion.stepLap
        const val CALM = 4000L // NV: KitMotion.stepLapCalm
        const val BREATHE = 1600L // NV: KitMotion.stepBreathe
    }
}
