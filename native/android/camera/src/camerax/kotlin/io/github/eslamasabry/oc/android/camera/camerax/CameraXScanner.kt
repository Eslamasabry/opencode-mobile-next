package io.github.eslamasabry.oc.android.camera.camerax

import android.content.Context
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.eslamasabry.oc.android.camera.scan.QrFrameDecoder
import io.github.eslamasabry.oc.android.camera.scan.ScanAttempt
import io.github.eslamasabry.oc.android.camera.scan.ScanSession
import io.github.eslamasabry.oc.android.camera.scan.ScanWindow
import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Clock

/**
 * The pairing scanner's camera (plan 10 §14.2): back camera, 720p analysis keeping only the latest frame, decoded by
 * zxing-cpp inside the window crop on one analysis thread. Starts on ON_RESUME, stops on ON_PAUSE, closes on
 * ON_DESTROY. The first code [parse] accepts unbinds the camera, then [onLatched] runs on the main thread. The decoded
 * text is never stored, shown or logged here. The kit renders [surfaceRequests]; [windowFraction] comes from
 * `ScanWindow.fractionFor(windowPx, previewShortSidePx)`.
 */
public class CameraXScanner<T : Any>(
    context: Context,
    owner: LifecycleOwner,
    clock: Clock,
    parse: (String) -> T?,
    onLatched: (T) -> Unit,
    onRejected: () -> Unit,
    private val onFailure: (Failure) -> Unit,
    @Volatile public var windowFraction: Float = 0.6f,
    decoder: QrFrameDecoder<ImageProxy> = ZxingCppQrDecoder(),
) {
    private val binding = CameraXBinding(context, owner)
    private val analysisThread: ExecutorService = Executors.newSingleThreadExecutor()
    private val session =
        ScanSession(
            decoder = decoder,
            parse = parse,
            clock = clock,
            // Both posted to main in this order: the camera is unbound before the host pops (FIFO executor).
            releaseCamera = { binding.mainExecutor.execute { stop() } },
            onLatched = { value -> binding.mainExecutor.execute { onLatched(value) } },
            onRejected = { binding.mainExecutor.execute(onRejected) },
        )
    private var analysis: ImageAnalysis? = null

    public val surfaceRequests: StateFlow<SurfaceRequest?> = binding.surfaceRequests
    public val latched: Boolean get() = session.latched

    private val observer =
        object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = start()

            override fun onPause(owner: LifecycleOwner) = stop()

            override fun onDestroy(owner: LifecycleOwner) = close()
        }

    init {
        owner.lifecycle.addObserver(observer)
    }

    /** Main thread. "Try again" after a failure calls this too. */
    public fun start() {
        if (session.latched) return
        stop()
        val attempt = session.begin()
        val useCase = buildAnalysis(attempt)
        analysis = useCase
        binding.bind(listOf(useCase), onBound = {}, onFailure = { f -> session.end(); onFailure(f) })
    }

    /** Main thread. */
    public fun stop() {
        session.end()
        analysis?.clearAnalyzer()
        analysis = null
        binding.unbind()
    }

    private fun close() {
        stop()
        analysisThread.shutdown()
    }

    private fun buildAnalysis(attempt: ScanAttempt): ImageAnalysis {
        val selector =
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
        return ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analysisThread) { proxy ->
                    proxy.use {
                        if (!session.isCurrent(attempt)) return@use
                        val c = ScanWindow.cropFor(it.width, it.height, windowFraction)
                        it.setCropRect(Rect(c.left, c.top, c.right, c.bottom))
                        session.analyze(attempt, it)
                    }
                }
            }
    }
}
