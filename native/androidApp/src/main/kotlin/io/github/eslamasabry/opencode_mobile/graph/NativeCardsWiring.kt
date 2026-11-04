package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.AgentFeaturesGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.cards.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.catalog.AgentConnectorSearch
import io.github.eslamasabry.oc.engine.integrations.catalog.ConnectorSearchServers
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/**
 * Resolves only an authenticated connection to the existing, app-owned phone helper. No host is
 * started here.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CardsQualifier>())
@Inject
class NativeCardsQualifier(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val payloads: PhonePayloadGuard,
    private val dispatchers: AppDispatchers,
) : CardsQualifier {
    private val linux by lazy { BuiltinLinux.get(app) }
    private val installer =
        ManagedCardsInstaller(
            CardsSetupRunner { profile, agent, enabled ->
                if (agent != CardAgent.Claude) CardsInstallOutcome.Failed
                else CardsInstallOutcome.ofExitCode(run(profile, enabled, verify = false) ?: -1)
            },
            CardsSetupVerifier { profile, agent ->
                agent == CardAgent.Claude && run(profile, true, verify = true) == 0
            },
        )

    override fun settingsOwner(profileId: String): String? {
        val id = ProfileId(profileId)
        val owner =
            registry.ownerOfTransient(id)
                ?: id.takeIf { stores.profiles.profile(it) != null }
                ?: return null
        return owner.raw.takeIf { PROFILE.matches(it) && stores.profiles.profile(owner) != null }
    }

    override fun activeSettingsOwner(profileId: String): Boolean =
        stores.profiles.activeId.value?.raw == profileId

    fun graph(profile: String): ConnectionGraph? {
        val id = ProfileId(profile)
        val parent = settingsOwner(profile)?.let(::ProfileId) ?: return null
        if (stores.profiles.activeId.value != parent || owner(profile) != parent.raw) return null
        val graph =
            if (registry.ownerOfTransient(id) == parent) registry.live.value[id]
            else
                registry.live.value.values
                    .filter { registry.ownerOfTransient(it.profile.id) == parent }
                    .singleOrNull()
        graph ?: return null
        val address =
            try {
                URI(graph.profile.origin.toString())
            } catch (_: Exception) {
                return null
            }
        if (
            address.host !in setOf("127.0.0.1", "localhost", "::1", "[::1]") || address.port != 4099
        )
            return null
        return graph.takeIf { it.session.server.facet<AgentFeaturesGateway>() != null }
    }

    fun owner(profile: String): String? {
        val owner = settingsOwner(profile) ?: return null
        return owner.takeIf {
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == it
        }
    }

    override fun managed(profileId: String): Boolean = graph(profileId) != null

    override fun agentsHere(profileId: String): List<CardAgent> =
        if (managed(profileId)) listOf(CardAgent.Claude) else emptyList()

    override fun permitsSession(profileId: String, sessionId: String): Boolean {
        val id = ProfileId(profileId)
        if (registry.ownerOfTransient(id) == null) return false
        val graph = graph(profileId)?.takeIf { it.profile.id == id } ?: return false
        return graph.session.server
            .facet<AgentFeaturesGateway>()
            ?.providerIdForSession(SessionId(sessionId)) == "claude"
    }

    private suspend fun run(profile: String, enabled: Boolean, verify: Boolean): Int? {
        val captured = graph(profile) ?: return null
        val owner = owner(profile) ?: return null
        val admission = payloads.reserveForProfile(captured.profile.id)
        val lease = (admission as? Outcome.Ok)?.value ?: return null
        try {
            if (
                lease.awaitReady() !is Outcome.Ok ||
                    graph(profile) !== captured ||
                    owner(profile) != owner
            )
                return null
            return withContext(dispatchers.io) {
                if (graph(profile) !== captured || owner(profile) != owner) return@withContext null
                val pins = AgentPins.builtIn
                val claude =
                    pins.catalog().byId(AgentId("claude"))?.recipe?.version
                        ?: return@withContext null
                val hostVersion = linux.agentHost.status(owner)["helperVersion"] as? String
                // Qualification belongs to these recorded pins, not to a future recipe or a
                // provider label.
                if (
                    claude != "2.1.316" ||
                        pins.node.version != "v24.21.0" ||
                        pins.paseo.version != "0.11.2" ||
                        hostVersion != pins.paseo.version
                )
                    return@withContext null
                val qualified = CardsPins(claude, pins.node.version, true)
                val script =
                    if (verify) CardsInstallScripts.verification(owner, CardAgent.Claude, qualified)
                    else CardsInstallScripts.install(owner, CardAgent.Claude, enabled, qualified)
                val code = linux.run(script, timeoutSeconds = 90, agentUser = true).exitCode
                if (graph(profile) !== captured || owner(profile) != owner) null else code
            }
        } finally {
            lease.release()
        }
    }

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> =
        CardsVerdict(
                if (run(profileId, true, true) == 0) listOf(CardAgent.Claude) else emptyList(),
                if (managed(profileId)) CardsProblem.VerificationFailed
                else CardsProblem.NotQualified,
                listOf(CardAgent.Claude),
            )
            .let { verdict ->
                (if (verdict.ready.isNotEmpty())
                        verdict.copy(problem = null, affected = emptyList())
                    else verdict)
                    .ok()
            }

    override suspend fun install(profileId: String): Outcome<CardsVerdict> {
        if (!managed(profileId))
            return CardsVerdict(emptyList(), CardsProblem.UnsupportedHost, agentsHere(profileId))
                .ok()
        return when (val status = installer.setEnabled(profileId, listOf(CardAgent.Claude), true)) {
            is CardsStatus.On -> CardsVerdict(status.agents).ok()
            is CardsStatus.Partial ->
                CardsVerdict(status.agents, status.reason, status.affected).ok()
            is CardsStatus.Failed -> CardsVerdict(emptyList(), status.reason, status.affected).ok()
            is CardsStatus.Unavailable ->
                CardsVerdict(emptyList(), status.reason, status.affected).ok()
            else ->
                CardsVerdict(emptyList(), CardsProblem.VerificationFailed, listOf(CardAgent.Claude))
                    .ok()
        }
    }

    override suspend fun remove(profileId: String): Outcome<Unit> =
        when (installer.setEnabled(profileId, listOf(CardAgent.Claude), false)) {
            is CardsStatus.Failed,
            is CardsStatus.Unavailable -> Failure.Refused(RefusalReason.StaleScope).failed()
            else -> Unit.ok()
        }

    companion object {
        private val PROFILE = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}

/**
 * Host filesystem operations are anchored to an open private directory, never through a final
 * symlink.
 */
