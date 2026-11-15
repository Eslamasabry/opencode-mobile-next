package io.github.eslamasabry.oc.ui.gate

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintFilterCanvas
import org.jetbrains.skia.Surface

/**
 * What one frame of a screen costs, counted as work rather than timed (a shared test machine's clock is noise):
 *
 * - [draws]: draw calls the whole frame issues (every one of them reaches the GPU on a phone, whatever changed);
 * - [masks]: draws that clear or keep content by its alpha (`DstIn` / `DstOut`): each one implies an offscreen layer the
 *   size of what it masks, which the GPU fills and blends again on every frame that layer's content moves;
 * - [filtered]: draws through an image filter (a blur, a render effect);
 * - [scopes]: composable scopes that ran again (recomposition) while the frame was produced;
 * - [busy]: whether the frame had any work at all (a composition, a layout or a draw was pending). A still page whose
 *   frames are all idle costs nothing between touches;
 * - [offscreen] / [offscreenPx]: the layers on screen the GPU renders into a buffer of their own and blends back (each
 *   one is a render-target switch and a second fill of its area, the costliest thing a tile-based phone GPU does), read
 *   from the layout tree's layers.
 *
 * Skia's paint filter does not see into the pictures Compose records its layers into, so [draws], [masks] and
 * [filtered] count the root frame's own draws only; [offscreen] is the layer-level measure.
 */
public data class FrameCost(
    val draws: Int,
    val masks: Int,
    val filtered: Int,
    val scopes: Int,
    val busy: Boolean,
    /** Layers composited offscreen (an explicit Offscreen strategy, or alpha, a blend, a filter or an effect). */
    val offscreen: Int = 0,
    /** Their pixels: what the GPU fills a second time and blends back on every frame their content moves. */
    val offscreenPx: Long = 0,
)

/**
 * The frame-cost meter for a desktop Compose test (c2-performance, 2026-11-15). Attach it before `setContent` with the
 * test's main clock held (`mainClock.autoAdvance = false`), then step frames through it:
 *
 * ```
 * runDesktopComposeUiTest(W, H) {
 *     mainClock.autoAdvance = false
 *     val meter = FrameMeter.attach(this)
 *     setContent { … }
 *     meter.settle()
 *     val still = meter.frames(120)               // nothing changes: how many frames still do work
 *     val scroll = meter.drag(Offset(x, y), 40, -24f) // a finger drags the content up 24 px a frame
 * }
 * ```
 *
 * Recompositions are counted through the runtime's composition observer on every composition the test's recomposer
 * registers (lazy list items are compositions of their own); draws through a Skia paint filter over a raster canvas the
 * scene is drawn into once more after the frame.
 */
@OptIn(ExperimentalTestApi::class, InternalTestApi::class, ExperimentalComposeRuntimeApi::class, InternalComposeUiApi::class)
public class FrameMeter private constructor(private val test: SkikoComposeUiTest) {
    private var scopes = 0
    private val handles = mutableListOf<CompositionObserverHandle>()
    private val frameRecomposer: FrameRecomposer =
        SkikoComposeUiTest::class.java.getDeclaredField("frameRecomposer").let {
            it.isAccessible = true
            it.get(test) as FrameRecomposer
        }

    private val counter =
        object : CompositionObserver {
            override fun onBeginComposition(composition: ObservableComposition) = Unit

            override fun onScopeEnter(scope: RecomposeScope) {
                scopes++
            }

            override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit

            override fun onScopeExit(scope: RecomposeScope) = Unit

            override fun onEndComposition(composition: ObservableComposition) = Unit

            override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit

            override fun onScopeDisposed(scope: RecomposeScope) = Unit
        }

    private val registration =
        (frameRecomposer.compositionContext as Recomposer).observe(
            object : CompositionRegistrationObserver {
                override fun onCompositionRegistered(composition: ObservableComposition) {
                    handles += composition.setObserver(counter)
                }

                override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
            }
        )

    private val scene: ComposeScene
        get() = test.scene

    /** Lets the first frames run (composition, the first layout, entry motion) until a frame has no work, at most [max]. */
    public fun settle(max: Int = 600): Int {
        var n = 0
        while (n < max) {
            if (!pending()) {
                test.mainClock.advanceTimeByFrame()
                if (!pending()) return n
            }
            test.mainClock.advanceTimeByFrame()
            n++
        }
        return n
    }

    /** Steps [n] frames with nothing changing from outside and returns what each cost. */
    public fun frames(n: Int): List<FrameCost> = List(n) { frame() }

    /**
     * A finger pressed at [at] drags the content [dy] px per frame for [n] frames (after a first move past the touch
     * slop), then lifts. Returns the cost of each dragged frame.
     */
    public fun drag(at: Offset, n: Int, dy: Float): List<FrameCost> {
        var y = at.y
        var t = 0L
        fun send(type: PointerEventType) =
            test.runOnUiThread { scene.sendPointerEvent(type, Offset(at.x, y), type = PointerType.Touch, timeMillis = t) }
        send(PointerEventType.Press)
        test.mainClock.advanceTimeByFrame()
        // Past the slop at once, so every measured frame scrolls by dy.
        t += FRAME_MS
        y += if (dy < 0) -SLOP_PX else SLOP_PX
        send(PointerEventType.Move)
        test.mainClock.advanceTimeByFrame()
        val costs =
            List(n) {
                t += FRAME_MS
                y += dy
                send(PointerEventType.Move)
                frame()
            }
        t += FRAME_MS
        send(PointerEventType.Release)
        test.mainClock.advanceTimeByFrame()
        return costs
    }

