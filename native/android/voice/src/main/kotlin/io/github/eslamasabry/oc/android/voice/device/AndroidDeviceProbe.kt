package io.github.eslamasabry.oc.android.voice.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbe
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.MemoryNow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Device facts for the pack gate (plan 10 §7.3; the old `oc/voice` `getDeviceInfo`). TOTAL RAM is
 * `ActivityManager.MemoryInfo.totalMem / 1 MiB` — never `memoryClass`, which is kept for diagnostics only. A probe that
 * fails or takes over 5 s is [DeviceProbeResult.Unknown] (capture still assumed possible).
 */
public class AndroidDeviceProbe(private val context: Context, private val io: CoroutineDispatcher) : DeviceProbe {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override suspend fun probe(): DeviceProbeResult =
        withTimeoutOrNull(5.seconds) {
            withContext(io) {
                try {
                    val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                    DeviceProbeResult.Known(
                        totalRamMb = memory.totalMem.takeIf { it > 0 }?.let { it / MIB },
                        availableStorageBytes = freeBytes(),
                        supportedAbis = Build.SUPPORTED_ABIS.toList().toImmutableList(),
                        hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
                        lowRamDevice = activityManager.isLowRamDevice,
                        memoryClassMb = activityManager.memoryClass,
                        cpuCount = Runtime.getRuntime().availableProcessors(),
                    )
                } catch (_: RuntimeException) {
                    DeviceProbeResult.Unknown
                }
            }
        } ?: DeviceProbeResult.Unknown

    override fun memoryNow(): MemoryNow? =
        try {
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            MemoryNow(availMb = memory.availMem / MIB, thresholdMb = memory.threshold / MIB, low = memory.lowMemory)
        } catch (_: RuntimeException) {
            null
        }

    private fun freeBytes(): Long? =
        try {
            StatFs(context.filesDir.path).availableBytes
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
