package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinScripts
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.projects.presentation.NoProjectCreation
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectCreation
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The same guest folder creator as Flutter, exclusively for the app's answering phone server. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoProjectCreation::class])
@Inject
class PhoneProjectCreation(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val platform: PlatformCapabilities,
    private val linux: LinuxHost,
) : ProjectCreation {
    private val lane = Mutex()

    override fun available(server: ProjectServer): Boolean {
        if (!platform.builtinLinux) return false
        val graph = registry.live.value[server.profile] ?: return false
        if (
            graph.session.server !== server.gateway ||
                !stores.lanes.forProfile(server.profile).isAdmitting
        )
            return false
        val saved = stores.profiles.profile(server.profile) ?: return false
        if (stores.prefs.string("oc.builtinServerOwner") != server.profile.raw) return false
        if (saved.profile.transientTransport || saved.toServerProfile() != graph.profile)
            return false
        // Termux is migration-only in native. Port 4097 is the app-owned Linux server, not another
        // localhost host.
        val origin = graph.profile.origin
        return origin.scheme == "http" &&
            origin.port == PhoneServerScripts.PORT &&
            origin.host in setOf("127.0.0.1", "localhost", "::1")
    }

    override suspend fun create(server: ProjectServer, name: String): Outcome<String> =
        lane.withLock {
            val folder =
                ProjectFolderName.validated(name)
                    ?: return@withLock Outcome.Failed(Failure.StorageRefused())
            if (!available(server)) return@withLock stale()
            if (linux.presence() != LinuxPresence.Ready || !linux.serviceRunning("server"))
                return@withLock Outcome.Failed(Failure.Folder(FolderProblem.NotInstalled))
            val path = "/root/projects/" + folder
            val script =
                BuiltinScripts.createFolderScript(path)
                    ?: return@withLock Outcome.Failed(Failure.StorageRefused())
            if (!available(server)) return@withLock stale()
            when (val result = linux.run(script, timeoutSeconds = 45)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    if (!available(server)) return@withLock stale()
                    val last =
                        result.value.output
                            .lineSequence()
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .lastOrNull()
                    if (
                        result.value.exitCode == 0 &&
                            (last == "created $path" || last == "exists $path")
                    )
                        Outcome.Ok(path)
                    else Outcome.Failed(Failure.Folder(FolderProblem.Denied))
                }
            }
        }

    private fun stale(): Outcome<Nothing> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}
