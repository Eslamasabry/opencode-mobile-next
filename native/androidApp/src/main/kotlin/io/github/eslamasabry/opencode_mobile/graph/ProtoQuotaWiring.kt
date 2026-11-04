package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.*

/**
 * The Plans page reads existing, consented collectors. A fresh installation never probes an
 * arbitrary collector.
 */
@Inject
class ProtoQuotaSource(private val stores: PreviewStores, private val http: HttpClientFactory) :
    UsageSource {
    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        val readings = mutableListOf<UsageReading>()
        val failures = mutableListOf<UsageFailure>()
        val unreached = mutableSetOf<String>()
        for (saved in stores.profiles.profiles.value.toList()) {
            val profile = saved.profile
            if (profile.backend != BackendKind.OpenCode2 || saved.reentry.password) continue
            val origin = saved.toServerProfile()?.origin ?: continue
            val own =
                InAppQuotaCollector.looksLikeInAppServer(
                    QuotaServerProfile(profile.id, profile.baseUrl, true)
                )
            val password = stores.profiles.secret(profile.id).getOrNull().orEmpty()
            for (provider in QuotaProvider.entries) {
                if (!quotaCollectionAvailable(provider)) continue
                if (own) {
                    if (stores.prefs.bool(InAppQuotaCollector.enabledKey(profile.id)) != true)
                        continue
                } else if (!consented(profile.id.raw, profile.baseUrl, profile.username, provider))
                    continue
                val target =
                    if (own) ServerOrigin("http", "127.0.0.1", IN_APP_QUOTA_PORT) else origin
                val connection = saved.toServerProfile() ?: continue
                val pinText = if (own) null else connection.trust.pinnedLeafSha256
                if (
                    pinText != null &&
                        (pinText.length != 64 || pinText.any { it.digitToIntOrNull(16) == null })
                )
                    continue
                val pin = pinText?.let { value ->
                    ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                }
                val net =
                    http.create(
                        TransportSpec(
                            target,
                            AddressRules.classify(target.host),
                            cleartextPermitted =
                                own ||
                                    !AddressRules.needsCleartextConsent(target) ||
                                    !saved.cleartextUnconfirmed,
                            pinnedLeafSha256 = pin,
                            connectTimeoutMs = 10_000,
                            readTimeoutMs = 10_000,
                        )
                    )
                val token =
                    if (own)
                        InAppQuotaCollector.tokenReaderFor(
                            QuotaServerProfile(profile.id, profile.baseUrl, true),
                            stores.secrets,
                        )
                    else null
                val gateway =
                    HttpProviderQuotaGateway(
                        profile.baseUrl,
                        profile.username,
                        password,
                        saved.reentry.password,
                        ProtoQuotaTransport(net),
                        provider,
                        token,
                    )
                val answer =
                    try {
                        gateway.readSnapshot()
                    } finally {
                        gateway.close()
                    }
                // A deleted or edited profile cannot put a late result back in its page or widget.
                if (stores.profiles.profile(profile.id) != saved) continue
                when (answer) {
                    is QuotaRead.Failed ->
                        when (answer.failure.kind) {
                            QuotaFailureKind.CollectorAuth ->
                                readings +=
                                    bare(
                                        profile.id.raw,
                                        provider,
                                        UsageReadingStatus.AuthRequired,
                                        context,
                                    )
                            QuotaFailureKind.Unavailable ->
                                if (own)
                                    failures +=
                                        UsageFailure(
                                            profile.id.raw,
                                            provider.wire,
                                            UsageProblem.Broken,
                                        )
                                else unreached += profile.id.raw
                            else ->
                                failures +=
                                    UsageFailure(profile.id.raw, provider.wire, UsageProblem.Broken)
                        }
                    is QuotaRead.Read -> {
                        val snapshot = answer.snapshot
                        when {
                            snapshot.status == ProviderQuotaStatus.Ok &&
                                snapshot.canShowWindows -> {
                                val windows =
                                    snapshot.windows.mapNotNull { w ->
                                        w.usedPercent?.let {
                                            UsageWindow(w.id, it, w.durationSeconds, w.resetsAt)
                                        }
                                    }
                                readings +=
                                    UsageReading(
                                        provider.wire,
                                        name(provider),
                                        profile.id.raw,
                                        if (windows.isEmpty()) UsageReadingStatus.NoLimits
                                        else UsageReadingStatus.Ok,
                                        snapshot.fetchedAt,
                                        windows,
                                        snapshot.account.ref,
                                        snapshot.account.plan,
                                        SignInMethod.Plan,
                                        context.now,
                                        if (snapshot.isStale(context.now)) UsageProblem.Offline
                                        else null,
                                    )
                            }
                            snapshot.status == ProviderQuotaStatus.AuthRequired ->
                                readings +=
                                    bare(
                                        profile.id.raw,
                                        provider,
                                        UsageReadingStatus.AuthRequired,
                                        context,
                                    )
                            snapshot.status == ProviderQuotaStatus.Unconfigured ->
                                if (context.lastReading(profile.id.raw, provider.wire) != null)
                                    readings +=
                                        bare(
                                            profile.id.raw,
                                            provider,
                                            UsageReadingStatus.NotSignedIn,
                                            context,
                                        )
                            else ->
                                failures +=
                                    UsageFailure(
                                        profile.id.raw,
                                        provider.wire,
                                        if (snapshot.status == ProviderQuotaStatus.RateLimited)
                                            UsageProblem.RateLimited
                                        else UsageProblem.Broken,
                                    )
                        }
                    }
                }
            }
        }
        return UsageSourceResult(readings, failures = failures, unreached = unreached).ok()
    }

    private fun consented(
        id: String,
        base: String,
        username: String,
        provider: QuotaProvider,
    ): Boolean {
        val text =
            stores.prefs.string("oc.quotaMonitor.$id")?.takeIf { it.length <= 65_536 }
                ?: return false
        val json =
            try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return false
        if ((json["version"] as? JsonPrimitive)?.intOrNull != 1) return false
        val rule = (json["rules"] as? JsonObject)?.get(provider.wire) as? JsonObject ?: return false
        // Flutter's consent names the source origin and Basic principal. It never follows an edited
        // server.
        val expected = DartJson.sha256Hex(DartJson.encode(listOf(base, username)))
        return (rule["source"] as? JsonPrimitive)?.content == expected
    }

    private fun bare(
        id: String,
        provider: QuotaProvider,
        status: UsageReadingStatus,
        context: UsageReadContext,
    ) =
        UsageReading(
            provider.wire,
            name(provider),
            id,
            status,
            context.now,
            method = SignInMethod.Plan,
        )

    private fun name(provider: QuotaProvider) =
        when (provider) {
            QuotaProvider.Codex -> "Codex"
            QuotaProvider.Claude -> "Claude"
            QuotaProvider.Glm -> "GLM"
            QuotaProvider.Minimax -> "MiniMax"
        }
}

