package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSkillGateway
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A captured graph and history boundary. Each callback checks precisely the target that the person
 * opened.
 */
public class SkillTarget(
    public val server: ServerGateway,
    public val skills: SessionSkillGateway,
    public val current: () -> Boolean,
    public val historyRevision: () -> Long,
    public val revertPending: () -> Boolean,
    public val refreshed: suspend () -> Unit,
)

/**
 * Shared per-conversation mutation owner; concurrent activations refuse rather than waiting and
 * sending twice.
 */
public class ConversationSkills {
    private val admission = Mutex()
    private val inFlight = mutableSetOf<ConversationRef>()

    public suspend fun list(target: SkillTarget): Outcome<List<SkillInfo>> {
        if (!target.current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val read = target.skills.sessionSkills()
        if (!target.current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return read.map {
            it.filter { s -> !s.id.isNullOrBlank() }.map { s -> s.copy(content = "") }
        }
    }

    public suspend fun activate(
        ref: ConversationRef,
        target: SkillTarget,
        skill: String,
    ): SkillActivation {
        if (!target.current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
        if (!admission.withLock { inFlight.add(ref) })
            return SkillActivation.Refused(SessionSkillFailure.Busy)
        try {
            val revision = target.historyRevision()
            fun current() = target.current() && target.historyRevision() == revision
            val fresh = target.server.session(ref.sessionId)
            if (!current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
            if (fresh is Outcome.Failed) return SkillActivation.Failed(fresh.failure)
            val session = (fresh as Outcome.Ok).value
            if (session.reverted || session.stagedRevert != null || target.revertPending())
                return SkillActivation.Refused(SessionSkillFailure.Staged)
            val sent =
                target.skills.activateSessionSkill(ref.sessionId, skill, resume = false) {
                    current() && !target.revertPending()
                }
            if (sent is Outcome.Failed)
                return if (sent.failure is Failure.Unavailable)
                    SkillActivation.Refused(SessionSkillFailure.Unsupported)
                else SkillActivation.Failed(sent.failure)
            if (!current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
            target.refreshed()
            return SkillActivation.Done
        } finally {
            withContext(NonCancellable) { admission.withLock { inFlight.remove(ref) } }
        }
    }
}
