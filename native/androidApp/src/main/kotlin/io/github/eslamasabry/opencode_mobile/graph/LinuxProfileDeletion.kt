package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser

/**
 * Recovery must stop admitting and drain before the profile sweep can erase its owner and saved
 * intent.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class LinuxProfileCloser(private val runtime: BuiltinRuntime) : ProfileCloser {
    override val owner: String = ":engine:runtime"

    override fun stopAdmission(profile: ProfileId) = runtime.stopProfileAdmission(profile.raw)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        runtime.drainProfile(profile.raw)

    override fun reopen(profile: ProfileId) = runtime.reopenProfile(profile.raw)
}

/** The owner pointer is shared app storage, so the generic per-profile key sweep cannot find it. */
@Inject
@ContributesIntoSet(
    AppScope::class,
    binding = binding<io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner>(),
)
class LinuxOwnerPointerCleaner(private val stores: PreviewStores) :
    io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner {
    private val cleaner =
        io.github.eslamasabry.oc.engine.profiles.EmbeddedRecordCleaner(
            stores.prefs,
            stores.lanes.app,
            io.github.eslamasabry.oc.engine.profiles.FlutterEraEdits.BuiltinServerOwner,
            io.github.eslamasabry.oc.engine.profiles.DeletionStep.DEVICE_SURFACES,
        )
    override val owner: String = ":engine:runtime"
    override val order: Int = io.github.eslamasabry.oc.engine.profiles.DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(
        profileId: ProfileId,
        options: io.github.eslamasabry.oc.engine.profiles.DeletionOptions,
    ): Outcome<Int> = cleaner.clear(profileId, options)
}
