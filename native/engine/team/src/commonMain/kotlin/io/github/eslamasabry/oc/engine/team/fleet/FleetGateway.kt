package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A read the host refused with [status] (Dart `OrchestrationHttpException`); [slug] is the problem's code. */
internal class FleetHttpError(val status: Int, val slug: String?) : Exception("refused by the team host") {
    val notFound: Boolean
        get() = status == 404 || slug?.endsWith("not-found") == true
}

/** The host did not answer (Dart `OrchestrationTransportException`). */
internal class FleetUnreachableError(val timedOut: Boolean) : Exception("team host unreachable")

/** Why a probe found no team to show (Dart `ProbeVerdict` without `ProbeFound`). */
public enum class FleetFailure { NotGasCity, CityNotRunning, PlainHttpRefused, Unreachable }

/** Dart `ProbeVerdict`. */
internal sealed interface FleetVerdict {
    data class Found(val host: FleetHost, val city: String, val supervisorUrl: String, val capabilities: FleetCapabilities) : FleetVerdict

    data class Refused(val failure: FleetFailure) : FleetVerdict
}

/** Dart `GcProblem.slug`: `code`, else the last `:` part of `type`. */
private fun slugOf(body: Json): String? = J.text(body, "code") ?: J.text(body, "type")?.let { it.substring(it.lastIndexOf(':') + 1) }

private fun decode(text: String): Json =
    when (val v = DartJson.decodeOrNull(text)) {
        is Map<*, *> -> J.map(v)
        is List<*> -> mapOf("items" to v)
        null -> emptyMap()
        else -> mapOf("value" to v)
    }

/** [block]'s value, or null when the host refused or did not answer; a cancellation still cancels. */
internal inline fun <T> quiet(block: () -> T): T? =
    try {
        block()
    } catch (_: FleetHttpError) {
        null
    } catch (_: FleetUnreachableError) {
        null
    }

/** One JSON GET: the body as a map (a bare array under `items`), or a thrown [FleetHttpError] / [FleetUnreachableError]. */
internal suspend fun FleetTransport.json(path: String, query: List<Pair<String, String>> = emptyList(), timeoutMs: Long = 30_000): Json =
    when (val reply = get(path, query, timeoutMs)) {
        is FleetReply.Unreachable -> throw FleetUnreachableError(reply.timedOut)
        is FleetReply.Answered -> {
            val body = decode(reply.text)
            if (!reply.ok) throw FleetHttpError(reply.status, slugOf(body))
            body
        }
    }

/** Dart `GasCityProbe.probe`: the front's well-known document, the supervisor's health, the city list, the city. */
internal object FleetProbe {
    const val WELL_KNOWN: String = "/.well-known/opencode-mobile-orchestration"
    private const val TIMEOUT_MS = 6_000L

    suspend fun probe(transports: FleetTransports, url: String, city: String?, hostMode: TeamHostMode): FleetVerdict {
        if (FleetAddress.plainHttpRefused(url)) return FleetVerdict.Refused(FleetFailure.PlainHttpRefused)
        if (!FleetAddress.allowed(url)) return FleetVerdict.Refused(FleetFailure.Unreachable)
        var base = FleetAddress.normalize(url)
        var transport = transports.open(base)
        try {
            // 0. A host front answers the well-known document; a bare supervisor (404) falls through to /health.
            val front =
                quiet { transport.json(WELL_KNOWN, timeoutMs = TIMEOUT_MS) }?.takeIf { J.text(it, "provider") == "gascity" && J.bool(it, "front") == true }
            val supervisor = front?.let { J.text(it, "supervisorUrl") }?.let(FleetAddress::normalize)
            if (supervisor != null && FleetAddress.allowed(supervisor) && supervisor != base) {
                transport.close()
                base = supervisor
                transport = transports.open(base)
            }
            val controls =
                front != null && J.bool(J.field(front, "capabilities"), "control") == true &&
                    J.bool(J.field(front, "identity"), "allowed") == true
            val verdict = checkCity(transport, base, city ?: front?.let { J.text(it, "city") }, hostMode, front != null, controls)
            // Dart `defaultGatewayFactory`: the front's address only when it lets this phone write, else the saved one.
            return if (verdict is FleetVerdict.Found && !controls) verdict.copy(supervisorUrl = FleetAddress.normalize(url)) else verdict
        } finally {
            transport.close()
        }
    }

