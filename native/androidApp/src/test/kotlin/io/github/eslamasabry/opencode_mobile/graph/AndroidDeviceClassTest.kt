package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.core.domain.capabilities.DeviceClass
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Android device-class detection tests (B-CAP-303..305). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceClassTest {
    @Test
    @Config(qualifiers = "sw600dp-w1280dp-h800dp-land")
    fun `B-CAP-303 Android tablet with smallest width at least 600 dp classifies as Tablet`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE, false)
        assertEquals(DeviceClass.Tablet, detectAndroidDeviceClass(app))
    }

    @Test
    @Config(qualifiers = "sw360dp-w360dp-h780dp-port")
    fun `B-CAP-303b Android phone under 600 dp classifies as Phone`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE, false)
        assertEquals(DeviceClass.Phone, detectAndroidDeviceClass(app))
    }

    @Test
    @Config(qualifiers = "sw720dp-w840dp-h720dp-land")
    fun `B-CAP-304 Android foldable with hinge sensor feature classifies as Phone even at tablet width`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE, true)
        assertEquals(DeviceClass.Phone, detectAndroidDeviceClass(app))
    }

    @Test
    @Config(qualifiers = "sw600dp-w1280dp-h800dp-land")
    fun `B-CAP-305 device class captured at start stays unchanged after window resize or split`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE, false)
        val initial = PlatformCapabilities.android(deviceClass = detectAndroidDeviceClass(app))
        PlatformCapabilities.recordDeviceClass(initial.deviceClass)
        assertEquals(DeviceClass.Tablet, initial.deviceClass)
        assertEquals(DeviceClass.Tablet, PlatformCapabilities.currentDeviceClass)

        // Simulate a split-screen / resized configuration under 600 dp: the start-time fact does not change.
        val narrow = Configuration(app.resources.configuration).apply {
            screenWidthDp = 420
            smallestScreenWidthDp = 420
        }
        val resizedContext = app.createConfigurationContext(narrow)
        assertEquals(420, resizedContext.resources.configuration.smallestScreenWidthDp)
        assertEquals(DeviceClass.Tablet, initial.deviceClass)
        assertEquals(DeviceClass.Tablet, PlatformCapabilities.currentDeviceClass)
    }
}
