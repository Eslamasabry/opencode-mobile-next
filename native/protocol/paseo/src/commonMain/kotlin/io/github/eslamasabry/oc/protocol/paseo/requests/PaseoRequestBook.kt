package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.QuestionChoice
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.PaseoWireJson
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * # Paseo requests: permissions, native questions/plans and ACP host permissions
 *
 * Ports of lib/paseo/gateway/permissions.dart, gateway/questions.dart, the request bookkeeping of gateway.dart
 * (`_answered`, `_rejectedCalls`, native snapshots) and the ACP part of host_agent_providers.dart. The book owns no
 * transport: the gateway implements [PaseoRequestHost] and forwards pushes and calls. Every method runs on the
 * gateway's lane (no internal locking). Failures are [PaseoException]s; the gateway converts them at its boundary.
 *
 * ## Wiring (gateway → book)
 * | Gateway sees | Call |
 * |---|---|
 * | push `agent_permission_request` {agentId, request} for a known session | [onPermissionRequest] (app id, request) |
 * | push `agent_permission_resolved` {requestId} | [onPermissionResolved] |
 * | an agent snapshot (`fetch_agents` entry, `fetch_agent`, `agent_update` upsert) | [syncSnapshot] (app id, `pendingPermissions`) |
 * | session removed (`agent_deleted`, `agent_archived`, `agent_update` remove, delete/archive) | [retireSession] |
 * | `setLocation` | [clear] |
 * | socket lost (transport `onDisconnect`) and `close()` | [disconnect] |
 * | stream `tool_call` with status failed | [wasRejected] (call id) → read as declined (B-AGENT-186) |
 *
 * ## ServerGateway / facets → book
 * `pendingPermissions()` → [pendingPermissions]; `replyPermission(session, request, reply, message)` →
 * [replyPermission]; `QuestionGateway.pendingQuestions/answerQuestion/rejectQuestion` → [pendingQuestions],
 * [answerQuestion], [rejectQuestion]; host-agent permissions → [pendingHostPermissions], [replyHostPermission].
 * Native snapshot revisions (B-SEC-115, B-CHAT-072): [permissionSnapshot], [isPermissionCurrent],
 * [questionSnapshot], [isQuestionCurrent]; feed attention: [revision], [hasPermission], [hasQuestion].
 *
 * ## Events
 * Permission asked/settled go out as `GatewayEvent.PermissionAsked` / `PermissionSettled`. Questions and plans are
 * never permissions: they go to [PaseoRequestHost.questionChanged] (a [PaseoQuestionSignal]); until core adds
 * `QuestionAsked`/`QuestionSettled` the gateway forwards them as `RefreshHint(HintKind("question"))`.
 *
 * Not ported here: gen-UI automatic card approval (`_tryAllowGenUiShow`, S5 gen-UI slice); [PaseoPermissionAnswer]
 * keeps its seam (a one-call `Once` reply).
 */
public interface PaseoRequestHost {
    /** The transport's current connection epoch. */
    public val epoch: Int

    /** Transport connected and gateway open. */
    public val connected: Boolean

    /** The pinned directory, or null when none is set. */
    public val scope: String?

    /** Bumped on every `setLocation`. */
    public val locationEpoch: Int

    /** The daemon's id for an app session id (drafts keep their app id until created). */
    public fun realId(appId: String): String

    /** The runtime (`provider`) of the session's agent, null while unknown. */
    public fun providerOf(appId: String): String?

    /** Transport request on [expectedEpoch]; throws [PaseoException]. */
    public suspend fun request(type: String, body: JsonObject, mutation: Boolean, expectedEpoch: Int): JsonObject

    /** Answer-less session message on [expectedEpoch]; throws [PaseoException] (StaleRequest when not connected). */
    public suspend fun send(type: String, body: JsonObject, expectedEpoch: Int)

    public fun emit(event: GatewayEvent)

    public fun questionChanged(signal: PaseoQuestionSignal)
}

