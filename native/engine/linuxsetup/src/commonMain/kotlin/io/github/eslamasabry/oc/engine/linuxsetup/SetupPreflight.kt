package io.github.eslamasabry.oc.engine.linuxsetup

/**
 * Pre-flight checks before phone setup downloads anything (port of lib/builtin/setup/preflight.dart): the CPU ABI the
 * Ubuntu rootfs ships for, total RAM, and free space against what the chosen components need. An unknown reading
 * never blocks: the person can still try.
 */
public object SetupPreflight {
    /** The Ubuntu rootfs ships only for these two ABIs. */
    public val supportedAbis: Set<String> = setOf("arm64-v8a", "x86_64")

    /** Total RAM floor (a nominal 2 GB phone reports ~1,972 MB and may set up). */
    public const val MINIMUM_MEMORY_MB: Int = 1800

    /** Under this, setup is allowed with a plain "may be slow" note. */
    public const val COMFORTABLE_MEMORY_MB: Int = 3072

    private const val SCRATCH_BYTES: Long = 64L * 1024 * 1024
    private const val FLOOR_BYTES: Long = 300L * 1000 * 1000

    /**
     * Free space needed before a download starts: twice the download (at least 300 MB), or, for a known extracted
     * payload, the retained download plus the payload plus 64 MiB of scratch, whichever is larger. Saturates.
     */
    public fun requiredFreeBytes(downloadBytes: Long, installedBytes: Long? = null): Long {
        val nonnegative = maxOf(downloadBytes, 0)
        val doubled = if (nonnegative > Long.MAX_VALUE / 2) Long.MAX_VALUE else nonnegative * 2
        val legacy = maxOf(doubled, FLOOR_BYTES)
        if (installedBytes == null || installedBytes <= 0) return legacy
        val peak = saturated(saturated(nonnegative, installedBytes), SCRATCH_BYTES)
        return maxOf(legacy, peak)
    }

    private fun saturated(first: Long, second: Long): Long =
        if (first >= Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

    /** Storage alone, so setup can repeat the same policy between installs. */
    public fun checkStorage(availableBytes: Long?, downloadBytes: Long, installedBytes: Long? = null): SetupPreflightResult {
        if (availableBytes == null) return SetupPreflightResult.Ok
        val required = requiredFreeBytes(downloadBytes, installedBytes)
        return if (availableBytes < required) SetupPreflightResult.LowSpace(required - availableBytes) else SetupPreflightResult.Ok
    }

    /** ABI first (nothing else matters without a rootfs), then RAM floor, then space, then the slow note. */
    public fun check(device: SetupDevice, downloadBytes: Long, installedBytes: Long? = null): SetupPreflightResult {
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

/** What the phone reports (the voice device info in Flutter). Null or empty means unknown. */
public data class SetupDevice(
    val supportedAbis: List<String>,
    val totalMemoryMb: Int?,
    val availableStorageBytes: Long?,
)

public enum class SetupPreflightIssue { UnsupportedAbi, LowMemory, LowSpace }

/** What the pre-flight found. */
public sealed interface SetupPreflightResult {
    public val issue: SetupPreflightIssue?
    public val supported: Boolean get() = issue == null

    /** Setup may go ahead with a note naming [MayBeSlow.totalMemoryMb]. */
    public val mayBeSlow: Boolean get() = this is MayBeSlow

    /** Set for low memory and for a phone that may be slow. */
    public val totalMemoryMb: Int? get() = null
    public val reportedAbi: String? get() = null
    public val bytesToFree: Long get() = 0

    public data object Ok : SetupPreflightResult {
        override val issue: SetupPreflightIssue? get() = null
    }

    public data class MayBeSlow(override val totalMemoryMb: Int) : SetupPreflightResult {
        override val issue: SetupPreflightIssue? get() = null
    }

    public data class UnsupportedAbi(override val reportedAbi: String) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.UnsupportedAbi
    }

    public data class LowMemory(override val totalMemoryMb: Int) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.LowMemory
    }

    public data class LowSpace(override val bytesToFree: Long) : SetupPreflightResult {
        override val issue: SetupPreflightIssue get() = SetupPreflightIssue.LowSpace
    }
}

/** English words of a preflight result (app_en.arb `phoneSetupPreflight*`); [size] is the formatted bytes to free. */
public object SetupPreflightWordsEn {
    public fun headline(result: SetupPreflightResult): String? =
        when (result) {
            is SetupPreflightResult.UnsupportedAbi -> "This phone can't run it"
            is SetupPreflightResult.LowMemory -> "This phone doesn't have enough memory"
            is SetupPreflightResult.LowSpace -> "Not enough free space"
            else -> null
        }

    public fun body(result: SetupPreflightResult, size: String = ""): String? =
        when (result) {
            is SetupPreflightResult.UnsupportedAbi ->
                "This app's Ubuntu only runs on a 64-bit Arm or Intel phone; this one reports ${result.reportedAbi}."
            is SetupPreflightResult.LowMemory ->
                "OpenCode needs a phone with at least ${SetupPreflight.MINIMUM_MEMORY_MB} MB of memory; this one has " +
                    "${result.totalMemoryMb} MB. Run it on a computer instead and connect this phone to it."
            is SetupPreflightResult.LowSpace -> "Free about $size on this phone, then come back to set this up."
            is SetupPreflightResult.MayBeSlow ->
                "It may be slow on this phone, which has ${result.totalMemoryMb} MB of memory."
            SetupPreflightResult.Ok -> null
        }
}
