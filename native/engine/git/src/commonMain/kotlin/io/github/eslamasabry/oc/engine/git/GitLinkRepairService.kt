package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId

public enum class GitLinkRepairResult {
    Healthy,
    Repaired,
    MissingObjects,
    TermuxMissing,
    TermuxPermission,
    TermuxUnavailable,
    NotRepository,
}

public data class GitLinkRepairReport(
    val result: GitLinkRepairResult,
    val restored: Int = 0,
    val missing: List<String> = emptyList(),
)

/** A repair is explicitly requested for this conversation's current phone project. */
public interface GitLinkRepairService {
    public fun available(ref: ConversationRef): Boolean

    public suspend fun repair(ref: ConversationRef, directory: String): Outcome<GitLinkRepairReport>

    /** Explicit recovery of a selected phone project before a conversation could be created. */
    public fun availableProject(profile: ProfileId, directory: String): Boolean = false

    public suspend fun repairProject(
        profile: ProfileId,
        directory: String,
    ): Outcome<GitLinkRepairReport> =
        Outcome.Failed(Failure.Unavailable(FeatureId("gitLinkRepair")))

    public fun openTermuxSettings()
}
