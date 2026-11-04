package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.collections.immutable.ImmutableList

/**
 * Session skills. Two units added this facet: PORT-SCREENS-2 (the library's activation, [sessionSkillsSupported])
 * and STATE (the composer's catalogue with a send fence, [sessionSkillsAvailable]). One interface carries both.
 */
public interface SessionSkillGateway {
    public val sessionSkillsSupported: Boolean

    /**
     * False once the server proved it has no session skills. Kept off ServerCapabilities
     * because those flags mirror Dart's 86 exactly (CapabilitySnapshotTest).
     */
    public val sessionSkillsAvailable: Boolean
        get() = sessionSkillsSupported

    public suspend fun sessionSkills(): Outcome<ImmutableList<SkillInfo>> = unavailable("sessionSkills")

    /**
     * Dispatch once to the named conversation; an uncertain result must never be automatically
     * retried.
     */
    public suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ): Outcome<Unit>

    /** The same dispatch behind a last-moment fence: [beforeSend] false sends nothing. */
    public suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> =
        if (!beforeSend()) Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope))
        else activateSessionSkill(session, skillId, resume)
}
