package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.devservers.DetectedDevServers
import io.github.eslamasabry.oc.engine.runtime.devservers.ProjectDevServer
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStore
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServices
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesHost
import io.github.eslamasabry.oc.feature.projects.presentation.NoProjectSignalsHost
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectSignalsHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class, replaces = [NoProjectSignalsHost::class])
@Inject
class LiveProjectSignalsHost(
    private val services: DevServicesHost,
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val stores: PreviewStores,
    private val recent: SavedRecentModels,
) : ProjectSignalsHost {
    override suspend fun applyNewChatDefault(
        server: io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer,
        session: io.github.eslamasabry.oc.core.common.ids.SessionId,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit> {
        val profile = server.profile
        val raw =
            (stores.prefs.get("oc.model.${profile.raw}")
                    as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)
                ?.value ?: return io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
        val parts = raw.split('|')
        if (parts.size != 2) return io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
        val providers =
            when (val answer = server.gateway.providers()) {
                // No catalogue: skip the default rather than fail the new chat.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    return io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> answer.value
            }
        if (
            registry.live.value[profile]?.session?.server !== server.gateway ||
                stores.profiles.profile(profile) == null
        )
            return io.github.eslamasabry.oc.core.common.Outcome.Failed(
                io.github.eslamasabry.oc.core.common.Failure.StorageRefused()
            )
        if (providers.providers.none { it.id == parts[0] && parts[1] in it.modelIds })
            return io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
        val selector =
            server.gateway as? io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
        if (selector != null) {
            // The server cannot switch a session's model: keep its own default.
            if (!server.gateway.capabilities.value.sessionModelProviderSwitching)
                return io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
            when (
                val saved =
                    selector.setSessionModel(
                        session,
                        io.github.eslamasabry.oc.core.model.ModelRef(parts[0], parts[1]),
                        "",
                    )
            ) {
                is io.github.eslamasabry.oc.core.common.Outcome.Failed -> return saved
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> Unit
            }
        }
        return recent.rememberSession(
            profile.raw,
            session.raw,
            io.github.eslamasabry.oc.feature.chat.world.SavedSessionModel(parts[0], parts[1], ""),
        )
    }

    override fun devServer(profile: ProfileId, directory: String): ProjectDevServer? {
        val context = services.open(profile, directory) ?: return null
        val graph = registry.live.value[profile] ?: return null
        val location = graph.session.server.location.value
        val revision = graph.session.server.scopeRevision
        fun current() =
            context.isCurrent() &&
                registry.live.value[profile] === graph &&
                graph.session.server.location.value == location &&
                graph.session.server.scopeRevision == revision
        val saved =
            if (context.supported())
                DevelopmentServices(
                    DevelopmentServiceStore(context.settings, profile, context.canWrite),
                    directory,
                    context.workspace,
                    context.shells,
                    ::current,
                    context.supported,
                    context.newId,
                )
            else null
        val listening =
            context.phone
                ?.takeIf { graph.profile.managedOnPhone }
                ?.let { phone ->
                    DetectedDevServers(
                        directory,
                        resolveShells = { null },
                        isCurrent = ::current,
                        runOnPhone =
                            io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptRunner {
                                script ->
                                when (val result = phone.run(script)) {
                                    is io.github.eslamasabry.oc.core.common.Outcome.Failed -> result
                                    is io.github.eslamasabry.oc.core.common.Outcome.Ok ->
                                        if (result.value.exitCode == 0) result
                                        else
                                            io.github.eslamasabry.oc.core.common.Outcome.Failed(
                                                io.github.eslamasabry.oc.core.common.Failure
                                                    .Runtime(
                                                        io.github.eslamasabry.oc.core.common
                                                            .RuntimeProblem
                                                            .Generic
                                                    )
                                            )
                                }
                            },
                        clock = context.clock,
                    )
                }
        return ProjectDevServer(directory, saved, listening, ::current)
    }
}

@dev.zacsweers.metro.ContributesBinding(
    AppScope::class,
    replaces = [io.github.eslamasabry.oc.engine.runtime.devservers.NoTerminalDevServerHost::class],
)
@Inject
class LiveTerminalDevServerHost(
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val stores: PreviewStores,
    private val projects: ProjectSignalsHost,
) : io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerHost {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun sessions(
        profile: ProfileId?
    ): kotlinx.coroutines.flow.Flow<
        io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerSession?
    > =
        kotlinx.coroutines.flow
            .combine(registry.live, stores.profiles.activeId) { live, selected ->
                val id = profile ?: selected
                id?.let { live[it] }
            }
            .distinctUntilChanged()
            .flatMapLatest { graph ->
                if (graph == null) kotlinx.coroutines.flow.flowOf(null)
                else
                    graph.session.server.location.map { location ->
                        val directory = location.directory
                        if (directory.isNullOrBlank()) null
                        else
                            projects.devServer(graph.profile.id, directory)?.let { signal ->
                                io.github.eslamasabry.oc.engine.runtime.devservers
                                    .TerminalDevServerSession(graph.profile.id, signal)
                            }
                    }
            }
}
