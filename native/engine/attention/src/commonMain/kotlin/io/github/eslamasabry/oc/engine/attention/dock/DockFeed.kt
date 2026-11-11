package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.SubAgents
import io.github.eslamasabry.oc.core.domain.SubAgentsRead
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Says whose child a session is. The dock does not care how a provider knows: the default reads the session record's
 * parent id; a provider-neutral source can be swapped in without touching the model.
 */
public fun interface DockChildSource {
    public fun links(profile: ProfileId, event: GatewayEvent): List<DockEvent.Link>
}

/**
 * The dock's sub-agents from the app's one provider-neutral source (`core/domain` [SubAgents]): a live session event
 * links a child to its parent by the same rule the sub-agent lists use (a parent, not a fork), and a backfill read
 * ([SubAgentsRead], from `SubAgentBackfill`) names the children that existed before the app connected, which no event
 * ever announces.
 */
public object SubAgentChildSource : DockChildSource {
    override fun links(profile: ProfileId, event: GatewayEvent): List<DockEvent.Link> {
        val session = (event as? GatewayEvent.SessionUpserted)?.session ?: return emptyList()
        val parent = session.parentId?.takeIf { SubAgents.isSubAgent(session) } ?: return emptyList()
        return listOf(DockEvent.Link(ConversationRef(profile, session.id), parent))
    }

    /** One backfill read as dock events: each sub-agent's title and name, then its parent. */
    public fun events(profile: ProfileId, read: SubAgentsRead): List<DockEvent> =
        read.subAgents.flatMap { s ->
            val ref = ConversationRef(profile, s.id)
            listOf(DockEvent.Meta(ref, s.title, s.name.takeIf { it != s.title }), DockEvent.Link(ref, s.parent))
        }
}

/**
 * Turns the facts the app already receives (the neutral gateway event stream and the reconcile reads) into
 * [DockEvent]s. Pure: it adds no network call and no polling. Request ids are prefixed by kind so a permission and a
 * question that happen to share an id never settle each other.
 */
public object DockFeed {
    private const val GLOBAL_FORM = "global"
    private val TODO_TOOLS = setOf("todowrite", "todo")

    public fun events(profile: ProfileId, event: GatewayEvent, children: DockChildSource = SubAgentChildSource): List<DockEvent> = base(profile, event) + children.links(profile, event)

    private fun base(profile: ProfileId, event: GatewayEvent): List<DockEvent> {
        fun ref(session: SessionId) = ConversationRef(profile, session)
        return when (event) {
            is GatewayEvent.SessionUpserted ->
                listOf(
                    DockEvent.Meta(ref(event.session.id), event.session.title, event.session.agent),
                    DockEvent.LastRunFailed(ref(event.session.id), event.session.lastRunFailed),
                )
            is GatewayEvent.SessionChanged ->
                if (event.title != null) listOf(DockEvent.Meta(ref(event.sessionId), event.title, null)) else emptyList()
            is GatewayEvent.SessionDeleted -> listOf(DockEvent.Deleted(ref(event.sessionId)))
            is GatewayEvent.RunStarted -> listOf(DockEvent.RunStarted(ref(event.sessionId)))
            is GatewayEvent.RunRetrying -> listOf(DockEvent.RunStarted(ref(event.sessionId)))
            is GatewayEvent.RunEnded ->
                listOf(
                    DockEvent.RunEnded(
                        ref(event.sessionId),
                        when (event.outcome) {
                            is RunOutcome.Failed -> DockRunEnd.Failed
                            is RunOutcome.Interrupted -> DockRunEnd.Stopped
                            RunOutcome.Succeeded, RunOutcome.Idle -> DockRunEnd.Succeeded
                        },
                    ),
                )
            is GatewayEvent.MessageUpserted ->
                // The agent that answered names the ring's mark when the session record has not said.
                if (event.message.role == io.github.eslamasabry.oc.core.model.transcript.Role.User) {
                    // The person's own message: a send. The dock tucks if that conversation is the one on screen.
                    listOf(DockEvent.Sent(ref(SessionId(event.message.sessionId))))
                } else {
                    event.message.agent?.takeIf { it.isNotBlank() && event.message.role == io.github.eslamasabry.oc.core.model.transcript.Role.Assistant }
                        ?.let { listOf(DockEvent.Meta(ref(SessionId(event.message.sessionId)), null, it)) }
                        .orEmpty()
                }
            is GatewayEvent.PartUpserted -> partEvents(profile, event.part)
            is GatewayEvent.PartDelta ->
                listOf(DockEvent.Reasoning(ref(event.sessionId), event.field == DeltaField.Reasoning))
            is GatewayEvent.PermissionAsked -> listOf(DockEvent.RequestOpened(ref(event.request.sessionId), permissionKey(event.request.id.raw)))
            is GatewayEvent.PermissionSettled -> listOf(DockEvent.RequestSettled(ref(event.sessionId), permissionKey(event.requestId.raw)))
            is GatewayEvent.QuestionAsked -> listOf(DockEvent.RequestOpened(ref(event.question.sessionId), questionKey(event.question.id.raw)))
            is GatewayEvent.QuestionSettled -> listOf(DockEvent.RequestSettled(ref(event.sessionId), questionKey(event.requestId.raw)))
            is GatewayEvent.FormCreated ->
                // "global" marks MCP elicitation forms: they belong to no conversation.
                if (event.form.sessionId.raw == GLOBAL_FORM) emptyList() else listOf(DockEvent.RequestOpened(ref(event.form.sessionId), formKey(event.form.id.raw)))
            is GatewayEvent.FormSettled -> listOf(DockEvent.RequestSettled(ref(event.sessionId), formKey(event.formId.raw)))
            else -> emptyList()
        }
    }

