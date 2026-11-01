package io.github.eslamasabry.oc.engine.voice.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** Plan 10 §4.5. Android: `AndroidDeviceProbe` (5 s timeout → [Unknown]); desktop binds [Unsupported]. */
public sealed interface DeviceProbeResult {
    public data class Known(
        /** `ActivityManager.MemoryInfo.totalMem / 1 MiB`: the ONLY memory value that gates a pack. */
        val totalRamMb: Long?,
        val availableStorageBytes: Long?,
        val supportedAbis: ImmutableList<String>,
        val hasMicrophone: Boolean,
        val lowRamDevice: Boolean,
        /** The Java heap class: diagnostics only, never a gate (memory `voice-model-memory-gate`). */
        val memoryClassMb: Int?,
        val cpuCount: Int,
    ) : DeviceProbeResult

    /** The probe failed or timed out: capture is assumed possible, every pack is shown. */
    public data object Unknown : DeviceProbeResult

    /** A platform with no capture path at all (desktop now): nothing to measure. */
    public data object Unsupported : DeviceProbeResult
}

public data class MemoryNow(val availMb: Long, val thresholdMb: Long, val low: Boolean)

public interface DeviceProbe {
    public suspend fun probe(): DeviceProbeResult

    /** Cheap, no timeout: `ActivityManager.MemoryInfo` now (the §6.3 memory check). null off Android. */
    public fun memoryNow(): MemoryNow?
}

/** Plan 10 §7.6. */
public enum class SetupNetwork { Unmetered, Metered, Offline, Unknown }

public interface SetupNetworkSource {
    public suspend fun current(): SetupNetwork

    /** Each class change, distinct. */
    public val changes: Flow<SetupNetwork>
}

/** Desktop and tests: no capture path, so every answer says so honestly (B-MEDIA-032). */
public object UnsupportedDeviceProbe : DeviceProbe {
    override suspend fun probe(): DeviceProbeResult = DeviceProbeResult.Unsupported

    override fun memoryNow(): MemoryNow? = null
}
