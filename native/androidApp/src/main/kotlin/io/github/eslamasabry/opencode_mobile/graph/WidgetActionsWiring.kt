package io.github.eslamasabry.opencode_mobile.graph

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.widgets.WidgetBackgroundRunner
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import java.security.MessageDigest
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A single tap owns one bounded transport; no connection settings, reconnect or mutation retry. */
@SingleIn(AppScope::class)
@Inject
class NativeWidgetRunner(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) : WidgetBackgroundRunner {
    override fun start(
        context: Context,
        payload: Map<String, Any?>,
        snapshotJson: String,
        deadlineEpochMs: Long,
        operation: String,
        complete: (Map<*, *>?) -> Unit,
    ): WidgetBackgroundRunner.Handle {
        val job =
            appScope.launch(dispatchers.io) {
                complete(execute(payload, snapshotJson, deadlineEpochMs, operation))
            }
        return WidgetBackgroundRunner.Handle { job.cancel() }
    }

    private suspend fun execute(
        payload: Map<String, Any?>,
        snapshotJson: String,
        deadline: Long,
        operation: String,
    ): Map<String, Any?> {
        var dispatched = false
        var owned: ServerGateway? = null
        var transportScope: ConnectionCoroutineScope? = null
        fun result(status: String, error: String? = null): Map<String, Any?> = buildMap {
            put("id", payload["id"])
            put("action", payload["action"])
            put("status", status)
            if (error != null) put("error", error)
        }
        try {
            if (stores.warm() is Outcome.Failed) return result("rejected", "unavailable")
            val snapshot = JSONObject(snapshotJson)
            val id =
                ProfileId(
                    payload["profileID"] as? String ?: return result("rejected", "invalidRequest")
                )
            val scope =
                payload["scopeToken"] as? String ?: return result("rejected", "invalidRequest")
            val session =
                SessionId(
                    payload["sessionID"] as? String ?: return result("rejected", "invalidRequest")
                )
            val fingerprint = snapshot.optJSONObject("onDemand")?.optString("fingerprint").orEmpty()
            val rawTarget =
                stores.prefs.string("oc.widgetTarget.${id.raw}")
                    ?: return result("rejected", "staleRequest")
            val target = JSONObject(rawTarget)
            val loaded = stores.profiles.profile(id) ?: return result("rejected", "staleRequest")
            val profile = loaded.toServerProfile() ?: return result("rejected", "staleRequest")
            val rows = snapshot.optJSONArray("sessions") ?: JSONArray()
            val row =
                (0 until rows.length()).mapNotNull(rows::optJSONObject).firstOrNull {
                    it.optString("id") == session.raw
                } ?: return result("rejected", "staleRequest")
            val action = payload["action"] as? String ?: return result("rejected", "invalidRequest")
            val coroutine = currentCoroutineContext()
            fun current(): Boolean {
                coroutine.ensureActive()
                if (
                    clock.now().toEpochMilliseconds() >= deadline ||
                        !stores.lanes.forProfile(id).isAdmitting
                )
                    return false
                if (stores.profiles.activeId.value != id || stores.profiles.profile(id) != loaded)
                    return false
                if (
                    snapshot.optString("profileID") != id.raw ||
                        snapshot.optString("scopeToken") != scope ||
                        scope.isBlank()
                )
                    return false
                if (fingerprint.isBlank() || fingerprint != widgetFingerprint(loaded)) return false
                if (stores.prefs.string("oc.widgetTarget.${id.raw}") != rawTarget) return false
                if (
                    target.optString("profileID") != id.raw ||
                        target.optString("scopeToken") != scope ||
                        target.optString("fingerprint") != fingerprint
                )
                    return false
                val liveSnapshot =
                    stores.prefs.string("oc.widgetSessions")?.let(::JSONObject) ?: return false
                if (
                    liveSnapshot.optString("profileID") != id.raw ||
                        liveSnapshot.optString("scopeToken") != scope
                )
                    return false
                val liveRows = liveSnapshot.optJSONArray("sessions") ?: return false
                val liveRow =
                    (0 until liveRows.length()).mapNotNull(liveRows::optJSONObject).firstOrNull {
                        it.optString("id") == session.raw
                    } ?: return false
                return liveRow.optString("requestID") == row.optString("requestID") &&
                    liveRow.optString("runMarker") == row.optString("runMarker")
            }
            if (!current()) return result("rejected", "staleRequest")
            val actions = row.optJSONArray("onDemandActions") ?: JSONArray()
            if (action !in (0 until actions.length()).map(actions::optString))
                return result("rejected", "unsupported")
            val location =
                ServerLocation(
                    target.optString("directory").ifEmpty { null },
                    target.optString("workspace").ifEmpty { null },
                )
            val live = registry.live.value[id]?.session?.server
            val gateway =
                if (live != null && live.location.value == location) live
                else {
                    // Socket permission identities belong to their original connection and cannot
                    // be reconstructed.
                    if (
                        loaded.profile.usesAgentSocket ||
                            loaded.cleartextUnconfirmed ||
                            loaded.profile.transientTransport ||
                            loaded.profile.requiresOpenCode2Migration ||
                            loaded.reentry.password ||
                            loaded.reentry.agentToken
                    )
                        return result("rejected", "unsupported")
                    val credential =
                        credentials.singleOrNull()?.resolve(profile) as? Outcome.Ok
                            ?: return result("rejected", "unavailable")
                    if (!current()) return result("rejected", "staleRequest")
                    val factory =
                        factories.singleOrNull { it.backend == profile.backend }
                            ?: return result("rejected", "unsupported")
                    transportScope =
                        ConnectionCoroutineScope(
                            supervisedContext(
                                currentCoroutineContext()[Job],
                                dispatchers.io,
                                "widget-action",
                            )
                        )
                    val pair =
                        factory.connect(
                            GatewaySpec(
                                profile,
                                credential.value,
                                transportScope!!,
                                location,
                                reconnectAllowed = MutableStateFlow(false),
                            )
                        )
                    owned =
                        (pair as? Outcome.Ok)?.value?.live
                            ?: return result("rejected", "unavailable")
                    owned!!
                }
            val remaining = (deadline - clock.now().toEpochMilliseconds()).coerceAtMost(30_000)
            if (remaining <= 0) return result("rejected", "unavailable")
            return withTimeoutOrNull(remaining) {
                suspend fun accepted(): Map<String, Any?> {
                    // One bounded read after acknowledgement. Failure preserves busy as unknown,
                    // never as idle.
                    val status =
                        withTimeoutOrNull(2_000) { gateway.sessionStatuses() } as? Outcome.Ok
                    if (!current()) return result("accepted")
                    val busy =
                        when (status?.value?.get(session)?.raw) {
                            "idle" -> false
                            "busy",
                            "retry" -> true
                            else -> null
                        }
                    val patch =
                        buildMap<String, Any?> {
                            put("id", session.raw)
                            put(
                                "requestID",
                                if (action in setOf("allowOnce", "reject")) ""
                                else row.optString("requestID"),
                            )
                            busy?.let { put("busy", it) }
                            put("onDemandActions", emptyList<String>())
                        }
                    return result("accepted") +
                        ("snapshot" to
                            mapOf(
                                "version" to 2,
                                "profileID" to id.raw,
                                "scopeToken" to scope,
                                "sessions" to listOf(patch),
                            ))
                }
                val health = gateway.health() as? Outcome.Ok
                if (health?.value?.healthy != true || !current())
                    return@withTimeoutOrNull result("rejected", "staleRequest")
                if (operation == "checkStatus") return@withTimeoutOrNull result("accepted")
                if (action !in setOf("allowOnce", "reject", "stop"))
                    return@withTimeoutOrNull result("rejected", "unsupported")
                if (action == "stop") {
                    val marker = row.optString("runMarker")
                    if (
                        marker.isBlank() ||
                            row.optLong("startedAt") !=
                                (payload["expectedStartedAt"] as? Number)?.toLong()
                    )
                        return@withTimeoutOrNull result("rejected", "staleRequest")
                    val statuses =
                        (gateway.sessionStatuses() as? Outcome.Ok)?.value
                            ?: return@withTimeoutOrNull result("rejected", "unavailable")
                    if (statuses[session]?.raw !in setOf("busy", "retry"))
                        return@withTimeoutOrNull result("rejected", "staleRequest")
                    val messages =
                        (gateway.messagePage(session, limit = 6) as? Outcome.Ok)?.value?.items
                            ?: return@withTimeoutOrNull result("rejected", "unavailable")
                    if (
                        widgetRunMessage(messages)?.info?.id != marker ||
                            !current() ||
                            gateway.location.value != location
                    )
                        return@withTimeoutOrNull result("rejected", "staleRequest")
                    dispatched = true
                    val stopped = gateway.stop(session)
                    return@withTimeoutOrNull if (
                        stopped is Outcome.Ok &&
                            stopped.value !=
                                io.github.eslamasabry.oc.core.model.StopResult.Uncertain
                    )
                        accepted()
                    else result("uncertain", "uncertainDelivery")
                }
                if (!gateway.capabilities.value.permissionRequests)
                    return@withTimeoutOrNull result("rejected", "unsupported")
                val requestId = payload["requestID"] as? String
                if (requestId.isNullOrBlank() || requestId != row.optString("requestID"))
                    return@withTimeoutOrNull result("rejected", "staleRequest")
                val pending =
                    (gateway.pendingPermissions() as? Outcome.Ok)?.value
                        ?: return@withTimeoutOrNull result("rejected", "unavailable")
                val exact =
                    pending.singleOrNull { it.id.raw == requestId && it.sessionId == session }
                        ?: return@withTimeoutOrNull result("rejected", "staleRequest")
                if (!current() || gateway.location.value != location)
                    return@withTimeoutOrNull result("rejected", "staleRequest")
                dispatched = true
                val reply =
                    gateway.replyPermission(
                        session,
                        exact.id,
                        if (action == "allowOnce") PermissionReply.Once else PermissionReply.Reject,
                    )
                if (reply is Outcome.Ok) accepted() else result("uncertain", "uncertainDelivery")
            }
                ?: result(
                    if (dispatched) "uncertain" else "rejected",
                    if (dispatched) "uncertainDelivery" else "unavailable",
                )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return result(
                if (dispatched) "uncertain" else "rejected",
                if (dispatched) "uncertainDelivery" else "unavailable",
            )
        } finally {
            withContext(NonCancellable) {
                try {
                    try {
                        withTimeoutOrNull(1_000) { owned?.close() }
                    } catch (_: RuntimeException) {
                        /* Close cannot turn an acknowledged action into a retry. */
                    }
                } finally {
                    transportScope?.coroutineContext?.get(Job)?.cancel()
                }
            }
        }
    }
}