@SingleIn(AppScope::class)
@Inject
class NativeCardsRootFs(private val app: Application, private val dispatchers: AppDispatchers) :
    CardsRootFs {
    override suspend fun supportRealPath(): String =
        withContext(dispatchers.io) { app.filesDir.canonicalPath }

    override suspend fun entry(path: String): RootEntry =
        withContext(dispatchers.io) {
            val p = File(path).toPath()
            when {
                Files.isSymbolicLink(p) -> RootEntry.Link
                Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.Directory
                Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.File
                !Files.exists(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.Missing
                else -> RootEntry.Other
            }
        }

    override suspend fun mode(path: String): Int? =
        withContext(dispatchers.io) { Os.lstat(path).st_mode and 0x1ff }

    override suspend fun publishPrivate(
        directory: String,
        name: String,
        bytes: ByteArray,
    ): Boolean =
        withContext(dispatchers.io) {
            if (name != CardSearchPublisher.TARGET || bytes.size > 1024) return@withContext false
            anchored(directory) { bound ->
                val target = File(bound, name)
                if (
                    Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)
                )
                    return@anchored false
                val temporary = File(bound, ".oc-search-${UUID.randomUUID()}")
                try {
                    val fd =
                        Os.open(
                            temporary.path,
                            OsConstants.O_WRONLY or
                                OsConstants.O_CREAT or
                                OsConstants.O_EXCL or
                                OsConstants.O_NOFOLLOW,
                            0x180,
                        )
                    java.io.FileOutputStream(fd).use {
                        it.write(bytes)
                        it.fd.sync()
                    }
                    Os.rename(temporary.path, target.path)
                    true
                } finally {
                    temporary.delete()
                }
            }
        }

    suspend fun clear(owner: String, expectedBearer: String) =
        withContext(dispatchers.io) {
            val root = app.filesDir.canonicalPath
            val directory = "$root/linux/ubuntu/home/oc/.oc-profiles/$owner/.oc-genui"
            try {
                anchored(directory) { bound ->
                    val target = File(bound, CardSearchPublisher.TARGET)
                    if (
                        !Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                            target.length() > 1024
                    )
                        return@anchored false
                    val value =
                        try {
                            val bytes =
                                java.io
                                    .FileInputStream(
                                        Os.open(
                                            target.path,
                                            OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
                                            0,
                                        )
                                    )
                                    .use { input ->
                                        val buffer = ByteArray(1025)
                                        var count = 0
                                        while (count < buffer.size) {
                                            val read =
                                                input.read(buffer, count, buffer.size - count)
                                            if (read < 0) break
                                            count += read
                                        }
                                        buffer.copyOf(count)
                                    }
                            if (bytes.size > 1024) return@anchored false
                            Json.parseToJsonElement(
                                bytes.decodeToString(throwOnInvalidSequence = true)
                            ) as? JsonObject
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    if ((value?.get("bearer") as? JsonPrimitive)?.content == expectedBearer)
                        target.delete()
                    else false
                }
            } catch (_: Exception) {}
            Unit
        }

    private fun anchored(directory: String, action: (String) -> Boolean): Boolean {
        val root = app.filesDir.canonicalPath + "/linux/ubuntu/"
        val dir = File(directory)
        if (
            !directory.startsWith(root) ||
                dir.canonicalPath != directory ||
                !dir.isDirectory ||
                Os.lstat(directory).st_mode and 0x3f != 0
        )
            return false
        val fd =
            Os.open(
                directory,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
                0,
            )
        return try {
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) return false
            android.os.ParcelFileDescriptor.dup(fd).use { held ->
                action("/proc/self/fd/${held.fd}")
            }
        } finally {
            Os.close(fd)
        }
    }
}

