package io.github.eslamasabry.oc.android.camera.camerax

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import io.github.eslamasabry.oc.android.camera.access.CameraFailures
import io.github.eslamasabry.oc.android.camera.capture.PhotoCapturer
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * In-app photo capture with CameraX `ImageCapture` into a [io.github.eslamasabry.oc.android.camera.capture.CaptureFiles]
 * target. [start] binds the back camera with a preview (the kit renders [surfaceRequests]); [capture] writes one JPEG.
 * A failed or cancelled capture leaves no file. The camera permission is the caller's (`CameraAccessFlow`) first.
 */
public class CameraXPhotoCapturer(context: Context, owner: LifecycleOwner) : PhotoCapturer {
    private val binding = CameraXBinding(context, owner)
    private val imageCapture =
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private var bound = false

    public val surfaceRequests: StateFlow<SurfaceRequest?> = binding.surfaceRequests

    /** Main thread. */
    public fun start(onFailure: (Failure) -> Unit) {
        binding.bind(listOf(imageCapture), onBound = { bound = true }, onFailure = { bound = false; onFailure(it) })
    }

    /** Main thread. */
    public fun stop() {
        bound = false
        binding.unbind()
    }

    override suspend fun capture(target: File): Outcome<Boolean> {
        if (!bound) return Outcome.Failed(Failure.Media(MediaProblem.CameraFailed))
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { target.delete() }
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(target).build(),
                binding.mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(Outcome.Ok(target.length() > 0)) else target.delete()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        target.delete()
                        if (cont.isActive) cont.resume(Outcome.Failed(CameraFailures.classify(exception)))
                    }
                },
            )
        }
    }
}
