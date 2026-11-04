package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

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

    public fun openTermuxSettings()
}
