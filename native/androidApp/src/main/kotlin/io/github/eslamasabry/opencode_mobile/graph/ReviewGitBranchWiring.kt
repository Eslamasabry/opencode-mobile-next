package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.git.GitDiffResult
import io.github.eslamasabry.oc.engine.git.GitServices
import io.github.eslamasabry.oc.feature.review.presentation.NoReviewGitBranch
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGitBranch

/**
 * The Changes page's Whole branch for a conversation whose server cannot diff a branch (a phone agent's helper): the
 * project's own Git, through the Git page's workspace and its phone ownership rule.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoReviewGitBranch::class])
@Inject
class AndroidReviewGitBranch(private val git: GitServices) : ReviewGitBranch {
    override suspend fun patch(ref: ConversationRef): Outcome<String> =
        when (val read = git.branchPatch(ref)) {
            is GitDiffResult.Ready -> Outcome.Ok(read.patch)
            is GitDiffResult.Failed ->
                Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("gitBranch")))
        }
}
