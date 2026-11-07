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
import io.github.eslamasabry.opencode_mobile.BuiltinNewProject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The same guest folder creator as Flutter for the app's answering phone server; N-41: a computer's OpenCode 2 server
 * makes the folder itself, beside its most recent project.
 */
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

    override fun available(server: ProjectServer): Boolean = onPhone(server) || onServer(server)

    /** N-41: a live server that makes folders on its own machine. */
    private fun onServer(server: ProjectServer): Boolean =
        server.operations != null && server.gateway.capabilities.value.projectFolderCreation &&
            registry.live.value[server.profile]?.session?.server === server.gateway

    private fun onPhone(server: ProjectServer): Boolean {
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
        // Port 4097 is the app-owned Linux server, not another localhost host.
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
            if (!onPhone(server)) return@withLock onServerCreate(server, folder)
            if (!available(server)) return@withLock stale()
            if (linux.presence() != LinuxPresence.Ready || !linux.serviceRunning("server"))
                return@withLock Outcome.Failed(Failure.Folder(FolderProblem.NotInstalled))
            val path = "/root/projects/" + folder
            if (!available(server)) return@withLock stale()
            val made = make(path) ?: return@withLock Outcome.Failed(Failure.StorageRefused())
            if (made is Outcome.Ok && !available(server)) return@withLock stale()
            made
        }

    /**
     * Makes [path] a git project. A direct child of /root/projects is made by the app itself, never
     * by `git init` in Linux: once AI Team is on, the sandbox denies the chmod `git init` needs
     * (issue #111). Null for a path no script may name.
     */
    private suspend fun make(path: String): Outcome<String>? {
        if (BuiltinNewProject.managedName(path) != null) {
            return when (val made = linux.createProject(path)) {
                is Outcome.Failed -> made
                is Outcome.Ok ->
                    if (made.value == "created" || made.value == "exists") Outcome.Ok(path)
                    else Outcome.Failed(Failure.Folder(FolderProblem.Denied))
            }
        }
        val script = BuiltinScripts.createFolderScript(path) ?: return null
        return when (val result = linux.run(script, timeoutSeconds = 45)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
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

    /** Checked inside the app's Linux, never by asking OpenCode, so a missing folder is not remembered as broken. */
    override suspend fun exists(server: ProjectServer, path: String): Boolean? =
        lane.withLock {
            if (!onPhone(server) || linux.presence() != LinuxPresence.Ready) return@withLock null
            if (!linux.serviceRunning("server")) return@withLock null
            val script = BuiltinScripts.folderExistsScript(path)
            if ('\n' in path || '\u0000' in path || !path.startsWith("/")) return@withLock null
            when (val result = linux.run(script, timeoutSeconds = 20)) {
                is Outcome.Failed -> null
                is Outcome.Ok -> result.value.exitCode == 0
            }
        }

    override suspend fun createAt(server: ProjectServer, path: String): Outcome<String> =
        lane.withLock {
            if (!onPhone(server)) return@withLock stale()
            if (linux.presence() != LinuxPresence.Ready || !linux.serviceRunning("server"))
                return@withLock Outcome.Failed(Failure.Folder(FolderProblem.NotInstalled))
            val made = make(path) ?: return@withLock Outcome.Failed(Failure.StorageRefused())
            if (made is Outcome.Ok && !available(server)) return@withLock stale()
            made
        }

    /** N-41: beside the server's most recent project (its parent folder); refused when it has none to go by. */
    private suspend fun onServerCreate(server: ProjectServer, folder: String): Outcome<String> {
        val ops = server.operations ?: return stale()
        if (!onServer(server)) return stale()
        val projects =
            when (val listed = ops.listProjects()) {
                is Outcome.Failed -> return listed
                is Outcome.Ok -> listed.value
            }
        // Real projects (Git repositories) lead; a plain folder someone opened is used only when there is nothing else.
        val placed =
            projects.filter { it.directory.startsWith("/") && it.directory.trimEnd('/').count { c -> c == '/' } >= 2 }
        val parent =
            (placed.filter { it.isGit == true }.ifEmpty { placed })
                .maxByOrNull { it.updatedAtMs }
                ?.directory
                ?.trimEnd('/')
                ?.substringBeforeLast('/')
                ?.takeIf { it.isNotEmpty() }
                ?: return Outcome.Failed(Failure.Folder(FolderProblem.Missing))
        return ops.makeProjectFolder(parent, folder)
    }

    private fun stale(): Outcome<Nothing> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}
