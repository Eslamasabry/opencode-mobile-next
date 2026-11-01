package io.github.eslamasabry.oc.android.camera.camerax

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.eslamasabry.oc.android.camera.access.CameraFailures
import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/**
 * The back camera bound to a lifecycle with a [Preview] plus the caller's use cases. The kit's viewfinder
 * (`CameraXViewfinder`, `:ui:kit-content`) renders [surfaceRequests]. All calls on the main thread.
 */
internal class CameraXBinding(private val context: Context, private val owner: LifecycleOwner) {
    private val main: Executor = ContextCompat.getMainExecutor(context)
    private val requests = MutableStateFlow<SurfaceRequest?>(null)
    private var provider: ProcessCameraProvider? = null
    private var generation = 0

    val surfaceRequests: StateFlow<SurfaceRequest?> = requests.asStateFlow()
    val mainExecutor: Executor get() = main

    /** Binds once the provider is ready, unless [unbind] ran in between (a late start after a pause is ignored). */
    fun bind(useCases: List<UseCase>, onBound: () -> Unit, onFailure: (Failure) -> Unit) {
        val mine = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (mine != generation) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider { r -> requests.value = r } }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, *useCases.toTypedArray())
                onBound()
            } catch (e: Exception) {
                onFailure(CameraFailures.classify(e.cause ?: e))
            }
        }, main)
    }

    fun unbind() {
        generation++
        provider?.unbindAll()
        requests.value = null
    }
}
