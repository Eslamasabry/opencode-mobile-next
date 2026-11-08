package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the phone says about itself for the device context (section D): numbers and fixed words only. */
public data class HealthDeviceContext(
    val ramBytes: Long = 0,
    val freeBytes: Long = -1,
    val abi: String? = null,
    val webviewMajor: Int? = null,
    val notificationsAllowed: Boolean? = null,
    /** Whether the installer was a store (`build_kind` store), else sideload. */
    val fromStore: Boolean = false,
) {
    /** `ram_range` word, or null when unknown. */
    val ramRange: String?
        get() = if (ramBytes > 0) HealthRanges.ramOf(ramBytes.toDouble() / GB) else null

    /** `free_storage_range` word, or null when unknown. */
    val freeStorageRange: String?
        get() = if (freeBytes >= 0) HealthRanges.storageOf(freeBytes.toDouble() / GB) else null

    /** The user properties this context gives (section D, without the server ones). */
    val properties: Map<String, Any?>
        get() =
            buildMap {
                ramRange?.let { put("ram_range", it) }
                freeStorageRange?.let { put("free_storage_range", it) }
                abi?.takeIf { it in HealthWords.abis }?.let { put("abi", it) }
                webviewMajor?.let { put("webview_major", it) }
                notificationsAllowed?.let { put("notif_allowed", if (it) "yes" else "no") }
            }

    public companion object {
        public const val GB: Double = 1024.0 * 1024.0 * 1024.0
    }
}

/** One process end Android remembers (ApplicationExitInfo): its `REASON_*` number and time. */
public data class HealthExit(val reason: Int, val atMs: Long)

/** The platform half: everything is read on demand and answers something safe where it cannot. */
public interface HealthPlatform {
    public suspend fun deviceContext(): HealthDeviceContext?

    /** The last few process ends, newest first; empty where Android keeps none (API < 30). */
    public suspend fun exits(limit: Int): List<HealthExit>

    /** Process start to the first frame, once it was drawn; null before. */
    public fun coldStartMs(): Long?

    /** `major.minor.patch` of this app. */
    public val appVersion: String

    /** The declared build kind (`ci`, `local`), or null to say store or sideload from the installer. */
    public val declaredBuildKind: String?
}

/** Where the newest reported process end is kept (`oc.telemetry.lastExitAt`, Flutter's key). */
public interface HealthExitMarks {
    public fun lastExitAtMs(): Long

    public suspend fun setLastExitAtMs(atMs: Long)
}

/** The contract word for an Android exit reason, or null for a normal end (the app quit itself, or was updated). */
public fun exitReasonWord(reason: Int): String? =
    when (reason) {
        EXIT_SELF, PACKAGE_UPDATED -> null
        4 -> "crash"
        5 -> "crash_native"
        6 -> "anr"
        3 -> "low_memory"
        9 -> "excessive_resource"
        2 -> "signaled"
        10 -> "user_requested"
        12 -> "dependency_died"
        else -> "other"
    }

private const val EXIT_SELF = 1
private const val PACKAGE_UPDATED = 16

/** The major.minor of a server version (`2.0.28` becomes `2.0`), or null when it does not start that way. */
public fun majorMinorOf(version: String?): String? {
    val m = Regex("^v?(\\d{1,2})\\.(\\d{1,2})").find(version?.trim() ?: return null) ?: return null
    return "${m.groupValues[1]}.${m.groupValues[2]}"
}

/**
 * Slow frames per kind of page, once per session per page kind (Flutter `JankMeter`). The platform feeds frame
 * counts only while [running] (it follows `usage`); with the switch off nothing is measured and nothing is kept.
 */
public class JankMeter(
    private val report: (screen: ProductScreen, pctRange: String, hz: String) -> Unit,
    private val refreshRate: () -> Double = { 60.0 },
    private val minFrames: Int = 30,
) {
    public var running: Boolean = false
        private set

    private var screen = ProductScreen.Other
    private val frames = HashMap<ProductScreen, Int>()
    private val janky = HashMap<ProductScreen, Int>()
    private val sent = HashSet<ProductScreen>()

    public fun start() {
        running = true
    }

    public fun stop() {
        running = false
        frames.clear()
        janky.clear()
    }

    /** A page of this kind came into view. */
    public fun screen(next: ProductScreen) {
        if (running && next != screen) flush(screen)
        screen = next
    }

    /** The newest frames: [total] drawn, [slow] over one refresh period. */
    public fun onFrames(total: Int, slow: Int) {
        if (!running) return
        frames[screen] = (frames[screen] ?: 0) + total
        janky[screen] = (janky[screen] ?: 0) + slow
    }

    /** The app went to the background: report what was measured; the next time in front is a new session. */
    public fun endSession() {
        if (!running) return
        flush(screen)
        frames.keys.toList().forEach(::flush)
        sent.clear()
        frames.clear()
        janky.clear()
    }

    private fun flush(kind: ProductScreen) {
        val count = frames[kind] ?: 0
        if (count < minFrames || kind in sent) return
        sent += kind
        val pct = (janky[kind] ?: 0) * 100.0 / count
        report(kind, HealthRanges.jankPctOf(pct), HealthRanges.refreshHzOf(refreshRate()))
    }
}

/**
 * Wires the health telemetry together once the reporter exists (Flutter `HealthTelemetry`): device context, app start,
 * jank, memory pressure, the custom keys and the last process end. Every part waits for its own consent and does
 * nothing while that is off.
 */
