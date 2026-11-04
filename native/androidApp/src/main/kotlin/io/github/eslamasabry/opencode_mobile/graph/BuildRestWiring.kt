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
class AppPromptTracing(trace: io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace) :
    io.github.eslamasabry.oc.engine.delivery.PromptTracing {
    private val timeline = io.github.eslamasabry.oc.engine.diagnostics.perf.PromptTimeline(trace)

    override fun sent(sessionId: String) = timeline.sent(sessionId)

    override fun accepted(sessionId: String, error: Boolean) = timeline.accepted(sessionId, error)

    override fun message(sessionId: String, messageId: String, assistant: Boolean) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Message(sessionId, messageId, assistant))

    override fun part(sessionId: String, messageId: String, kind: String) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Part(sessionId, messageId, kind))

    override fun delta(sessionId: String) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Delta(sessionId))

    override fun busy(sessionId: String) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Busy(sessionId))

    override fun idle(sessionId: String) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Idle(sessionId))

    override fun error(sessionId: String) =
        timeline.observe(io.github.eslamasabry.oc.engine.diagnostics.perf.PromptSignal.Error(sessionId))
}