/** Question lifecycle, the shape plan 04 §8 calls `QuestionAsked` / `QuestionSettled(Replied|Rejected)`. */
public sealed interface PaseoQuestionSignal {
    public data class Asked(val question: PendingQuestion, val plan: Boolean) : PaseoQuestionSignal

    public data class Settled(val sessionId: SessionId, val requestId: RequestId, val rejected: Boolean) : PaseoQuestionSignal
}

/** A permission reply, including Paseo's `cancel` (deny and interrupt the turn). */
public enum class PaseoPermissionAnswer { Once, Always, Reject, Cancel }

public fun PermissionReply.toPaseo(): PaseoPermissionAnswer =
    when (this) {
        PermissionReply.Once -> PaseoPermissionAnswer.Once
        PermissionReply.Always -> PaseoPermissionAnswer.Always
        PermissionReply.Reject -> PaseoPermissionAnswer.Reject
    }

/** A detached display copy (immutable values) and an opaque revision for exact-revision replies. */
public data class PaseoPermissionSnapshot(val permission: PermissionRequest, val revision: Any)

public data class PaseoQuestionSnapshot(val question: PendingQuestion, val revision: Any)

public class PaseoRequestBook(private val host: PaseoRequestHost) {
    private class Permission(
        val epoch: Int,
        val permission: PermissionRequest,
        val suggestions: JsonArray,
        val host: HostAgentPermissionRequest?,
        val revision: String,
    )

    private class Question(
        val epoch: Int,
        val question: PendingQuestion,
        val canonical: String,
        val answerKeys: List<String>,
        val plan: Boolean,
    )

    private val permissions = LinkedHashMap<String, Permission>()
    private val questions = LinkedHashMap<String, Question>()
    private val answered = LinkedHashSet<String>()
    private val rejectedCalls = LinkedHashSet<String>()
    private val changes = MutableStateFlow(0)

    /** Bumped whenever the pending permission or question set changes (feed attention without a refresh). */
    public val revision: StateFlow<Int> = changes.asStateFlow()

    private fun changed() {
        changes.value += 1
    }

    // ---- pushes

    /** A permission push (or one snapshot entry). Malformed or foreign requests never become cards. */
    public fun onPermissionRequest(appId: String, request: JsonObject) {
        val id = request.str("id")?.takeIf { it.isNotEmpty() && it.length <= 512 } ?: return
        val previous = permissions[id]
        val previousQuestion = questions[id]
        if ((previous != null && previous.permission.sessionId.raw != appId) ||
            (previousQuestion != null && previousQuestion.question.sessionId.raw != appId) ||
            id in answered
        ) {
            return
        }
        val kind = request["kind"].stringOrNull()
        if (kind == "question" || kind == "plan") {
            if (previous != null) resolvePermission(id)
            addQuestion(appId, id, request)
            return
        }
        val pending = try {
            parsePermission(appId, id, request)
        } catch (_: PaseoException) {
            // A malformed replacement retires the old grant instead of leaving it answerable.
            if (previous != null) resolvePermission(id)
            resolveQuestion(id)
            return
        }
        if (previous != null && previous.epoch == pending.epoch && previous.revision == pending.revision) return
        resolveQuestion(id)
        if (previous == null && permissions.size >= MAX_PENDING) resolvePermission(permissions.keys.first())
        permissions[id] = pending
        host.emit(GatewayEvent.PermissionAsked(pending.permission))
        changed()
    }

