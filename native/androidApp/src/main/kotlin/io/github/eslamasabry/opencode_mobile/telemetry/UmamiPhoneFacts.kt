package io.github.eslamasabry.opencode_mobile.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import io.github.eslamasabry.oc.engine.analytics.health.HealthDeviceContext
import io.github.eslamasabry.oc.engine.analytics.health.HealthRanges
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiField
import io.github.eslamasabry.opencode_mobile.BuildConfig

/**
 * What the phone itself tells our own analytics server, as fixed words only: the release channel, the window width
 * class, the text-size step, the processor family, ranges for memory and free storage, battery saver and how long the
 * app has been installed. Never a model, maker, id, exact size, time zone or place. Every word is checked against
 * [UmamiField] again before it can join an event, and the request body guard checks it a third time.
 *
 * Read only when an event is about to be queued with usage on (the sender asks for the device then), and kept for a
 * minute so a burst of events reads the phone once.
 */
internal class UmamiPhoneFacts(private val app: Context, private val now: () -> Long) {
    @Volatile private var readAt = 0L
    @Volatile private var cached: Map<String, String> = emptyMap()

    private val staticFacts: Map<String, String> by lazy {
        buildMap {
            put(UmamiField.Channel.wire, if ("-test." in BuildConfig.VERSION_NAME) "test" else "public")
            when (Build.SUPPORTED_ABIS.firstOrNull()) {
                "arm64-v8a" -> put(UmamiField.Abi.wire, "arm64")
                "armeabi-v7a", "armeabi" -> put(UmamiField.Abi.wire, "arm32")
                "x86_64" -> put(UmamiField.Abi.wire, "x86_64")
                else -> put(UmamiField.Abi.wire, "other")
            }
            runCatching {
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
                if (memory.totalMem > 0) {
                    val gb = memory.totalMem.toDouble() / HealthDeviceContext.GB
                    put(UmamiField.RamRange.wire, HealthRanges.ramOf(gb))
                }
            }
        }
    }

    fun read(): Map<String, String> {
        val at = now()
        val old = cached
        if (old.isNotEmpty() && at - readAt in 0 until TTL_MS) return old
        val fresh = buildMap {
            putAll(staticFacts)
            val config = app.resources.configuration
            put(UmamiField.WidthClass.wire, UmamiField.widthClassOf(config.screenWidthDp))
            put(UmamiField.TextScale.wire, UmamiField.textScaleOf(config.fontScale))
            runCatching {
                val free = StatFs(app.filesDir.path).availableBytes
                if (free >= 0) {
                    val gb = free.toDouble() / HealthDeviceContext.GB
                    put(UmamiField.FreeStorageRange.wire, HealthRanges.storageOf(gb))
                }
            }
            runCatching {
                val saver = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode
                if (saver != null) put(UmamiField.BatterySaver.wire, if (saver) "yes" else "no")
            }
            runCatching {
                @Suppress("DEPRECATION")
                val first = app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
                if (first > 0) put(UmamiField.InstallAge.wire, UmamiField.installAgeOf(at - first))
            }
        }
        cached = fresh
        readAt = at
        return fresh
    }

    private companion object {
        const val TTL_MS = 60_000L
    }
}