    /** One frame: its recompositions, and the draws of the picture it leaves on screen. */
    public fun frame(): FrameCost {
        val busy = pending()
        val s0 = scopes
        test.mainClock.advanceTimeByFrame()
        val ran = scopes - s0
        val (draws, masks, filtered) = drawCounts()
        val (layers, px) = offscreenLayers()
        return FrameCost(draws, masks, filtered, ran, busy || ran > 0, layers, px)
    }

    /** The placed layers of the whole layout tree that composite offscreen, and their pixels. */
    public fun offscreenLayers(): Pair<Int, Long> {
        var n = 0
        var px = 0L
        fun walk(node: Any) {
            val info = node as LayoutInfo
            if (!info.isPlaced) return
            for (m in info.getModifierInfo()) {
                val layer = m.extra?.let(::graphicsLayerOf) ?: continue
                if (layer.isReleased) continue
                val off =
                    layer.compositingStrategy == CompositingStrategy.Offscreen ||
                        (layer.compositingStrategy == CompositingStrategy.Auto &&
                            (layer.alpha < 1f || layer.renderEffect != null || layer.colorFilter != null ||
                                layer.blendMode != androidx.compose.ui.graphics.BlendMode.SrcOver))
                if (off) {
                    n++
                    px += layer.size.width.toLong() * layer.size.height
                }
            }
            @Suppress("UNCHECKED_CAST")
            (childrenOf.invoke(node) as List<Any>).forEach(::walk)
        }
        val roots = roots()
        test.runOnUiThread { roots.forEach(::walk) }
        return n to px
    }

    private fun roots(): List<Any> =
        test.runWithoutImplicitWait {
            test.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        }.map { layoutNodeOf.invoke(it) }

    private fun graphicsLayerOf(extra: Any): GraphicsLayer? =
        runCatching {
            extra.javaClass.getDeclaredField("graphicsLayer").let { f ->
                f.isAccessible = true
                f.get(extra) as? GraphicsLayer
            }
        }.getOrNull()

    /** Draws the scene as it stands into a counting canvas: (draws, masks, filtered). */
    public fun drawCounts(): Triple<Int, Int, Int> {
        var draws = 0
        var masks = 0
        var filtered = 0
        val size = scene.size ?: return Triple(0, 0, 0)
        val surface = Surface.makeRasterN32Premul(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        try {
            val canvas =
                object : PaintFilterCanvas(surface.canvas, true) {
                    override fun onFilter(paint: Paint): Boolean {
                        draws++
                        val mode = paint.blendMode
                        if (mode == BlendMode.DST_IN || mode == BlendMode.DST_OUT) masks++
                        if (paint.imageFilter != null) filtered++
                        return true
                    }
                }
            test.runOnUiThread { scene.draw(canvas.asComposeCanvas()) }
            canvas.close()
        } finally {
            surface.close()
        }
        return Triple(draws, masks, filtered)
    }

    private fun pending(): Boolean = frameRecomposer.hasPendingWork() || scene.hasPendingDraw || scene.hasPendingMeasureOrLayout

    /** Stops counting (the test's compositions are closed with it anyway). */
    public fun close() {
        handles.forEach { it.dispose() }
        registration.dispose()
    }

    public companion object {
        private val layoutNodeOf =
            SemanticsNode::class.java.methods.first { it.name.startsWith("getLayoutNode") && it.parameterCount == 0 }
        private val childrenOf =
            Class.forName("androidx.compose.ui.node.LayoutNode").methods.first {
                it.name.startsWith("getChildren") && it.parameterCount == 0
            }

        /** Attach before `setContent`, with the main clock held. */
        public fun attach(test: SkikoComposeUiTest): FrameMeter = FrameMeter(test)

        private const val FRAME_MS = 16L
        private const val SLOP_PX = 40f
    }
}

/** A short line per run of frames for a report: busy frames, then median/largest draws, masks, filtered and scopes. */
public fun List<FrameCost>.summary(name: String): String {
    if (isEmpty()) return "$name: no frames"
    fun med(f: (FrameCost) -> Int) = map(f).sorted()[size / 2]
    fun max(f: (FrameCost) -> Int) = maxOf(f)
    return "$name busy ${count { it.busy }}/$size d ${med { it.draws }}/${max { it.draws }} m ${med { it.masks }}/${max { it.masks }} " +
        "f ${max { it.filtered }} s ${med { it.scopes }}/${max { it.scopes }}/${sumOf { it.scopes }} " +
        "off ${med { it.offscreen }}/${max { it.offscreen }} px ${map { it.offscreenPx }.sorted()[size / 2]}"
}
