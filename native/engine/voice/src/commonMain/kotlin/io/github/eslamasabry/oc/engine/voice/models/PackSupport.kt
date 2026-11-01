package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.state.PackId

/** Why a pack cannot be used on this phone (Dart `VoicePackUnsupported` plus the platform case). */
public enum class PackBlock(public val problem: MediaProblem?) {
    /** No capture path on this platform: the feature is `Unavailable`, not a media problem. */
    Platform(null),
    Abi(MediaProblem.PackAbiUnsupported),
    Memory(MediaProblem.PackMemoryTooSmall),
    Storage(MediaProblem.PackStorageTooSmall),
}

public sealed interface Support {
    public data object Supported : Support

    public data class Blocked(val block: PackBlock, val requiredStorageBytes: Long? = null) : Support
}

/** Plan 10 §7.3 (B-MEDIA-002, 011, 024). The gate is TOTAL RAM, never the Java heap class. */
public object PackSupport {
    private const val MIB = 1024L * 1024L
    private val runtimeAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    /** Free space needed: the bytes still to fetch plus `max(128 MiB, size / 4)` (M3: partials count as fetched). */
    public fun requiredStorageBytes(pack: ModelPackSpec, partialBytes: Long = 0): Long =
        (pack.totalBytes - partialBytes).coerceAtLeast(0) + maxOf(128 * MIB, pack.totalBytes / 4)

    /**
     * [installed] packs are not storage-checked unless [replacing]. Unknown RAM blocks nothing here (the person may try
     * a manual pick); unknown storage blocks nothing either.
     */
    public fun supportFor(
        pack: ModelPackSpec,
        device: DeviceProbeResult,
        installed: Boolean = false,
        partialBytes: Long = 0,
        replacing: Boolean = false,
    ): Support {
        val known =
            when (device) {
                DeviceProbeResult.Unsupported -> return Support.Blocked(PackBlock.Platform)
                DeviceProbeResult.Unknown -> return Support.Supported
                is DeviceProbeResult.Known -> device
            }
        if (known.supportedAbis.isNotEmpty() && known.supportedAbis.none { it in runtimeAbis }) {
            return Support.Blocked(PackBlock.Abi)
        }
        val ram = known.totalRamMb
        if (ram != null && ram < pack.minTotalRamMb) return Support.Blocked(PackBlock.Memory)
        val available = known.availableStorageBytes
        val required = requiredStorageBytes(pack, partialBytes)
        if ((!installed || replacing) && available != null && available < required) {
            return Support.Blocked(PackBlock.Storage, required)
        }
        return Support.Supported
    }

    /** Supported, or short only of storage (storage never changes the automatic choice). */
    public fun isEligible(support: Support): Boolean =
        support == Support.Supported || (support is Support.Blocked && support.block == PackBlock.Storage)
}

/** Plan 10 §7.3 `automaticPack` and Dart `automaticVoicePack` (B-MEDIA-011). */
public object AutomaticPack {
    /**
     * None when capture is unsupported, there is no microphone, or total RAM is unknown or zero. A selected pack that
     * is installed and eligible stays; otherwise the eligible speech pack with the highest RAM floor (small > base >
     * tiny). A stored preference alone never overrides the phone's capacity.
     */
    public fun choose(
        selected: PackId,
        device: DeviceProbeResult,
        isInstalled: (PackId) -> Boolean,
        partialBytes: (PackId) -> Long = { 0 },
        packs: List<ModelPackSpec> = ModelCatalog.speech,
    ): ModelPackSpec? {
        val known = device as? DeviceProbeResult.Known ?: return null
        if (!known.hasMicrophone) return null
        val ram = known.totalRamMb
        if (ram == null || ram <= 0) return null
        fun eligible(pack: ModelPackSpec) =
            PackSupport.isEligible(PackSupport.supportFor(pack, device, isInstalled(pack.id), partialBytes(pack.id)))
        val chosen = packs.firstOrNull { it.id == selected }
        if (chosen != null && isInstalled(chosen.id) && eligible(chosen)) return chosen
        return packs.filter(::eligible).maxByOrNull { it.minTotalRamMb }
    }

    /**
     * The interaction default (Dart `InteractionDefaults.voicePack`, B-MEDIA-002): physical RAM thresholds over the
     * supported packs; an explicit choice is kept only when the RAM allows it; unknown RAM → the smallest pack.
     */
    public fun defaultFor(totalRamMb: Long?, supported: List<ModelPackSpec>, explicitId: String? = null): ModelPackSpec? {
        val fitting = supported.filter { totalRamMb == null || totalRamMb >= it.minTotalRamMb }
        fitting.firstOrNull { it.id.raw == explicitId }?.let { return it }
        if (totalRamMb == null) return supported.minByOrNull { it.minTotalRamMb }
        return fitting.maxByOrNull { it.minTotalRamMb }
    }
}
