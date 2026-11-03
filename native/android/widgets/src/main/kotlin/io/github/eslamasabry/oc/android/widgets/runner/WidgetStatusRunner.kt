package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A scheduled widget status check (port of Flutter `WidgetStatusRunner`, B-ANDR-118/119): one bounded read of the
 * original turn, no command API. The ticket carries opaque identity only, never an endpoint or a credential.
 *
 * Result maps: `{status: idle, preview?}`, `{status: busy}`, `{status: stale}` (the world changed: new turn, new
 * permission, changed run identity, profile scope or metadata edit, stale ticket) or `{status: unavailable}`
 * (cancelled, deadline, unhealthy, failed read). The transport closes on every path, including a timeout.
 * [preview] turns the transcript into the sanitized assistant reply.
 */
public class WidgetStatusRunner(
    private val world: WidgetStatusWorld,
    private val now: () -> Long,
    private val preview: (List<io.github.eslamasabry.oc.core.model.transcript.MessageWithParts>) -> String,
) {
    @Volatile private var cancelled = false
    @Volatile private var transport: WidgetRunTransport? = null

    public suspend fun cancel() {
        cancelled = true
        transport?.close()
    }

    private class Stale : Exception(null, null, false, false)

    private class Unavailable : Exception(null, null, false, false)

    public suspend fun check(ticket: Map<String, Any?>, deadlineEpochMs: Long): Map<String, Any?> {
        try {
            if (TICKET_KEYS.any { (ticket[it] as? String).isNullOrEmpty() }) return STALE
            suspend fun guard() {
                if (cancelled || now() >= deadlineEpochMs) throw Unavailable()
                if (!world.isCurrent(ticket)) throw Stale()
                if (cancelled || now() >= deadlineEpochMs) throw Unavailable()
            }
            suspend fun <T> read(block: suspend () -> T): T {
                guard()
                val remaining = deadlineEpochMs - now()
                if (remaining <= 0) throw Unavailable()
                val value = withTimeoutOrNull(remaining.milliseconds) { block() } ?: throw Unavailable()
                guard()
                return value
            }
            suspend fun <T> readOk(block: suspend () -> Outcome<T>): T =
                when (val outcome = read(block)) {
                    is Outcome.Ok -> outcome.value
                    is Outcome.Failed -> throw Unavailable()
                }

            val profileId = ticket["profileID"] as String
            val fingerprint = ticket["fingerprint"] as String
            val session = ticket["sessionID"] as String
            val profile = read { world.loadProfile(profileId) ?: throw Stale() }
            if (profile.fingerprint != fingerprint) throw Stale()
            val tap =
                WidgetTap(
                    id = ticket["token"] as String,
                    action = "stop",
                    profileId = profileId,
                    scopeToken = ticket["scopeToken"] as String,
                    sessionId = session,
                )
            val target = read { world.loadTarget(tap, fingerprint) ?: throw Stale() }
            if (
                target["profileID"] != profileId ||
                    target["scopeToken"] != ticket["scopeToken"] ||
                    target["fingerprint"] != fingerprint
            )
                throw Stale()
            val gateway =
                when (val opened = read { world.open(profile, target) }) {
                    is WidgetOpen.Refused -> throw Unavailable()
                    is WidgetOpen.Opened -> opened.transport.also { transport = it }
                }
            if (cancelled) throw Unavailable()
            if (!readOk { gateway.health() }) throw Unavailable()
            if (widgetRunMarker(readOk { gateway.messages(session) }) != ticket["runMarker"]) throw Stale()
            val statuses = readOk { gateway.sessionStatuses() }
            if (readOk { gateway.pendingPermissions() }.any { it.sessionId == session }) throw Stale()
            // Recheck the server turn identity after the status read too: a new turn can start while the app stays
            // closed and its snapshot unchanged.
            val finalMessages = readOk { gateway.messages(session) }
            if (widgetRunMarker(finalMessages) != ticket["runMarker"]) throw Stale()
            guard()
            return when (statuses[session]) {
                null, "idle" -> {
                    val reply = preview(finalMessages)
                    if (reply.isEmpty()) mapOf("status" to "idle") else mapOf("status" to "idle", "preview" to reply)
                }
                "busy", "retry" -> mapOf("status" to "busy")
                else -> UNAVAILABLE
            }
        } catch (_: Stale) {
            return STALE
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UNAVAILABLE
        } finally {
            val open = transport
            transport = null
            if (open != null) withContext(NonCancellable) { open.close() }
        }
    }

    private companion object {
        val TICKET_KEYS = listOf("profileID", "scopeToken", "sessionID", "fingerprint", "runMarker", "token")
        val STALE = mapOf<String, Any?>("status" to "stale")
        val UNAVAILABLE = mapOf<String, Any?>("status" to "unavailable")
    }
}