/** Only metadata is hashed; a public snapshot never carries its address or project routing. */
internal fun widgetFingerprint(loaded: LoadedProfile): String =
    widgetDigest(
        StoredProfileJson.encodeRows(
            listOf(StoredProfileJson.encode(loaded.profile).apply { remove("name") })
        ) + loaded.trust.toString()
    )

internal fun widgetDigest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
        ""
    ) {
        "%02x".format(it)
    }

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class NativeWidgetCleaner(
    application: android.app.Application,
    persistence: io.github.eslamasabry.oc.android.widgets.WidgetPersistence,
) : ProfileResourceCleaner {
    private val surface =
        io.github.eslamasabry.oc.android.widgets.AndroidWidgetSurface(application, persistence)
    override val owner: String = ":android:widgets"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (surface.clearProfile(profileId.raw)) {
            io.github.eslamasabry.oc.android.widgets.ClearOutcome.Failed ->
                Outcome.Failed(Failure.StorageRefused())
            io.github.eslamasabry.oc.android.widgets.ClearOutcome.NothingToClear -> Outcome.Ok(0)
            io.github.eslamasabry.oc.android.widgets.ClearOutcome.Cleared -> Outcome.Ok(1)
        }
}

/** Durable user-message identity, excluding OC2 timeline notices. Never returns message content. */
internal fun widgetRunMessage(
    messages: List<io.github.eslamasabry.oc.core.model.transcript.MessageWithParts>
): io.github.eslamasabry.oc.core.model.transcript.MessageWithParts? =
    messages
        .filter {
            it.info.role == io.github.eslamasabry.oc.core.model.transcript.Role.User &&
                it.parts.none { part -> part.rawType.startsWith("v2:") }
        }
        .maxByOrNull { it.info.time?.createdMs ?: 0L }
