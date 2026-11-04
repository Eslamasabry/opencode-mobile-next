package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.setupassist.*
import io.github.eslamasabry.oc.feature.servers.presentation.ServersSetupSession
import io.github.eslamasabry.oc.feature.servers.presentation.ServersSetupSource
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2SetupInspection
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One setup owner per open page, fenced by its graph, location, profile lifetime and the global
 * deletion lane.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServersSetupSource>())
@Inject
class AndroidServersSetup(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val device: DeviceFacts,
    scope: ProfileScope,
) : ServersSetupSource {
    private val blocked = mutableSetOf<ProfileId>()
    private val sessions = mutableMapOf<ProfileId, MutableSet<ServersSetupSession>>()

    init {
        for (prefix in SetupProfileKeys.prefixes) scope.register(
            KeyShape(
                prefix,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:setupassist",
                "Setup consent, safe metadata and bounded audit records.",
            )
        )
    }

    override suspend fun open(profile: ProfileId): Outcome<ServersSetupSession> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        if (profile in blocked || stores.profiles.profile(profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph =
            registry.live.value[profile]
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val gateway = graph.session.server
        val capturedLocation = gateway.location.value
        if (!gateway.capabilities.value.setupConfigRead)
            return Failure.Unavailable(FeatureId("setup.read")).failed()
        val prefs = KeyValueSetupPrefs(stores.prefs, stores.lanes.forProfile(profile))
        val presence = ProfilePresence { id ->
            id == profile.raw && profile !in blocked && stores.profiles.profile(profile) != null
        }
        val audits = SetupAuditRegistry(prefs, redactor, clock, presence)
        var stopped = false
        fun liveScope(): Boolean =
            !stopped &&
                profile !in blocked &&
                stores.profiles.profile(profile) != null &&
                registry.live.value[profile] === graph &&
                gateway.location.value == capturedLocation
        val client =
            object : SetupHttpClient {
                override val location: SetupLocation =
                    SetupLocation(capturedLocation.directory, capturedLocation.workspace)

                override suspend fun getJson(
                    path: String,
                    query: Map<String, String>,
                ): SetupHttpReply {
                    if (!liveScope()) return SetupHttpReply.Network
                    val result = Oc2SetupInspection.read(gateway, capturedLocation, path)
                    if (!liveScope()) return SetupHttpReply.Network
                    return when (result) {
                        is Outcome.Ok -> SetupHttpReply.Json(result.value)
                        is Outcome.Failed ->
                            when (val failure = result.failure) {
                                is Failure.SignIn -> SetupHttpReply.Status(401)
                                is Failure.Network,
                                is Failure.TimedOut -> SetupHttpReply.Network
                                is Failure.Unavailable,
                                is Failure.NotFound -> SetupHttpReply.RemovedInStable
                                else -> SetupHttpReply.Unreadable
                            }
                    }
                }
            }
        val controller =
            SetupController(
                Oc2SetupConfigGateway(client, redactor),
                audits,
                profile.raw,
                capturedLocation.directory ?: capturedLocation.workspace ?: "default",
                redactor,
                SetupIds {
                    ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") {
                        "%02x".format(it.toInt() and 255)
                    }
                },
                ::liveScope,
            )
        val planner = SetupPlanner(redactor)
        val listings =
            SetupRegistryStore(
                prefs,
                profile.raw,
                SetupRegistryClient(PublicRegistryHttp(dispatchers), redactor),
                redactor,
                clock,
            )
        listings.load()
        val owner =
            object : ServersSetupSession {
                override val controller: SetupController = controller
                override val planner: SetupPlanner = planner
                override val registry: SetupRegistryStore = listings

                override fun current(): Boolean = liveScope()

                override fun online(): Boolean = device.network.value.online == Online.Yes

                override fun stop() {
                    stopped = true
                    planner.dispose()
                }

                override suspend fun close() {
                    stop()
                    listings.close()
                    controller.dispose()
                    audits.closeProfile(profile.raw)
                    sessions[profile]?.remove(this)
                }
            }
        sessions.getOrPut(profile) { mutableSetOf() }.add(owner)
        return owner.ok()
    }

    fun stop(profile: ProfileId) {
        blocked.add(profile)
        sessions[profile]?.toList()?.forEach { it.stop() }
    }

    suspend fun drain(profile: ProfileId) {
        sessions[profile]?.toList()?.forEach { it.close() }
        sessions.remove(profile)
    }

    fun reopen(profile: ProfileId) {
        blocked.remove(profile)
    }
}

/**
 * Public metadata request: fixed HTTPS origin, no cookies, auth, redirects or unbounded buffering.
 */
private class PublicRegistryHttp(private val dispatchers: AppDispatchers) : RegistryHttp {
    private val client =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    override suspend fun get(
        request: RegistryRequest,
        cancellation: RegistryCancellation,
    ): RegistryHttpReply =
        withContext(dispatchers.io) {
            if (
                cancellation.isCancelled ||
                    request.url != SetupRegistryClient.ENDPOINT ||
                    request.followRedirects ||
                    request.maxRedirects != 0 ||
                    request.headers != mapOf("Accept" to "application/json") ||
                    request.query.keys.any { it !in setOf("limit", "version", "search") }
            ) {
                return@withContext RegistryHttpReply.Failed
            }
            try {
                val url =
                    request.url
                        .toHttpUrl()
                        .newBuilder()
                        .also { builder ->
                            request.query.forEach { (key, value) ->
                                builder.addQueryParameter(key, value)
                            }
                        }
                        .build()
                val call =
                    client.newCall(
                        Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .get()
                            .build()
                    )
                call.execute().use { response ->
                    if (response.code != 200)
                        return@withContext RegistryHttpReply.Body(response.code, byteArrayOf())
                    val maximum = minOf(request.maxBytes, SetupRegistryClient.MAX_RESPONSE_BYTES)
                    val body = response.body ?: return@withContext RegistryHttpReply.Failed
                    if (body.contentLength() > maximum)
                        return@withContext RegistryHttpReply.TooLarge
                    val bytes = ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val chunk = ByteArray(8192)
                        while (!cancellation.isCancelled) {
                            val read =
                                input.read(chunk, 0, minOf(chunk.size, maximum - bytes.size() + 1))
                            if (read < 0) return@use
                            if (bytes.size() + read > maximum)
                                return@withContext RegistryHttpReply.TooLarge
                            bytes.write(chunk, 0, read)
                        }
                    }
                    if (cancellation.isCancelled) RegistryHttpReply.Failed
                    else RegistryHttpReply.Body(response.code, bytes.toByteArray())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RegistryHttpReply.Failed
            }
        }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class SetupProfileCloser(private val source: AndroidServersSetup) : ProfileCloser {
    override val owner: String = ":engine:setupassist"

    override fun stopAdmission(profile: ProfileId): Unit = source.stop(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        source.drain(profile)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId): Unit = source.reopen(profile)
}
