package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.runtime.ownership.BuiltinOwnerStorage
import io.github.eslamasabry.oc.engine.runtime.ownership.BuiltinServerOwner

/** Shared by actual loopback connection claims and the real Servers › Remove transaction. */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class RuntimeProfileOwner(
    private val stores: PreviewStores,
    private val platform: PlatformCapabilities,
) : ProfileCloser, ProfileResourceCleaner {
    private val pointer =
        BuiltinServerOwner(
            object : BuiltinOwnerStorage {
                override suspend fun read(): Outcome<String?> {
                    val read = attempt({ Failure.StorageRefused() }) { stores.prefs.get(KEY) }
                    if (read is Outcome.Failed) return read
                    return when (val value = (read as Outcome.Ok).value) {
                        null -> null.ok()
                        is PrefValue.Str -> value.value.ok()
                        else -> Outcome.Failed(Failure.StorageRefused())
                    }
                }

                override suspend fun write(value: String): Outcome<Unit> =
                    stores.prefs.commit(stores.lanes.app) { putString(KEY, value) }
            }
        )
    override val owner: String = ":engine:runtime (phone server owner)"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    suspend fun claim(profile: ProfileId, origin: ServerOrigin): Outcome<Unit> {
        if (!platform.builtinLinux || origin != ServerOrigin("http", "127.0.0.1", 4097))
            return Unit.ok()
        return pointer.claim(profile.raw) { stores.profiles.profile(profile) != null }
    }

    override fun stopAdmission(profile: ProfileId) {
        pointer.stopAdmission(profile.raw)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        pointer.drain()
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        if (stores.profiles.profile(profile) != null) pointer.reopen(profile.raw)
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        pointer.clearProfile(profileId.raw)

    private companion object {
        const val KEY = "oc.builtinServerOwner"
    }
}
