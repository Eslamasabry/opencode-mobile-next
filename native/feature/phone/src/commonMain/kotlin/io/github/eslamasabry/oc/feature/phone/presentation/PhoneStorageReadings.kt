package io.github.eslamasabry.oc.feature.phone.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.engine.runtime.storage.HostStorageAvailability
import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSnapshot
import io.github.eslamasabry.oc.engine.runtime.storage.StoragePressure

/** The local read-only storage measurement. Large recursive scans require an explicit request. */
public interface PhoneStorageReadings {
    public val supported: Boolean

    public suspend fun read(includeBuiltinHost: Boolean = false): PhoneStorageSnapshot
}

@ContributesBinding(AppScope::class)
@Inject
public class NoPhoneStorageReadings : PhoneStorageReadings {
    override val supported: Boolean = false

    override suspend fun read(includeBuiltinHost: Boolean): PhoneStorageSnapshot =
        PhoneStorageSnapshot(
            null,
            StoragePressure.Unknown,
            null,
            HostStorageAvailability.NotRequested,
        )
}
