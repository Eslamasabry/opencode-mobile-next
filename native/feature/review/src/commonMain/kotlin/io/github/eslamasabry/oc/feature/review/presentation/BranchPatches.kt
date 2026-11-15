package io.github.eslamasabry.oc.feature.review.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.FileDiff
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The whole branch of a conversation's project read through its Git (the phone's own Linux), for a connection whose
 * server cannot diff a branch itself (the phone agents' helper). The answer is one patch.
 */
public fun interface ReviewGitBranch {
    public suspend fun patch(ref: ConversationRef): Outcome<String>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoReviewGitBranch : ReviewGitBranch {
    override suspend fun patch(ref: ConversationRef): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("gitBranch")))
}

/** A multi-file `git diff` as one [FileDiff] per file, with the counts of its added and removed lines. */
public object BranchPatches {
    public fun split(patch: String): ImmutableList<FileDiff> {
        if (patch.isBlank()) return persistentListOf()
        val parts = ArrayList<MutableList<String>>()
        for (line in patch.split('\n')) {
            if (line.startsWith("diff --git ")) parts += mutableListOf(line) else parts.lastOrNull()?.add(line)
        }
        return parts
            .mapNotNull { lines ->
                val path = pathOf(lines) ?: return@mapNotNull null
                val text = lines.joinToString("\n").trimEnd('\n')
                var added = 0
                var removed = 0
                var inHunk = false
                for (l in lines) {
                    if (l.startsWith("@@")) inHunk = true
                    else if (inHunk && l.startsWith("+")) added++
                    else if (inHunk && l.startsWith("-")) removed++
                }
                FileDiff(path, patch = text, additions = added, deletions = removed)
            }
            .toImmutableList()
    }

    /** The file's new path: the `+++ b/…` line, else the `diff --git a/x b/x` header (deleted files use the old one). */
    private fun pathOf(lines: List<String>): String? {
        lines.firstOrNull { it.startsWith("+++ b/") }?.let { return it.removePrefix("+++ b/") }
        val header = lines.first().removePrefix("diff --git ")
        val half = header.length / 2
        return header.takeIf { it.length > 3 }?.substring(half + 1)?.removePrefix("b/")?.takeIf { it.isNotEmpty() }
    }
}
