package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.agents.providers.presentation.*

@ContributesTo(ConnectionScope::class)
interface ProviderConnectionAccess {
    val providerOperations: ServerOperationsGateway
}

/** A sign-in borrows an authenticated graph; no copied password or independent profile client. */
@Inject
@ContributesBinding(AppScope::class, binding = binding<ProviderSource>())
class ProtoProviderSource(private val registry: ConnectionRegistry) : ProviderSource {
    override suspend fun connect(profile: ProfileId): Outcome<ProviderConnection> {
        val graph =
            registry.live.value[profile]
                ?: return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        val gateway = graph.session.server
        val location = gateway.location.value
        val operations =
            (graph as? ProviderConnectionAccess)?.providerOperations
                ?: return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        return ProviderConnection(
                operations,
                gateway.capabilities.value.integrationCredentials,
                isCurrent = { registry.live.value[profile] === graph },
                atLocation = { gateway.location.value == location },
            )
            .ok()
    }
}

/**
 * One user-reviewed browser approval. The original ApprovedLink is consumed once, without reparsing
 * a URL.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ProviderBrowser>())
@Inject
class ProtoProviderBrowser(
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : ProviderBrowser {
    private var generation = 0L
    private var held: Pair<Long, ApprovedLink>? = null

    override fun review(url: String): ProviderLink? {
        held = null
        val decision =
            policy.check(url, LinkOrigin.ServerContent) as? LinkDecision.AskFirst ?: return null
        val token = ++generation
        held = token to decision.link
        return ProviderLink(token, decision.shownHost, decision.insecure)
    }

    override suspend fun open(token: Long): Outcome<Unit> {
        val approved =
            held?.takeIf { it.first == token }?.second
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        held = null
        return opener.open(approved)
    }

    override fun discard(token: Long) {
        if (held?.first == token) held = null
    }
}
