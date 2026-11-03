package io.github.eslamasabry.oc.engine.runtime.setup

/**
 * Port of lib/builtin/setup/preflight.dart (pre-flight checks before phone setup downloads anything): the CPU ABI the
 * Ubuntu rootfs ships for, total RAM, and free space against what the chosen components need. An unknown reading never
 * blocks: the person can still try.
 */
public object SetupPreflight {
    /** The ABIs BuiltinLinux.kt ships a rootfs for. */
    public val supportedAbis: Set<String> = setOf("arm64-v8a", "x86_64")

    /** Total RAM below which setup is refused (coordinator decision B2, 2026-09-28). */
    public const val MINIMUM_MEMORY_MB: Int = 1800

    /** Total RAM under which setup is allowed with a plain "may be slow" note. */
    public const val COMFORTABLE_MEMORY_MB: Int = 3072

    private const val SCRATCH_BYTES: Long = 64L * 1024 * 1024
    private const val FLOOR_BYTES: Long = 300L * 1000 * 1000

    /**
     * Free space needed before a download starts. A known extracted payload coexists with its retained archive during
     * installation, plus 64 MiB of scratch. Unknown payload sizes keep the 300 MB / 2x policy. Saturates at
     * [Long.MAX_VALUE] instead of overflowing.
     */
    public fun requiredFreeBytes(downloadBytes: Long, installedBytes: Long? = null): Long {
        val nonnegative = maxOf(downloadBytes, 0L)
        val doubled = if (nonnegative > Long.MAX_VALUE / 2) Long.MAX_VALUE else nonnegative * 2
        val legacy = maxOf(doubled, FLOOR_BYTES)
        if (installedBytes == null || installedBytes <= 0) return legacy
        val retainedAndExtracted = saturated(nonnegative, installedBytes)
        val peak = saturated(retainedAndExtracted, SCRATCH_BYTES)
        return maxOf(legacy, peak)
    }

    private fun saturated(first: Long, second: Long): Long =
        if (first >= Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

    /** Storage alone, so setup can repeat the same policy between installs. An unavailable reading allows a try. */
    public fun checkStorage(availableBytes: Long?, downloadBytes: Long, installedBytes: Long? = null): SetupPreflightResult {
        if (availableBytes == null) return SetupPreflightResult.Ok
        val required = requiredFreeBytes(downloadBytes, installedBytes)
        return if (availableBytes < required) SetupPreflightResult.LowSpace(required - availableBytes)
        else SetupPreflightResult.Ok
    }

    /** ABI first, then RAM, then free space; RAM between the floor and comfortable is [SetupPreflightResult.MayBeSlow]. */
    public fun check(device: SetupDeviceInfo, downloadBytes: Long, installedBytes: Long? = null): SetupPreflightResult {
        val abis = device.supportedAbis
        if (abis.isNotEmpty() && abis.none { it in supportedAbis }) return SetupPreflightResult.UnsupportedAbi(abis.first())
        val memory = device.totalMemoryMb
        if (memory != null && memory < MINIMUM_MEMORY_MB) return SetupPreflightResult.LowMemory(memory)
        val storage = checkStorage(device.availableStorageBytes, downloadBytes, installedBytes)
        if (!storage.supported) return storage
        if (memory != null && memory < COMFORTABLE_MEMORY_MB) return SetupPreflightResult.MayBeSlow(memory)
        return SetupPreflightResult.Ok
    }
}

/** What the device reports (the `oc/voice` device info): null/empty is an unknown reading. */
public data class SetupDeviceInfo(
    val supportedAbis: List<String>,
    val totalMemoryMb: Int?,
    val availableStorageBytes: Long?,
)

public enum class SetupPreflightIssue { UnsupportedAbi, LowMemory, LowSpace }

/** What the pre-flight found (`SetupPreflightResult`). */
public sealed interface SetupPreflightResult {
    public val issue: SetupPreflightIssue?

    public val supported: Boolean get() = issue == null

    public data object Ok : SetupPreflightResult {
        override val issue: SetupPreflightIssue? get() = null
    }

    /** Nothing blocks setup, but the phone has under the comfortable RAM. */
    public data class MayBeSlow(val totalMemoryMb: Int) : SetupPreflightResult {
        override val issue: SetupPreflightIssue? get() = null
    }

    public data class UnsupportedAbi(val reportedAbi: String) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.UnsupportedAbi
    }

    public data class LowMemory(val totalMemoryMb: Int) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.LowMemory
    }

    public data class LowSpace(val bytesToFree: Long) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.LowSpace
    }
}
