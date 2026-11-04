package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.widgets.WidgetPresentation
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** Real phone-engine schema-1 workspace and reviewed commands, never a simulated Team feed. */
@SingleIn(AppScope::class)
@Inject
class NativeWidgetTeam(
    private val application: android.app.Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("widget-team"))
    private val presentation = WidgetPresentation(application, redactor)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            while (true) {
                delay(30_000)
                val profile = stores.profiles.activeId.value ?: continue
                if (
                    registry.live.value[profile]?.session?.server?.events?.status?.value !=
                        StreamStatus.Connected
                )
                    continue
                val before = stores.prefs.string("oc.widgetSessions") ?: continue
                val snapshot = parse(before) ?: continue
                if (snapshot.optString("profileID") != profile.raw) continue
                augment(snapshot, profile)
                if (stores.profiles.activeId.value != profile) continue
                val saved =
                    stores.prefs.compareAndSet(
                        stores.lanes.forProfile(profile),
                        "oc.widgetSessions",
                        before,
                        snapshot.toString(),
                    )
                if ((saved as? Outcome.Ok)?.value == true)
                    SessionsWidgetProvider.refreshAll(application)
            }
        }
    }

    /** Called by the ordinary widget publisher before its private-target/public-snapshot commit. */
    suspend fun augment(snapshot: JSONObject, profileId: ProfileId) {
        val rows = snapshot.optJSONArray("sessions") ?: return
        val ordinary =
            (0 until rows.length()).mapNotNull(rows::optJSONObject).filter { !it.has("team") }
        snapshot.put("sessions", JSONArray(ordinary))
        snapshot.put("runningCount", ordinary.count { it.optBoolean("busy") })
        snapshot.put(
            "pendingCount",
            ordinary.count {
                it.optBoolean("needsYou") || it.optString("requestID").isNotEmpty()
            },
        )
        val saved = stores.profiles.profile(profileId) ?: return
        val graph = registry.live.value[profileId] ?: return
        val location = graph.session.server.location.value
        val fingerprint = widgetFingerprint(saved)
        val capturedScope = snapshot.optString("scopeToken")
        suspend fun current(): Boolean =
            stores.lanes.forProfile(profileId).isAdmitting &&
                stores.profiles.activeId.value == profileId &&
                stores.profiles.profile(profileId) == saved &&
                registry.live.value[profileId] === graph &&
                graph.session.server.location.value == location &&
                graph.session.server.events.status.value == StreamStatus.Connected &&
                snapshot.optJSONObject("onDemand")?.optString("fingerprint") == fingerprint &&
                capturedScope.isNotBlank()
        if (!current()) return
        try {
            withTimeoutOrNull(4_000) {
                val session = open(saved) ?: return@withTimeoutOrNull
                try {
                    if (!current()) return@withTimeoutOrNull
                    val health = session.request("/v1/health")
                    validateHealth(health, profileId)
                    if (!current() || !session.credentialCurrent()) return@withTimeoutOrNull
                    val workspace = session.request("/v1/workspace")
                    val projects = projects(workspace)
                    if (!current() || !session.credentialCurrent()) return@withTimeoutOrNull
                    val ordered = projects.sortedBy { project ->
                        if (action(project, health, session.front) != null) 0
                        else if (project.optString("status") == "running") 1 else 2
                    }
                    val project = ordered.firstOrNull() ?: return@withTimeoutOrNull
                    val id = project.getString("id")
                    val revision = project.getLong("revision")
                    val offered = action(project, health, session.front)
                    val tasks = project.getJSONArray("tasks")
                    val team =
                        JSONObject()
                            .put("runID", id)
                            .put("revision", revision)
                            .put("gateID", widgetDigest("$capturedScope|$id|$revision"))
                            .put(
                                "done",
                                (0 until tasks.length()).count {
                                    tasks.optJSONObject(it)?.optString("status") in
                                        setOf("done", "merged", "accepted")
                                },
                            )
                            .put("total", tasks.length())
                    if (offered != null) team.put("action", offered)
                    val row =
                        JSONObject()
                            .put("id", "team:$id")
                            .put("profileID", profileId.raw)
                            .put("title", presentation.title(project.optString("name")))
                            .put("busy", project.optString("status") == "running")
                            .put("needsYou", offered != null)
                            .put("updatedAt", timestamp(project.optString("updatedAt")))
                            .put("steerMode", "unknown")
                            .put("todos", JSONArray())
                            .put("team", team)
                            .put("onDemandActions", JSONArray(listOfNotNull(offered)))
                    val output = if (offered != null) listOf(row) + ordinary else ordinary + row
                    snapshot.put("sessions", JSONArray(output))
                    snapshot.put("runningCount", output.count { it.optBoolean("busy") })
                    snapshot.put(
                        "pendingCount",
                        output.count {
                            it.optBoolean("needsYou") || it.optString("requestID").isNotEmpty()
                        },
                    )
                } finally {
                    session.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            /* Unavailable workspace never becomes synthetic project truth. */
        }
    }

    /**
     * The call boundary flag is set only immediately before the one POST; no command is retried.
     */
    suspend fun execute(
        payload: Map<String, Any?>,
        row: JSONObject,
        guard: suspend () -> Unit,
        dispatched: () -> Unit,
    ): String {
        val profile = ProfileId(payload["profileID"] as? String ?: return "rejected")
        val saved = stores.profiles.profile(profile) ?: return "rejected"
        val session = open(saved) ?: return "rejected"
        try {
            guard()
            val health = session.request("/v1/health")
            validateHealth(health, profile)
            guard()
            val workspace = session.request("/v1/workspace")
            val run = payload["runID"] as? String ?: return "rejected"
            val project =
                projects(workspace).singleOrNull { it.optString("id") == run } ?: return "rejected"
            val old = row.optJSONObject("team") ?: return "rejected"
            val offered = action(project, health, session.front) ?: return "rejected"
            val revision = project.getLong("revision")
            val gate = widgetDigest("${payload["scopeToken"]}|$run|$revision")
            if (
                old.optLong("revision", -1) != revision ||
                    old.optString("runID") != run ||
                    old.optString("gateID") != payload["gateID"] ||
                    gate != payload["gateID"] ||
                    offered != payload["action"] ||
                    old.optString("action") != offered
            )
                return "rejected"
            guard()
            if (!session.credentialCurrent()) return "rejected"
            val command =
                JSONObject()
                    .put("requestId", payload["id"])
                    .put("projectId", run)
                    .put("expectedRevision", revision)
                    .put("action", if (offered == "resume") "resumeProject" else "approvePlan")
            val response =
                session.request("/v1/commands", command) {
                    guard()
                    dispatched()
                }
            if (
                response.opt("accepted") !is Boolean ||
                    response.opt("code") !is String ||
                    !response.integer("revision") ||
                    response.opt("replayed") !is Boolean ||
                    response.optString("projectId") != run
            )
                throw TeamUnavailable()
            if (!response.getBoolean("accepted")) {
                if (
                    response.getString("code") in
                        setOf(
                            "staleRevision",
                            "revisionConflict",
                            "unsupportedCommand",
                            "boundaryUnverified",
                            "protocolUnverified",
                            "invalidState",
                        )
                )
                    return "rejected"
                throw TeamUnavailable()
            }
            return "accepted"
        } finally {
            session.close()
        }
    }

    private suspend fun open(saved: LoadedProfile): Session? {
        val config = saved.profile.orchestration?.encoded?.let(::parse) ?: return null
        if (config.optString("provider") != "phoneEngine" || saved.profile.transientTransport)
            return null
        val uri =
            try {
                URI(config.optString("url"))
            } catch (_: Exception) {
                return null
            }
        if (
            uri.scheme != "http" ||
                uri.host !in setOf("127.0.0.1", "::1", "[::1]") ||
                uri.rawUserInfo != null ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.rawPath !in setOf("", "/")
        )
            return null
        val token =
            (stores.secrets.read(ProfileKeys.TEAM_ENGINE_AUTH + saved.id.raw) as? Outcome.Ok)?.value
                ?: return null
        if (token.isBlank() || '\r' in token || '\n' in token) return null
        redactor.registerSecret(token)
        return Session(saved, uri.toString().trimEnd('/'), token, config.optBoolean("front"))
    }

    private fun validateHealth(health: JSONObject, profile: ProfileId) {
        val capabilities = health.optJSONObject("capabilities") ?: throw TeamUnavailable()
        if (
            health.opt("schemaVersion") != 1 ||
                health.optString("profileId") != profile.raw ||
                health.optString("engineVersion").isBlank() ||
                health.optJSONArray("commandActions") == null ||
                (0 until (health.optJSONArray("commandActions")?.length() ?: 0)).any {
                    health.optJSONArray("commandActions")?.opt(it) !is String
                } ||
                listOf("execution", "boundary", "oc2").any { capabilities.opt(it) !is Boolean }
        )
            throw TeamUnavailable()
    }

    private fun projects(workspace: JSONObject): List<JSONObject> {
        if (
            workspace.opt("schemaVersion") != 1 ||
                !workspace.integer("revision") ||
                workspace.opt("simulated") != false ||
                listOf("projects", "servers", "roles").any { workspace.optJSONArray(it) == null }
        )
            throw TeamUnavailable()
        val projects = workspace.getJSONArray("projects")
        if (projects.length() > 256) throw TeamUnavailable()
        return (0 until projects.length()).map { index ->
            val project = projects.getJSONObject(index)
            if (
                project.opt("id") !is String ||
                    project.optString("id").isBlank() ||
                    !project.integer("revision") ||
                    project.opt("simulated") != false ||
                    project.optJSONArray("tasks") == null ||
                    project.optJSONArray("specVersions") == null ||
                    project.opt("planApproved") !is Boolean
            )
                throw TeamUnavailable()
            project
        }
    }

    private fun action(project: JSONObject, health: JSONObject, front: Boolean): String? {
        val capabilities = health.getJSONObject("capabilities")
        if (
            !front ||
                listOf("execution", "boundary", "oc2", "oc2Verified").any {
                    capabilities.opt(it) != true
                }
        )
            return null
        val actions = health.getJSONArray("commandActions")
        val implemented = (0 until actions.length()).map(actions::optString)
        return when {
            !project.getBoolean("planApproved") &&
                project.optString("status") in setOf("plan", "planned") &&
                project.getJSONArray("tasks").length() > 0 &&
                project.getJSONArray("specVersions").length() > 0 &&
                "approvePlan" in implemented -> "approvePlan"
            project.getBoolean("planApproved") &&
                project.optString("status") in setOf("paused", "interrupted") &&
                "resumeProject" in implemented -> "resume"
            else -> null
        }
    }

    private inner class Session(
        val saved: LoadedProfile,
        val origin: String,
        val token: String,
        val front: Boolean,
    ) {
        val client =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .build()

        suspend fun credentialCurrent(): Boolean =
            stores.profiles.profile(saved.id) == saved &&
                stores.lanes.forProfile(saved.id).isAdmitting &&
                (stores.secrets.read(ProfileKeys.TEAM_ENGINE_AUTH + saved.id.raw) as? Outcome.Ok)
                    ?.value == token

        suspend fun request(
            path: String,
            body: JSONObject? = null,
            beforeCall: (suspend () -> Unit)? = null,
        ): JSONObject {
            currentCoroutineContext().ensureActive()
            if (!credentialCurrent()) throw TeamUnavailable()
            beforeCall?.invoke()
            currentCoroutineContext().ensureActive()
            val request =
                Request.Builder()
                    .url(origin + path)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .apply {
                        if (body == null) get()
                        else post(body.toString().toRequestBody("application/json".toMediaType()))
                    }
                    .build()
            val call = client.newCall(request)
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: java.io.IOException) {
                            if (continuation.isActive)
                                continuation.resumeWith(Result.failure(TeamUnavailable()))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val parsed = response.use {
                                    if (it.isRedirect) throw TeamUnavailable()
                                    val bytes =
                                        it.body.byteStream().use { stream ->
                                            val output = java.io.ByteArrayOutputStream()
                                            val buffer = ByteArray(8192)
                                            while (true) {
                                                val count = stream.read(buffer)
                                                if (count < 0) break
                                                if (output.size() + count > 512_000)
                                                    throw TeamUnavailable()
                                                output.write(buffer, 0, count)
                                            }
                                            output.toByteArray()
                                        }
                                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                                    if (
                                        it.code !in 200..299 &&
                                            !(body != null && json.opt("accepted") == false)
                                    )
                                        throw TeamUnavailable()
                                    json
                                }
                                if (continuation.isActive)
                                    continuation.resumeWith(Result.success(parsed))
                            } catch (_: Exception) {
                                if (continuation.isActive)
                                    continuation.resumeWith(Result.failure(TeamUnavailable()))
                            }
                        }
                    }
                )
            }
        }

        fun close() {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun JSONObject.integer(key: String): Boolean = opt(key) is Int || opt(key) is Long

    private class TeamUnavailable : Exception()

    private fun parse(raw: String): JSONObject? =
        try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }

    private fun timestamp(raw: String): Long =
        try {
            Instant.parse(raw).toEpochMilliseconds()
        } catch (_: Exception) {
            0L
        }
}
