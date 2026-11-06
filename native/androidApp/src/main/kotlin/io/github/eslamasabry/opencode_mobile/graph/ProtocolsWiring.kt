package io.github.eslamasabry.opencode_mobile.graph

import android.text.format.DateUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.feature.computer.ComputerTools
import io.github.eslamasabry.oc.feature.computer.ImportItem
import io.github.eslamasabry.oc.feature.computer.ImportList
import io.github.eslamasabry.oc.feature.computer.UpdateAnswer
import io.github.eslamasabry.oc.feature.computer.UpdateStep
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGatewayFactory
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.updateHostDaemon
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Unit BUILD-PROTOCOLS: Android bindings for the Codex app-server backend (:protocol:codex). With this factory in the
 * ConnectionRegistry's set, a saved Codex server (Flutter format, carried over by the migration) connects like any
 * other: Servers → the server → Conversations → Chat (B-AGENT-067). Codex offers no Project tab tools (B-AGENT-068).
 */

/**
 * The Codex backend for the ConnectionRegistry. A saved Codex profile keeps its project folder in `codexDirectory`
 * (the Flutter format); the gateway is pinned to it unless the restored location names one, so threads from other
 * projects never enter the list (B-AGENT-063). It is the only Codex factory: the Agents hub's add/check
 * (AgentsWiring) and the server editor's check use this same instance.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())
class AppCodexGatewayFactory(
    dispatchers: AppDispatchers,
    clock: Clock,
    appScope: AppCoroutineScope,
    private val profiles: ProfileStore,
) : GatewayFactory {
    /** The one Codex client: chats, the Agents hub, pairing checks and probes all open it. */
    val codex = CodexGatewayFactory(AndroidCodexSockets(dispatchers, appScope), dispatchers, clock)

    override val backend: BackendKind = BackendKind.Codex

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> = codex.probe(target)

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val folder = profiles.profile(spec.profile.id)?.profile?.codexDirectory?.takeIf { it.isNotBlank() }
        val pinned =
            if (spec.location.directory == null && folder != null)
                spec.copy(location = ServerLocation(directory = folder, workspace = spec.location.workspace))
            else spec
        return codex.connect(pinned)
    }
}

/**
 * Settings › Agents › a Paseo computer (:feature:computer) over the live connection graph: the helper update
 * (B-AGENT-167) and the Claude Code import (B-AGENT-174). Connects first when the computer is not connected yet.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<ComputerTools>())
class AppComputerTools(
    private val profiles: ProfileStore,
    private val connections: ServerConnections,
    private val registry: ConnectionRegistry,
    private val clock: Clock,
) : ComputerTools {
    /** The last listed conversations per computer, so an import sends exactly what the person saw. */
    private val listed = ConcurrentHashMap<ProfileId, Map<String, ImportableConversation>>()

    private suspend fun paseo(id: ProfileId): Outcome<PaseoGateway> {
        if (!registry.live.value.containsKey(id)) {
            val connected = connections.connect(id)
            if (connected is Outcome.Failed) return connected
        }
        val gateway = registry.live.value[id]?.session?.server as? PaseoGateway
        return gateway?.ok() ?: Failure.Refused(RefusalReason.StaleScope).failed()
    }

    override fun name(profile: ProfileId): String? = profiles.profile(profile)?.profile?.name

    override suspend fun version(profile: ProfileId): Outcome<String?> =
        when (val gateway = paseo(profile)) {
            is Outcome.Failed -> gateway
            is Outcome.Ok -> gateway.value.transport.serverVersion.ok()
        }

    override suspend fun update(profile: ProfileId, onStep: (UpdateStep) -> Unit): Outcome<UpdateAnswer> {
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        val result =
            gateway.updateHostDaemon { phase ->
                onStep(
                    when (phase) {
                        HostUpdatePhase.Starting -> UpdateStep.Starting
                        HostUpdatePhase.Downloading -> UpdateStep.Downloading
                        HostUpdatePhase.Installing -> UpdateStep.Installing
                        HostUpdatePhase.Complete -> UpdateStep.Finishing
                    },
                )
            }
        // The helper may have applied the update before its link dropped. Read only; never repeat
        // daemon.update.request, and never interpret a recovered connection as proof it updated.
        val uncertain = (result as? Outcome.Failed)?.failure.let { failure ->
            failure is Failure.Network || failure is Failure.TimedOut ||
                (failure is Failure.Refused && failure.reason == RefusalReason.DispatchUncertain)
        }
        val restarting = (result as? Outcome.Ok)?.value?.let { it.success && !it.alreadyUpToDate } == true
        if (uncertain || restarting) refreshAfterUpdate(profile, gateway)
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                when {
                    result.value.alreadyUpToDate -> UpdateAnswer.Current
                    result.value.success -> UpdateAnswer.Done(result.value.newVersion)
                    // The computer's own reason stays out of the page (B-AGENT-167: a result, never its words).
                    else -> UpdateAnswer.Refused
                }.ok()
        }
    }

    /** Four bounded health reads, cancelled if the saved target or its live graph changes. */
    private suspend fun refreshAfterUpdate(profile: ProfileId, gateway: PaseoGateway) {
        val saved = profiles.profile(profile) ?: return
        val graph = registry.live.value[profile] ?: return
        if (graph.session.server !== gateway) return
        repeat(4) {
            delay(3_000)
            if (profiles.profile(profile) != saved || registry.live.value[profile] !== graph) return
            val answer = withTimeoutOrNull(5_000) { gateway.health() }
            if (profiles.profile(profile) != saved || registry.live.value[profile] !== graph) return
            if (answer is Outcome.Ok && answer.value.healthy) return
        }
    }

    override suspend fun importable(profile: ProfileId): Outcome<ImportList> {
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        return when (val read = gateway.importableConversations()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val now = clock.now().toEpochMilliseconds()
                val items = read.value.items.sortedByDescending { it.lastUsed }
                listed[profile] = items.associateBy(::keyOf)
                ImportList(
                        project = gateway.providerImportDirectory,
                        items =
                            items
                                .map {
                                    ImportItem(
                                        key = keyOf(it),
                                        title = it.displayTitle,
                                        folder = it.directory,
                                        whenLabel =
                                            DateUtils.getRelativeTimeSpanString(
                                                    it.lastUsed.toEpochMilliseconds(),
                                                    now,
                                                    DateUtils.MINUTE_IN_MILLIS,
                                                )
                                                .toString(),
                                    )
                                }
                                .toImmutableList(),
                        alreadyHere = read.value.alreadyImported,
                    )
                    .ok()
            }
        }
    }

    override suspend fun import(profile: ProfileId, item: ImportItem): Outcome<ConversationRef> {
        val conversation = listed[profile]?.get(item.key) ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        return when (val made = gateway.importConversation(conversation)) {
            is Outcome.Failed -> made
            is Outcome.Ok -> ConversationRef(profile, made.value.id).ok()
        }
    }

    private fun keyOf(conversation: ImportableConversation): String = conversation.agentId + "/" + conversation.handle
}