/**
 * A dedicated transport: trust-bound origin, header auth, no redirects, bounded streaming before
 * buffering.
 */
private class ProtoQuotaTransport(private val net: NetClient) : QuotaTransport {
    override suspend fun fetch(request: QuotaRequest): QuotaResponse =
        net.http
            .prepareGet(request.url) {
                request.headers.forEach { (key, value) -> header(key, value) }
                timeout {
                    requestTimeoutMillis = request.receiveTimeoutMs
                    connectTimeoutMillis = request.connectTimeoutMs
                    socketTimeoutMillis = request.receiveTimeoutMs
                }
            }
            .execute { response ->
                val status = response.status.value
                if (status != 200)
                    return@execute QuotaResponse(status, emptyFlow(), status in 300..399)
                val body = response.bodyAsChannel()
                val bytes = ArrayList<Byte>()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = body.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (bytes.size + count > HttpProviderQuotaGateway.MAX_RESPONSE_BYTES)
                        return@execute QuotaResponse(
                            200,
                            flowOf(ByteArray(HttpProviderQuotaGateway.MAX_RESPONSE_BYTES + 1)),
                        )
                    for (i in 0 until count) bytes += buffer[i]
                }
                QuotaResponse(status, flowOf(bytes.toByteArray()))
            }

    override fun close(force: Boolean) = net.close()
}
