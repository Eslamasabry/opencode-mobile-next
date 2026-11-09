package io.github.eslamasabry.oc.ui.kit.platform

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The words' ink sampler of one liquid pane (smoothness 2026-11-09). It keeps ONE small render target for the pane's
 * life: a [HardwareRenderer] drawing into a CPU-readable [ImageReader] of the sample's size, on the app's own render
 * thread. A sample records the pane's backdrop into a [RenderNode] (a display list, no pixels), asks the render thread to
 * draw it, and reads the pixels on a background thread when they arrive; the UI thread never waits on the GPU.
 *
 * It replaces `GraphicsLayer.toImageBitmap()` and a hardware-bitmap copy, which on every sample made a new renderer and
 * image reader and waited for the GPU twice on the UI thread (the owner's phone held 230 sample buffers, and the UI
 * thread stalled while the transcript moved under the glass).
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class AndroidBackdropSampler : KitBackdropSampler {
    private val node = RenderNode("kit-ink-sample")
    private val scope = CanvasDrawScope()
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var broken = false

    /** The sample waiting for its pixels; the reader's listener completes it (or drops a frame nobody waits for). */
    private val pending = AtomicReference<CompletableDeferred<IntArray?>?>(null)

    override suspend fun sample(width: Int, height: Int, density: Density, draw: DrawScope.() -> Unit): IntArray? {
        if (broken || width <= 0 || height <= 0) return null
        val hw = target(width, height) ?: return null
        val canvas = node.beginRecording(width, height)
        try {
            scope.draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(width.toFloat(), height.toFloat()), draw)
        } finally {
            node.endRecording()
        }
        val result = CompletableDeferred<IntArray?>()
        pending.getAndSet(result)?.complete(null)
        val sync = hw.createRenderRequest().syncAndDraw()
        if (sync and NOT_DRAWN != 0) result.complete(null)
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { result.await() }
        } finally {
            pending.compareAndSet(result, null)
        }
    }

    /** The renderer for a [w]×[h] sample, made again only when the pane's sample size changes. */
    private fun target(w: Int, h: Int): HardwareRenderer? {
        val r = renderer
        if (r != null && w == width && h == height) return r
        release()
        val made = runCatching {
            val image = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2, HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            // Every frame is taken and closed at once, so the render thread never waits for a free buffer.
            image.setOnImageAvailableListener({ source -> drain(source) }, handler)
            node.setPosition(0, 0, w, h)
            val hw = HardwareRenderer()
            hw.setSurface(image.surface)
            hw.setContentRoot(node)
            hw.isOpaque = false
            hw to image
        }.getOrNull()
        // A device whose buffers cannot be both drawn and read: no samples; the words keep the theme's ink.
        if (made == null) {
            broken = true
            return null
        }
        renderer = made.first
        reader = made.second
        width = w
        height = h
        return made.first
    }

    private fun drain(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        image.use { img -> pending.getAndSet(null)?.complete(argb(img)) }
    }

    override fun release() {
        renderer?.destroy()
        // Closed on the reader's own thread, after any frame it is reading.
        reader?.let { r -> handler.post { r.close() } }
        renderer = null
        reader = null
        width = 0
        height = 0
        pending.getAndSet(null)?.complete(null)
    }

    private companion object {
        const val TIMEOUT_MS = 500L

        /** The render request did not draw: the context is stopped or the frame was dropped. */
        const val NOT_DRAWN = HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED

        /** One background thread reads every pane's samples. */
        val handler: Handler by lazy { Handler(HandlerThread("kit-ink-sampler").apply { start() }.looper) }

        /** The image's RGBA bytes as ARGB ints, row after row. */
        fun argb(image: Image): IntArray {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val buffer = plane.buffer
            val stride = plane.rowStride
            val px = plane.pixelStride
            val out = IntArray(w * h)
            for (y in 0 until h) {
                var at = y * stride
                for (x in 0 until w) {
                    val r = buffer.get(at).toInt() and 0xFF
                    val g = buffer.get(at + 1).toInt() and 0xFF
                    val b = buffer.get(at + 2).toInt() and 0xFF
                    val a = buffer.get(at + 3).toInt() and 0xFF
                    out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    at += px
                }
            }
            return out
        }
    }
}
