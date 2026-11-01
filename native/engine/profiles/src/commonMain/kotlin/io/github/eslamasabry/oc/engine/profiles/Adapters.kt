package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok

/**
 * Adapts a per-profile store with the S1-ENG contract (`closeProfile(id)` synchronous, `forgetProfile(id):
 * Outcome<Unit>` draining then removing) into a [ProfileCloser]; [reopen] is a no-op unless the store offers one.
 */
public fun profileCloser(
    owner: String,
    closeProfile: (ProfileId) -> Unit,
    drain: suspend (ProfileId) -> Outcome<Unit> = { Unit.ok() },
    reopen: (ProfileId) -> Unit = {},
): ProfileCloser = object : ProfileCloser {
    override val owner: String = owner

    override fun stopAdmission(profile: ProfileId) = closeProfile(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = drain(profile)

    override fun reopen(profile: ProfileId) = reopen(profile)
}

/** Adapts a store's `forgetProfile(id): Outcome<Unit>` into a [ProfileResourceCleaner] at [order]. */
public fun forgetProfileCleaner(
    owner: String,
    order: Int,
    forgetProfile: suspend (ProfileId) -> Outcome<Unit>,
): ProfileResourceCleaner = object : ProfileResourceCleaner {
    override val owner: String = owner
    override val order: Int = order

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> = forgetProfile(profileId).map { 0 }
}