    private fun parsePermission(appId: String, id: String, request: JsonObject): Permission {
        // Display mappers are lenient; approval admission rejects malformed input.
        for (key in listOf("name", "kind", "provider")) {
            val v = request[key]
            if (v != null && !(v is JsonPrimitive && v.isString)) paseoFail(PaseoFailureKind.InvalidResponse)
        }
        for (key in listOf("detail", "input", "metadata")) {
            if (key in request && request[key] !is JsonObject) paseoFail(PaseoFailureKind.InvalidResponse)
        }
        val detail = request.obj("detail")
        if (detail != null && detail.str("type") == "shell" && detail.str("command") == null) paseoFail(PaseoFailureKind.InvalidResponse)
        if ("suggestions" in request && request["suggestions"] !is JsonArray) paseoFail(PaseoFailureKind.InvalidResponse)
        val provider = host.providerOf(appId)
        val hostRequest = paseoHostPermission(appId, request, provider)
        // The trusted session provider decides ACP admission; a contradictory native claim is refused.
        if (hostRequest == null && request.str("provider") != null && request.str("provider") != provider) {
            paseoFail(PaseoFailureKind.InvalidResponse)
        }
        val permission = if (hostRequest == null) {
            paseoPermission(appId, request)
        } else {
            // Only the structured task preview; no host title, description, input or raw metadata (B-AGENT-145).
            paseoPermission(
                appId,
                JsonObject(
                    buildMap {
                        put("id", JsonPrimitive(hostRequest.requestId))
                        put("name", JsonPrimitive("tool"))
                        put("kind", JsonPrimitive("tool"))
                        request["detail"]?.let { put("detail", it) }
                    },
                ),
            )
        }
        val suggestions = if (hostRequest == null) request.arr("suggestions") ?: JsonArray(emptyList()) else JsonArray(emptyList())
        return Permission(host.epoch, permission, suggestions, hostRequest, canonical(request) + "|" + provider)
    }

    /** `agent_permission_resolved`, or any answer: the card and question with this id retire. */
    public fun onPermissionResolved(requestId: String) {
        resolvePermission(requestId)
    }

    private fun resolvePermission(requestId: String) {
        resolveQuestion(requestId)
        val removed = permissions.remove(requestId) ?: return
        host.emit(GatewayEvent.PermissionSettled(removed.permission.sessionId, removed.permission.id))
        changed()
    }

    /**
     * An agent snapshot lists everything still waiting: it adds requests this client missed and retires ones
     * answered elsewhere. Requests answered here never come back from a stale snapshot. A non-list is ignored.
     */
    public fun syncSnapshot(appId: String, pending: JsonElement?) {
        if (pending !is JsonArray) return
        val current = mutableSetOf<String>()
        for (raw in pending.take(MAX_PENDING)) {
            if (raw !is JsonObject) continue
            raw.str("id")?.let { current += it }
            onPermissionRequest(appId, raw)
        }
        permissions.filter { it.value.permission.sessionId.raw == appId && it.key !in current }.keys.toList().forEach(::resolvePermission)
        questions.filter { it.value.question.sessionId.raw == appId && it.key !in current }.keys.toList().forEach { resolveQuestion(it) }
    }

    /** The session is gone: its cards and questions retire. */
    public fun retireSession(appId: String) {
        permissions.filter { it.value.permission.sessionId.raw == appId }.keys.toList().forEach(::resolvePermission)
        questions.filter { it.value.question.sessionId.raw == appId }.keys.toList().forEach { resolveQuestion(it) }
    }

    /** Location change: everything belongs to the old project; no events, one change. */
    public fun clear() {
        permissions.clear()
        questions.clear()
        changed()
    }

    /** The socket was lost: every request retires without being answered or resent (B-SEC-115). */
    public fun disconnect() {
        questions.keys.toList().forEach { resolveQuestion(it) }
        permissions.keys.toList().forEach(::resolvePermission)
    }

    /** Whether the person declined this tool call at its permission card (a failed call then reads as declined). */
    public fun wasRejected(callId: String): Boolean = callId in rejectedCalls

    // ---- permissions, ServerGateway-facing

    public fun pendingPermissions(): ImmutableList<PermissionRequest> = permissions.values.map { it.permission }.toImmutableList()

    public fun hasPermission(sessionId: String): Boolean = permissions.values.any { it.permission.sessionId.raw == sessionId }

