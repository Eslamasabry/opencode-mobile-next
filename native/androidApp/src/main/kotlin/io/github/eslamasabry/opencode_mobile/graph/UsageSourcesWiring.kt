package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import java.io.IOException
import java.net.Proxy
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * B-STATE-148…154: read only the existing authenticated transports and explicitly consented
 * collectors.
 */
@SingleIn(AppScope::class)
@Inject
class AndroidAdditionalUsageSources(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
) : UsageSource {
    private val snapshots = mutableMapOf<Pair<String, String>, ProviderQuotaSnapshot>()
    private val policyWrites = Mutex()
    private val random = SecureRandom()

    /**
     * The existing metadata-only quota watch policy, retained across restart and shared with
     * migrated rules.
     */
    val alertBridge: UsageAlertBridge =
        object : UsageAlertBridge {
            override fun legacyRules(): List<LegacyQuotaRule> =
                stores.profiles.profiles.value.flatMap { profile ->
                    quotaRules(profile.id).mapNotNull { (provider, rule) ->
                        val threshold =
                            (rule["threshold"] as? Number)?.toDouble()?.takeIf {
                                it.isFinite() && it > 0 && it <= 100
                            } ?: return@mapNotNull null
                        LegacyQuotaRule(
                            profile.id.raw,
                            provider,
                            rule["account"] as String,
                            threshold,
                        )
                    }
                }

            override suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean) =
                policyWrites.withLock {
                    for ((id, watches) in wanted.groupBy { it.connectionId }) {
                        val profileId = ProfileId(id)
                        val saved = stores.profiles.profile(profileId) ?: continue
                        val shape =
                            QuotaServerProfile(
                                profileId,
                                saved.profile.baseUrl,
                                saved.profile.backend == BackendKind.OpenCode2,
                            )
                        val inApp =
                            InAppQuotaCollector.looksLikeInAppServer(shape) &&
                                stores.prefs.bool(InAppQuotaCollector.enabledKey(profileId)) == true
                        val key = "oc.quotaMonitor.$id"
                        val raw = stores.prefs.string(key)
                        val data = raw?.let { DartJson.decodeOrNull(it) as? Map<*, *> }
                        // Untrusted history is never overwritten with a new watch.
                        if (
                            raw != null &&
                                (raw.length > 65_536 ||
                                    data?.get("version") != 1L ||
                                    data?.get("rules") !is Map<*, *>)
                        )
                            continue
                        val rules =
                            (data?.get("rules") as? Map<*, *>)
                                ?.entries
                                ?.associate { it.key.toString() to it.value }
                                ?.toMutableMap() ?: mutableMapOf()
                        val valid = quotaRules(profileId)
                        var changed = false
                        for (watch in watches) {
                            if (
                                !watch.threshold.isFinite() ||
                                    watch.threshold <= 0 ||
                                    watch.threshold > 100
                            )
                                continue
                            val previous = valid[watch.providerId]
                            val snapshot = snapshots[id to watch.providerId]
                            val rule =
                                when {
                                    previous != null ->
                                        previous.entries
                                            .associate { it.key.toString() to it.value }
                                            .toMutableMap()
                                    inApp && alertsOn && snapshot?.account?.ref != null ->
                                        mutableMapOf<String, Any?>(
                                            "source" to
                                                source(
                                                    saved.profile.baseUrl,
                                                    saved.profile.username,
                                                ),
                                            "account" to snapshot.account.ref,
                                            "token" to
                                                ByteArray(32).also(random::nextBytes).joinToString(
                                                    ""
                                                ) {
                                                    (it.toInt() and 255)
                                                        .toString(16)
                                                        .padStart(2, '0')
                                                },
                                            "notifications" to true,
                                            "wifiOnly" to false,
                                            "quietStart" to null,
                                            "quietEnd" to null,
                                            "alerted" to emptyMap<String, String>(),
                                        )
                                    else -> continue
                                }
                            if (rule["threshold"] == watch.threshold) continue
                            rule["threshold"] = watch.threshold
                            rules[watch.providerId] = rule
                            changed = true
                        }
                        if (changed && stores.profiles.profile(profileId) == saved) {
                            val payload =
                                DartJson.encode(linkedMapOf("version" to 1, "rules" to rules))
                            stores.prefs.commit(stores.lanes.forProfile(profileId)) {
                                putString(key, payload)
                            }
                        }
                    }
                }
        }

    private fun source(url: String, username: String): String =
        DartJson.sha256Hex(DartJson.encode(listOf(url, username)))

    fun connections(): List<UsageConnection> =
        stores.profiles.profiles.value
            .filter { it.profile.backend == BackendKind.Paseo }
            .map { UsageConnection(it.id.raw, it.profile.name, UsageConnectionKind.PaseoServer) }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        if (stores.warm() !is Outcome.Ok) return Outcome.Failed(Failure.StorageRefused())
        val readings = ArrayList<UsageReading>()
        val failures = ArrayList<UsageFailure>()
        val signIns = ArrayList<UsageSignIn>()
        val authoritative = LinkedHashSet<String>()
        val unreached = LinkedHashSet<String>()
        for (saved in stores.profiles.profiles.value) {
            val id = saved.id
            if (saved.profile.backend == BackendKind.Paseo) {
                val graph = registry.live.value[id]
                val gateway = graph?.session?.server as? PaseoGateway
                if (gateway == null || !gateway.transport.connected) {
                    unreached += id.raw
                    continue
                }
                val response =
                    attempt({ Failure.Network() }) {
                        val payload =
                            gateway.transport.request(
                                "provider.usage.list.request",
                                JsonObject(emptyMap()),
                                timeoutMs = 10_000,
                                expectedEpoch = gateway.transport.epoch,
                            )
                        parseProviderUsageList(
                            DartJson.decodeOrNull(payload.toString()),
                            context.now,
                        )
                    }
                if (registry.live.value[id] !== graph || stores.profiles.profile(id) == null)
                    continue
                if (response !is Outcome.Ok) {
                    unreached += id.raw
                    continue
                }
                // Flutter's PaseoUsageSource reports readings only, never sign-ins or authority:
                // the parser drops errored/rate-limited providers, so claiming authority here would
                // turn their cached numbers into "Not signed in".
                for (reading in response.value) readings += reading.asUsage(id.raw)
                continue
            }
            if (saved.profile.backend != BackendKind.OpenCode2 || saved.reentry.password) continue
            val shape = QuotaServerProfile(id, saved.profile.baseUrl, isOpenCode = true)
            val local = InAppQuotaCollector.looksLikeInAppServer(shape)
            if (!local && saved.cleartextUnconfirmed) continue
            val enabled = local && stores.prefs.bool(InAppQuotaCollector.enabledKey(id)) == true
            val consent = quotaRules(id)
            if (!enabled && consent.isEmpty()) continue
            val password =
                when (val secret = stores.profiles.secret(id)) {
                    is Outcome.Ok -> secret.value.orEmpty()
                    is Outcome.Failed -> continue
                }
            for (provider in QuotaProvider.entries.filter(::quotaCollectionAvailable)) {
                if (!enabled && provider.wire !in consent) continue
                val reader =
                    HttpProviderQuotaGateway(
                        baseUrl = saved.profile.baseUrl,
                        username = saved.profile.username.ifBlank { "opencode" },
                        password = password,
                        transport =
                            PinnedQuotaTransport(
                                dispatchers,
                                if (local) null
                                else saved.toServerProfile()?.trust?.pinnedLeafSha256,
                            ),
                        provider = provider,
                        inAppToken =
                            if (local) ({ InAppQuotaCollector.readToken(stores.secrets, id) })
                            else null,
                    )
                val result =
                    try {
                        reader.readSnapshot()
                    } finally {
                        reader.close()
                    }
                if (stores.profiles.profile(id) != saved) continue
                when (result) {
                    is QuotaRead.Failed ->
                        when (result.failure.kind) {
                            QuotaFailureKind.CollectorAuth ->
                                readings +=
                                    bare(id.raw, provider, UsageReadingStatus.AuthRequired, context)
                            QuotaFailureKind.Unavailable ->
                                if (local)
                                    failures +=
                                        UsageFailure(id.raw, provider.wire, UsageProblem.Broken)
                                else unreached += id.raw
                            else ->
                                failures += UsageFailure(id.raw, provider.wire, UsageProblem.Broken)
                        }
                    is QuotaRead.Read -> {
                        val snapshot = result.snapshot
                        if (
                            !enabled &&
                                consent[provider.wire]?.get("account") != snapshot.account.ref
                        ) {
                            failures += UsageFailure(id.raw, provider.wire, UsageProblem.Broken)
                            continue
                        }
                        if (snapshot.canShowWindows) snapshots[id.raw to provider.wire] = snapshot
                        when {
                            snapshot.status == ProviderQuotaStatus.Ok && snapshot.canShowWindows ->
                                readings +=
                                    UsageReading(
                                        providerId = provider.wire,
                                        displayName = provider.wire,
                                        connectionId = id.raw,
                                        status =
                                            if (snapshot.windows.none { it.usedPercent != null })
                                                UsageReadingStatus.NoLimits
                                            else UsageReadingStatus.Ok,
                                        fetchedAt = snapshot.fetchedAt,
                                        windows =
                                            snapshot.windows.mapNotNull { w ->
                                                w.usedPercent?.let {
                                                    UsageWindow(
                                                        w.id,
                                                        it,
                                                        w.durationSeconds,
                                                        w.resetsAt,
                                                    )
                                                }
                                            },
                                        accountRef = snapshot.account.ref,
                                        plan = snapshot.account.plan,
                                        method = SignInMethod.Plan,
                                    )
                            snapshot.status == ProviderQuotaStatus.AuthRequired ->
                                readings +=
                                    bare(id.raw, provider, UsageReadingStatus.AuthRequired, context)
                            snapshot.status == ProviderQuotaStatus.Unconfigured ->
                                if (context.lastReading(id.raw, provider.wire) != null)
                                    readings +=
                                        bare(
                                            id.raw,
                                            provider,
                                            UsageReadingStatus.NotSignedIn,
                                            context,
                                        )
                            snapshot.status == ProviderQuotaStatus.RateLimited ->
                                failures +=
                                    UsageFailure(id.raw, provider.wire, UsageProblem.RateLimited)
                            else ->
                                failures += UsageFailure(id.raw, provider.wire, UsageProblem.Broken)
                        }
                    }
                }
            }
        }
        return Outcome.Ok(
            UsageSourceResult(
                readings = readings,
                signIns = signIns,
                failures = failures,
                unreached = unreached,
                authoritativeSignIns = authoritative,
            )
        )
    }

    private fun quotaRules(id: ProfileId): Map<String, Map<*, *>> {
        val raw =
            stores.prefs.string("oc.quotaMonitor.${id.raw}")?.takeIf { it.length <= 65_536 }
                ?: return emptyMap()
        val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyMap()
        if (data["version"] != 1L) return emptyMap()
        val rules = data["rules"] as? Map<*, *> ?: return emptyMap()
        val hash = Regex("^[a-f0-9]{64}$")
        return rules.entries
            .mapNotNull { (provider, value) ->
                val rule = value as? Map<*, *> ?: return@mapNotNull null
                if (
                    provider !is String ||
                        listOf("source", "account", "token").any {
                            (rule[it] as? String)?.matches(hash) != true
                        }
                )
                    return@mapNotNull null
                val profile = stores.profiles.profile(id)?.profile ?: return@mapNotNull null
                if (rule["source"] != source(profile.baseUrl, profile.username))
                    return@mapNotNull null
                if (rule["notifications"] !is Boolean || rule["wifiOnly"] !is Boolean)
                    return@mapNotNull null
                provider to rule
            }
            .toMap()
    }

    private fun bare(
        id: String,
        provider: QuotaProvider,
        status: UsageReadingStatus,
        context: UsageReadContext,
    ) =
        UsageReading(
            provider.wire,
            provider.wire,
            id,
            status,
            context.now,
            method = SignInMethod.Plan,
        )

    private fun ProviderUsageReading.asUsage(id: String) =
        UsageReading(
            providerId = providerId,
            displayName = displayName,
            connectionId = id,
            status =
                if (needsSignIn) UsageReadingStatus.NotSignedIn
                else if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
            fetchedAt = fetchedAt,
            method = SignInMethod.Plan,
            windows =
                windows.map {
                    UsageWindow(
                        if (it.durationSeconds == 18_000L) "5h"
                        else if (it.durationSeconds == 604_800L) "weekly"
                        else it.label.ifEmpty { "window" },
                        100.0 - it.remainingPercent,
                        it.durationSeconds,
                        it.resetsAt,
                        it.label,
                    )
                },
        )
}

