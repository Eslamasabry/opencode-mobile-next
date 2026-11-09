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
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.engine.team.PhoneEngineAddress
import io.github.eslamasabry.oc.engine.team.PhoneSetupPorts
import io.github.eslamasabry.oc.engine.team.SetupHealth
import io.github.eslamasabry.oc.engine.team.SetupHost
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.profiles.OpaqueJson
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.LocalTerminal
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHealth
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.team.TeamLastKnown
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
    fun engine(host: AppTeamHost, kept: Set<TeamLastKnown>, clock: kotlin.time.Clock): TeamEngine =
        TeamEngine(host, kept = kept.firstOrNull(), clock = clock)
}

/**
 * The phone engine as Flutter's `PhoneEngineGateway` reads it: the saved `orchestration` config, the Keystore bearer
 * (`oc.teamEngineAuth.<id>`), `GET /v1/health` for this profile, then `GET /v1/workspace`. Each read owns its client
 * and closes it; closing never stops the daemon. The token is registered with the redactor and never logged.
 */
@SingleIn(AppScope::class)
@Inject
class AppTeamHost(
    private val app: android.app.Application,
    private val runtime: BuiltinRuntime,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : TeamHost {
    override suspend fun config(profileId: String): TeamConfig? {
        stores.warm()
        return TeamConfig.parse(stores.profiles.profile(ProfileId(profileId))?.profile?.orchestration?.encoded)
    }

    private val linux by lazy { BuiltinLinux.get(app) }

    /** Flutter `openPhoneTeamSetup` can run here: this is the phone's own OpenCode and the engine is bundled. */
    override suspend fun canTurnOnPhoneTeam(profileId: String): Boolean {
        stores.warm()
        val profile = stores.profiles.profile(ProfileId(profileId))?.profile ?: return false
        return BuiltinSetupFinisher.managesServerUrl(profile.baseUrl) &&
            kotlinx.coroutines.withContext(dispatchers.io) { linux.installed }
    }

    /** Flutter's one-tap setup, see [PhoneTeamSetup]; the engine's port is Flutter's default 4098. */
    override fun phoneSetup(profileId: String): PhoneSetupPorts = SetupPorts(profileId)

    /** Flutter `turnOffTeam` for the phone's team: the engine stops (a failure leaves the team on), then the config goes. */
    override suspend fun turnOffPhoneTeam(profileId: String): Outcome<Unit> {
        stores.warm()
        val loaded = stores.profiles.profile(ProfileId(profileId)) ?: return Outcome.Failed(Failure.Unexpected())
        val stopped =
            attempt({ Failure.Unexpected() }) {
                kotlinx.coroutines.withContext(dispatchers.io) { linux.stopPhoneEngine(profileId) }
            }
        if (stopped is Outcome.Failed) return stopped
        return when (val saved = stores.profiles.upsert(loaded.profile.copy(orchestration = null), SecretEdit.Keep, expected = loaded)) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> saved
        }
    }

    /** The phone as the set-up flow asks of it (Dart `BuiltinPhoneTeamSetupPorts`): the same controls This phone uses. */
    private inner class SetupPorts(private val profileId: String) : PhoneSetupPorts {
        /** The engine's address and token from the last start, for the probe and the save; never logged. */
        private var started: PhoneEngineAddress? = null

        override suspend fun hasServer(): Boolean = canTurnOnPhoneTeam(profileId)

        override suspend fun replyRunning(): Boolean =
            kotlinx.coroutines.withContext(dispatchers.io) { runtime.replyRunning() }

        override suspend fun inspect(): SetupHost {
            val serverRunning = (runtime.runtimeStatus() as? Outcome.Ok)?.value?.serverRunning == true
            val (terminals, engine) =
                kotlinx.coroutines.withContext(dispatchers.io) {
                    // Unknown reads count as nothing running; the start reports for itself.
                    val shells = try { LocalTerminal.get(app).list().count { it.running } } catch (_: Exception) { 0 }
                    val restart = try { linux.phoneEngineStatus(profileId)["restartRequired"] == true } catch (_: Exception) { false }
                    shells to restart
                }
            return SetupHost(serverRunning, terminals, engine)
        }

        override suspend fun stopServer(): Outcome<Unit> = runtime.stopServer()

        override suspend fun closeTerminals(): Outcome<Unit> =
            attempt({ Failure.Unexpected() }) {
                kotlinx.coroutines.withContext(dispatchers.io) {
                    val terminal = LocalTerminal.get(app)
                    terminal.list().forEach { runCatching { it.stop() }; terminal.remove(it.id) }
                }
            }

        override suspend fun startEngine(): Outcome<SetupHealth> =
            attempt({ error ->
                Failure.Unavailable(FeatureId("team.engine"), redactor.details(linux.phoneEngineFailureCode(error) ?: "engineFailed"))
            }) {
                kotlinx.coroutines.withContext(dispatchers.io) {
                    try {
                        val status = linux.startPhoneEngine(profileId, 4098, null)
                        val credentials = linux.phoneEngineCredentials(profileId)
                        val address = PhoneEngineAddress(checkNotNull(credentials["baseUrl"]), checkNotNull(credentials["bearerToken"]))
                        check(TeamConfig.loopback(address.baseUrl) != null)
                        redactor.registerSecret(address.token)
                        started = address
                        val boundary = status["boundary"] == true
                        SetupHealth(
                            boundary = boundary,
                            canExecute = boundary && status["execution"] == true,
                            restartRequired = status["restartRequired"] == true,
                            boundaryReason = status["boundaryReason"] as? String ?: "",
                            boundaryTier = status["boundaryTier"] as? String ?: "none",
                        )
                    } catch (error: Exception) {
                        // A proof that failed is the phone not being safe, not the engine not starting.
                        val code = linux.phoneEngineFailureCode(error)
                        if (code != null && (code.startsWith("boundary_") || code.startsWith("attestation_"))) {
                            SetupHealth(boundary = false, canExecute = false, boundaryReason = code)
                        } else {
                            throw error
                        }
                    }
                }
            }

        override suspend fun stopEngine() {
            attempt({ Failure.Unexpected() }) { kotlinx.coroutines.withContext(dispatchers.io) { linux.stopPhoneEngine(profileId) } }
            started = null
        }

        override suspend fun probeEngine(): Outcome<SetupHealth> {
            val address = started ?: return Outcome.Failed(Failure.Unexpected())
            val transport = AndroidQuotaTransport(dispatchers)
            return try {
                attempt({ Failure.Unexpected() }) {
                    val health = health(transport, address.baseUrl.trimEnd('/'), address.token, profileId)
                    SetupHealth(
                        boundary = health.boundary,
                        canExecute = health.canExecute,
                        boundaryReason =
                            when {
                                !health.boundary -> "boundary_unverified"
                                !health.protocolVerified -> "protocolUnverified"
                                !health.canExecute -> "notExecutable"
                                else -> ""
                            },
                        boundaryTier = health.boundaryTier,
                    )
                }
            } finally {
                transport.close(true)
            }
        }

        override suspend fun startServer(): Outcome<Unit> = runtime.startServer()

        override suspend fun restoreServer(): Boolean = runtime.startServer() is Outcome.Ok

        override suspend fun saveTeam(): Outcome<Unit> {
            val address = started ?: return Outcome.Failed(Failure.Unexpected())
            redactor.registerSecret(address.token)
            val loaded = stores.profiles.profile(ProfileId(profileId)) ?: return Outcome.Failed(Failure.Unexpected())
            val written = stores.secrets.write(ProfileKeys.TEAM_ENGINE_AUTH + profileId, address.token)
            if (written is Outcome.Failed) return written
            val previous = TeamConfig.parse(loaded.profile.orchestration?.encoded)?.enabledAt
            val enabledAt = previous ?: java.time.Instant.now().toString()
            val config =
                OpaqueJson.parse(
                    DartJson.encode(
                        mapOf(
                            "provider" to "phoneEngine", "url" to address.baseUrl, "city" to "", "hostMode" to "phone",
                            "hostKind" to "phone", "front" to false, "enabledAt" to enabledAt,
                        ),
                    ),
                ) ?: return Outcome.Failed(Failure.Unexpected())
            return when (val saved = stores.profiles.upsert(loaded.profile.copy(orchestration = config), SecretEdit.Keep, expected = loaded)) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Failed -> saved
            }
        }
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
