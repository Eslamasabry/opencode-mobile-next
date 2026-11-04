package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Reads only existing explicit collector enrollments. A new or changed account is never silently
 * enrolled.
 */
@SingleIn(AppScope::class)
@Inject
class PlansLegacyCollectors(
    private val stores: PreviewStores,
    private val clients: HttpClientFactory,
    private val redactor: Redactor,
) : UsageSource {
    private class Enrollment(
        val provider: QuotaProvider,
        val account: String,
        val threshold: Double,
    )

    private fun current(p: LoadedProfile): Boolean =
        stores.profiles.profile(p.id) == p && stores.lanes.forProfile(p.id).isAdmitting

    private fun rules(p: LoadedProfile): List<Enrollment> {
        if (p.profile.backend != BackendKind.OpenCode2) return emptyList()
        val raw =
            stores.prefs.string(key(p.id.raw))?.takeIf { it.length <= 65_536 } ?: return emptyList()
        val root = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyList()
        if ((root["version"] as? Number)?.toDouble() != 1.0) return emptyList()
        val records = root["rules"] as? Map<*, *> ?: return emptyList()
        val source =
            DartJson.sha256Hex(DartJson.encode(listOf(p.profile.baseUrl, p.profile.username)))
        fun hash(value: Any?) = value is String && HASH.matches(value)
        fun minute(value: Any?) =
            value == null ||
                value is Number &&
                    value.toLong().toDouble() == value.toDouble() &&
                    value.toLong() in 0..1439
        return QuotaProvider.entries.filter(::quotaCollectionAvailable).mapNotNull { provider ->
            val r = records[provider.wire] as? Map<*, *> ?: return@mapNotNull null
            val threshold =
                if (r["threshold"] == null) 100.0
                else (r["threshold"] as? Number)?.toDouble() ?: return@mapNotNull null
            val alerted = r["alerted"] as? Map<*, *> ?: return@mapNotNull null
            if (
                r["source"] != source ||
                    !hash(r["account"]) ||
                    !hash(r["token"]) ||
                    r["notifications"] !is Boolean ||
                    r["wifiOnly"] !is Boolean ||
                    !minute(r["quietStart"]) ||
                    !minute(r["quietEnd"]) ||
                    !threshold.isFinite() ||
                    threshold <= 0 ||
                    threshold > 100 ||
                    alerted.size > 64 ||
                    alerted.any { !hash(it.key) || !hash(it.value) }
            )
                return@mapNotNull null
            Enrollment(provider, r["account"] as String, threshold)
        }
    }

    fun legacyRules(): List<LegacyQuotaRule> =
        stores.profiles.profiles.value.flatMap { p ->
            rules(p).map { LegacyQuotaRule(p.id.raw, it.provider.wire, it.account, it.threshold) }
        }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        val readings = ArrayList<UsageReading>()
        val failures = ArrayList<UsageFailure>()
        val unreached = HashSet<String>()
        for (p in stores.profiles.profiles.value) {
            // The app's collector is managed by its private token reader, never by remote Basic
            // auth.
            if (stores.prefs.string("oc.builtinServerOwner") == p.id.raw) continue
            val enrollments = rules(p)
            if (enrollments.isEmpty() || !current(p)) continue
            for (rule in enrollments) {
                if (!current(p)) break
                val snapshot =
                    when (val result = withTimeoutOrNull(12_000) { fetch(p, rule.provider) }) {
                        is Outcome.Ok -> result.value
                        is Outcome.Failed -> {
                            when (result.failure) {
                                is Failure.SignIn ->
                                    readings +=
                                        bare(
                                            p.id.raw,
                                            rule.provider,
                                            UsageReadingStatus.AuthRequired,
                                            context,
                                        )
                                is Failure.Network,
                                is Failure.TimedOut -> unreached += p.id.raw
                                else ->
                                    failures +=
                                        UsageFailure(
                                            p.id.raw,
                                            rule.provider.wire,
                                            UsageProblem.Broken,
                                        )
                            }
                            continue
                        }
                        null -> {
                            unreached += p.id.raw
                            continue
                        }
                    }
                if (
                    !current(p) ||
                        rules(p).none { it.provider == rule.provider && it.account == rule.account }
                )
                    continue
                when {
                    snapshot.status == ProviderQuotaStatus.Ok &&
                        snapshot.canShowWindows &&
                        snapshot.account.ref == rule.account &&
                        !snapshot.isStale(context.now) ->
                        readings +=
                            UsageReading(
                                providerId = rule.provider.wire,
                                displayName = providerName(rule.provider),
                                connectionId = p.id.raw,
                                status =
                                    if (snapshot.windows.any { it.usedPercent != null })
                                        UsageReadingStatus.Ok
                                    else UsageReadingStatus.NoLimits,
                                fetchedAt = snapshot.fetchedAt,
                                accountRef = snapshot.account.ref,
                                plan = snapshot.account.plan,
                                method = SignInMethod.Plan,
                                windows =
                                    snapshot.windows.mapNotNull { w ->
                                        w.usedPercent?.let {
                                            UsageWindow(w.id, it, w.durationSeconds, w.resetsAt)
                                        }
                                    },
                            )
                    snapshot.status == ProviderQuotaStatus.Unconfigured ->
                        if (context.lastReading(p.id.raw, rule.provider.wire) != null)
                            readings +=
                                bare(
                                    p.id.raw,
                                    rule.provider,
                                    UsageReadingStatus.NotSignedIn,
                                    context,
                                )
                    snapshot.status == ProviderQuotaStatus.AuthRequired ->
                        readings +=
                            bare(p.id.raw, rule.provider, UsageReadingStatus.AuthRequired, context)
                    snapshot.status == ProviderQuotaStatus.RateLimited ->
                        failures +=
                            UsageFailure(p.id.raw, rule.provider.wire, UsageProblem.RateLimited)
                    else ->
                        failures += UsageFailure(p.id.raw, rule.provider.wire, UsageProblem.Broken)
                }
            }
        }
        return UsageSourceResult(readings = readings, failures = failures, unreached = unreached)
            .ok()
    }

    private fun bare(
        id: String,
        provider: QuotaProvider,
        status: UsageReadingStatus,
        context: UsageReadContext,
    ) =
        UsageReading(
            provider.wire,
            providerName(provider),
            id,
            status,
            context.now,
            method = SignInMethod.Plan,
        )

    private fun providerName(provider: QuotaProvider) =
        when (provider) {
            QuotaProvider.Codex -> "Codex"
            QuotaProvider.Claude -> "Claude"
            QuotaProvider.Glm -> "GLM"
            QuotaProvider.Minimax -> "MiniMax"
        }

    private suspend fun fetch(
        p: LoadedProfile,
        provider: QuotaProvider,
    ): Outcome<ProviderQuotaSnapshot> {
        if (!current(p) || p.reentry.password || p.reentry.agentToken)
            return Failure.SecretUnavailable().failed()
        val model =
            p.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val origin = model.origin
        val loopback = AddressRules.isLoopback(origin.host)
        if (origin.scheme != "https" && !loopback && model.trust.cleartextConsentAtMs == null)
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val pin =
            model.trust.pinnedLeafSha256?.let { raw ->
                val hex = raw.replace(":", "")
                if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null })
                    return Failure.UnreadableReply().failed()
                ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }
        if (origin.scheme != "https" && pin != null)
            return Failure.Refused(RefusalReason.PinNeedsHttps).failed()
        val secret =
            when (val value = stores.profiles.secret(p.id)) {
                is Outcome.Failed -> return value
                is Outcome.Ok ->
                    value.value?.takeIf { it.isNotEmpty() }
                        ?: return Failure.SecretUnavailable().failed()
            }
        redactor.registerSecret(secret)
        if (
            ':' in p.profile.username ||
                CONTROL.containsMatchIn(p.profile.username) ||
                CONTROL.containsMatchIn(secret)
        )
            return Failure.SecretUnavailable().failed()
        if (!current(p)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val client =
            clients.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    loopback || model.trust.cleartextConsentAtMs != null,
                    pin,
                    10_000,
                    10_000,
                )
            )
        return try {
            val response =
                client.exchange(
                    build = {
                        url(origin.toString() + quotaPathFor(provider))
                        method = HttpMethod.Get
                        header(HttpHeaders.Accept, "application/json")
                        header(
                            HttpHeaders.Authorization,
                            "Basic " +
                                Base64.getEncoder()
                                    .encodeToString(
                                        (p.profile.username + ":" + secret).toByteArray(
                                            Charsets.UTF_8
                                        )
                                    ),
                        )
                    }
                ) { response ->
                    val channel = response.bodyAsChannel()
                    val bytes = ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    while (true) {
                        val count = channel.readAvailable(chunk, 0, chunk.size)
                        if (count < 0) break
                        if (bytes.size() + count > 65_536) return@exchange null
                        bytes.write(chunk, 0, count)
                    }
                    try {
                        ProviderQuotaSnapshot.fromJson(
                            Json.parseToJsonElement(
                                bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
                            )
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            if (!current(p)) Failure.Refused(RefusalReason.StaleScope).failed()
            else
                response.flatMap { value ->
                    if (value != null && value.provider == provider) value.ok()
                    else Failure.UnreadableReply().failed()
                }
        } finally {
            client.close()
        }
    }

    companion object {
        private val HASH = Regex("^[a-f0-9]{64}$")
        private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")

        fun key(id: String) = "oc.quotaMonitor.$id"

        fun register(scope: ProfileScope) {
            listOf("oc.quotaMonitor.", "oc.usageLast.", "oc.widgetUsage.").forEach { prefix ->
                scope.register(
                    KeyShape(
                        prefix,
                        KeyScope.Profile,
                        PrefType.Str,
                        ":engine:integrations",
                        "profile usage readings and consent",
                    )
                )
            }
        }
    }
}
