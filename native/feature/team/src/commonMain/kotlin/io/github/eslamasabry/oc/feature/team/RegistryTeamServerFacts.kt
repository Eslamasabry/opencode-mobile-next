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
        return TeamServer(kindOf(saved.managedOnPhone, saved.origin.host), saved.name)
    }

    public companion object {
        private val LOOPBACK = setOf("127.0.0.1", "localhost", "::1")

        /**
         * A server this app started (flag) or any server reached on the phone's own loopback address is this phone;
         * the connect path does not always carry the flag, and the off page then said "Runs on This phone" with a
         * computer's icon.
         */
        public fun kindOf(managedOnPhone: Boolean, host: String): TeamServerKind =
            if (managedOnPhone || host.lowercase() in LOOPBACK) TeamServerKind.ThisPhone else TeamServerKind.Computer
    }
}
