package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.os.PowerManager
import io.github.eslamasabry.oc.core.domain.platform.ThermalLevel
import io.github.eslamasabry.oc.core.domain.platform.UnknownNetworkStatus
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.model.NetworkState.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The thin Android bindings over the real (shadowed) system services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPlatformRobolectricTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun caps(vararg transports: Int, validated: Boolean = true): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            val shadow = shadowOf(caps)
            transports.forEach { shadow.addTransportType(it) }
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    @Test
    fun `B-ANDR-037 the monitor registers one default callback, follows it and unregisters on close`() {
        val monitor = AndroidNetworkStatus(app)
        assertEquals(NetworkState.Unknown, monitor.state.value, "unknown until started")
        monitor.start()
        monitor.start()
        idle()
        val callbacks = shadowOf(connectivity).networkCallbacks
        assertEquals(1, callbacks.size, "start is idempotent")

        val network = ShadowNetwork.newInstance(42)
        callbacks.single().onAvailable(network)
        callbacks.single().onCapabilitiesChanged(network, caps(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals(Online.Yes, monitor.state.value.online)
        assertEquals(Transport.Mobile, monitor.state.value.transport)
        assertEquals(true, monitor.state.value.metered)

        callbacks.single().onLost(network)
        assertEquals(Online.No, monitor.state.value.online)

        monitor.close()
        idle()
        assertTrue(shadowOf(connectivity).networkCallbacks.isEmpty())
        assertEquals(NetworkState.Unknown, monitor.state.value)
    }

    @Test
    fun `B-ANDR-037 a stale callback after close changes nothing`() {
        val monitor = AndroidNetworkStatus(app)
        monitor.start()
        idle()
        val stale = shadowOf(connectivity).networkCallbacks.single()
        monitor.close()
        idle()
        stale.onAvailable(ShadowNetwork.newInstance(9))
        assertEquals(NetworkState.Unknown, monitor.state.value)
    }

    @Test
    fun `B-ANDR-037 device facts read the network from the one network status`() {
        val monitor = AndroidNetworkStatus(app)
        val facts = AndroidDeviceFacts(app, monitor)
        assertTrue(facts.network === monitor.state)
        assertEquals(NetworkState.Unknown, AndroidDeviceFacts(app, UnknownNetworkStatus).network.value)
    }

    @Test
    fun `B-ANDR-093 thermal status maps to levels and changes arrive as readings`() {
        assertEquals(
            listOf(ThermalLevel.None, ThermalLevel.Light, ThermalLevel.Moderate, ThermalLevel.Severe,
                ThermalLevel.Critical, ThermalLevel.Emergency, ThermalLevel.Shutdown, ThermalLevel.None, ThermalLevel.None),
            listOf(0, 1, 2, 3, 4, 5, 6, 7, -1).map(::thermalLevelOf),
        )
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        val facts = AndroidDeviceFacts(app, UnknownNetworkStatus)
        assertEquals(ThermalLevel.None, facts.thermal.value, "nothing is read before start")
        facts.start()
        idle()
        assertEquals(ThermalLevel.Moderate, facts.thermal.value)
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        idle()
        assertEquals(ThermalLevel.Severe, facts.thermal.value)
        facts.close()
        idle()
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_CRITICAL)
        idle()
        assertEquals(ThermalLevel.Severe, facts.thermal.value, "no reading after close")
    }

    @Test
    fun `PLAT0 power save follows the system broadcast`() {
        val facts = AndroidDeviceFacts(app, UnknownNetworkStatus)
        facts.start()
        idle()
        assertFalse(facts.powerSave.value)
        shadowOf(power).setIsPowerSaveMode(true)
        app.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).setPackage(app.packageName))
        idle()
        assertTrue(facts.powerSave.value)
        facts.close()
        idle()
    }

    @Test
    fun `PLAT0 total RAM comes from the platform memory info`() {
        val facts = AndroidDeviceFacts(app, UnknownNetworkStatus)
        assertTrue(facts.totalRamBytes >= 0L)
    }

    @Test
    fun `PLAT0 the app is visible while an activity is started and a rotation does not flap it`() {
        val facts = AndroidDeviceFacts(app, UnknownNetworkStatus)
        facts.start()
        idle()
        assertFalse(facts.appVisible.value)
        val controller = Robolectric.buildActivity(Activity::class.java).create().start()
        assertTrue(facts.appVisible.value)
        controller.resume().pause().stop()
        assertFalse(facts.appVisible.value)
        controller.destroy()
        facts.close()
        idle()
    }

    @Test
    fun `PLAT0 visibility counts several activities and ignores configuration-change stops`() {
        val tracker = VisibilityTracker()
        tracker.onStarted()
        tracker.onStarted()
        tracker.onStopped(isChangingConfigurations = false)
        assertTrue(tracker.visible.value, "one activity still started")
        tracker.onStopped(isChangingConfigurations = true)
        assertTrue(tracker.visible.value, "a rotation keeps the app visible")
        tracker.onStarted() // the recreated activity
        assertTrue(tracker.visible.value)
        tracker.onStopped(isChangingConfigurations = false)
        assertFalse(tracker.visible.value)
        tracker.onStopped(isChangingConfigurations = false)
        assertFalse(tracker.visible.value, "an extra stop never goes negative")
        tracker.onStarted()
        assertTrue(tracker.visible.value)
    }
}