    /**
     * Answers a permission. [legacySessionId], when given, must own the request. Native: `Once` allows, `Always` also
     * sends the provider's suggested rule (Unavailable when it suggested none), `Reject` denies with [message],
     * `Cancel` denies and interrupts. ACP host requests accept only `Once` (when exactly one allow-once action is
     * offered), `Reject` and `Cancel` (both through [replyHostPermission] with no selection).
     */
    public suspend fun replyPermission(
        requestId: String,
        answer: PaseoPermissionAnswer,
        legacySessionId: String? = null,
        message: String? = null,
    ) {
        val pending = permissions[requestId]
        if (pending == null || (legacySessionId != null && legacySessionId != pending.permission.sessionId.raw)) {
            paseoFail(PaseoFailureKind.StaleRequest)
        }
        val hostRequest = pending.host
        if (hostRequest != null) {
            val action = when (answer) {
                PaseoPermissionAnswer.Always -> paseoFail(PaseoFailureKind.Unavailable)
                PaseoPermissionAnswer.Once -> {
                    val allow = hostRequest.choices.filter { it.behavior == HostAgentPermissionBehavior.AllowOnce }
                    // More than one allow choice needs the exact-action card, not a guess.
                    if (allow.size != 1) paseoFail(PaseoFailureKind.Unavailable)
                    allow.single().actionId
                }
                PaseoPermissionAnswer.Reject, PaseoPermissionAnswer.Cancel -> null
            }
            return replyHostPermission(requestId, action)
        }
        if (answer == PaseoPermissionAnswer.Always && pending.suggestions.isEmpty()) paseoFail(PaseoFailureKind.Unavailable)
        val response = when (answer) {
            PaseoPermissionAnswer.Once -> jsonOf("behavior" to "allow")
            PaseoPermissionAnswer.Always -> jsonOf("behavior" to "allow", "updatedPermissions" to pending.suggestions)
            PaseoPermissionAnswer.Reject -> {
                val text = message?.trim()?.takeIf { it.isNotEmpty() }
                if (text == null) jsonOf("behavior" to "deny") else jsonOf("behavior" to "deny", "message" to paseoText(JsonPrimitive(text), max = 4096))
            }
            PaseoPermissionAnswer.Cancel -> jsonOf("behavior" to "deny", "interrupt" to true)
        }
        checkEpoch(pending.epoch)
        val call = pending.permission.tool?.callId
        if (answer == PaseoPermissionAnswer.Reject && call != null) remember(rejectedCalls, call)
        host.send(
            "agent_permission_response",
            jsonOf("agentId" to host.realId(pending.permission.sessionId.raw), "requestId" to requestId, "response" to response),
            pending.epoch,
        )
        remember(answered, requestId)
        resolvePermission(requestId)
    }

    // ---- ACP host permissions (B-SEC-109)

    public fun pendingHostPermissions(): ImmutableList<HostAgentPermissionRequest> = permissions.values.mapNotNull { it.host }.toImmutableList()

    /**
     * Selects exactly an offered one-call action. A null selection rejects: the offered reject-once action, or, when
     * there is none, cancelling the turn (Paseo's implicit deny would fall back to reject_always). A cancel that the
     * daemon acknowledges while the request is still pending, or that belongs to an older project, retires nothing.
     */
    public suspend fun replyHostPermission(requestId: String, selectedActionId: String?) {
        val pending = permissions[requestId]
        val request = pending?.host ?: paseoFail(PaseoFailureKind.StaleRequest)
        checkEpoch(pending.epoch)
        val scope = host.scope
        val location = host.locationEpoch
        val realAgentId = host.realId(request.sessionId)
        val choice = if (selectedActionId != null) {
            request.choices.firstOrNull { it.actionId == selectedActionId } ?: paseoFail(PaseoFailureKind.StaleRequest)
        } else {
            request.choices.firstOrNull { it.behavior == HostAgentPermissionBehavior.RejectOnce }
        }
        if (choice == null) {
            val result = host.request("cancel_agent_request", jsonOf("agentId" to realAgentId), mutation = true, expectedEpoch = pending.epoch)
            if (host.scope != scope || host.locationEpoch != location || scope == null) paseoFail(PaseoFailureKind.ScopeMismatch)
            checkEpoch(pending.epoch)
            val current = permissions[requestId]
            if (current != null && current !== pending) paseoFail(PaseoFailureKind.StaleRequest)
            val agent = result.obj("agent")
            val stillPending = agent?.get("pendingPermissions")
            // An idle agent may acknowledge a cancel without interrupting anything: no retirement on that alone.
            if (permissions[requestId] === pending &&
                (agent == null || agent.str("id") != realAgentId || agent.str("cwd") != scope || stillPending !is JsonArray ||
                    stillPending.any { (it as? JsonObject)?.str("id") == requestId })
            ) {
                paseoFail(PaseoFailureKind.Unavailable)
            }
        } else {
            host.send(
                "agent_permission_response",
                jsonOf(
                    "agentId" to realAgentId,
                    "requestId" to requestId,
                    "response" to mapOf(
                        "behavior" to if (choice.behavior == HostAgentPermissionBehavior.AllowOnce) "allow" else "deny",
                        "selectedActionId" to choice.actionId,
                    ),
                ),
                pending.epoch,
            )
        }
        remember(answered, requestId)
        resolvePermission(requestId)
    }