public class HealthTelemetry(
    private val telemetry: Telemetry,
    private val platform: HealthPlatform,
    private val marks: HealthExitMarks,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    refreshRate: () -> Double = { 60.0 },
) {
    public val jank: JankMeter =
        JankMeter(
            report = { screen, pct, hz ->
                telemetry.track(
                    ProductEvent.Jank,
                    mapOf("screen" to screen, "janky_pct_range" to pct, "refresh_hz" to hz),
                )
            },
            refreshRate = refreshRate,
        )

    private var context: HealthDeviceContext? = null
    private var coldReported = false
    private var exitRead = false
    private var started = false
    private val memorySent = HashMap<String, Long>()
    private var liveKeys: Map<String, Any?> = emptyMap()
    private var liveProperties: Map<String, Any?> = emptyMap()

    /** Starts following the two switches. Idempotent. */
    public fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(telemetry.usage, telemetry.crashes) { u, c -> u to c }
                .distinctUntilChanged()
                .collect { (usage, crashes) -> apply(usage, crashes) }
        }
    }

    private suspend fun apply(usage: Boolean, crashes: Boolean) {
        if (usage && telemetry.available) {
            jank.start()
            applyUsage()
        } else {
            jank.stop()
        }
        if (crashes && telemetry.available) applyCrashes()
    }

    private suspend fun readContext(): HealthDeviceContext? =
        context ?: runCatching { platform.deviceContext() }.getOrNull().also { context = it }

    /** The device context for a problem report the person asked for: read whatever the switches say. */
    public suspend fun deviceContextForReport(): HealthDeviceContext? = readContext()

    /** Free storage in gigabytes as last read, or null before. */
    public val freeGigabytes: Double?
        get() = context?.freeBytes?.takeIf { it >= 0 }?.let { it / HealthDeviceContext.GB }

    private suspend fun applyUsage() {
        val device = readContext()
        if (!telemetry.usage.value) return
        telemetry.setUserProperties((device?.properties ?: emptyMap()) + liveProperties)
        reportColdStart()
    }

    /** Process start to the first frame, once per process, with its `app_cold_start` trace. */
    private fun reportColdStart() {
        if (coldReported) return
        val cold = platform.coldStartMs() ?: return
        coldReported = true
        telemetry.track(ProductEvent.AppStart, mapOf("kind" to "cold", "ms_range" to HealthRanges.appStartMsOf(cold)))
        telemetry.performanceTrace(PerfSchema.APP_COLD_START, cold)
    }

    /** The first frame was drawn: report the cold start now if `usage` is already on. */
    public fun firstFrame() {
        if (telemetry.usage.value && telemetry.available) reportColdStart()
    }

    private suspend fun applyCrashes() {
        val device = readContext()
        if (!telemetry.crashes.value) return
        telemetry.setHealthKeys(
            buildMap {
                put("app_version", platform.appVersion)
                put("build_kind", buildKind(device))
                putAll(liveKeys)
            }
        )
        reportLastExit()
    }

    private fun buildKind(device: HealthDeviceContext?): String =
        platform.declaredBuildKind?.takeIf { it in HealthWords.buildKinds }
            ?: if (device?.fromStore == true) "store" else "sideload"

    /** Android's note on how the previous process ended, once, as a non-fatal carrying only `exit_reason`. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reportLastExit() {
        if (exitRead) return
        exitRead = true
        try {
            val newest = platform.exits(limit = 3).maxByOrNull { it.atMs } ?: return
            if (newest.atMs <= marks.lastExitAtMs()) return
            marks.setLastExitAtMs(newest.atMs)
            val word = exitReasonWord(newest.reason) ?: return
            telemetry.recordFailure("process_exit", mapOf("exit_reason" to word))
        } catch (_: Throwable) {
            // Nothing to report.
        }
    }

    /**
     * The facts that change with the connection: server kind, engine state, number of saved connections, OpenCode
     * version. Cheap when nothing changed.
     */
    public fun updateConnection(
        serverKind: String,
        engineState: String,
        connections: Int,
        userServerKind: String? = null,
        serverVersion: String? = null,
    ) {
        val oc = majorMinorOf(serverVersion)
        val keys =
            buildMap<String, Any?> {
                put("server_kind", serverKind)
                put("engine_state", engineState)
                put("connections", HealthRanges.connectionsOf(connections))
                oc?.let { put("oc_server_version", it) }
            }
        val properties =
            buildMap<String, Any?> {
                userServerKind?.let { put("server_kind", it) }
                oc?.let { put("oc_server_version", it) }
            }
        val changedKeys = keys != liveKeys
        val changedProps = properties != liveProperties
        liveKeys = keys
        liveProperties = properties
        if (changedKeys) telemetry.setHealthKeys(keys)
        if (changedProps) telemetry.setUserProperties((context?.properties ?: emptyMap()) + properties)
    }

    /** A warm start measured to its first frame ([elapsedMs]); only while `usage` is on. */
    public fun warmStart(elapsedMs: Long) {
        if (!telemetry.usage.value || !telemetry.available) return
        telemetry.track(ProductEvent.AppStart, mapOf("kind" to "warm", "ms_range" to HealthRanges.appStartMsOf(elapsedMs)))
    }

    /** Android asked the app to free memory ([level] moderate, low or critical); at most every 5 minutes per level. */
    public fun memoryPressure(level: String) {
        if (!telemetry.usage.value || level !in HealthWords.memoryLevels) return
        val now = clock.now().toEpochMilliseconds()
        val last = memorySent[level]
        if (last != null && now - last < MEMORY_QUIET_MS) return
        memorySent[level] = now
        telemetry.track(ProductEvent.MemoryPressure, mapOf("level" to level))
    }

    private companion object {
        const val MEMORY_QUIET_MS = 5 * 60 * 1000L
    }
}
