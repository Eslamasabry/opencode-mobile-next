package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Opening a monitored alert (port of `ConnectionController.prepareMonitoredRequest`, B-ANDR-061): before the app
 * switches to the alert's server it re-checks, on a throwaway gateway, that the route still names this exact source and
 * request. A source edit while that preflight runs refuses the switch; the preflight gateway is always closed.
 */
public class MonitorRequestOpener(
    private val profiles: MonitorProfiles,
    private val createGateway: (MonitorProfile) -> MonitorGateway,
    private val monitorEnabled: (String) -> Boolean,
    private val isReadable: (String) -> Boolean,
    private val clock: Clock,
    private val timeout: Duration = 8.seconds,
) {
    /** True only when the route is still valid after the reads: the caller may then switch the active transport. */
    public suspend fun prepare(route: MonitoredRoute): Boolean {
        val now = clock.now()
        if (!isReadable(route.profileId) || !monitorEnabled(route.profileId) || route.createdAt > now || now - route.createdAt > 1.days) {
            return false
        }
        val target = profiles.profiles().firstOrNull { it.id == route.profileId } ?: return false
        if (!valid(target, route)) return false
        val before = sourceOf(target)
        val gateway = createGateway(target)
        val found =
            try {
                gateway.setLocation(route.directory, route.workspace)
                if (target.backend == MonitorBackend.Paseo) timedOk { gateway.sessionPage(100).getOrNull() } ?: return false
                val listed =
                    when (route.kind) {
                        MonitoredRequestKind.Permission -> pending { listOf(gateway.pendingPermissions(), gateway.pendingPermissionsV2()) }
                        MonitoredRequestKind.Question -> pending { listOf(gateway.listQuestions(), gateway.pendingQuestionsV2()) }
                        MonitoredRequestKind.Form -> if (gateway.formsSupported) pending { listOf(gateway.pendingForms()) } else null
                        // A check-in has no server request: the session read below is the whole check.
                        MonitoredRequestKind.CheckIn -> listOf(MonitorPending(route.requestId, route.sessionId))
                    }
                if (listed == null || listed.none { it.id == route.requestId && it.sessionId == route.sessionId }) {
                    false
                } else if (route.sessionId == "global") {
                    true
                } else {
                    val session = timedOk { gateway.session(route.sessionId).getOrNull() }
                    session != null &&
                        session.id == route.sessionId &&
                        (session.directory == null || session.directory == route.directory) &&
                        session.workspace == route.workspace
                }
            } finally {
                gateway.close()
            }
        if (!found) return false
        // The source may have changed while the preflight read ran: never switch to a different server then.
        val after = profiles.profiles().firstOrNull { it.id == route.profileId } ?: return false
        return isReadable(route.profileId) && sourceOf(after) == before && valid(after, route)
    }

    private fun valid(p: MonitorProfile, route: MonitoredRoute): Boolean =
        p.baseUrl == route.serverUrl && route.sourceIdentity == ProfileMonitor.routeSourceIdentity(p) && p.usable && p.backend != MonitorBackend.Codex

    private fun sourceOf(p: MonitorProfile): List<Any?> = listOf(p.baseUrl, p.username, p.credential, p.flavor)

    private suspend fun <T> timedOk(block: suspend () -> T?): T? =
        attempt({ Failure.Unexpected() }) { withTimeoutOrNull(timeout) { block() } }.getOrNull()

    private suspend fun pending(reads: suspend () -> List<io.github.eslamasabry.oc.core.common.Outcome<List<MonitorPending>>>): List<MonitorPending>? =
        timedOk {
            val all = reads().mapNotNull { it.getOrNull() }
            if (all.isEmpty()) null else all.flatten()
        }
}

/**
 * What a monitored alert hands the notification layer (Flutter `showCodingAlert` arguments for a monitor alert). It
 * carries the profile id and the opaque token only: no title, password, folder or prompt, and never reply actions.
 */
public data class MonitorAlertPayload(
    val kind: MonitoredRequestKind,
    val sessionId: String,
    val key: String,
    val profileId: String,
    val monitorToken: String,
) {
    /** Monitored alerts never offer Allow/Deny/Reply on the shade; they open the app. */
    val allowActions: Boolean get() = false

    /** The wire map, keys exactly as Android reads them. */
    public fun toWire(): Map<String, Any> =
        mapOf(
            "kind" to wireKind(kind),
            "sessionID" to sessionId,
            "key" to key,
            "profileID" to profileId,
            "monitorToken" to monitorToken,
            "allowActions" to false,
        )

    public companion object {
        public fun wireKind(kind: MonitoredRequestKind): String =
            when (kind) {
                MonitoredRequestKind.Permission -> "permission"
                MonitoredRequestKind.Question -> "question"
                MonitoredRequestKind.Form -> "question"
                MonitoredRequestKind.CheckIn -> "checkin"
            }

        public fun of(profileId: String, request: MonitoredRequest, key: String, token: String): MonitorAlertPayload =
            MonitorAlertPayload(request.kind, request.sessionId, key, profileId, token)
    }
}

/** A tap on a monitored alert, read back from the platform: the route is resolved through [ProfileMonitor.routeForToken]. */
public data class MonitorAlertOpen(val profileId: String, val monitorToken: String, val sessionId: String) {
    public companion object {
        public fun fromWire(value: Map<String, Any?>): MonitorAlertOpen? {
            val kind = value["kind"]?.toString()
            val session = value["sessionID"]?.toString()?.trim().orEmpty()
            if (kind !in setOf("permission", "question", "checkin") || session.isEmpty()) return null
            return MonitorAlertOpen(
                profileId = value["profileID"]?.toString()?.trim().orEmpty(),
                monitorToken = value["monitorToken"]?.toString()?.trim().orEmpty(),
                sessionId = session,
            )
        }
    }
}
