package io.github.eslamasabry.oc.feature.git

import io.github.eslamasabry.oc.feature.git.presentation.GitSection
import io.github.eslamasabry.oc.feature.git.presentation.gitPullLinksBranch
import io.github.eslamasabry.oc.feature.git.presentation.gitSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-GIT-147 the Git page puts what a person came for first (issue 147 walk F-021). */
class GitPageOrderTest {
    @Test
    fun `B-GIT-147 changed files, then Save, then the result, then sync, then history, rare tools last`() {
        assertEquals(
            listOf(
                GitSection.Notices,
                GitSection.Changes,
                GitSection.Save,
                GitSection.Result,
                GitSection.Sync,
                GitSection.History,
                GitSection.Tools,
            ),
            gitSections(hasChanges = true, hasHistory = true),
        )
    }

    @Test
    fun `B-GIT-147 with nothing changed there is no Save, and tools are still last`() {
        assertEquals(
            listOf(GitSection.Notices, GitSection.Changes, GitSection.Result, GitSection.Sync, GitSection.Tools),
            gitSections(hasChanges = false, hasHistory = false),
        )
    }

    @Test
    fun `B-GIT-147 a name form with nothing changed still has its place, before sync`() {
        assertEquals(
            listOf(GitSection.Notices, GitSection.Changes, GitSection.Save, GitSection.Result, GitSection.Sync, GitSection.Tools),
            gitSections(hasChanges = false, hasHistory = false, needsIdentity = true),
        )
    }

    @Test
    fun `B-GIT-157 Get latest is open when the remote has the same-named branch`() {
        assertTrue(gitPullLinksBranch(false, "main", "origin", listOf("origin/main")))
        assertFalse(gitPullLinksBranch(false, "main", "origin", listOf("origin/dev")))
        assertFalse(gitPullLinksBranch(false, null, "origin", listOf("origin/main")))
        assertFalse(gitPullLinksBranch(false, "main", null, listOf("origin/main")))
        assertFalse(gitPullLinksBranch(true, "main", "origin", listOf("origin/main")))
    }
}

class GitSyncRowsTest {
    @kotlin.test.Test
    fun `B-GIT-147 Get latest and Send both open Remotes while there is no remote, and neither otherwise`() {
        kotlin.test.assertTrue(io.github.eslamasabry.oc.feature.git.presentation.gitSyncOpensRemotes(false, false, false))
        kotlin.test.assertFalse(io.github.eslamasabry.oc.feature.git.presentation.gitSyncOpensRemotes(true, false, false))
        kotlin.test.assertFalse(io.github.eslamasabry.oc.feature.git.presentation.gitSyncOpensRemotes(false, true, false))
        kotlin.test.assertFalse(io.github.eslamasabry.oc.feature.git.presentation.gitSyncOpensRemotes(false, false, true))
    }
}
