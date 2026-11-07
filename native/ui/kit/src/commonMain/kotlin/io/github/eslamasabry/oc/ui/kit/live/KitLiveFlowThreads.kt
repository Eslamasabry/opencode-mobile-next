package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleFlowThreads
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flow threads (Dart `KitLiveFlowThreads`, `styles/kit_live_flow_threads.dart`): a couple of hundred short threads
 * follow a slow noise current, each leaving a twelve-point trail; faster and in the glow's colour while a reply runs.
 * Each thread keeps one reused path, so a frame allocates nothing.
 */
public class KitLiveFlowThreads : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleFlowThreads

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val a = strengthOf(frame) * .54 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:22
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val st = frame.memory.of("s") { Threads(w, h) }
        if (st.w != w || st.h != h) {
            st.reset(w, h)
            st.last = t
        }
        var dt = t - st.last
        st.last = t
        if (dt < 0 || dt > .2) dt = 1.0 / 24 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:34
        val steps = dt * 30 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:35
        val sp = (.35 + run * .9) * w / 260 * steps // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:36
        val colour = withA(mixColor(p.teal, p.glow, run), .5 * a) // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:41
        for (q in st.p) {
            val ang = fbm(q.x / w * 1.5 + t * .01, q.y / w * 1.5) * PI * 4 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:43
            q.x += cos(ang) * sp
            q.y += sin(ang) * sp
            if (q.x < 0 || q.x > w || q.y < 0 || q.y > h) {
                q.x = st.rnd.nextDouble() * w
                q.y = st.rnd.nextDouble() * h
                q.clear()
            }
            q.add(q.x, q.y)
            if (q.count > 1) {
                q.path.reset()
                q.path.moveTo(q.xAt(0), q.yAt(0))
                for (k in 1 until q.count) q.path.lineTo(q.xAt(k), q.yAt(k))
                scope.drawPath(q.path, colour, style = STROKE)
            }
        }
    }

    /** One thread and its trail of at most [TRAIL] points, in a ring buffer (Dart `_Thread`). */
    internal class Thread(var x: Double, var y: Double) {
        private val xs = FloatArray(TRAIL)
        private val ys = FloatArray(TRAIL)
        private var start = 0
        var count = 0
            private set
        val path = Path()

        fun clear() {
            start = 0
            count = 0
        }

        /** Dart `trail.add` then `removeAt(0)` past [TRAIL]. */
        fun add(px: Double, py: Double) {
            val at = (start + count) % TRAIL
            xs[at] = px.toFloat()
            ys[at] = py.toFloat()
            if (count < TRAIL) count++ else start = (start + 1) % TRAIL
        }

        fun xAt(k: Int): Float = xs[(start + k) % TRAIL]

        fun yAt(k: Int): Float = ys[(start + k) % TRAIL]
    }

    /** Dart `_Threads`: one generator (seed 7) for the first places and every respawn. */
    internal class Threads(w: Double, h: Double) {
        var w = 0.0
        var h = 0.0
        var last = 0.0
        val rnd = DartRandom(SEED)
        var p: List<Thread> = emptyList()

        init {
            reset(w, h)
        }

        fun reset(nw: Double, nh: Double) {
            w = nw
            h = nh
            p = List(COUNT) { Thread(rnd.nextDouble() * nw, rnd.nextDouble() * nh) }
        }
    }

    public companion object {
        public const val ID: String = "flowThreads"
        internal const val COUNT: Int = 220 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:82
        internal const val TRAIL: Int = 12 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:52
        internal const val SEED: Int = 7 // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:75
        internal val STROKE: Stroke = Stroke(width = 1.1f, cap = StrokeCap.Round) // F: lib/ui/kit/live_background/styles/kit_live_flow_threads.dart:39-40
    }
}
