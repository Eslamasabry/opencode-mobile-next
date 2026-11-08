package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaRequest
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHealth
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.team.TeamWorkspaceJson
import kotlinx.collections.immutable.persistentMapOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** s23-team: the AI Team engine over the phone engine's loopback API. */
@BindingContainer
@ContributesTo(AppScope::class)
object TeamBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun engine(host: AppTeamHost): TeamEngine = TeamEngine(host)
}

/**
 * The phone engine as Flutter's `PhoneEngineGateway` reads it: the saved `orchestration` config, the Keystore bearer
 * (`oc.teamEngineAuth.<id>`), `GET /v1/health` for this profile, then `GET /v1/workspace`. Each read owns its client
 * and closes it; closing never stops the daemon. The token is registered with the redactor and never logged.
 */
@SingleIn(AppScope::class)
@Inject
class AppTeamHost(
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : TeamHost {
    override suspend fun config(profileId: String): TeamConfig? {
        stores.warm()
        return TeamConfig.parse(stores.profiles.profile(ProfileId(profileId))?.profile?.orchestration?.encoded)
    }

    private suspend fun token(profileId: String): Outcome<String> {
        val token =
            when (val read = stores.secrets.read(ProfileKeys.TEAM_ENGINE_AUTH + profileId)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
                ?: return Outcome.Failed(Failure.SecretUnavailable())
        redactor.registerSecret(token)
        return Outcome.Ok(token)
    }

    /** `GET /v1/health` answering for [profileId] in schema 1 (Dart `PhoneEngineHealth.fromJson`), or a thrown refusal. */
    private suspend fun health(transport: AndroidQuotaTransport, base: String, token: String, profileId: String): TeamHealth =
        checkNotNull(TeamHealth.parse(DartJson.decodeOrNull(get(transport, "$base/v1/health", token, HEALTH_BODY)), profileId))

    override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> {
        val base = config.loopbackBase ?: return Outcome.Failed(Failure.SecretUnavailable())
        val token =
            when (val t = token(profileId)) {
                is Outcome.Ok -> t.value
                is Outcome.Failed -> return t
            }
        val transport = AndroidQuotaTransport(dispatchers)
        return try {
            attempt({ Failure.Unexpected() }) {
                val health = health(transport, base, token, profileId)
                TeamRead(health, checkNotNull(TeamWorkspaceJson.decode(get(transport, "$base/v1/workspace", token, WORKSPACE_BODY))))
            }
        } finally {
            transport.close(true)
        }
    }

    override suspend fun command(
        profileId: String,
        config: TeamConfig,
        command: TeamCommand,
        body: Map<String, Any?>,
    ): Outcome<TeamCommandResult> {
        val base = config.loopbackBase ?: return Outcome.Failed(Failure.SecretUnavailable())
        val token =
            when (val t = token(profileId)) {
                is Outcome.Ok -> t.value
                is Outcome.Failed -> return t
            }
        val transport = AndroidQuotaTransport(dispatchers)
        val health =
            try {
                when (val h = attempt({ Failure.Unexpected() }) { health(transport, base, token, profileId) }) {
                    is Outcome.Ok -> h.value
                    is Outcome.Failed -> return h
                }
            } finally {
                transport.close(true)
            }
        health.refusal(command)?.let { return Outcome.Ok(TeamCommandResult(false, it, command.projectId)) }
        return attempt({ Failure.Unexpected() }) {
            val answer = JSONObject(post("$base/v1/commands", token, redacted(body).toString()))
            check(
                answer.opt("accepted") is Boolean &&
                    answer.opt("code") is String &&
                    answer.opt("projectId") is String &&
                    answer.opt("revision") is Number &&
                    answer.opt("replayed") is Boolean
            )
            val code = answer.getString("code")
            // Dart `_refusalCode`: a protocol word only, never free text.
            check(code.isEmpty() || CODE.matches(code))
            TeamCommandResult(
                answer.getBoolean("accepted"),
                code,
                answer.getString("projectId"),
                answer.getLong("revision"),
                answer.getBoolean("replayed"),
            )
        }
    }

    override suspend fun readDrafts(profileId: String): String? {
        stores.warm()
        return (stores.prefs.get(DRAFTS + profileId) as? PrefValue.Str)?.value
    }

    /** Dart `saveEditorDraft`: the map is redacted before it is written (no secrets in drafts). */
    override suspend fun writeDrafts(profileId: String, json: String?): Outcome<Unit> {
        stores.warm()
        return stores.prefs.commit(stores.lanes.forProfile(ProfileId(profileId))) {
            if (json == null) remove(DRAFTS + profileId) else putString(DRAFTS + profileId, redactor.redact(json))
        }
    }

    /** Dart `safe`: every string the person typed passes the redactor before it leaves the app. */
    private fun redacted(value: Any?): Any? =
        when (value) {
            is String -> redactor.redact(value)
            is List<*> -> org.json.JSONArray(value.map(::redacted))
            is Map<*, *> -> JSONObject().also { o -> value.forEach { (k, v) -> o.put(k as String, redacted(v) ?: JSONObject.NULL) } }
            null -> JSONObject.NULL
            else -> value
        }

    /** One POST, no redirects, no retry; the body is JSON. */
    private suspend fun post(url: String, token: String, json: String): String =
        kotlinx.coroutines.withContext(dispatchers.io) {
            val client =
                okhttp3.OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .retryOnConnectionFailure(false)
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            val request =
                okhttp3.Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .post(json.toRequestBody(JSON))
                    .build()
            client.newCall(request).execute().use { response ->
                check(response.code == 200)
                val bytes = response.body.bytes()
                check(bytes.size <= HEALTH_BODY)
                bytes.toString(Charsets.UTF_8)
            }
        }

    private suspend fun get(transport: AndroidQuotaTransport, url: String, token: String, limit: Int): String {
        val answer =
            transport.fetch(
                QuotaRequest(
                    "GET",
                    url,
                    persistentMapOf("Accept" to "application/json", "Authorization" to "Bearer $token"),
                    false,
                    0,
                    3_000,
                    3_000,
                    15_000,
                )
            )
        check(answer.status == 200 && !answer.isRedirect)
        val bytes = java.io.ByteArrayOutputStream()
        answer.body.collect { chunk ->
            check(bytes.size() + chunk.size <= limit)
            bytes.write(chunk)
        }
        return bytes.toString("UTF-8")
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val CODE = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")
        const val HEALTH_BODY = 64 * 1024

        /** Flutter `TeamProjectController._draftKey` (registered with the profile sweep as deferred team data). */
        const val DRAFTS = "oc.teamEditorDrafts."

        /** Tasks carry their diffs and messages; Flutter's Dio had no cap, this keeps one answer bounded. */
        const val WORKSPACE_BODY = 16 * 1024 * 1024
    }
}