    private suspend fun checkCity(
        transport: FleetTransport,
        base: String,
        city: String?,
        hostMode: TeamHostMode,
        front: Boolean,
        controls: Boolean,
    ): FleetVerdict {
        // 1. The supervisor's own health: shape and version.
        val health =
            try {
                WireHealth.parse(transport.json("/health", timeoutMs = TIMEOUT_MS)).takeIf { it.looksLikeGasCity }
                    ?: return FleetVerdict.Refused(FleetFailure.NotGasCity)
            } catch (e: FleetHttpError) {
                return FleetVerdict.Refused(if (e.notFound && e.slug == "city-not-found") FleetFailure.CityNotRunning else FleetFailure.NotGasCity)
            } catch (_: FleetUnreachableError) {
                return FleetVerdict.Refused(FleetFailure.Unreachable)
            }
        val wanted = city?.takeIf { it.isNotEmpty() } ?: health.city?.takeIf { it.isNotEmpty() } ?: return FleetVerdict.Refused(FleetFailure.NotGasCity)
        // 2. Is the city registered and running? Older supervisors may not list cities: the city's health decides.
        try {
            val cities = J.items(transport.json("/v0/cities", timeoutMs = TIMEOUT_MS), { it })
            val entry = cities.firstOrNull { J.text(it, "name") == wanted } ?: return FleetVerdict.Refused(FleetFailure.CityNotRunning)
            val running = J.bool(entry, "running")
            if (running == false || (running == null && J.text(entry, "status")?.lowercase() == "stopped")) {
                return FleetVerdict.Refused(FleetFailure.CityNotRunning)
            }
        } catch (_: FleetHttpError) {
            Unit
        } catch (_: FleetUnreachableError) {
            return FleetVerdict.Refused(FleetFailure.Unreachable)
        }
        // 3. The city's own health and status (identity).
        val cityPath = "/v0/city/${FleetAddress.segment(wanted)}"
        val cityHealth =
            try {
                WireHealth.parse(transport.json("$cityPath/health", timeoutMs = TIMEOUT_MS))
            } catch (e: FleetHttpError) {
                return FleetVerdict.Refused(if (e.notFound) FleetFailure.CityNotRunning else FleetFailure.NotGasCity)
            } catch (_: FleetUnreachableError) {
                return FleetVerdict.Refused(FleetFailure.Unreachable)
            }
        val status = quiet { WireStatus.parse(transport.json("$cityPath/status", timeoutMs = TIMEOUT_MS)) }
        val host = FleetMap.host(base, hostMode, if (cityHealth.version != null) cityHealth else health, status, front)
        val capabilities =
            when {
                controls -> FleetCapabilities.FRONT
                // TEAM-301: the team on this phone, on loopback, takes controls without a front.
                hostMode == TeamHostMode.Phone && FleetAddress.host(base)?.let(FleetAddress::isLoopback) == true -> FleetCapabilities.LOOPBACK
                else -> FleetCapabilities.READ
            }
        return FleetVerdict.Found(host.copy(city = host.city ?: wanted), wanted, base, capabilities)
    }
}

/** The runs, the work and the raw beads one read gave (gates need the beads, Dart `_RunsSnapshot`). */
internal data class FleetRunsRead(val runs: List<FleetRun>, val work: List<FleetWork>, val beads: List<WireBead>, val pending: List<WirePending>)

/**
 * Reads and writes for one city (Dart `GasCityGateway` + `GasCityControl`). Reads throw [FleetHttpError] or
 * [FleetUnreachableError]; writes never throw and answer a [FleetReceipt].
 */
