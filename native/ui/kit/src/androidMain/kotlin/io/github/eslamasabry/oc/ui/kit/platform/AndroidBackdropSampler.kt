package io.github.eslamasabry.oc.ui.kit.platform

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The words' ink sampler of one liquid pane (smoothness 2026-11-09). It keeps ONE small render target for the pane's
 * life: a [HardwareRenderer] drawing into a CPU-readable [ImageReader], and reads the pixels on a background thread when
 * they arrive; the UI thread never waits on the GPU.
 *
 * Threads (hardening 2026-11-12, after an ANR where the UI thread sat in `syncAndDraw` while the render thread hung in
 * the emulator's `eglCreateWindowSurface`):
 * - The UI thread only checks state, records the backdrop into [node] (a display list, no pixels) and posts the work.
 * - The "kit-ink-gpu" thread owns the renderer and the reader: it makes them, sets the surface, runs `syncAndDraw` and
 *   destroys them. Every call that can wait on the render thread runs there, never on the UI thread.
 * - The "kit-ink-sampler" thread only takes each frame from the reader, copies its pixels and closes readers.
 * - [gpu] lets one sync run at a time in the whole process. The UI thread records [node] only after taking it, and the
 *   GPU thread gives it back only after `syncAndDraw` has returned, so the node is never recorded while it is synced.
 *   While a sync is still out (a slow or stuck GPU) a sample returns null at once; the pane keeps its ink.
 *
 * The target only grows, in [bucket] steps, so a pane whose size changes by a few pixels (a search bar opening and
 * closing) draws into the top-left corner of the target it has instead of making a new surface ([InkTargetSize]).
 *
 * It replaces `GraphicsLayer.toImageBitmap()` and a hardware-bitmap copy, which on every sample made a new renderer and
 * image reader and waited for the GPU twice on the UI thread (the owner's phone held 230 sample buffers, and the UI
 * thread stalled while the transcript moved under the glass).
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class AndroidBackdropSampler : KitBackdropSampler {
    private val node = RenderNode("kit-ink-sample")
    private val scope = CanvasDrawScope()

    /** The target's size as the UI thread last asked for it (UI thread only). */
    private val size = InkTargetSize()

    // The GPU thread's own: the target it made and that target's size.
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    private var madeWidth = 0
    private var madeHeight = 0

    /** A device whose buffers cannot be both drawn and read: no samples ever; the words keep the theme's ink. */
    @Volatile private var broken = false

    /** The sample waiting for its pixels; the reader's listener completes it (or drops a frame nobody waits for). */
    private val pending = AtomicReference<InkRequest?>(null)

    override suspend fun sample(width: Int, height: Int, density: Density, draw: DrawScope.() -> Unit): IntArray? {
        if (broken || width <= 0 || height <= 0) return null
        // Busy (another sync is out), or resting after slow syncs: skip this sample without touching the node.
        if (!gpu.tryBegin(SystemClock.uptimeMillis())) return null
        val request = InkRequest(width, height, CompletableDeferred())
        var posted = false
        try {
            size.request(width, height)
            val targetWidth = size.width
            val targetHeight = size.height
            node.setPosition(0, 0, width, height)
            val canvas = node.beginRecording(width, height)
            try {
                scope.draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(width.toFloat(), height.toFloat()), draw)
            } finally {
                node.endRecording()
            }
            pending.getAndSet(request)?.result?.complete(null)
            posted = gpuHandler.post { syncOnGpu(targetWidth, targetHeight, request) }
        } finally {
            if (!posted) {
                pending.compareAndSet(request, null)
                request.result.complete(null)
                gpu.abandon()
            }
        }
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { request.result.await() }
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    /** GPU thread: draws [request] into a [targetWidth]×[targetHeight] target, then hands the guard back. */
    private fun syncOnGpu(targetWidth: Int, targetHeight: Int, request: InkRequest) {
        try {
            val hw = targetOnGpu(targetWidth, targetHeight)
            val sync = hw?.let { r -> runCatching { r.createRenderRequest().syncAndDraw() }.getOrDefault(NOT_DRAWN) } ?: NOT_DRAWN
            if (sync and NOT_DRAWN != 0) {
                pending.compareAndSet(request, null)
                request.result.complete(null)
            }
        } finally {
            gpu.end(SystemClock.uptimeMillis())
        }
    }

    /** GPU thread: the renderer for a [w]×[h] target, made again only when the size the UI thread asks for changes. */
    private fun targetOnGpu(w: Int, h: Int): HardwareRenderer? {
        renderer?.takeIf { w == madeWidth && h == madeHeight }?.let { return it }
        dropOnGpu()
        val image =
            runCatching {
                ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2, HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            }.getOrNull()
        val hw = image?.let(::rendererFor)
        if (image == null || hw == null) {
            image?.let { r -> readerHandler.post { r.close() } }
            broken = true
            return null
        }
        // Every frame is taken and closed at once, so the render thread never waits for a free buffer.
        image.setOnImageAvailableListener({ source -> drain(source) }, readerHandler)
        renderer = hw
        reader = image
        madeWidth = w
        madeHeight = h
        return hw
    }

    /** GPU thread: a renderer drawing [node] into [image], or null (and nothing left behind) when it cannot be made. */
    private fun rendererFor(image: ImageReader): HardwareRenderer? {
        val hw = runCatching { HardwareRenderer() }.getOrNull() ?: return null
        val ready =
            runCatching {
                hw.setContentRoot(node)
                hw.isOpaque = false
                hw.setSurface(image.surface)
            }.isSuccess
        if (!ready) hw.destroy()
        return hw.takeIf { ready }
    }

    /** GPU thread: destroys the renderer, then closes the reader on its own thread after any frame it is reading. */
    private fun dropOnGpu() {
        renderer?.destroy()
        reader?.let { r -> readerHandler.post { r.close() } }
        renderer = null
        reader = null
        madeWidth = 0
        madeHeight = 0
    }

    /** Reader thread: the latest frame's top-left corner, the size its sample asked for. */
    private fun drain(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        image.use { img ->
            val request = pending.getAndSet(null) ?: return
            val plane = img.planes[0]
            val pixels =
                if (fits(img.width, img.height, request.width, request.height)) {
                    argbRegion(plane.buffer, plane.rowStride, plane.pixelStride, request.width, request.height)
                } else {
                    null
                }
            request.result.complete(pixels)
        }
    }

    /** Returns at once; the GPU thread destroys the target after any sync it is running. A waiting sample gets null. */
    override fun release() {
        size.reset()
        pending.getAndSet(null)?.result?.complete(null)
        gpuHandler.post { dropOnGpu() }
    }

    private class InkRequest(val width: Int, val height: Int, val result: CompletableDeferred<IntArray?>)

    private companion object {
        const val TIMEOUT_MS = 500L

        /** The render request did not draw: the context is stopped or the frame was dropped. */
        const val NOT_DRAWN = HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED

        /** One sync at a time across every pane, and the rest after slow ones. */
        val gpu = InkGpuGuard()

        /** One background thread makes, syncs and destroys every pane's renderer. */
        val gpuHandler: Handler by lazy { Handler(HandlerThread("kit-ink-gpu").apply { start() }.looper) }

        /** One background thread reads every pane's samples (kept free of renderer calls to deliver pixels). */
        val readerHandler: Handler by lazy { Handler(HandlerThread("kit-ink-sampler").apply { start() }.looper) }
    }
}

/** A target side for an [n]-pixel sample: the next multiple of 16, at least 16. */
internal fun bucket(n: Int): Int = max(INK_BUCKET, (n + INK_BUCKET - 1) / INK_BUCKET * INK_BUCKET)

/** Whether a [w]×[h] sample fits in a [targetWidth]×[targetHeight] target. */
internal fun fits(targetWidth: Int, targetHeight: Int, w: Int, h: Int): Boolean = w <= targetWidth && h <= targetHeight

/**
 * The top-left [w]×[h] pixels of an RGBA frame as ARGB ints, row after row. [stride] is the bytes from one row to the
 * next (it can be wider than the region, and than the frame), [pixelStride] the bytes from one pixel to the next.
 */
internal fun argbRegion(buffer: ByteBuffer, stride: Int, pixelStride: Int, w: Int, h: Int): IntArray {
    val out = IntArray(w * h)
    for (y in 0 until h) {
        var at = y * stride
        for (x in 0 until w) {
            val r = buffer.get(at).toInt() and 0xFF
            val g = buffer.get(at + 1).toInt() and 0xFF
            val b = buffer.get(at + 2).toInt() and 0xFF
            val a = buffer.get(at + 3).toInt() and 0xFF
            out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            at += pixelStride
        }
    }
    return out
}

/**
 * The size of a pane's ink target: grows in [bucket] steps only when a sample no longer fits, so small size changes and
 * shrink-then-grow cycles reuse it. Bounded: after [SHRINK_AFTER] samples in a row that each need, in both directions,
 * less than the target and less than a quarter of its area, it is made again at the size the sample needs.
 */
internal class InkTargetSize {
    var width: Int = 0
        private set

    var height: Int = 0
        private set

    private var oversized = 0

    /** Fits a [w]×[h] sample; true when the target must be made (again) at [width]×[height]. */
    fun request(w: Int, h: Int): Boolean {
        val needWidth = bucket(w)
        val needHeight = bucket(h)
        if (!fits(width, height, w, h)) {
            width = max(width, needWidth)
            height = max(height, needHeight)
            oversized = 0
            return true
        }
        val tooBig = width > needWidth && height > needHeight && width.toLong() * height > SHRINK_AREA * needWidth.toLong() * needHeight
        oversized = if (tooBig) oversized + 1 else 0
        if (oversized < SHRINK_AFTER) return false
        width = needWidth
        height = needHeight
        oversized = 0
        return true
    }

    /** The target is gone; the next sample makes a new one. */
    fun reset() {
        width = 0
        height = 0
        oversized = 0
    }

    companion object {
        const val SHRINK_AREA = 4L
        const val SHRINK_AFTER = 50
    }
}

/**
 * One sync at a time, process-wide, and a rest after slow syncs: a sync that has not returned [stuckMs] after it began
 * is late (seen from the UI side while it still holds the guard, or when it ends); [strikes] late syncs in a row make
 * every sampler rest for [restMs] (samples return null and post nothing), then try again. Times are any monotonic clock
 * in milliseconds; nothing here waits, and the lock is held only to read and write these fields.
 */
internal class InkGpuGuard(
    private val stuckMs: Long = 2_000,
    private val strikes: Int = 3,
    private val restMs: Long = 10_000,
) {
    private var busy = false
    private var since = 0L
    private var counted = false
    private var late = 0
    private var restUntil = Long.MIN_VALUE

    /** Takes the guard for a sync starting [now]; false while one is out or while resting. */
    @Synchronized
    fun tryBegin(now: Long): Boolean {
        if (busy) {
            if (!counted && now - since >= stuckMs) {
                counted = true
                strike(now)
            }
            return false
        }
        if (now < restUntil) return false
        busy = true
        since = now
        counted = false
        return true
    }

    /** The sync that took the guard returned at [now]. */
    @Synchronized
    fun end(now: Long) {
        if (!busy) return
        if (!counted) {
            if (now - since >= stuckMs) strike(now) else late = 0
        }
        busy = false
        counted = false
    }

    /** The guard was taken but nothing was posted: give it back without judging the GPU. */
    @Synchronized
    fun abandon() {
        busy = false
        counted = false
    }

    /** Whether samplers rest at [now]. */
    @Synchronized
    fun resting(now: Long): Boolean = now < restUntil

    private fun strike(now: Long) {
        late++
        if (late >= strikes) {
            restUntil = now + restMs
            late = 0
        }
    }
}

private const val INK_BUCKET = 16
