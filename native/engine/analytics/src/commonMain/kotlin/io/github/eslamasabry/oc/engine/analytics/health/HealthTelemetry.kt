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
public data class HealthExit(val reason: Int, val atMs: Long, val foreground: Boolean? = null)

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

    /** This app's build number (`57`), for the problem report. */
    public val appBuild: String
        get() = ""

    /** Android's release (`15`), for the problem report. */
    public val osRelease: String
        get() = ""
}

/** Where the newest reported process end is kept (`oc.telemetry.lastExitAt`, Flutter's key). */
public interface HealthExitMarks {
    public fun lastExitAtMs(): Long

    public suspend fun setLastExitAtMs(atMs: Long)

    public fun lastAnalyticsExitAtMs(): Long = 0L
    public suspend fun setLastAnalyticsExitAtMs(atMs: Long): Boolean = false
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

/** Analytics includes normal exits as other; native crashes join crash. */
public fun appExitReasonWord(reason: Int): String = when (val word = exitReasonWord(reason)) {
    "crash_native" -> "crash"
    null -> "other"
    else -> word
}

private const val EXIT_SELF = 1
private const val REASON_CRASH = 4
private const val REASON_CRASH_NATIVE = 5
private const val REASON_ANR = 6
private const val REASON_INITIALIZATION_FAILURE = 7
private const val PACKAGE_UPDATED = 16

private fun isFaultExitReason(reason: Int): Boolean =
    reason == REASON_CRASH ||
        reason == REASON_CRASH_NATIVE ||
        reason == REASON_ANR ||
        reason == REASON_INITIALIZATION_FAILURE

/** The major.minor of a server version (`2.0.28` becomes `2.0`), or null when it does not start that way. */
public fun majorMinorOf(version: String?): String? {
    val m = Regex("^v?(\\d{1,2})\\.(\\d{1,2})").find(version?.trim() ?: return null) ?: return null
    return "${m.groupValues[1]}.${m.groupValues[2]}"
}

/**
 * Slow frames per kind of page, once per session per page kind (Flutter `JankMeter`): every visit to a kind of page in
 * one foreground session is summed, and the report is made when the session ends, so a short calm first visit cannot
 * stand for a long janky one (analytics truth, 2026-11-16). A kind of page with fewer than [minFrames] frames in the
 * session says nothing. The platform feeds frame
 * counts only while [running] (it follows `usage`); with the switch off nothing is measured and nothing is kept.
 *
 * Two measures go out side by side (c2-performance, 2026-11-15), so today's numbers stay comparable for a release:
 * - `janky_pct_range`, as before: frames whose whole duration took longer than one refresh period. Android pipelines
 *   frames, so a frame can take longer than a period and still be shown on time: this overstates (on a Galaxy S22 an
 *   hour read 60 % by it, 8.3 % by Android's own count).
 * - `late_pct_range`: frames that missed their own deadline, Android's judgement (API 31+; one period below), first
 *   frames of a window left out (a page's first draw is not a frame anyone waited on in a scroll).
 * With it `seen_hz`: the refresh rate most of that page's frames were given (a phone switches between 60, 90 and
 * 120 Hz; `refresh_hz` stays the display's mode when the report is made).
 */
public class JankMeter(
    private val report: (screen: ProductScreen, frames: JankReport) -> Unit,
    private val refreshRate: () -> Double = { 60.0 },
    private val minFrames: Int = 30,
    /** What the person is doing in the chat right now (a word of [HealthWords.interactions]); read per frame batch. */
    private val interaction: () -> String? = { null },
) {
    public var running: Boolean = false
        private set

    private var screen = ProductScreen.Other

    /** One page kind's frames; in the chat also split by what was going on (null elsewhere). */
    private data class Key(val screen: ProductScreen, val interaction: String?)

    private val counts = HashMap<Key, Counts>()

    private class Counts {
        var frames = 0
        var janky = 0
        var frozen = 0
        var judged = 0
        var late = 0
        val hz = HashMap<String, Int>()
    }

    public fun start() {
        running = true
    }

    public fun stop() {
        running = false
        counts.clear()
    }

    /** A page of this kind came into view: its frames add to the session's sum for that kind. */
    public fun screen(next: ProductScreen) {
        screen = next
    }

    /**
     * The newest frames: [total] drawn, [slow] over one refresh period, [frozen] of 700 ms or more (also in [slow]);
     * [judged] of them judged against their deadline (first draws left out), [late] of those past it; [hz] the refresh
     * rate they were given (0 when unknown).
     */
    @Suppress("LongParameterList") // one number per counter the platform reports; all but two have defaults
    public fun onFrames(total: Int, slow: Int, frozen: Int = 0, judged: Int = total, late: Int = slow, hz: Double = 0.0) {
        if (!running) return
        val word = if (screen == ProductScreen.Chat) interaction()?.takeIf { it in HealthWords.interactions } else null
        val c = counts.getOrPut(Key(screen, word)) { Counts() }
        c.frames += total
        c.janky += slow
        c.frozen += frozen
        c.judged += judged
        c.late += late
        if (hz > 0.0 && total > 0) {
            val bucket = HealthRanges.refreshHzOf(hz)
            c.hz[bucket] = (c.hz[bucket] ?: 0) + total
        }
    }

    /** The app went to the background: one report per kind of page, from the session's summed visits; then a new session. */
    public fun endSession() {
        if (!running) return
        counts.keys.toList().forEach(::flush)
        counts.clear()
    }

    private fun flush(key: Key) {
        val c = counts[key] ?: return
        if (c.frames < minFrames) return
        val shown = HealthRanges.refreshHzOf(refreshRate())
        report(
            key.screen,
            JankReport(
                pctRange = HealthRanges.jankPctOf(c.janky * 100.0 / c.frames),
                frozenRange = HealthRanges.frozenOf(c.frozen),
                hz = shown,
                latePctRange = HealthRanges.jankPctOf(if (c.judged > 0) c.late * 100.0 / c.judged else 0.0),
                seenHz = c.hz.maxByOrNull { it.value }?.key ?: shown,
                interaction = key.interaction,
            ),
        )
    }
}

/**
 * How one drawn frame counts (c2-performance, 2026-11-15), from the platform's frame metrics: [totalNs] the frame's
 * whole duration, [deadlineNs] the time it was given (Android 12+, else null), [periodNs] one refresh period now,
 * [firstDraw] a window's first frame.
 */
public object FrameJudge {
    /** A frame this slow is frozen by Android vitals' definition (700 ms). */
    public const val FROZEN_NS: Long = 700_000_000L

