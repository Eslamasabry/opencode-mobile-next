package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaRequest
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import java.net.URI
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

/** Only the shipped phone engine contract; Gas City metadata must never be treated as this API. */
internal fun phoneMonitorConfig(encoded: String?): String? =
    try {
        val config = encoded?.let(::JSONObject)
        if (config?.optString("provider") != "phoneEngine") null
        else
            config
                .optString("url")
                .takeIf { base ->
                    val uri = URI(base)
                    uri.scheme == "http" &&
                        uri.host in setOf("127.0.0.1", "[::1]", "::1") &&
                        uri.rawUserInfo == null &&
                        uri.rawQuery == null &&
                        uri.rawFragment == null &&
                        uri.rawPath in setOf("", "/") &&
                        uri.port in 1..65535
                }
                ?.trimEnd('/')
    } catch (_: Exception) {
        null
    }

/** One owned, cancellable health/workspace read. Closing the client never stops the daemon. */
internal class AndroidMonitorPhoneEngine(
    private val profile: String,
    private val stores: PreviewStores,
    dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : MonitorPhoneEngine {
    private val transport = AndroidQuotaTransport(dispatchers)
    private var encoded: String? = null
    private var base: String? = null
    private var token: String? = null
    private var verified = false
    private var closed = false
    override val projects: Boolean
        get() = verified && !closed

    private fun current(): Boolean =
        !closed &&
            stores.profiles.profile(ProfileId(profile))?.profile?.orchestration?.encoded == encoded

    private suspend fun read(path: String): Outcome<JSONObject> =
        attempt({ Failure.Unexpected() }) {
            check(current())
            val answer =
                transport.fetch(
                    QuotaRequest(
                        "GET",
                        checkNotNull(base) + path,
                        persistentMapOf(
                            "Accept" to "application/json",
                            "Authorization" to "Bearer ${checkNotNull(token)}",
                        ),
                        false,
                        0,
                        3_000,
                        3_000,
                        5_000,
                    )
                )
            check(answer.status == 200 && !answer.isRedirect)
            val bytes = java.io.ByteArrayOutputStream()
            answer.body.collect { chunk ->
                check(current() && bytes.size() + chunk.size <= MAX_BODY)
                bytes.write(chunk)
            }
            check(current())
            JSONObject(bytes.toString("UTF-8"))
        }

    override suspend fun health(): Outcome<String> {
        val saved =
            stores.profiles.profile(ProfileId(profile))
                ?: return Failure.SecretUnavailable().failed()
        encoded = saved.profile.orchestration?.encoded
        base = phoneMonitorConfig(encoded) ?: return Failure.SecretUnavailable().failed()
        token =
            when (val value = stores.secrets.read(ProfileKeys.TEAM_ENGINE_AUTH + profile)) {
                is Outcome.Ok -> value.value
                is Outcome.Failed -> return value
            }
        val secret =
            token?.takeIf { it.isNotEmpty() && '\r' !in it && '\n' !in it }
                ?: return Failure.SecretUnavailable().failed()
        redactor.registerSecret(secret)
        return when (val result = read("/v1/health")) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val raw = result.value
                val capabilities = raw.optJSONObject("capabilities")
                if (
                    raw.optInt("schemaVersion") != 1 ||
                        raw.optString("profileId") != profile ||
                        raw.optString("engineVersion").isBlank() ||
                        capabilities == null ||
                        listOf("execution", "boundary", "oc2").any {
                            capabilities.opt(it) !is Boolean
                        } ||
                        raw.optJSONArray("commandActions") == null
                )
                    Failure.Unexpected().failed()
                else {
                    verified = true
                    profile.ok()
                }
            }
        }
    }

    override suspend fun workspace(): Outcome<MonitorTeamWorkspace> {
        if (!projects) return Failure.Unexpected().failed()
        return when (val result = read("/v1/workspace")) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                attempt({ Failure.Unexpected() }) {
                    val raw = result.value
                    check(
                        raw.optInt("schemaVersion") == 1 &&
                            raw.opt("simulated") == false &&
                            raw.opt("revision") is Number
                    )
                    check(raw.optJSONArray("servers") != null && raw.optJSONArray("roles") != null)
                    val projects = raw.getJSONArray("projects")
                    check(projects.length() <= MAX_RECORDS)
                    var count = projects.length()
                    MonitorTeamWorkspace(
                        (0 until projects.length()).map { index ->
                            val project = projects.getJSONObject(index)
                            val tasks = project.getJSONArray("tasks")
                            val requests = project.getJSONArray("requests")
                            count += tasks.length() + requests.length()
                            check(count <= MAX_RECORDS)
                            MonitorTeamProject(
                                project.getString("id"),
                                (0 until tasks.length()).map { i ->
                                    tasks.getJSONObject(i).let {
                                        MonitorTeamTask(
                                            it.getString("id"),
                                            it.optString("title").takeIf(String::isNotBlank),
                                            it.getString("status"),
                                        )
                                    }
                                },
                                (0 until requests.length()).map { i ->
                                    requests.getJSONObject(i).let {
                                        MonitorTeamRequest(
                                            it.getString("id"),
                                            it.optString("taskId"),
                                            it.optString("title").takeIf(String::isNotBlank),
                                            it.optBoolean("answered"),
                                        )
                                    }
                                },
                            )
                        }
                    )
                }
        }
    }

    override fun close() {
        closed = true
        verified = false
        token = null
        transport.close(true)
    }

    private companion object {
        const val MAX_BODY = 1024 * 1024
        const val MAX_RECORDS = 256
    }
}
