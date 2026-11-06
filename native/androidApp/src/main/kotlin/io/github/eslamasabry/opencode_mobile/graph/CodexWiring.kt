package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocket
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The one Codex socket factory: bounded HTTP/1.1 upgrade and RFC 6455 framing ([openCodexFrames]),
 * shared by every Codex connection, pairing check and probe through [AppCodexGatewayFactory].
 */
class AndroidCodexSockets(
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
) : CodexSocketFactory {
    override suspend fun open(endpoint: CodexEndpoint, secret: String): CodexSocket =
        openCodexFrames(endpoint, secret, dispatchers, appScope)
}

/** Only connected runtimes advertising the account facet have a visible Account action. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(
    AppScope::class,
    binding = binding<io.github.eslamasabry.oc.feature.agents.hub.AgentAccounts>(),
    replaces = [io.github.eslamasabry.oc.feature.agents.hub.NoAgentAccounts::class],
)
class AppAgentAccounts(
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    appScope: AppCoroutineScope,
    private val opener: io.github.eslamasabry.oc.core.security.ExternalLinkOpener,
) : io.github.eslamasabry.oc.feature.agents.hub.AgentAccounts {
    override val accounts =
        registry.live
            .map { live ->
                live
                    .filterValues {
                        it.session.server is CodexGateway &&
                            it.session.server.capabilities.value.agentAccount
                    }
                    .map { (id, graph) ->
                        io.github.eslamasabry.oc.feature.agents.hub.AgentAccountTarget(
                            id,
                            graph.profile.name,
                        )
                    }
                    .toImmutableList()
            }
            .stateIn(
                appScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                kotlinx.collections.immutable.persistentListOf(),
            )

    override suspend fun open(
        profileId: io.github.eslamasabry.oc.core.common.ids.ProfileId
    ): Outcome<io.github.eslamasabry.oc.engine.agents.account.AgentAccountSession> {
        val gateway =
            registry.live.value[profileId]?.session?.server as? CodexGateway
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        // The same account bridge as Settings › Agents › Account: one Codex client everywhere.
        return AccountSessionBridge(gateway.openAccountSession()) {
                registry.live.value[profileId]?.session?.server === gateway
            }
            .ok()
    }

    override suspend fun openSignIn(url: String): Outcome<Unit> {
        // The account protocol already rejects all but this exact HTTPS host and device path.
        val parsed = url.toHttpUrlOrNull()
        if (
            parsed == null ||
                parsed.scheme != "https" ||
                parsed.host != "auth.openai.com" ||
                parsed.encodedPath != "/codex/device" ||
                parsed.port != 443 ||
                parsed.username.isNotEmpty() ||
                parsed.password.isNotEmpty()
        )
            return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val policy = io.github.eslamasabry.oc.core.security.ExternalLinkPolicy()
        val decision =
            policy.check(url, io.github.eslamasabry.oc.core.security.LinkOrigin.ServerContent)
        val link =
            when (decision) {
                is io.github.eslamasabry.oc.core.security.LinkDecision.Open -> decision.link
                is io.github.eslamasabry.oc.core.security.LinkDecision.AskFirst -> decision.link
                is io.github.eslamasabry.oc.core.security.LinkDecision.Refuse ->
                    return Failure.Refused(RefusalReason.InvalidAddress).failed()
            }
        return opener.open(link)
    }
}
