package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.domain.platform.NetworkStatus
import io.github.eslamasabry.oc.core.domain.platform.ThermalLevel
import io.github.eslamasabry.oc.core.model.NetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Basic DeviceFacts (plan 03 §10.4, plan 11 §1.1 rows 42, 43 basic part): network from the one NetworkStatus, the
// current thermal status, power save, total RAM and app visibility. Battery and thermal headroom (CR11-5) and the heat
// policy wait for S3. Nothing here is persisted or logged.

/** PowerManager.THERMAL_STATUS_* (0..6) to [ThermalLevel]; an unknown value reads as [ThermalLevel.None]. */
public fun thermalLevelOf(status: Int): ThermalLevel = when (status) {
    1 -> ThermalLevel.Light
    2 -> ThermalLevel.Moderate
    3 -> ThermalLevel.Severe
    4 -> ThermalLevel.Critical
    5 -> ThermalLevel.Emergency
    6 -> ThermalLevel.Shutdown
    else -> ThermalLevel.None
}

/**
 * App visibility from activity start/stop counts (what ProcessLifecycleOwner does, without the library). A stop that is
 * part of a configuration change (rotation, locale, dark mode) does not count, so visibility never flaps false there.
 * Main thread only, like the activity callbacks that drive it.
 */
public class VisibilityTracker {
    private val mutable = MutableStateFlow(false)
    public val visible: StateFlow<Boolean> = mutable.asStateFlow()
    private var started = 0
    private var changingConfiguration = false

    public fun onStarted() {
        if (changingConfiguration) changingConfiguration = false else started++
        mutable.value = started > 0
    }

    public fun onStopped(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) {
            changingConfiguration = true
            return
        }
        if (started > 0) started--
        mutable.value = started > 0
    }
}

/**
 * The Android [DeviceFacts]. Listeners are registered by [start] and removed by [close]; both repeat safely and may be
 * called from any thread (the work runs on the main looper). Before [start], thermal is None, power save false and the
 * app not visible. Pass the [Application] so visibility is tracked from the first activity.
 */
public class AndroidDeviceFacts(
    private val application: Application,
    private val networkStatus: NetworkStatus,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : DeviceFacts, AutoCloseable {
    private val power: PowerManager? = application.getSystemService(PowerManager::class.java)
    private val thermalState = MutableStateFlow(ThermalLevel.None)
    private val powerSaveState = MutableStateFlow(false)
    private val visibility = VisibilityTracker()
    private var started = false // main looper only
    private var thermalListener: Any? = null // PowerManager.OnThermalStatusChangedListener on API 29+

    override val network: StateFlow<NetworkState> get() = networkStatus.state
    override val thermal: StateFlow<ThermalLevel> = thermalState.asStateFlow()
    override val powerSave: StateFlow<Boolean> = powerSaveState.asStateFlow()
    override val appVisible: StateFlow<Boolean> get() = visibility.visible

    /** Total physical RAM (voice packs gate on this, never memoryClass). 0 when the platform does not say. */
    override val totalRamBytes: Long by lazy {
        try {
            val info = ActivityManager.MemoryInfo()
            application.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            info.totalMem.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            readPowerSave()
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = visibility.onStarted()

        override fun onActivityStopped(activity: Activity) = visibility.onStopped(activity.isChangingConfigurations)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    public fun start() {
        handler.post { startOnMain() }
    }

    override fun close() {
        handler.post { stopOnMain() }
    }

    private fun startOnMain() {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        try {
            ContextCompat.registerReceiver(
                application, powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
            // Power save stays at its last reading.
        }
        readPowerSave()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startThermal()
    }

    private fun stopOnMain() {
        if (!started) return
        started = false
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        try {
            application.unregisterReceiver(powerSaveReceiver)
        } catch (_: Exception) {
            // Registration may have failed.
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stopThermal()
    }

    private fun readPowerSave() {
        powerSaveState.value = try {
            power?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun startThermal() {
        val manager = power ?: return
        try {
            thermalState.value = thermalLevelOf(manager.currentThermalStatus)
            val listener = PowerManager.OnThermalStatusChangedListener { thermalState.value = thermalLevelOf(it) }
            manager.addThermalStatusListener(listener) // delivered on the main thread
            thermalListener = listener
        } catch (_: Exception) {
            thermalState.value = ThermalLevel.None
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun stopThermal() {
        val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
        thermalListener = null
        try {
            power?.removeThermalStatusListener(listener)
        } catch (_: Exception) {
            // Already removed.
        }
    }
}