    // ---- native snapshots (B-SEC-115)

    public fun permissionSnapshot(sessionId: String): PaseoPermissionSnapshot? {
        if (!host.connected) return null
        val pending = permissions.values.firstOrNull { it.permission.sessionId.raw == sessionId && it.epoch == host.epoch } ?: return null
        return PaseoPermissionSnapshot(pending.permission, pending)
    }

    public fun isPermissionCurrent(sessionId: String, requestId: String, revision: Any): Boolean {
        val pending = permissions[requestId]
        return host.connected && pending != null && pending === revision && pending.epoch == host.epoch &&
            pending.permission.sessionId.raw == sessionId
    }

    // ---- questions and plans (B-CHAT-072)

    public fun pendingQuestions(): ImmutableList<PendingQuestion> = questions.values.map { it.question }.toImmutableList()

    public fun hasQuestion(sessionId: String): Boolean = questions.values.any { it.question.sessionId.raw == sessionId }

    public fun questionSnapshot(sessionId: String): PaseoQuestionSnapshot? {
        if (!host.connected) return null
        val pending = questions.values.firstOrNull { it.question.sessionId.raw == sessionId && it.epoch == host.epoch } ?: return null
        return PaseoQuestionSnapshot(pending.question, pending)
    }

    public fun isQuestionCurrent(sessionId: String, requestId: String, revision: Any): Boolean {
        val pending = questions[requestId]
        return host.connected && pending != null && pending === revision && pending.epoch == host.epoch &&
            pending.question.sessionId.raw == sessionId
    }

    private fun addQuestion(appId: String, id: String, request: JsonObject) {
        val previous = questions[id]
        if ((previous != null && previous.question.sessionId.raw != appId) || id in permissions || id in answered) return
        val pending = try {
            parseQuestion(appId, id, request)
        } catch (_: PaseoException) {
            null // a malformed new revision must not leave the old prompt answerable
        }
        if (pending == null) {
            if (previous != null) resolveQuestion(id)
            return
        }
        if (previous != null && previous.epoch == pending.epoch && previous.plan == pending.plan &&
            previous.canonical == pending.canonical && previous.answerKeys == pending.answerKeys
        ) {
            return
        }
        if (previous == null && questions.size >= MAX_PENDING) return
        questions[id] = pending
        host.questionChanged(PaseoQuestionSignal.Asked(pending.question, pending.plan))
        changed()
    }