/**
 * Exactly one phone owner publishes a descriptor; source/consent changes retire it before the new
 * one starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CardsLifecycle>())
@Inject
class NativeCardsSearchLifecycle(
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val qualifier: NativeCardsQualifier,
    private val payloads: PhonePayloadGuard,
    private val files: NativeCardsRootFs,
    private val servers: ConnectorSearchServers,
    private val search: AgentConnectorSearch,
) : CardsLifecycle {
    private var started = false

    override fun attach(setup: CardsSetup) {
        if (started) return
        started = true
        appScope.launch(dispatchers.main) {
            if (stores.warm() !is Outcome.Ok) return@launch
            combine(registry.live, stores.profiles.activeId) { _, id ->
                    id?.let { qualifier.graph(it.raw) }
                }
                .flatMapLatest { graph ->
                    if (graph == null) flowOf(null)
                    else
                        combine(
                            graph.session.server.location,
                            graph.session.server.events.status,
                            setup.changes,
                            stores.prefs.changes.onStart { emit(emptySet()) },
                            stores.profiles.profiles,
                        ) { location, status, _, _, _ ->
                            val profile = graph.profile.id.raw
                            val owner = qualifier.owner(profile)
                            if (
                                status != StreamStatus.Connected ||
                                    qualifier.graph(profile) !== graph ||
                                    owner == null ||
                                    !setup.effective(profile, CardAgent.Claude)
                            )
                                null
                            else Source(graph, owner, location, consent(owner))
                        }
                }
                .distinctUntilChanged()
                .collectLatest { source ->
                    if (source == null) return@collectLatest
                    val profile = source.graph.profile.id.raw
                    fun current(): Boolean =
                        qualifier.graph(profile) === source.graph &&
                            qualifier.owner(profile) == source.owner &&
                            source.graph.session.server.location.value == source.location &&
                            source.graph.session.server.events.status.value ==
                                StreamStatus.Connected &&
                            setup.effective(profile, CardAgent.Claude) &&
                            consent(source.owner) == source.consent
                    val endpoint =
                        servers.open(::current) { request ->
                            if (!current()) throw IllegalStateException()
                            val lease =
                                (payloads.reserveForProfile(source.graph.profile.id) as? Outcome.Ok)
                                    ?.value ?: throw IllegalStateException()
                            try {
                                if (lease.awaitReady() !is Outcome.Ok || !current())
                                    throw IllegalStateException()
                                search.find(source.owner, request, ::current)
                            } finally {
                                lease.release()
                            }
                        }
                    try {
                        val published =
                            CardSearchPublisher(files)
                                .publish(
                                    source.owner,
                                    CardsHelperAgent.Claude,
                                    endpoint.endpoint,
                                    endpoint.bearer,
                                )
                        if (published && current()) awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            endpoint.close()
                            files.clear(source.owner, endpoint.bearer)
                        }
                    }
                }
        }
    }

    private fun consent(profile: String): Boolean {
        val raw =
            (stores.prefs.get("oc.setupRegistry.$profile") as? PrefValue.Str)?.value ?: return false
        return try {
            ((Json.parseToJsonElement(raw) as? JsonObject)?.get("optedIn") as? JsonPrimitive)
                ?.booleanOrNull == true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private data class Source(
        val graph: ConnectionGraph,
        val owner: String,
        val location: ServerLocation,
        val consent: Boolean,
    )
}
