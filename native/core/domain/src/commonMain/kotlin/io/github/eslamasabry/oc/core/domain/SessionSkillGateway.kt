package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * Optional session-scoped skill activation. Unsupported replies retire this facet for the live
 * gateway.
 */
public interface SessionSkillGateway {
    public val sessionSkillsSupported: Boolean

    /**
     * Dispatch once to the named conversation; an uncertain result must never be automatically
     * retried.
     */
    public suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ): Outcome<Unit>
}
