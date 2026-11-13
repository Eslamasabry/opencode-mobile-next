package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a waiting request says, in words that are safe to show: secrets are already masked and the text is one short
 * line. The UI composes the sentence ("Asks to edit: wait7.txt", "Asks: ..."), so no copy lives here.
 */
public data class DockRequestWords(
    val kind: DockRequestKind,
    val id: String,
    /** A permission's action token: `run`, `edit`, `read` or `folder`; empty when the ask names none or for a question or form. */
    val action: String,
    /** A permission's short target (a command head or a file name); empty when none. */
    val target: String,
    /** A question's or form's one-line text; empty for a permission. */
    val text: String,
)

/**
 * The facts about a conversation that the dock's reducer does not keep because they need a clock or a request's content:
 * when it last did anything, and the words of what it asks. Fed from the same event stream and reconcile reads the dock
 * already follows (no read of its own). Memory only and bounded. Call from the one dispatcher the dock runs on.
 */
public class DockFacts(private val now: () -> Long) {
    private val heard = LinkedHashMap<ConversationRef, Long>()
    private val listedAt = HashMap<ConversationRef, Long>()
    private val words = LinkedHashMap<Pair<ConversationRef, DockRequestRef>, DockRequestWords>()

    /** The activity the stream just showed, stamped with the clock, and the words of a request that opened or settled. */
    public fun feed(profile: ProfileId, event: GatewayEvent) {
        fun ref(session: SessionId) = ConversationRef(profile, session)
        when (event) {
            is GatewayEvent.SessionUpserted -> touch(ref(event.session.id))
            is GatewayEvent.RunStarted -> touch(ref(event.sessionId))
            is GatewayEvent.RunRetrying -> touch(ref(event.sessionId))
            is GatewayEvent.RunEnded -> touch(ref(event.sessionId))
            is GatewayEvent.MessageUpserted -> touch(ref(SessionId(event.message.sessionId)))
            is GatewayEvent.PartUpserted -> touch(ref(SessionId(event.part.sessionId)))
            is GatewayEvent.PermissionAsked -> {
                touch(ref(event.request.sessionId))
                remember(ref(event.request.sessionId), permissionWords(event.request))
            }
            is GatewayEvent.QuestionAsked -> {
                touch(ref(event.question.sessionId))
                remember(ref(event.question.sessionId), questionWords(event.question))
            }
            is GatewayEvent.FormCreated -> {
                if (event.form.sessionId.raw != GLOBAL_FORM) {
                    touch(ref(event.form.sessionId))
                    remember(ref(event.form.sessionId), formWords(event.form))
                }
            }
            is GatewayEvent.PermissionSettled -> settle(ref(event.sessionId), DockRequestRef(DockRequestKind.Permission, event.requestId.raw))
            is GatewayEvent.QuestionSettled -> settle(ref(event.sessionId), DockRequestRef(DockRequestKind.Question, event.requestId.raw))
            is GatewayEvent.FormSettled -> settle(ref(event.sessionId), DockRequestRef(DockRequestKind.Form, event.formId.raw))
            is GatewayEvent.SessionDeleted -> forget(ref(event.sessionId))
            else -> Unit
        }
    }

    /** The reconcile read of one server's waiting requests: replaces the words held for that server. */
    public fun requestsRead(
        profile: ProfileId,
        permissions: List<PermissionRequest>?,
        questions: List<PendingQuestion>?,
        forms: List<FormSpec>? = null,
    ) {
        if (permissions == null || questions == null) return
        words.keys.removeAll { (ref, request) ->
            ref.profileId == profile && (forms != null || request.kind != DockRequestKind.Form)
        }
        permissions.forEach { remember(ConversationRef(profile, it.sessionId), permissionWords(it)) }
        questions.forEach { remember(ConversationRef(profile, it.sessionId), questionWords(it)) }
        forms?.filter { it.sessionId.raw != GLOBAL_FORM }?.forEach { remember(ConversationRef(profile, it.sessionId), formWords(it)) }
    }

    /** The home list's rows of a server: the listed last activity (epoch ms) of conversations the dock has not heard from. */
    public fun listed(profile: ProfileId, rows: List<DockRecent>) {
        for (row in rows) listedAt[ConversationRef(profile, row.session)] = row.lastActivityMs
    }

    /** When [ref] last did anything, epoch ms: the newer of what the stream showed and what the home list says. Null when unknown. */
    public fun lastActivity(ref: ConversationRef): Long? {
        val live = heard[ref]
        val saved = listedAt[ref]
        return if (live != null && saved != null) maxOf(live, saved) else live ?: saved
    }

    /** What [request] of [ref] says, masked; null when the dock has not seen its content. */
    public fun requestWords(ref: ConversationRef, request: DockRequestRef): DockRequestWords? = words[ref to request]

    public fun forget(ref: ConversationRef) {
        heard.remove(ref)
        listedAt.remove(ref)
        words.keys.removeAll { it.first == ref }
    }

    public fun forgetProfile(profile: ProfileId) {
        heard.keys.removeAll { it.profileId == profile }
        listedAt.keys.removeAll { it.profileId == profile }
        words.keys.removeAll { it.first.profileId == profile }
    }

    private fun touch(ref: ConversationRef) {
        heard.remove(ref)
        heard[ref] = now()
        while (heard.size > MAX_CONVERSATIONS) heard.remove(heard.keys.first())
    }

    private fun remember(ref: ConversationRef, entry: DockRequestWords) {
        val key = ref to DockRequestRef(entry.kind, entry.id)
        words.remove(key)
        words[key] = entry
        while (words.size > MAX_REQUESTS) words.remove(words.keys.first())
    }

    private fun settle(ref: ConversationRef, request: DockRequestRef) {
        words.remove(ref to request)
    }

    private fun permissionWords(request: PermissionRequest): DockRequestWords {
        val metadata =
            request.metadata.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
            }.toMap()
        val ask = NotificationText.permissionAskOf(NotificationText.PermissionAsk(request.permission, metadata, request.patterns.toList()))
        return DockRequestWords(DockRequestKind.Permission, request.id.raw, ask.action, ask.target, "")
    }

    private fun questionWords(question: PendingQuestion): DockRequestWords {
        val first = question.prompts.firstOrNull()
        val line = first?.question?.takeIf { it.isNotBlank() } ?: first?.title.orEmpty()
        return DockRequestWords(DockRequestKind.Question, question.id.raw, "", "", NotificationText.redact(line))
    }

    private fun formWords(form: FormSpec): DockRequestWords =
        DockRequestWords(DockRequestKind.Form, form.id.raw, "", "", NotificationText.redact(form.title.orEmpty()))

    private companion object {
        const val GLOBAL_FORM = "global"
        const val MAX_CONVERSATIONS = 256
        const val MAX_REQUESTS = 128
    }
}
