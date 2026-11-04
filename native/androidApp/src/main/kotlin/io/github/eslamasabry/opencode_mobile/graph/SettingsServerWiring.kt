package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorPort
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import kotlin.time.TimeSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ContributesTo(ConnectionScope::class)
interface ServerSettingsConnection {
    val serverSettingsOperations: ServerOperationsGateway
}

/** Saved-profile reads and live commands never resolve through the currently selected server. */
@ContributesBinding(AppScope::class, binding = binding<ServerSettingsSource>())
@SingleIn(AppScope::class)
@Inject
class NativeServerSettings(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val connections: ServerConnections,
    private val editor: ServerEditorPort,
) : ServerSettingsSource {
    private val shellWrites = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun watch(profile: ProfileId): Flow<ServerSettingsFacts?> = flow {
        stores.warm()
        emitAll(
            combine(stores.profiles.profiles, registry.live) { saved, live ->
                    saved.firstOrNull { it.id == profile } to live[profile]
                }
                .flatMapLatest { (loaded, graph) ->
                    if (loaded == null) flowOf(null)
                    else if (graph == null || graph.profile != loaded.toServerProfile())
                        flowOf(
                            ServerSettingsFacts(
                                loaded.profile.name,
                                loaded.profile.baseUrl,
                                false,
                                loaded.hasSecret,
                                loaded.profile.serverVersion,
                                !loaded.profile.usesAgentSocket,
                                null,
                                loaded.profile.backend == BackendKind.Codex,
                                loaded.profile.backend == BackendKind.OpenCode2 &&
                                    (ManagedLocalServer.isBuiltin(loaded.profile.baseUrl) ||
                                        ManagedLocalServer.isTermux(loaded.profile.baseUrl)),
                            )
                        )
                    else
                        graph.session.capabilities.let { capabilities ->
                            combine(capabilities, graph.session.server.events.status) { flags, _ ->
                                ServerSettingsFacts(
                                    loaded.profile.name,
                                    loaded.profile.baseUrl,
                                    true,
                                    loaded.hasSecret,
                                    loaded.profile.serverVersion,
                                    !loaded.profile.usesAgentSocket,
                                    flags,
                                    loaded.profile.backend == BackendKind.Codex,
                                    loaded.profile.backend == BackendKind.OpenCode2 &&
                                        (ManagedLocalServer.isBuiltin(loaded.profile.baseUrl) ||
                                            ManagedLocalServer.isTermux(loaded.profile.baseUrl)),
                                )
                            }
                        }
                }
        )
    }

    private fun live(profile: ProfileId): Outcome<ConnectionGraph> {
        val loaded =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph = registry.live.value[profile] ?: return Failure.Network().failed()
        if (loaded.toServerProfile() != graph.profile)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        return graph.ok()
    }

    private fun current(profile: ProfileId, graph: ConnectionGraph): Boolean =
        registry.live.value[profile] === graph &&
            stores.profiles.profile(profile)?.toServerProfile() == graph.profile

    override suspend fun health(profile: ProfileId): Outcome<Health> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val graph =
            when (val read = live(profile)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val result = graph.session.server.health()
        return if (current(profile, graph)) result
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    override suspend fun check(profile: ProfileId): Outcome<ImmutableList<ConnectionResult>> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val record =
            when (val read = editor.read(profile)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val input =
            ServerEditorInput(
                record.id,
                record.name,
                record.address,
                record.username,
                record.backend,
                record.directory,
                null,
                true,
                record.tailscale,
                record.cleartextOrigin,
                record.certificatePin,
                record.certificateOrigin,
                editToken = record.editToken,
            )
        val timer = TimeSource.Monotonic.markNow()
        val result = editor.check(input)
        val millis = timer.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
        val after = editor.read(profile)
        if (after !is Outcome.Ok || after.value != record)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        if (
            result is Outcome.Failed &&
                (result.failure is Failure.Certificate ||
                    result.failure is Failure.Refused ||
                    result.failure is Failure.SecretUnavailable)
        )
            return result
        val rows =
            ConnectionStep.entries
                .map {
                    ConnectionResult(it, ConnectionMark.Skipped, ConnectionReason.EarlierFailed)
                }
                .toMutableList()
        fun set(
            step: ConnectionStep,
            mark: ConnectionMark,
            reason: ConnectionReason = ConnectionReason.Ok,
            version: String? = null,
            duration: Long? = null,
        ) {
            rows[step.ordinal] = ConnectionResult(step, mark, reason, version, duration)
        }
        when (result) {
            is Outcome.Failed ->
                when (val failure = result.failure) {
                    is Failure.SignIn -> {
                        set(ConnectionStep.Address, ConnectionMark.Pass)
                        val missing =
                            failure.why in listOf(SignInWhy.PasswordMissing, SignInWhy.TokenMissing)
                        set(
                            ConnectionStep.SignIn,
                            ConnectionMark.Fail,
                            if (missing) ConnectionReason.PasswordMissing
                            else ConnectionReason.PasswordRejected,
                        )
                    }
                    is Failure.Incompatible -> {
                        set(ConnectionStep.Address, ConnectionMark.Pass)
                        set(ConnectionStep.SignIn, ConnectionMark.Pass)
                        set(
                            ConnectionStep.Version,
                            ConnectionMark.Fail,
                            ConnectionReason.VersionUnsupported,
                        )
                    }
                    is Failure.Server,
                    is Failure.Busy -> {
                        set(ConnectionStep.Address, ConnectionMark.Pass)
                        set(ConnectionStep.SignIn, ConnectionMark.Pass)
                        set(ConnectionStep.Version, ConnectionMark.Warn, ConnectionReason.NotReady)
                    }
                    else ->
                        set(
                            ConnectionStep.Address,
                            ConnectionMark.Fail,
                            if (failure is Failure.UnreadableReply || failure is Failure.NotFound)
                                ConnectionReason.NotOpenCode
                            else ConnectionReason.Unreachable,
                        )
                }
            is Outcome.Ok -> {
                set(ConnectionStep.Address, ConnectionMark.Pass)
                set(ConnectionStep.SignIn, ConnectionMark.Pass)
                set(ConnectionStep.Version, ConnectionMark.Pass, version = result.value.version)
                when (registry.live.value[profile]?.session?.server?.events?.status?.value) {
                    null ->
                        set(
                            ConnectionStep.LiveUpdates,
                            ConnectionMark.Skipped,
                            ConnectionReason.LiveNotChecked,
                        )
                    StreamStatus.Connected -> set(ConnectionStep.LiveUpdates, ConnectionMark.Pass)
                    StreamStatus.Connecting,
                    is StreamStatus.Reconnecting ->
                        set(
                            ConnectionStep.LiveUpdates,
                            ConnectionMark.Warn,
                            ConnectionReason.LiveConnecting,
                        )
                    else ->
                        set(
                            ConnectionStep.LiveUpdates,
                            ConnectionMark.Fail,
                            ConnectionReason.LiveDown,
                        )
                }
                set(
                    ConnectionStep.Speed,
                    if (millis > 1500) ConnectionMark.Warn else ConnectionMark.Pass,
                    if (millis > 1500) ConnectionReason.Slow else ConnectionReason.Ok,
                    duration = millis,
                )
            }
        }
        return rows.toImmutableList().ok()
    }

    override suspend fun shells(profile: ProfileId): Outcome<TerminalShellSettings> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val graph =
            when (val read = live(profile)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        if (!graph.session.capabilities.value.shellSettings)
            return Failure.Unavailable(FeatureId("shellSettings")).failed()
        val result =
            (graph as ServerSettingsConnection).serverSettingsOperations.terminalShellSettings()
        return if (current(profile, graph)) result
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    override suspend fun selectShell(
        profile: ProfileId,
        selected: String,
    ): Outcome<TerminalShellSettings> = shellWrites.withLock {
        val graph =
            when (val read = live(profile)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return@withLock read
            }
        if (!graph.session.capabilities.value.shellSettings)
            return@withLock Failure.Unavailable(FeatureId("shellSettings")).failed()
        val ready = graph.session.server.awaitActionTransport()
        if (ready is Outcome.Failed) return@withLock ready
        if (!current(profile, graph))
            return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val saved =
            (graph as ServerSettingsConnection)
                .serverSettingsOperations
                .selectTerminalShell(selected)
        if (saved is Outcome.Failed) return@withLock saved
        if (!current(profile, graph))
            return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        shells(profile)
    }

    override suspend fun connect(profile: ProfileId): Outcome<Unit> = connections.connect(profile)

    override suspend fun disconnect(profile: ProfileId): Outcome<Unit> =
        connections.disconnect(profile)

    override suspend fun recheckAgents(profile: ProfileId): Outcome<ImmutableList<String>> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val graph =
            when (val read = live(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        if (!graph.session.capabilities.value.hostAgentProviders)
            return Failure.Unavailable(FeatureId("hostAgentProviders")).failed()
        val gateway =
            graph.session.server as? PaseoGateway
                ?: return Failure.Unavailable(FeatureId("hostAgentProviders")).failed()
        val location = gateway.location.value
        val result = gateway.hostAgentProviders(refresh = true)
        return if (current(profile, graph) && gateway.location.value == location)
            result.map { it.selectable.map { p -> p.displayName }.toImmutableList() }
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }
}
