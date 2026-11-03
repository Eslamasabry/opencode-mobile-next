package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One explicit launcher tap with no live connection, including bounded status reconciliation (port of Flutter
 * `WidgetActionRunner`). No controller, runtime start, reconnect retry or background preference write.
 *
 * Rules (B-ANDR-104..107, B-SEC-180, B-SEC-183):
 * - the same tap id is dispatched once; a second call gets the first result;
 * - a stale snapshot, profile, target or scope never builds a transport; an unsupported row action is rejected
 *   before connecting; an expired tap never connects;
 * - health comes first; nothing is read or answered on an unhealthy, cancelled, failed or deadline-hit transport;
 * - allow once / reject answer only the exact pending request of the tapped conversation;
 * - stop checks the server user-message identity and busy status, so an old run never stops a replacement and an
 *   idle run is never stopped;
 * - once a mutation is sent, every failure is `uncertain/uncertainDelivery` (never a retryable rejection, never
 *   replayed); before it, failures are certain rejections with a bounded token, never server words;
 * - after the acknowledgement the server status is read (not replayed) until idle or the settle budget ends, and the
 *   transport closes only after that read.
 *
 * [now] is the same epoch-millisecond clock that set the deadline. OpenCode 2 only: Team actions are unsupported.
 */
public class WidgetActionRunner(
    private val world: WidgetActionWorld,
    private val now: () -> Long,
    private val statusSettleTimeout: Duration = 5.seconds,
    private val statusPollInterval: Duration = 250.milliseconds,
) {
    private val lock = Any()
    private val requests = mutableMapOf<String, CompletableDeferred<WidgetRunResult>>()
    @Volatile private var cancelled = false
    @Volatile private var transport: WidgetRunTransport? = null

    /** A deletion, profile switch or lease end: no later step may run; the open transport is released. */
    public suspend fun cancel() {
        cancelled = true
        transport?.close()
    }

    public suspend fun execute(tap: WidgetTap, snapshot: Map<String, Any?>, deadlineEpochMs: Long): WidgetRunResult {
        val (mine, deferred) =
            synchronized(lock) {
                val existing = requests[tap.id]
                if (existing != null) false to existing
                else true to CompletableDeferred<WidgetRunResult>().also { requests[tap.id] = it }
            }
        if (!mine) return deferred.await()
        val result = run(tap, snapshot, deadlineEpochMs)
        deferred.complete(result)
        return result
    }

    private class Refused(val code: String) : Exception(null, null, false, false)

    private suspend fun run(tap: WidgetTap, snapshot: Map<String, Any?>, deadline: Long): WidgetRunResult {
        var dispatched = false
        fun result(status: String, error: String? = null, patch: Map<String, Any?>? = null) =
            WidgetRunResult(tap.id, tap.action, status, error, patch)
        try {
            val descriptor = snapshot["onDemand"] as? Map<*, *>
            val fingerprint = descriptor?.get("fingerprint") as? String
            if (fingerprint == null || snapshot["profileID"] != tap.profileId || snapshot["scopeToken"] != tap.scopeToken)
                throw Refused("staleRequest")

            suspend fun guard() {
                if (cancelled || now() >= deadline) throw Refused("unavailable")
                if (!world.isCurrent(tap, fingerprint)) throw Refused("staleRequest")
                if (cancelled || now() >= deadline) throw Refused("unavailable")
            }

            suspend fun <T> read(block: suspend () -> T): T {
                guard()
                val remaining = deadline - now()
                if (remaining <= 0) throw Refused("unavailable")
                val value = withTimeoutOrNull(remaining.milliseconds) { block() } ?: throw Refused("unavailable")
                guard()
                return value
            }

            suspend fun <T> readOk(block: suspend () -> Outcome<T>): T =
                when (val outcome = read(block)) {
                    is Outcome.Ok -> outcome.value
                    is Outcome.Failed -> throw Refused("unavailable")
                }

            suspend fun mutate(block: suspend () -> Outcome<*>) {
                guard()
                val remaining = deadline - now()
                if (remaining <= 0) throw Refused("unavailable")
                dispatched = true
                val sent = withTimeoutOrNull(remaining.milliseconds) { block() }
                if (sent !is Outcome.Ok) throw Refused("uncertainDelivery")
            }

            val rows = snapshot.rows()
            val row =
                rows.firstOrNull {
                    if (tap.runId.isNotEmpty()) (it["team"] as? Map<*, *>)?.get("runID") == tap.runId
                    else it["id"] == tap.sessionId
                }
            val actions = row?.actions()
            // Unsupported before connecting: OpenCode 2 cold taps are allow once, reject and stop only.
            if (row == null || actions == null || tap.action !in actions || tap.action !in COLD_ACTIONS)
                throw Refused("unsupported")
            val profile = read { world.loadProfile(tap.profileId) ?: throw Refused("staleRequest") }
            if (profile.transientTransport || profile.fingerprint != fingerprint) throw Refused("staleRequest")
            val target = read { world.loadTarget(tap, fingerprint) ?: throw Refused("staleRequest") }
            if (
                target["profileID"] != tap.profileId ||
                    target["scopeToken"] != tap.scopeToken ||
                    target["fingerprint"] != fingerprint
            )
                throw Refused("staleRequest")
            val gateway =
                when (val opened = read { world.open(profile, target) }) {
                    is WidgetOpen.Refused ->
                        throw Refused(if (opened.why == WidgetOpenRefusal.Unsupported) "unsupported" else "unavailable")
                    is WidgetOpen.Opened -> opened.transport.also { transport = it }
                }
            if (cancelled) throw Refused("unavailable")

            // A fresh OpenCode 2 transport has not negotiated its dialect: health settles it before any command,
            // under the same deadline and guards.
            if (!readOk { gateway.health() }) throw Refused("unavailable")
            var runMarker = row["runMarker"] as? String
            when (tap.action) {
                "allowOnce", "reject" -> {
                    if (row["requestID"] != tap.requestId || tap.requestId.isEmpty()) throw Refused("staleRequest")
                    val pending = readOk { gateway.pendingPermissions() }
                    if (pending.none { it.requestId == tap.requestId && it.sessionId == tap.sessionId })
                        throw Refused("staleRequest")
                    // The server turn identity before answering: a delayed read-only check never settles a newer turn.
                    runMarker = widgetRunMarker(readOk { gateway.messages(tap.sessionId) })
                    guard()
                    mutate { gateway.answerPermission(tap.sessionId, tap.requestId, tap.action == "allowOnce") }
                }
                "stop" -> {
                    if (
                        row["busy"] != true ||
                            row["startedAt"].asLong() != tap.expectedStartedAt ||
                            row["runMarker"] !is String
                    )
                        throw Refused("staleRequest")
                    val messages = readOk { gateway.messages(tap.sessionId) }
                    if (widgetRunMarker(messages) != row["runMarker"]) throw Refused("staleRequest")
                    val statuses = readOk { gateway.sessionStatuses() }
                    if (statuses[tap.sessionId] !in BUSY) throw Refused("staleRequest")
                    guard()
                    mutate { gateway.stop(tap.sessionId) }
                }
                else -> throw Refused("unsupported")
            }
            val serverBusy = settle(tap, gateway, deadline) { guard() }
            guard()
            return result("accepted", patch = patched(tap, snapshot, rows, row, serverBusy, runMarker))
        } catch (e: Refused) {
            return if (dispatched) result("uncertain", "uncertainDelivery") else result("rejected", e.code)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never return server messages, credential errors, prompts or traces.
            return if (dispatched) result("uncertain", "uncertainDelivery") else result("rejected", "unavailable")
        } finally {
            val open = transport
            transport = null
            if (open != null) withContext(NonCancellable) { open.close() }
        }
    }

    /**
     * Acknowledgement does not mean the turn ended, even for stop: read server truth through the same transport
     * before closing it, leaving 500 ms of the lease for native settlement. Returns the last known busy state, or
     * null when nothing was learned. A failed read keeps the command accepted and proves nothing.
     */
    private suspend fun settle(
        tap: WidgetTap,
        gateway: WidgetRunTransport,
        deadline: Long,
        guard: suspend () -> Unit,
    ): Boolean? {
        val budget = (deadline - now() - 500).coerceIn(0, statusSettleTimeout.inWholeMilliseconds)
        val start = now()
        var serverBusy: Boolean? = null
        while (now() - start < budget) {
            guard()
            val remaining = budget - (now() - start)
            if (remaining <= 0) break
            val statuses =
                (withTimeoutOrNull(remaining.milliseconds) { gateway.sessionStatuses() } as? Outcome.Ok)?.value
                    ?: break
            guard()
            val status = statuses[tap.sessionId]
            if (status == null || status == "idle") {
                serverBusy = false
                break
            }
            if (status !in BUSY) break
            serverBusy = true
            val wait = (budget - (now() - start)).coerceIn(0, statusPollInterval.inWholeMilliseconds)
            if (wait <= 0) break
            delay(wait)
        }
        return serverBusy
    }

    /** Only reviewed status fields of the already privacy-safe snapshot change; the input is never mutated. */
    private fun patched(
        tap: WidgetTap,
        snapshot: Map<String, Any?>,
        rows: List<Map<*, *>>,
        row: Map<*, *>,
        serverBusy: Boolean?,
        runMarker: String?,
    ): Map<String, Any?> {
        val answered = tap.action == "allowOnce" || tap.action == "reject"
        val sessions =
            rows.map { old ->
                if (old !== row) old
                else
                    buildMap<Any?, Any?> {
                        for ((key, value) in old) if (!(answered && key == "requestID")) put(key, value)
                        if (serverBusy != null) put("busy", serverBusy)
                        if (runMarker != null) put("runMarker", runMarker)
                        if (serverBusy == false) {
                            put("busy", false)
                            put("startedAt", 0L)
                            put("step", "")
                        }
                        val keepStop =
                            answered &&
                                (serverBusy ?: (old["busy"] == true)) &&
                                old["runMarker"] is String &&
                                old.actions()?.contains("stop") == true
                        put("onDemandActions", if (keepStop) listOf("stop") else emptyList<String>())
                    }
            }
        return LinkedHashMap(snapshot).apply {
            put("connected", false)
            put("sessions", sessions)
            put("runningCount", sessions.count { it["busy"] == true })
            if (answered) put("pendingCount", ((snapshot["pendingCount"].asLong() ?: 1L) - 1).coerceAtLeast(0).toInt())
        }
    }

    private companion object {
        val BUSY = setOf("busy", "retry")
        val COLD_ACTIONS = setOf("allowOnce", "reject", "stop")
    }
}