    public data class Frame(val slow: Int, val frozen: Int, val judged: Int, val late: Int, val hz: Double)

    public fun of(totalNs: Long, deadlineNs: Long?, periodNs: Long, firstDraw: Boolean): Frame {
        val deadline = deadlineNs?.takeIf { it > 0 } ?: periodNs
        return Frame(
            slow = if (totalNs > periodNs) 1 else 0,
            frozen = if (totalNs > FROZEN_NS) 1 else 0,
            judged = if (firstDraw) 0 else 1,
            late = if (!firstDraw && totalNs > deadline) 1 else 0,
            hz = if (periodNs > 0) 1e9 / periodNs else 0.0,
        )
    }
}

/** One page kind's frames, as the `jank` event sends them (see [JankMeter]). */
public data class JankReport(
    val pctRange: String,
    val frozenRange: String,
    val hz: String,
    val latePctRange: String,
    val seenHz: String,
    /** The chat's own split: what was going on (null for any other page). */
    val interaction: String? = null,
) {
    /** The event's parameters besides `screen`. */
    public fun params(): Map<String, String> =
        buildMap {
            put("janky_pct_range", pctRange)
            put("frozen_range", frozenRange)
            put("refresh_hz", hz)
            put("late_pct_range", latePctRange)
            put("seen_hz", seenHz)
            if (interaction != null) put("interaction", interaction)
        }
}

/**
 * Wires the health telemetry together once the reporter exists (Flutter `HealthTelemetry`): device context, app start,
 * jank, memory pressure, the custom keys and the last process end. Every part waits for its own consent and does
 * nothing while that is off.
 */
// One start-up wiring for every health report, each part waiting on its own consent; the inputs are injected parts.
@Suppress("TooManyFunctions", "LongParameterList")
public class HealthTelemetry(
    private val telemetry: Telemetry,
    private val platform: HealthPlatform,
    private val marks: HealthExitMarks,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    refreshRate: () -> Double = { 60.0 },
    interaction: () -> String? = { null },
) {
    public val jank: JankMeter =
        JankMeter(
            report = { screen, frames -> telemetry.track(ProductEvent.Jank, mapOf("screen" to screen) + frames.params()) },
            refreshRate = refreshRate,
            interaction = interaction,
        )

    private var context: HealthDeviceContext? = null
    private var coldReported = false
    private var exitRead = false
    private var analyticsExitRead = false
    private var previousExitRead = false
    private var previousExit: HealthExit? = null
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
        reportAnalyticsExit()
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

    private suspend fun readPreviousExit(): HealthExit? {
        if (previousExitRead) return previousExit
        previousExitRead = true
        previousExit = try {
            platform.exits(limit = 3).maxByOrNull { it.atMs }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) { null }
        return previousExit
    }

    /** Once per retained exit, only under usage consent. The timestamp stays on this phone for deduplication. */
    private suspend fun reportAnalyticsExit() {
        if (analyticsExitRead) return
        analyticsExitRead = true
        val newest = readPreviousExit() ?: return
        if (!telemetry.usage.value || newest.atMs <= marks.lastAnalyticsExitAtMs()) return
        if (!marks.setLastAnalyticsExitAtMs(newest.atMs) || !telemetry.usage.value) return
        telemetry.track(ProductEvent.AppExit, buildMap {
            put("exit_reason", appExitReasonWord(newest.reason))
            newest.foreground?.let { put("foreground", if (it) "yes" else "no") }
        })
    }

    /**
     * Records a `process_exit` non-fatal only when the app died in the foreground or from our own fault
     * (crash, native crash, ANR, or initialization failure); silent background OS reclaims and user-requested
     * stops stay analytics-only.
     */
    private suspend fun reportLastExit() {
        if (exitRead) return
        exitRead = true
        val newest = readPreviousExit() ?: return
        if (!telemetry.crashes.value || newest.atMs <= marks.lastExitAtMs()) return
        marks.setLastExitAtMs(newest.atMs)
        val word = exitReasonWord(newest.reason) ?: return
        val report = word != "user_requested" && (newest.foreground == true || isFaultExitReason(newest.reason))
        if (report) telemetry.recordFailure("process_exit", mapOf("exit_reason" to word))
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
