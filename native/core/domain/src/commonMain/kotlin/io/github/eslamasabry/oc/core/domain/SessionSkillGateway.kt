package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.collections.immutable.ImmutableList

/** Session-targeted skills, with final generation admission immediately before the one mutation. */
public interface SessionSkillGateway {
    /**
     * False once the server proved it has no session skills. Kept off ServerCapabilities
     * because those flags mirror Dart's 86 exactly (CapabilitySnapshotTest).
     */
    public val sessionSkillsAvailable: Boolean

    public suspend fun sessionSkills(): Outcome<ImmutableList<SkillInfo>>

    public suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
        beforeSend: () -> Boolean,
    ): Outcome<Unit>
}
