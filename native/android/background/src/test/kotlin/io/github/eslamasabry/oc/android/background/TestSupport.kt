package io.github.eslamasabry.oc.android.background

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope

/** The fixed stamp of the Flutter pause tests (`_at`). */
internal const val AT = 1_791_428_400_000L

internal val unpaused = PauseState.Off
internal val running = PauseState.Running

internal fun pausedFor(reason: PauseReason = PauseReason.TimeLimit, at: Long = AT, canResume: Boolean = true) =
    PauseState(supported = true, paused = true, reason = reason, atMs = at, canResume = canResume)

/** Flutter `_status(active)`: enabled/active together, notifications granted, the matching receipt. */
internal fun status(active: Boolean, pause: PauseState? = if (active) running else unpaused, enabled: Boolean? = active) =
    ServiceStatus(
        enabled = enabled,
        active = active,
        notificationGranted = true,
        batteryOptimizationIgnored = false,
        pause = pause,
    )

internal fun <T> ok(value: T): ServiceReply<T> = ServiceReply.Ok(value)

internal fun refused(code: String): ServiceReply<Nothing> = ServiceReply.Refused(ServiceRefusal(code))

internal val fixedClock: Clock =
    object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(AT + 60_000L)
    }

/** The native service as the Flutter tests' `invoke` fakes saw it; [calls] uses the channel's method names. */
internal class FakeLivePort : LiveServicePort {
    val calls = mutableListOf<String>()
    var onPause: suspend () -> ServiceReply<PauseState> = { ok(unpaused) }
    var onEnable: suspend () -> ServiceReply<ServiceStatus> = { ok(status(true)) }
    var onDisable: suspend () -> ServiceReply<ServiceStatus> = { ok(status(false)) }
    var onStatus: suspend () -> ServiceReply<ServiceStatus> = { ok(status(false)) }
    var onBattery: suspend () -> ServiceReply<ServiceStatus> = { ok(status(false)) }

    /** Every method answers the same (the Flutter `invoke: (method, …) async => …` fakes). */
    fun all(reply: suspend (String) -> ServiceReply<ServiceStatus>) {
        onEnable = { reply("enable") }
        onDisable = { reply("disable") }
        onStatus = { reply("getStatus") }
        onBattery = { reply("requestBatteryOptimizationExemption") }
    }

    override suspend fun pause(): ServiceReply<PauseState> {
        calls += "getBackgroundPause"
        return onPause()
    }

    override suspend fun enable(): ServiceReply<ServiceStatus> {
        calls += "enable"
        return onEnable()
    }

    override suspend fun disable(): ServiceReply<ServiceStatus> {
        calls += "disable"
        return onDisable()
    }

    override suspend fun status(): ServiceReply<ServiceStatus> {
        calls += "getStatus"
        return onStatus()
    }

    override suspend fun requestBatteryOptimizationExemption(): ServiceReply<ServiceStatus> {
        calls += "requestBatteryOptimizationExemption"
        return onBattery()
    }
}

/** `oc.keepLiveInBackground` in memory; null = never saved. */
internal class FakeOptIn(var value: Boolean? = null) : LiveOptInStore {
    var failWrites = false

    override fun read(): Boolean? = value

    override suspend fun write(value: Boolean): Boolean {
        if (failWrites) return false
        this.value = value
        return true
    }
}

internal class MutableSupport(var supported: Boolean = true) : BackgroundSupport {
    override fun supported(): Boolean = supported
}

internal fun liveMode(
    scope: CoroutineScope,
    port: LiveServicePort,
    optIn: LiveOptInStore = FakeOptIn(),
    support: BackgroundSupport = MutableSupport(),
): LiveModeController = LiveModeController(port, optIn, support, scope, fixedClock)
