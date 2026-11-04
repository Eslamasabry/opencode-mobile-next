package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.SetupInspectionGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.runtime.setupassistant.*
import io.github.eslamasabry.oc.feature.setupassistant.presentation.SetupHost
import io.github.eslamasabry.oc.feature.setupassistant.presentation.SetupWorkspace
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One shared audit and registry owner per profile; every screen reads the live connection it opened
 * against.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SetupHost>())
class AndroidSetupHost(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    app: AppCoroutineScope,
) : SetupHost {
    private val opening = Mutex()
    private val random = SecureRandom()
    private val blocked = ConcurrentHashMap.newKeySet<ProfileId>()
    private val registries = ConcurrentHashMap<ProfileId, SetupRegistryStore>()
    private val inspections =
        ConcurrentHashMap<ProfileId, CopyOnWriteArrayList<SetupInspectionController>>()
    private val audits =
        SetupAuditOwners(stores.prefs, stores.lanes, redactor, clock) {
            stores.profiles.profile(it) != null && it !in blocked
        }

    init {
        CoroutineScope(app.coroutineContext).launch {
            registry.live.collect { live ->
                for ((profile, controllers) in inspections) controllers.forEach {
                    it.setOnline(profile in live && profile !in blocked)
                }
            }
        }
    }

    override suspend fun open(profile: ProfileId): Outcome<SetupWorkspace> = opening.withLock {
        if (
            profile in blocked ||
                stores.warm() !is Outcome.Ok ||
                stores.profiles.profile(profile) == null
        )
            return@withLock stale()
        val controller =
            if (bind(profile) == null) null
            else SetupInspectionController { bind(profile) }
        controller?.let {
            inspections.getOrPut(profile) { CopyOnWriteArrayList() }.apply {
                removeAll { old -> old.isClosed }
                add(it)
            }
        }
        val publicRegistry =
            registries.getOrPut(profile) {
                SetupRegistryStore(
                        stores.prefs,
                        stores.lanes,
                        profile,
                        SetupRegistryReader(::fetchRegistry),
                        redactor,
                        clock,
                    ) {
                        profile !in blocked && stores.profiles.profile(profile) != null
                    }
                    .also { it.load() }
            }
        Outcome.Ok(
            SetupWorkspace(
                controller,
                publicRegistry,
                audits.forProfile(profile),
                { registry.live.value[profile] != null && profile !in blocked },
                ::newId,
            )
        )
    }

    /** The connection live now, fenced to its graph and location until the reply arrives. */
    private fun bind(profile: ProfileId): SetupInspectionBinding? {
        val graph = registry.live.value[profile] ?: return null
        val server = graph.session.server
        val location = server.location.value
        if (graph.session.capabilities.value.setupConfigRead != true) return null
        val gateway = server.facet<SetupInspectionGateway>() ?: return null
        return SetupInspectionBinding(gateway) {
            profile !in blocked &&
                stores.profiles.profile(profile) != null &&
                registry.live.value[profile] === graph &&
                server.location.value == location
        }
    }

    fun stop(profile: ProfileId) {
        blocked += profile
        audits.stop(profile)
        registries[profile]?.stopAdmission()
        inspections[profile]?.forEach { it.stopAdmission() }
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> {
        audits.close(profile)
        registries[profile]?.close()
        inspections[profile]?.forEach { it.close() }
        return Outcome.Ok(Unit)
    }

    fun reopen(profile: ProfileId) {
        registries.remove(profile)
        inspections.remove(profile)
        audits.reopen(profile)
        blocked -= profile
    }

    private fun newId(): String =
        ByteArray(24).also(random::nextBytes).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }

    private fun stale(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))

    /**
     * The public client never borrows a server's authentication, follows redirects, or reads over
     * 512 KiB.
     */
    private suspend fun fetchRegistry(search: String?): Outcome<String> =
        withContext(dispatchers.io) {
            var connection: HttpURLConnection? = null
            try {
                val query =
                    "?limit=100&version=latest" +
                        (search?.let { "&search=" + URLEncoder.encode(it, "UTF-8") } ?: "")
                connection =
                    URL("https://registry.modelcontextprotocol.io/v0.1/servers" + query)
                        .openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != 200)
                    return@withContext Outcome.Failed(Failure.Server(connection.responseCode))
                if (connection.contentLengthLong > 512 * 1024)
                    return@withContext Outcome.Failed(Failure.UnreadableReply())
                val bytes = ByteArrayOutputStream()
                val deadline = clock.now() + kotlin.time.Duration.parse("30s")
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > 512 * 1024 || clock.now() > deadline)
                            return@withContext Outcome.Failed(Failure.UnreadableReply())
                        bytes.write(buffer, 0, count)
                    }
                }
                Outcome.Ok(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
            } catch (_: IOException) {
                Outcome.Failed(Failure.Device())
            } catch (_: IllegalArgumentException) {
                Outcome.Failed(Failure.UnreadableReply())
            } finally {
                connection?.disconnect()
            }
        }
}

/**
 * Deletion closes immediately, drains accepted disk writes, and reopens only fresh owners on abort.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class SetupStateCloser(private val host: AndroidSetupHost) : ProfileCloser {
    override val owner: String = ":engine:runtime setup-assistant"

    override fun stopAdmission(profile: ProfileId): Unit = host.stop(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = host.drain(profile)

    override fun reopen(profile: ProfileId): Unit = host.reopen(profile)
}
