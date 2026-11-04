package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.protocol.codex.*
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Bounded HTTP/1.1 upgrade and RFC 6455 framing, shared by all Codex connections. */
class AndroidCodexSockets(
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
) : CodexSocketFactory {
    override suspend fun open(endpoint: String, token: String): CodexSocket =
        openCodexFrames(endpoint, token, dispatchers, appScope)
}

/**
 * AGENT-1's Codex client (root `protocol.codex` package): the Agents hub's Codex pairing check and
 * save use it. It is not in the `GatewayFactory` set: PROTO's [AppCodexGatewayFactory]
 * (ProtocolsWiring.kt) is the one live Codex backend, so one backend never has two factories.
 */
@Inject
@SingleIn(AppScope::class)
class AgentCodexGatewayFactory(
    dispatchers: AppDispatchers,
    clock: Clock,
    appScope: AppCoroutineScope,
    private val profiles: ProfileStore,
) : GatewayFactory {
    val codex = CodexGatewayFactory(AndroidCodexSockets(dispatchers, appScope), dispatchers, clock)
    override val backend: BackendKind = BackendKind.Codex

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> = codex.probe(target)

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val folder = profiles.profile(spec.profile.id)?.profile?.codexDirectory
        val pinned =
            if (spec.location.directory == null && !folder.isNullOrBlank())
                spec.copy(location = ServerLocation(folder))
            else spec
        return codex.connect(pinned)
    }
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
        val session = gateway.accountSession()
        return object : io.github.eslamasabry.oc.engine.agents.account.AgentAccountSession {
                override val active: Boolean
                    get() =
                        session.active &&
                            registry.live.value[profileId]?.session?.server === gateway

                override val supported: Boolean
                    get() = session.supported

                override val events =
                    session.events.map { event ->
                        io.github.eslamasabry.oc.engine.agents.account.AccountEvent(
                            io.github.eslamasabry.oc.engine.agents.account.AccountEventKind.valueOf(
                                event.kind.name
                            ),
                            event.success,
                        )
                    }

                override suspend fun read() =
                    session.read().map {
                        io.github.eslamasabry.oc.engine.agents.account.AgentRuntimeAccount(
                            it.type,
                            it.email,
                            it.plan,
                            it.requiresSignIn,
                        )
                    }

                override suspend fun rateLimits() =
                    session.rateLimits().map { buckets ->
                        buckets
                            .map { bucket ->
                                fun window(value: CodexRateWindow?) = value?.let {
                                    io.github.eslamasabry.oc.engine.agents.account
                                        .AccountRateWindow(
                                            it.usedPercent,
                                            it.durationMinutes,
                                            it.resetsAtEpochMillis,
                                        )
                                }
                                io.github.eslamasabry.oc.engine.agents.account.AccountRateBucket(
                                    bucket.name,
                                    window(bucket.primary),
                                    window(bucket.secondary),
                                )
                            }
                            .toImmutableList()
                    }

                override suspend fun usage() =
                    session.usage().map {
                        io.github.eslamasabry.oc.engine.agents.account.AccountTokenUsage(
                            it.lifetimeTokens,
                            it.peakDailyTokens,
                        )
                    }

                override suspend fun startDeviceLogin() =
                    session.startDeviceLogin().map {
                        io.github.eslamasabry.oc.engine.agents.account.AccountDeviceCode(
                            it.verificationUrl,
                            it.userCode,
                        )
                    }

                override suspend fun cancelLogin() = session.cancelLogin()

                override suspend fun close() = session.close()
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
