package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import kotlinx.collections.immutable.toImmutableList
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionReply

/** The home slip answers the captured conversation's current request, never the active server. */
@Inject
@ContributesIntoSet(AppScope::class)
class HomePermissionAnswerWiring(private val registry: ConnectionRegistry) : HomePermissionAnswers {
    override suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        allow: Boolean,
    ): Outcome<Unit> = answer(ref, requestId, if (allow) HomePermissionReply.Once else HomePermissionReply.Reject)

    override suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        reply: HomePermissionReply,
    ): Outcome<Unit> {
        val graph =
            registry.graphFor(ref)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val engine = (graph as ChatGraphAccessors).chatRequests.engine
        val request =
            engine.pending.value.filterIsInstance<PendingRequest.Permission>().singleOrNull {
                it.request.id.raw == requestId &&
                    it.request.sessionId == ref.sessionId &&
                    it.phase == AnswerPhase.Waiting
            } ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        if (registry.graphFor(ref) !== graph)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        // Always only where the server keeps the grant and this request can be granted for good (as the chat's card).
        if (
            reply == HomePermissionReply.Always &&
                !(graph.session.server.capabilities.value.persistentPermissionGrants && request.request.canAlwaysAllow)
        )
            return Outcome.Failed(Failure.Unavailable(FeatureId("persistentPermissionGrants")))
        return engine.answerNow(
            request.request.id,
            RequestAnswer.Permission(
                when (reply) {
                    HomePermissionReply.Once -> PermissionReply.Once
                    HomePermissionReply.Always -> PermissionReply.Always
                    HomePermissionReply.Reject -> PermissionReply.Reject
                }
            ),
        )
    }

    /**
     * H1: the same engine path the chat's question card sends through (`answerNow`: one claim, one write, the
     * request's own checks), for the captured conversation only.
     */
    override suspend fun answerQuestion(
        ref: ConversationRef,
        requestId: String,
        answers: List<List<String>>,
    ): Outcome<Unit> {
        val graph =
            registry.graphFor(ref)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val engine = (graph as ChatGraphAccessors).chatRequests.engine
        val request =
            engine.pending.value.filterIsInstance<PendingRequest.Question>().singleOrNull {
                it.question.id.raw == requestId &&
                    it.sessionId == ref.sessionId &&
                    (it.phase == AnswerPhase.Waiting || it.phase is AnswerPhase.NotAccepted)
            } ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        if (registry.graphFor(ref) !== graph)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return engine.answerNow(
            request.question.id,
            RequestAnswer.Question(answers.map { it.toImmutableList() }.toImmutableList()),
        )
    }
}
