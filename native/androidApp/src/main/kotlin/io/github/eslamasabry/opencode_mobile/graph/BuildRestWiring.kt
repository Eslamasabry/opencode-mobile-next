package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.ShadeReply
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import kotlinx.collections.immutable.persistentListOf

/**
 * BUILD-REST app wiring (one file per unit). B-PROTO-046: a notification Reply answers the exact request it was posted
 * for. On a question the typed text is the answer; on a permission it rejects with the text as the note (Dart
 * `connection_interaction_test`: "Notification Reply on an OpenCode 2 permission rejects with a message"). An empty
 * reply never reaches here (the shade does not deliver it).
 */
internal object ShadeReplyAnswers {
    fun of(kind: AlertKind, reply: ShadeReply): RequestAnswer =
        when (reply) {
            ShadeReply.AllowOnce -> RequestAnswer.Permission(PermissionReply.Once)
            ShadeReply.Reject ->
                if (kind == AlertKind.Question) RequestAnswer.Decline else RequestAnswer.Permission(PermissionReply.Reject)
            is ShadeReply.Text ->
                if (kind == AlertKind.Question) {
                    RequestAnswer.Question(persistentListOf(persistentListOf(reply.text)))
                } else {
                    RequestAnswer.Permission(PermissionReply.Reject, note = reply.text)
                }
        }
}

/**
 * B-BUILD-027: the prompt timeline (sent → accepted → first token → turn done) fed by the real send path and the live
 * stream of every connection (delivery's `PromptTracing` port). Spans carry ids and timings only.
 */
@dev.zacsweers.metro.ContributesIntoSet(dev.zacsweers.metro.AppScope::class, binding = dev.zacsweers.metro.binding<io.github.eslamasabry.oc.engine.delivery.PromptTracing>())
@dev.zacsweers.metro.Inject
// One method per step of the prompt timeline that the delivery port names; each only forwards to the timeline.
@Suppress("TooManyFunctions")
class AppPromptTracing(trace: io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace) :
    io.github.eslamasabry.oc.engine.delivery.PromptTracing {
    private val timeline = io.github.eslamasabry.oc.engine.diagnostics.perf.PromptTimeline(trace)

    /**
     * B-190: the chat journey (`send_result`, `chat_turn`, `turn_stall`, `first_reply`), built by the telemetry start-up
     * once the stored answers are adopted; before that, and with consent off, nothing is kept or sent.
     */
    private val journey: io.github.eslamasabry.oc.engine.analytics.health.ChatJourney?
        get() = io.github.eslamasabry.oc.engine.analytics.health.AppJourneys.chatOrNull

    override fun sent(sessionId: String) = timeline.sent(sessionId)

    override fun sent(sessionId: String, onPhone: Boolean) {
        timeline.sent(sessionId)
        journey?.sent(sessionId, onPhone)
    }

    override fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean) {
        timeline.sent(sessionId)
        journey?.sent(
            sessionId,
            onPhone,
            io.github.eslamasabry.oc.engine.analytics.health.PromptTraits(agent, hasAttachment),
        )
    }

    override fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean, connection: String) {
        timeline.sent(sessionId)
        journey?.sent(
            sessionId,
            onPhone,
            io.github.eslamasabry.oc.engine.analytics.health.PromptTraits(agent, hasAttachment),
            connection,
        )
    }

    override fun tool(sessionId: String, partId: String) {
        journey?.tool(sessionId, partId)
    }

    override fun stopped(sessionId: String) {
        journey?.stopped(sessionId)
    }

    override fun stream(onPhone: Boolean, connected: Boolean) {
        journey?.stream(onPhone, connected)
    }

    override fun stream(connection: String, onPhone: Boolean, connected: Boolean, dropReason: String?) {
        journey?.stream(connection, onPhone, connected, dropReason)
    }

    override fun error(sessionId: String, kind: String?, tag: String?, reason: String?) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Error(sessionId))
        journey?.ended(sessionId, turnErrorWord(kind, tag, reason))
    }

    override fun accepted(sessionId: String, error: Boolean) {
        timeline.accepted(sessionId, error)
        journey?.accepted(sessionId, error)
    }

    override fun message(sessionId: String, messageId: String, assistant: Boolean) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Message(sessionId, messageId, assistant))
        journey?.message(sessionId, messageId, assistant)
    }

    override fun part(sessionId: String, messageId: String, kind: String) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Part(sessionId, messageId, kind))
        journey?.part(sessionId, messageId, kind)
    }

    override fun delta(sessionId: String) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Delta(sessionId))
        journey?.delta(sessionId)
    }

    override fun delta(sessionId: String, field: io.github.eslamasabry.oc.core.domain.events.DeltaField) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Delta(sessionId))
        val kind = when (field) {
            io.github.eslamasabry.oc.core.domain.events.DeltaField.Text -> "text"
            io.github.eslamasabry.oc.core.domain.events.DeltaField.Reasoning -> "reasoning"
            io.github.eslamasabry.oc.core.domain.events.DeltaField.ToolInputRaw -> "tool"
        }
        journey?.delta(sessionId, kind)
    }

    override fun retrying(sessionId: String) { journey?.retrying(sessionId) }
    override fun runChecked(sessionId: String, busy: Boolean?) { journey?.runChecked(sessionId, busy) }
    override fun recovered(sessionId: String) { journey?.recovered(sessionId) }

    override fun text(sessionId: String, messageId: String, streamed: Boolean) {
        journey?.text(sessionId, messageId, streamed)
    }

    override fun busy(sessionId: String) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Busy(sessionId))
        journey?.busy(sessionId)
    }

    override fun idle(sessionId: String) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Idle(sessionId))
        journey?.ended(sessionId)
    }

    override fun error(sessionId: String) {
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Error(sessionId))
        journey?.ended(sessionId, "server")
    }
}

private val FREE_LIMIT_TAGS = setOf("provider.free-limit", "provider.go-limit", "provider.quota")

private fun tagErrorWord(tag: String?): String? {
    val normalized = tag?.trim()?.lowercase().orEmpty()
    return when {
        normalized in FREE_LIMIT_TAGS -> "free_limit"
        normalized.startsWith("provider.") && normalized.endsWith("budget-limit") -> "free_limit"
        normalized == "provider.no-route" -> "model_not_found"
        else -> null
    }
}

/**
 * The closed word for a failed turn: symbolic provider limit/no-route tags read first; otherwise the server's own
 * words are classified (rate limit, no credit, sign-in, model not offered, provider connection) and dropped; only the
 * word goes on. Falls back to the error kind and symbolic tag.
 */
internal fun turnErrorWord(kind: String?, tag: String?, reason: String?): String {
    val word =
        tagErrorWord(tag)
            ?: when (io.github.eslamasabry.oc.engine.transcript.turnfacts.classifyAgentRunFailure(reason)) {
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.SignIn -> "provider_auth"
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.NoCredit -> "free_limit"
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.Busy -> "rate_limit"
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.ModelUnavailable ->
                    "model_not_found"
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.ConnectionLost ->
                    "provider_connection"
                io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.Unknown -> null
            }
    return word ?: io.github.eslamasabry.oc.engine.analytics.health.ChatJourney.errorKindWord(kind, tag)
}
