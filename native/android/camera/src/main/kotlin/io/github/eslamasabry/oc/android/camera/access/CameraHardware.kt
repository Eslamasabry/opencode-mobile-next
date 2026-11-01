package io.github.eslamasabry.oc.android.camera.access

import android.content.Context
import android.content.pm.PackageManager

/** Whether this device has any camera. Feeds `PlatformCapabilities.qrPairing` and the scanner's NoCamera stage. */
public fun interface CameraHardware {
    public fun hasCamera(): Boolean
}

/** `FEATURE_CAMERA_ANY`; a probe that fails answers "no camera", never a false yes (B-ANDR-049). */
public class AndroidCameraHardware(private val context: Context) : CameraHardware {
    override fun hasCamera(): Boolean =
        try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        } catch (_: RuntimeException) {
            false
        }
}