    private fun parseQuestion(appId: String, id: String, request: JsonObject): Question? {
        val provider = host.providerOf(appId)
        val agent = NativeQuestionAdapter.forProvider(provider)
        if (agent == null || (request.str("provider") != null && request.str("provider") != provider)) return null
        val input = paseoObject(request["input"])
        val plan = request.str("kind") == "plan"
        val prompts = mutableListOf<QuestionPrompt>()
        val keys = mutableListOf<String>()
        if (plan) {
            if (agent.planToolName == null || request.str("name") != agent.planToolName) return null
            val actions = paseoList(request["actions"], max = 16)
            fun offers(id: String, behavior: String) = actions.any { (it as? JsonObject)?.let { a -> a.str("id") == id && a.str("behavior") == behavior } == true }
            if (!offers("implement", "allow") || !offers("reject", "deny")) return null
            val text = paseoString(input["plan"]?.takeIf { it !is JsonNull } ?: request.obj("metadata")?.get("planText"), max = 65536)
            if (text.isBlank()) return null
            prompts += QuestionPrompt(
                title = "Plan",
                question = text,
                multiple = false,
                custom = false,
                optional = false,
                // Choice labels are the wire contract of the answer (`Approve` / `Keep planning`); their words and
                // descriptions are :ui:words'.
                choices = listOf(QuestionChoice(PLAN_APPROVE, ""), QuestionChoice(PLAN_KEEP, "")).toImmutableList(),
                markdown = true,
            )
        } else {
            if (agent.questionToolName != null && request.str("name") != agent.questionToolName) return null
            val raw = paseoList(input["questions"], max = 16)
            if (raw.isEmpty()) return null
            for (item in raw) {
                val value = paseoObject(item)
                val question = paseoString(value["question"], max = 16384)
                val header = paseoString(value["header"], max = 512)
                if (question.isBlank() || header.isBlank()) return null
                for (flag in listOf("multiSelect", "allowOther", "allowEmpty", "isOther")) {
                    val f = value[flag]
                    if (f != null && f !is JsonNull && !(f is JsonPrimitive && !f.isString && (f.content == "true" || f.content == "false"))) return null
                }
                val key = if (agent.keysByQuestionText) question else header
                if (key in keys) return null
                keys += key
                val options = mutableListOf<QuestionChoice>()
                val labels = mutableSetOf<String>()
                for (rawOption in paseoList(value["options"], max = 64)) {
                    val option = paseoObject(rawOption)
                    val label = paseoString(option["label"], max = 512)
                    if (label.isBlank() || !labels.add(label)) return null
                    val description = option["description"]?.takeIf { it !is JsonNull }
                    options += QuestionChoice(label, if (description == null) "" else paseoString(description, max = 4096, empty = true))
                }
                prompts += QuestionPrompt(
                    title = header,
                    question = question,
                    multiple = value.flag("multiSelect"),
                    custom = options.isEmpty() || value.flag("allowOther") || value.flag("isOther"),
                    optional = value.flag("allowEmpty"),
                    choices = options.toImmutableList(),
                    markdown = false,
                )
            }
        }
        val question = PendingQuestion(RequestId(id), SessionId(appId), prompts.toImmutableList())
        val canonical = question.toString()
        if (canonical.length > 128 * 1024) return null
        return Question(host.epoch, question, canonical, keys, plan)
    }

    private fun currentQuestion(sessionId: String, requestId: String): Question {
        val pending = questions[requestId]
        if (pending == null || pending.question.sessionId.raw != sessionId || !host.connected || pending.epoch != host.epoch) {
            paseoFail(PaseoFailureKind.StaleRequest)
        }
        return pending
    }

