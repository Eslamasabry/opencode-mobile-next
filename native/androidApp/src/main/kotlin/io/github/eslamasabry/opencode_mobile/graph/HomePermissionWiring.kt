package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionAnswers

/** The home slip answers the captured conversation's current request, never the active server. */
@Inject
@ContributesIntoSet(AppScope::class)
class HomePermissionAnswerWiring(private val registry: ConnectionRegistry) : HomePermissionAnswers {
    override suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        allow: Boolean,
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
        return engine.answerNow(
            request.request.id,
            RequestAnswer.Permission(if (allow) PermissionReply.Once else PermissionReply.Reject),
        )
    }
}
