package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.engine.setupscripts.HostScripts
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerServer
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateSource

/**
 * s23-oc1move: the computer update guide (SetupRoute.UpdateComputer) over the saved profile, the app's own connect
 * (Check again) and the app-authored walkthrough link. Nothing here runs a command; the person copies it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ComputerUpdateSource>())
@Inject
class AndroidComputerUpdate(
    private val stores: PreviewStores,
    private val connections: ServerConnections,
    private val links: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : ComputerUpdateSource {
    override suspend fun server(profile: ProfileId): ComputerServer? {
        if (stores.warm() is Outcome.Failed) return null
        val loaded = stores.profiles.profile(profile) ?: return null
        // This phone's own server moves through This phone's offer, never through computer commands.
        if (loaded.profile.usesAgentSocket || ManagedLocalServer.isBuiltin(loaded.profile.baseUrl)) return null
        val port = loaded.toServerProfile()?.origin?.port ?: return null
        return ComputerServer(loaded.profile.name, port)
    }

    override suspend fun recheck(profile: ProfileId): Outcome<Unit> = connections.connect(profile)

    override fun openGuide() {
        when (val decision = links.check(HostScripts.UBUNTU_GUIDE_URL, LinkOrigin.AppAuthored)) {
            is LinkDecision.Open -> opener.open(decision.link)
            else -> Unit
        }
    }
}
