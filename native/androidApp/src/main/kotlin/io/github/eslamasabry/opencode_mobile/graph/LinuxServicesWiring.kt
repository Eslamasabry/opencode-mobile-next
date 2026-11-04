package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.domain.LocatedManagedShellGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.procs.detectedDevelopmentServers
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServices
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShell
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellGateway
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellOutput
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellStatus
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesSession
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesSource
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One project-bound service panel. Only the captured live graph may dispatch, and only its profile
 * lane may write.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class LiveLinuxServices(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val linux: LinuxHost,
    private val redactor: Redactor,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
    private val random: Random,
    dispatchers: AppDispatchers,
) : LinuxServicesSource {
    private val lane = dispatchers.serial("development-services")
    private val commands = Mutex()
    override val changes: Flow<Unit> =
        combine(registry.live, stores.profiles.profiles) { _, _ -> Unit }

    override suspend fun open(
        profile: ProfileId,
        directory: String,
        workspace: String?,
        current: () -> Boolean,
    ): Outcome<LinuxServicesSession> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        if (directory.isBlank() || !directory.startsWith("/") || directory.length > 4096)
            return refused()
        val graph = registry.live.value[profile] ?: return refused()
        val bound =
            graph.session.server
                .facet<LocatedManagedShellGateway>()
                ?.managedShellsAt(ServerLocation(directory, workspace))
        val adapter = bound?.let(::RuntimeShellGateway)
        val live = {
            current() &&
                registry.live.value[profile] === graph &&
                stores.profiles.profile(profile) != null
        }
        val supported = {
            live() && graph.session.capabilities.value.developmentServices && adapter != null
        }
        val store = StoredDevelopmentServices(stores, profile)
        val services =
            DevelopmentServices(
                store,
                directory,
                workspace,
                { if (supported()) adapter else null },
                live,
                supported,
                redactor::redact,
                {
                    random.nextBytes(24).joinToString("") {
                        (it.toInt() and 255).toString(16).padStart(2, '0')
                    }
                },
            )
        val local =
            AddressRules.isLoopback(graph.profile.origin.host) &&
                linux.presence() == LinuxPresence.Ready
        val runPhone: (suspend (String) -> Outcome<String>)? =
            if (local)
                ({ script ->
                    if (!live()) Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
                    else
                        when (val answer = linux.run(script, 25)) {
                            is Outcome.Failed -> answer
                            is Outcome.Ok ->
                                if (answer.value.exitCode == 0) Outcome.Ok(answer.value.output)
                                else
                                    Outcome.Failed(
                                        Failure.Runtime(
                                            io.github.eslamasabry.oc.core.common.RuntimeProblem
                                                .Generic
                                        )
                                    )
                        }
                })
            else null
        val detected =
            detectedDevelopmentServers(
                directory = directory,
                resolveGateway = { if (supported()) adapter else null },
                isCurrent = live,
                ownedShellIds = { services.services.mapNotNull { it.run?.shellId }.toSet() },
                runOnPhone = runPhone,
                clock = clock,
            )
        return Outcome.Ok(
            object : LinuxServicesSession {
                override val services = services
                override val detected = detected
                override val supported: Boolean
                    get() = supported()

                override fun redact(text: String): String = redactor.redact(text)

                override suspend fun command(block: suspend () -> Unit) {
                    appScope.async(lane) { commands.withLock { block() } }.await()
                }
            }
        )
    }

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}

/**
 * The runtime engine keeps only command, location and its own ownership tag; arbitrary metadata is
 * discarded.
 */
private class RuntimeShellGateway(
    private val gateway: io.github.eslamasabry.oc.core.domain.ManagedShellGateway
) : ManagedShellGateway {
    override suspend fun startManagedShell(
        command: String,
        directory: String,
        ownerToken: String,
    ): Outcome<ManagedShell> =
        gateway.startManagedShell(command, directory, ownerToken).map(::shell)

    override suspend fun loadRunningShells(): Outcome<List<ManagedShell>> =
        gateway.runningShells().map { it.shells.map(::shell) }

    override suspend fun getManagedShell(id: String): Outcome<ManagedShell?> =
        gateway.managedShell(id).map { it?.let(::shell) }

    override suspend fun readManagedShellOutput(
        id: String,
        cursor: Long,
        limit: Int,
    ): Outcome<ManagedShellOutput> =
        gateway.managedShellOutput(id, cursor, limit).map {
            ManagedShellOutput(it.text, it.cursor, it.size, it.truncated)
        }

    override suspend fun stopManagedShell(id: String): Outcome<Unit> = gateway.stopManagedShell(id)

    private fun shell(value: io.github.eslamasabry.oc.core.model.ManagedShell): ManagedShell =
        ManagedShell(
            value.id,
            value.command,
            ManagedShellStatus.valueOf(value.status.name),
            value.startedAtMs,
            value.exitCode,
            value.directory,
            value.ownerToken,
        )
}