    /**
     * Sends exact typed answers: one list per prompt, each value a choice label or (where custom input is allowed)
     * the person's text. Claude is keyed by the full question text, Pi/OMP by header; several values join with ", ";
     * an optional empty answer is "". A plan accepts only `Approve` (→ `implement`) or `Keep planning` (→ `reject`,
     * no interrupt); `implement_resume` is never selected. Invalid answers throw InvalidResponse and send nothing.
     */
    public suspend fun answerQuestion(sessionId: String, requestId: String, answers: List<List<String>>) {
        val pending = currentQuestion(sessionId, requestId)
        val prompts = pending.question.prompts
        if (answers.size != prompts.size) paseoFail(PaseoFailureKind.InvalidResponse)
        val values = answers.mapIndexed { i, selected ->
            val prompt = prompts[i]
            if (selected.size > 64 ||
                (!prompt.multiple && selected.size > 1) ||
                selected.toSet().size != selected.size ||
                selected.any { it.length > 16384 || it.isBlank() } ||
                (selected.isEmpty() && !prompt.optional) ||
                (!prompt.custom && selected.any { v -> prompt.choices.none { it.label == v } })
            ) {
                paseoFail(PaseoFailureKind.InvalidResponse)
            }
            selected.joinToString(", ")
        }
        if (values.sumOf { it.length } > 128 * 1024) paseoFail(PaseoFailureKind.InvalidResponse)
        if (pending.plan) {
            val approve = values.single() == PLAN_APPROVE
            sendQuestionResponse(
                pending,
                jsonOf("behavior" to if (approve) "allow" else "deny", "selectedActionId" to if (approve) "implement" else "reject"),
                rejected = !approve,
            )
        } else {
            sendQuestionResponse(
                pending,
                jsonOf("behavior" to "allow", "updatedInput" to mapOf("answers" to pending.answerKeys.zip(values).toMap())),
                rejected = false,
            )
        }
    }

    /** Denies the question (a plan with `reject`); never an approval. */
    public suspend fun rejectQuestion(sessionId: String, requestId: String) {
        val pending = currentQuestion(sessionId, requestId)
        val response = if (pending.plan) jsonOf("behavior" to "deny", "selectedActionId" to "reject") else jsonOf("behavior" to "deny")
        sendQuestionResponse(pending, response, rejected = true)
    }

    private suspend fun sendQuestionResponse(pending: Question, response: JsonObject, rejected: Boolean) {
        val question = pending.question
        host.send(
            "agent_permission_response",
            jsonOf("agentId" to host.realId(question.sessionId.raw), "requestId" to question.id.raw, "response" to response),
            pending.epoch,
        )
        remember(answered, question.id.raw)
        resolveQuestion(question.id.raw, rejected)
    }

    private fun resolveQuestion(requestId: String, rejected: Boolean = false) {
        val removed = questions.remove(requestId) ?: return
        host.questionChanged(PaseoQuestionSignal.Settled(removed.question.sessionId, removed.question.id, rejected))
        changed()
    }

    private fun checkEpoch(epoch: Int) {
        if (!host.connected || epoch != host.epoch) paseoFail(PaseoFailureKind.StaleRequest)
    }

    private companion object {
        const val MAX_PENDING = 64
        const val MAX_REMEMBERED = 256
        const val PLAN_APPROVE = "Approve"
        const val PLAN_KEEP = "Keep planning"

        fun remember(set: LinkedHashSet<String>, id: String) {
            set += id
            while (set.size > MAX_REMEMBERED) set.remove(set.first())
        }

        fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true

        /** Key-sorted JSON text: equal requests have equal revisions whatever the daemon's key order. */
        fun canonical(value: JsonElement): String = PaseoWireJson.encodeToString(JsonElement.serializer(), sorted(value))

        fun sorted(value: JsonElement): JsonElement =
            when (value) {
                is JsonObject -> JsonObject(value.entries.sortedBy { it.key }.associate { it.key to sorted(it.value) })
                is JsonArray -> JsonArray(value.map(::sorted))
                else -> value
            }
    }
}

/**
 * The agent facts native questions need (port of the relevant fields of lib/domain/agent_tools/agent_tool_adapter.dart):
 * Claude Code answers by question text with AskUserQuestion/ExitPlanMode; Pi and Oh My Pi answer by header.
 */
internal class NativeQuestionAdapter(val keysByQuestionText: Boolean, val questionToolName: String?, val planToolName: String?) {
    companion object {
        private val CLAUDE = NativeQuestionAdapter(true, "AskUserQuestion", "ExitPlanMode")
        private val BY_HEADER = NativeQuestionAdapter(false, null, null)

        fun forProvider(provider: String?): NativeQuestionAdapter? =
            when (provider) {
                "claude" -> CLAUDE
                "pi", "omp" -> BY_HEADER
                else -> null
            }
    }
}
