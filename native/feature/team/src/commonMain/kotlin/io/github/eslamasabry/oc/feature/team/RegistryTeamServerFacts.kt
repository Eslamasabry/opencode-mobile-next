package io.github.eslamasabry.oc.feature.team

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.team.presentation.TeamServer
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerFacts
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind

/**
 * The connected server's own profile: OpenCode this app runs on the phone is [TeamServerKind.ThisPhone] (Flutter
 * `teamServerKindOf`: in-app), any other server a computer.
 */
@Inject
@ContributesBinding(AppScope::class)
public class RegistryTeamServerFacts(private val registry: ConnectionRegistry) : TeamServerFacts {
    override fun server(profile: ProfileId): TeamServer? {
        val saved = registry.live.value[profile]?.profile ?: return null
        return TeamServer(kindOf(saved.managedOnPhone), saved.name)
    }

    public companion object {
        /**
         * Flutter `teamServerKindOf`: in-app when `looksLikeInAppServer`, which the profile store now answers for every
         * connection (PhoneServerProfile). A tunnel to a computer on the phone's loopback is a computer.
         */
        public fun kindOf(managedOnPhone: Boolean): TeamServerKind =
            if (managedOnPhone) TeamServerKind.ThisPhone else TeamServerKind.Computer
    }
}