internal class FleetGateway(
    private val transport: FleetTransport,
    city: String,
    val capabilities: FleetCapabilities,
    /** Behind a host front: every write carries an `Idempotency-Key`. */
    private val front: Boolean,
    private val nowMs: () -> Long,
) {
    private val cityPath = "/v0/city/${FleetAddress.segment(city)}"

    private suspend fun city(tail: String, query: List<Pair<String, String>> = emptyList()): Json = transport.json("$cityPath$tail", query)

    /** A city read that may be missing on this host: an HTTP refusal reads as empty, an unreachable host still throws. */
    private suspend fun optional(tail: String, query: List<Pair<String, String>> = emptyList()): Json =
        try {
            city(tail, query)
        } catch (_: FleetHttpError) {
            emptyMap()
        }

    fun close() = transport.close()

    suspend fun status(): WireStatus = WireStatus.parse(city("/status"))

    suspend fun projects(): List<FleetProject> = FleetMap.rigs(status())

    /** Dart `_workContext` + `_workSnapshot` + `_runs`: `/runs` merged with `/convoys`, over one work snapshot. */
    suspend fun runsAndWork(): FleetRunsRead =
        coroutineScope {
            val runs = async { city("/runs") }
            val convoys = async { city("/convoys") }
            val beads = async { city("/beads") }
            val ready = async { optional("/beads", listOf("ready" to "true")) }
            val waits = async { optional("/waits") }
            val pending = async { optional("/pending") }
            val convoyList = J.items(convoys.await(), { WireConvoy.parse(it) })
            val pendingList = J.items(pending.await(), { WirePending.parse(it) })
            val context =
                WorkContext.from(
                    ready = J.items(ready.await(), { WireBead.parse(it) }),
                    waits = J.items(waits.await(), { WireWait.parse(it) }, key = "waits"),
                    pending = pendingList,
                    convoys = convoyList,
                )
            val beadList = J.items(beads.await(), { WireBead.parse(it) })
            val work = FleetMap.beads(beadList, context)
            val byId = work.associateBy { it.id }
            val formula = J.items(runs.await(), { WireRun.parse(it) }, key = "runs").map { FleetMap.run(it) }
            val finished = finishedConvoys(convoyList.map { it.id }.toSet())
            val open = convoyList.map { FleetMap.convoy(it, byId, context) }
            val done = finished.map { (convoy, closedAt) -> FleetMap.convoy(convoy, byId, context, closedAt) }
            FleetRunsRead(formula + open + done, work, beadList, pendingList)
        }

    /** `GET /convoy/<id>` answers of finished convoys: a closed convoy does not change, so each is read once. */
    private val finishedDetails = HashMap<String, WireConvoy>()

    /**
     * Dart `_finishedConvoyHistory` + `_finishedConvoyDetails`: `/convoys` lists open convoys only, so a finished batch
     * would vanish. The closed ones of the last week (at most [FINISHED_LIMIT], newest first) come from
     * `/beads?status=closed&type=convoy` dated by `/events?type=convoy.closed`. Optional: history never blanks a read.
     */
    private suspend fun finishedConvoys(openIds: Set<String>): List<Pair<WireConvoy, Long?>> =
        try {
            val closed = J.items(optional("/beads", listOf("status" to "closed", "type" to "convoy", "limit" to "$FINISHED_LIMIT")), { WireBead.parse(it) })
            val events =
                J.items(
                    optional("/events", listOf("type" to "convoy.closed", "since" to "${FINISHED_WINDOW_MS / 60_000}m", "limit" to "$FINISHED_LIMIT")),
                    { WireEvent.parse(it) },
                )
            val closedAt = HashMap<String, Long>()
            events.filter { it.type == "convoy.closed" }.forEach { e ->
                val id = e.subject ?: return@forEach
                val ts = e.tsMs ?: return@forEach
                if ((closedAt[id] ?: Long.MIN_VALUE) < ts) closedAt[id] = ts
            }
            val since = nowMs() - FINISHED_WINDOW_MS
            val picked =
                closed
                    .filter { it.isConvoy && it.isClosed && it.id.isNotEmpty() && it.id !in openIds }
                    .mapNotNull { b -> (closedAt[b.id] ?: b.updatedAtMs ?: b.createdAtMs)?.takeIf { it >= since }?.let { b to it } }
                    .sortedByDescending { it.second }
                    .take(FINISHED_LIMIT)
            finishedDetails.keys.retainAll(picked.map { it.first.id }.toSet())
            picked.map { (bead, _) ->
                val detail =
                    finishedDetails[bead.id]
                        ?: quiet { WireConvoy.parse(city("/convoy/${FleetAddress.segment(bead.id)}")) }
                            ?.takeIf { it.id == bead.id && it.bead.isClosed }
                            ?.also { finishedDetails[bead.id] = it }
                (detail ?: WireConvoy(bead, emptyList(), null, null)) to closedAt[bead.id]
            }
        } catch (_: FleetUnreachableError) {
            emptyList()
        }

    /** Dart `agents()`: `/agents` with `/sessions`, `/pending` and `/waits`. */
    suspend fun agents(): List<FleetAgent> =
        coroutineScope {
            val agents = async { city("/agents") }
            val sessions = async { city("/sessions") }
            val pending = async { optional("/pending") }
            val waits = async { optional("/waits") }
            FleetMap.agents(
                J.items(agents.await(), { WireAgent.parse(it) }),
                J.items(sessions.await(), { WireSession.parse(it) }),
                J.items(pending.await(), { WirePending.parse(it) }),
                J.items(waits.await(), { WireWait.parse(it) }, key = "waits"),
            )
        }

    /**
     * Dart `_withQuestions`: `/pending` names only the kind and the session; the question and its options are on
     * `/session/<id>/pending`, read once per session. A session the host does not answer for keeps the bare entry.
     */
    suspend fun withQuestions(items: List<WirePending>): List<WirePending> {
        val bySession = HashMap<String, Json>()
        return items.map { item ->
            val session = item.sessionId
            if (item.prompt != null || session.isNullOrEmpty()) return@map item
            val detail = bySession.getOrPut(session) { optional("/session/${FleetAddress.segment(session)}/pending") }
            val pending = detail["pending"] as? Map<*, *> ?: return@map item
            val merged = J.map(pending)
            if (merged["request_id"] != item.requestId) return@map item
            val parsed = WirePending.parse(merged + ("session_id" to session))
            parsed.copy(createdAtMs = parsed.createdAtMs ?: item.createdAtMs, kind = parsed.kind ?: item.kind)
        }
    }

    suspend fun usage(): FleetUsage =
        coroutineScope {
            val usage = async { city("/usage") }
            val status = async { quiet { status() } }
            FleetMap.usage(usage.await(), status.await())
        }

    suspend fun scheduledJobs(): List<FleetJob> = FleetMap.orders(J.list(city("/orders"), "orders", { WireOrder.parse(it) }))

    /** `/events/stream` from [lastEventId]; frames go to [onFrame] until it ends. */
    suspend fun events(lastEventId: String?, onFrame: suspend (FleetFrame) -> Unit): FleetStreamEnd =
        transport.stream("$cityPath/events/stream", lastEventId, onFrame = onFrame)

    /** `GET /session/<id>/stream` (Dart `agentOutput`): the agent's `turn` frames until the stream ends; a 404 ends it for good. */
    suspend fun sessionOutput(sessionId: String, lastEventId: String?, onFrame: suspend (FleetFrame) -> Unit): FleetStreamEnd =
        transport.stream("${session(sessionId)}/stream", lastEventId, onFrame = onFrame)

    // ---- writes (Dart GasCityControl)

    private fun session(id: String) = "$cityPath/session/${FleetAddress.segment(id)}"

    suspend fun control(control: FleetControl, requestId: String, agents: List<FleetAgent>, runs: List<FleetRun>, gates: List<FleetGate>): FleetReceipt {
        if (!allowed(control)) return FleetReceipt.rejected(requestId, FleetReceipt.FRONT_REQUIRED)
        val agentOf = { id: String -> agents.firstOrNull { it.id == id || it.name == id || it.sessionId == id } }
        val sessionOf = { id: String -> agentOf(id)?.sessionId ?: id }
        return when (control) {
            is FleetControl.Answer -> {
                val sessionId =
                    gates.firstOrNull { it.id == control.gateId }?.agentId
                        ?: return FleetReceipt.rejected(requestId, "no pending interaction on the host")
                val body = LinkedHashMap<String, Any?>()
                body["action"] =
                    when (val a = control.answer) {
                        is FleetAnswer.Choice -> a.option
                        is FleetAnswer.Confirm -> if (a.yes) "allow" else "deny"
                        is FleetAnswer.Text -> "text"
                    }
                body["request_id"] = control.gateId
                (control.answer as? FleetAnswer.Text)?.let { body["text"] = it.text }
                post("${session(sessionId)}/respond", requestId, body)
            }
            is FleetControl.Message ->
                if (control.text.isBlank()) FleetReceipt.rejected(requestId, "message is empty")
                else post("${session(sessionOf(control.agentId))}/messages", requestId, mapOf("message" to control.text))
            is FleetControl.Agent -> agent(control.agentId, control.action, requestId, agentOf(control.agentId))
            is FleetControl.CancelRun -> {
                val id = FleetAddress.segment(control.runId)
                val batch = runs.firstOrNull { it.id == control.runId }?.kind == RunKind.Batch
                post(if (batch) "$cityPath/convoy/$id/close" else "$cityPath/runs/$id/cancel", requestId)
            }
            is FleetControl.Assign ->
                post("$cityPath/sling", requestId, mapOf("bead" to control.workId, "target" to control.agentId, "reassign" to true))
            is FleetControl.CreateWork -> {
                if (control.title.isBlank()) return FleetReceipt.rejected(requestId, "title is empty")
                val body = LinkedHashMap<String, Any?>()
                body["title"] = control.title.trim()
                control.description?.trim()?.takeIf { it.isNotEmpty() }?.let { body["description"] = it }
                control.projectId?.takeIf { it.isNotEmpty() }?.let { body["rig"] = it }
                body["type"] = "task"
                body["priority"] = 2
                body["labels"] = listOf("opencode-mobile")
                post("$cityPath/beads", requestId, body)
            }
            is FleetControl.Project -> {
                val rig = "$cityPath/rig/${FleetAddress.segment(control.projectId)}"
                when (control.action) {
                    FleetProjectAction.Suspend -> post("$rig/suspend", requestId)
                    FleetProjectAction.Resume -> post("$rig/resume", requestId)
                    FleetProjectAction.Remove -> post(rig, requestId, method = "DELETE")
                }
            }
            is FleetControl.Job ->
                post("$cityPath/order/${FleetAddress.segment(control.jobId)}/${if (control.enabled) "enable" else "disable"}", requestId)
        }
    }

    /** The capability each write needs; a write it does not have is refused here without a request. */
    private fun allowed(control: FleetControl): Boolean =
        when (control) {
            is FleetControl.Answer -> capabilities.controlRespond
            is FleetControl.Message -> capabilities.controlMessage
            is FleetControl.Agent ->
                when (control.action) {
                    FleetAgentAction.Nudge -> capabilities.controlMessage
                    FleetAgentAction.Kill -> capabilities.controlAgentKill
                    else -> capabilities.controlAgent
                }
            is FleetControl.CancelRun -> capabilities.controlCancelRun
            is FleetControl.Assign -> capabilities.controlAssign
            is FleetControl.CreateWork -> capabilities.controlCreateWork
            is FleetControl.Project ->
                if (control.action == FleetProjectAction.Remove) capabilities.controlProjectRemove else capabilities.controlProject
            is FleetControl.Job -> capabilities.controlScheduledJobs
        }

    /** Dart `controlAgent`: agent routes for an agent with its own entry, session routes for a pool instance. */
    private suspend fun agent(agentId: String, action: FleetAgentAction, requestId: String, agent: FleetAgent?): FleetReceipt {
        val sessionId = agent?.sessionId ?: agentId
        suspend fun agentOrSession(agentAction: String, sessionAction: String): FleetReceipt =
            if (agent != null && agent.hasAgentEntry) {
                val segments = agent.name.split('/').joinToString("/") { FleetAddress.segment(it) }
                post("$cityPath/agent/$segments/$agentAction", requestId)
            } else {
                post("${session(sessionId)}/$sessionAction", requestId)
            }
        return when (action) {
            FleetAgentAction.Nudge -> post("${session(sessionId)}/messages", requestId, mapOf("message" to NUDGE))
            FleetAgentAction.Pause -> agentOrSession("suspend", "suspend")
            FleetAgentAction.Resume -> agentOrSession("resume", "wake")
            FleetAgentAction.Stop -> post("${session(sessionId)}/stop", requestId)
            FleetAgentAction.Kill -> post("${session(sessionId)}/kill", requestId)
            FleetAgentAction.Restart -> {
                val stopped = post("${session(sessionId)}/stop", requestId)
                if (!stopped.accepted) stopped
                else post("${session(sessionId)}/wake", requestId, idempotencyKey = "$requestId:wake").copy(requestId = requestId)
            }
        }
    }

    /** Dart `_post` + `receiptFromFront`. */
    private suspend fun post(
        path: String,
        requestId: String,
        body: Map<String, Any?>? = null,
        method: String = "POST",
        idempotencyKey: String? = null,
    ): FleetReceipt {
        val reply = transport.send(method, path, body ?: emptyMap(), requestId, if (front) idempotencyKey ?: requestId else null)
        return receipt(requestId, reply)
    }

    companion object {
        /** Dart `defaultFinishedRunsLimit` and `defaultFinishedRunsWindow`. */
        const val FINISHED_LIMIT: Int = 20
        const val FINISHED_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** Dart `nudgeMessage`. */
        const val NUDGE: String = "please continue"
        private const val FRONT_PROBLEM = "urn:opencode-mobile:front:"

        private fun looksLikeProblem(json: Json): Boolean =
            J.text(json, "type")?.startsWith("urn:") == true || (json["status"] is Number && J.text(json, "title") != null)

        private fun problemMessage(json: Json): String? = J.text(json, "detail") ?: J.text(json, "title") ?: slugOf(json)

        /** Dart `receiptFromFront`: a front receipt, a plain 2xx, or a problem. */
        fun receipt(requestId: String, reply: FleetReply): FleetReceipt {
            val answered = reply as? FleetReply.Answered ?: return FleetReceipt(requestId, ReceiptStatus.Pending, "no answer")
            val body = decode(answered.text)
            val status = J.text(body, "status")
            if (body.containsKey("upstream_status") && (status == "accepted" || status == "rejected")) {
                val inner = J.field(body, "body")
                val accepted = status == "accepted"
                return FleetReceipt(
                    requestId = requestId,
                    status = if (accepted) ReceiptStatus.Accepted else ReceiptStatus.Rejected,
                    message = if (accepted) null else problemMessage(inner) ?: J.text(inner, "error") ?: "HTTP ${J.int(body, "upstream_status") ?: "?"}",
                    hostRequestId = J.text(body, "request_id") ?: answered.hostRequestId,
                    correlationId = if (accepted) J.text(inner, "request_id") else null,
                    createdId = J.text(inner, "id"),
                )
            }
            if (answered.ok) {
                return FleetReceipt(
                    requestId,
                    ReceiptStatus.Accepted,
                    hostRequestId = answered.hostRequestId,
                    correlationId = J.text(body, "request_id"),
                    createdId = J.text(body, "id"),
                )
            }
            val problem = looksLikeProblem(body)
            val fromFront = problem && J.text(body, "type")?.startsWith(FRONT_PROBLEM) == true
            val retryable = fromFront && slugOf(body) == "upstream-unavailable"
            return FleetReceipt(
                requestId = requestId,
                status = if (retryable) ReceiptStatus.Pending else ReceiptStatus.Rejected,
                message = (if (problem) problemMessage(body) else null) ?: "HTTP ${answered.status}",
                retryable = retryable,
                hostRequestId = answered.hostRequestId,
            )
        }
    }
}