/**
 * Dedicated collector HTTP client: no redirects, proxy, cookies, logging or unbounded body reads.
 */
private class PinnedQuotaTransport(private val dispatchers: AppDispatchers, expectedPin: String?) :
    QuotaTransport {
    private val client =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (expectedPin != null)
                    addNetworkInterceptor { chain ->
                        val certificate =
                            chain.connection()?.handshake()?.peerCertificates?.firstOrNull()
                        val actual =
                            certificate
                                ?.encoded
                                ?.let {
                                    java.security.MessageDigest.getInstance("SHA-256").digest(it)
                                }
                                ?.joinToString("") {
                                    (it.toInt() and 255).toString(16).padStart(2, '0')
                                }
                        if (actual != expectedPin.lowercase())
                            throw javax.net.ssl.SSLPeerUnverifiedException("certificate changed")
                        chain.proceed(chain.request())
                    }
            }
            .build()

    override suspend fun fetch(request: QuotaRequest): QuotaResponse {
        val call =
            client
                .newBuilder()
                .connectTimeout(request.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(request.sendTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.receiveTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
                .newCall(
                    Request.Builder()
                        .url(request.url)
                        .apply { for ((name, value) in request.headers) header(name, value) }
                        .get()
                        .build()
                )
        val response =
            suspendCancellableCoroutine<Response> { waiting ->
                waiting.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (waiting.isActive) waiting.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (waiting.isActive)
                                waiting.resume(response) { _, value, _ -> value.close() }
                            else response.close()
                        }
                    }
                )
            }
        return withContext(dispatchers.io) {
            response.use { reply ->
                val bytes =
                    if (reply.code == 200)
                        reply.body.byteStream().use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8_192)
                            while (out.size() <= 262_144) {
                                val count =
                                    input.read(chunk, 0, minOf(chunk.size, 262_145 - out.size()))
                                if (count < 0) break
                                if (count == 0) continue
                                out.write(chunk, 0, count)
                            }
                            out.toByteArray()
                        }
                    else byteArrayOf()
                QuotaResponse(reply.code, flow { emit(bytes) }, reply.isRedirect)
            }
        }
    }

    override fun close(force: Boolean) {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}
