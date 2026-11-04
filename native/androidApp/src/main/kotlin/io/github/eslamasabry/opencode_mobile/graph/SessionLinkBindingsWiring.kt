package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.SessionLinkBindingOwners
import kotlinx.coroutines.launch

/** Stores metadata only. No transport is registered and address handoff remains unavailable. */
@BindingContainer
@ContributesTo(AppScope::class)
object SessionLinkBindingsWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun owners(stores: PreviewStores, redactor: Redactor): SessionLinkBindingOwners =
        SessionLinkBindingOwners(stores.prefs, stores.lanes, stores.profiles, redactor)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class SessionLinkBindingDeletion(
    private val bindings: SessionLinkBindingOwners,
    private val scope: AppCoroutineScope,
) : ProfileCloser {
    override val owner: String = ":engine:profiles/session-link-bindings"

    override fun stopAdmission(profile: ProfileId) {
        bindings.stopAdmission(profile)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        bindings.drain(profile)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        scope.launch { bindings.cancelDeletion(profile) }
    }
}
