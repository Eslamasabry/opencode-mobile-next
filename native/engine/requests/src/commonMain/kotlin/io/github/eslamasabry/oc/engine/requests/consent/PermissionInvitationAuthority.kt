package io.github.eslamasabry.oc.engine.requests.consent

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** Settings and visible request cards share one durable invitation owner for each saved server. */
public interface PermissionInvitationAuthority {
    public val revision: kotlinx.coroutines.flow.StateFlow<Long>

    public suspend fun forProfile(profileId: ProfileId): AlwaysAllowInvitation?
}

@ContributesTo(AppScope::class)
public interface PermissionInvitationBindings {
    @Multibinds(allowEmpty = true)
    public val permissionInvitationAuthorities: Set<PermissionInvitationAuthority>
}