    /** The reconcile read after every Connected: the waiting permissions, questions and forms of one server. */
    public fun requestsRead(
        profile: ProfileId,
        permissions: List<PermissionRequest>?,
        questions: List<PendingQuestion>?,
        forms: List<FormSpec>? = null,
    ): DockEvent.RequestsRead? {
        // A read that failed says nothing; a partial picture would clear requests the server still holds.
        if (permissions == null || questions == null) return null
        val by = mutableMapOf<SessionId, MutableSet<String>>()
        permissions.forEach { by.getOrPut(it.sessionId) { mutableSetOf() } += permissionKey(it.id.raw) }
        questions.forEach { by.getOrPut(it.sessionId) { mutableSetOf() } += questionKey(it.id.raw) }
        forms?.filter { it.sessionId.raw != GLOBAL_FORM }?.forEach { by.getOrPut(it.sessionId) { mutableSetOf() } += formKey(it.id.raw) }
        return DockEvent.RequestsRead(profile, by)
    }

    public fun statusesRead(profile: ProfileId, statuses: Map<SessionId, SessionStatus>): DockEvent.StatusesRead =
        DockEvent.StatusesRead(
            profile,
            statuses.filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }.keys,
        )

    private fun partEvents(profile: ProfileId, part: Part): List<DockEvent> {
        val ref = ConversationRef(profile, SessionId(part.sessionId))
        val out = mutableListOf<DockEvent>()
        taskProgress(part)?.let { (done, total) -> out += DockEvent.Tasks(ref, done, total) }
        out += DockEvent.Reasoning(ref, part.type == PartType.Reasoning)
        return out
    }

    /**
     * Done and total of a todo tool part, counted like the chat's task list (completed of everything not cancelled).
     * Null when the part is not a todo list. The chat's own parser is stricter; the dock only needs the two numbers.
     */
    public fun taskProgress(part: Part): Pair<Int, Int>? {
        if (part.type != PartType.Tool || part.toolName !in TODO_TOOLS) return null
        val tool = part.tool ?: return null
        val todos = (tool.metadata?.get("todos") ?: tool.input["todos"]) as? JsonArray ?: return null
        var done = 0
        var total = 0
        for (item in todos) {
            val status = ((item as? JsonObject)?.get("status") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (status == "cancelled") continue
            total++
            if (status == "completed") done++
        }
        return done to total
    }

    private fun permissionKey(id: String) = "permission:$id"

    private fun questionKey(id: String) = "question:$id"

    private fun formKey(id: String) = "form:$id"
}
