package io.github.eslamasabry.oc.android.camera.access

import android.content.pm.PackageManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidCameraHardwareTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `B-ANDR-049 a phone with any camera can scan`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        assertTrue(AndroidCameraHardware(app).hasCamera())
    }

    @Test
    fun `B-ANDR-049 a device without a camera reports none`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        assertFalse(AndroidCameraHardware(app).hasCamera())
    }
}
