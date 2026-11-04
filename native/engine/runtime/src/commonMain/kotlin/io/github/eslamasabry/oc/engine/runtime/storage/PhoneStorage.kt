package io.github.eslamasabry.oc.engine.runtime.storage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.runtime.projects.ProjectRemoval
import io.github.eslamasabry.oc.engine.runtime.projects.ProjectStorage
import io.github.eslamasabry.oc.engine.runtime.setup.LinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.channelInt

/** An absolute free-space warning, not a percentage of the phone's capacity. */
public enum class StoragePressure { Unknown, NearlyFull, AboveThreshold }

/** Whether an explicit in-app host scan produced a measurement. */
public enum class HostStorageAvailability { NotRequested, Unavailable, Available }

/**
 * A transient measurement (port of lib/platform/phone_storage.dart `PhoneStorageSnapshot`). Null is unknown; zero is a
 * measured value. Nothing is persisted: read again after cleanup or on returning to the app.
 */
public data class PhoneStorageSnapshot(
    /** Space available on the filesystem with the app's private files; not capacity, not a remote or Termux disk. */
    val availableBytes: Long?,
    val pressure: StoragePressure,
    /** Logical sizes of the built-in Ubuntu and its projects; not allocation, not the whole app. */
    val builtinHost: ProjectStorage?,
    val builtinAvailability: HostStorageAvailability,
)

/**
 * The two read-only sources, each an existing native method: the voice channel's `getDeviceInfo`
 * (`availableStorageBytes`) and the Linux channel's `projectStorage` ([io.github.eslamasabry.oc.engine.runtime.projects.ProjectRemoval.measure]).
 * No new channel method, no permission, no background service.
 */
public interface PhoneStorageSources {
    /** `oc/voice` `getDeviceInfo` → `availableStorageBytes`; Ok(null) when the device does not say. */
    public suspend fun availableBytes(): Outcome<Long?>

    /** `projectStorage`: a recursive walk, so only on explicit request. */
    public suspend fun builtinStorage(): Outcome<ProjectStorage>
}

/**
 * The default sources over the existing native methods: [voice] is the `oc/voice` channel (any [LinuxChannel]-shaped
 * port works: it is only `invoke`), [linux] the built-in Linux channel.
 */
public class ChannelPhoneStorageSources(private val voice: LinuxChannel, linux: LinuxChannel) : PhoneStorageSources {
    private val removal = ProjectRemoval(linux)

    override suspend fun availableBytes(): Outcome<Long?> =
        voice.invoke("getDeviceInfo", null).map { channelInt((it as? Map<*, *>)?.get("availableStorageBytes")) }

    override suspend fun builtinStorage(): Outcome<ProjectStorage> = removal.measure()
}

/**
 * The free-space warning (port of `PhoneStorageService`; B-ANDR-052). Read on entry or explicit refresh, never from a
 * build or a timer. Each source fails to unknown on its own and never erases the other one; their failures are not
 * shown. A platform that is not Android performs no probe at all. [nearlyFullAtBytes] (inclusive, default 1 GiB) is
 * app policy, not Android's low-storage signal.
 */
public class PhoneStorage(
    private val sources: PhoneStorageSources,
    /** PlatformCapabilities.isAndroid: the capability to try a local measurement, not proof that it works. */
    public val isSupported: Boolean,
    public val nearlyFullAtBytes: Long = DEFAULT_NEARLY_FULL_AT_BYTES,
) {
    init {
        require(nearlyFullAtBytes >= 0) { "nearlyFullAtBytes must not be negative" }
    }

    /** [includeBuiltinHost] is false by default, to avoid an implicit disk walk. */
    public suspend fun read(includeBuiltinHost: Boolean = false): PhoneStorageSnapshot {
        var available: Long? = null
        var host: ProjectStorage? = null
        if (isSupported) {
            available = (sources.availableBytes() as? Outcome.Ok)?.value?.takeIf { it >= 0 }
            if (includeBuiltinHost) {
                host = (sources.builtinStorage() as? Outcome.Ok)?.value?.takeIf {
                    it.runtimeBytes >= 0 && it.projectsBytes >= 0 && it.measuredAtMilliseconds >= 0
                }
            }
        }
        return PhoneStorageSnapshot(
            availableBytes = available,
            pressure =
                when {
                    available == null -> StoragePressure.Unknown
                    available <= nearlyFullAtBytes -> StoragePressure.NearlyFull
                    else -> StoragePressure.AboveThreshold
                },
            builtinHost = host,
            builtinAvailability =
                when {
                    !includeBuiltinHost -> HostStorageAvailability.NotRequested
                    host == null -> HostStorageAvailability.Unavailable
                    else -> HostStorageAvailability.Available
                },
        )
    }

    public companion object {
        public const val DEFAULT_NEARLY_FULL_AT_BYTES: Long = 1024L * 1024 * 1024
    }
}
