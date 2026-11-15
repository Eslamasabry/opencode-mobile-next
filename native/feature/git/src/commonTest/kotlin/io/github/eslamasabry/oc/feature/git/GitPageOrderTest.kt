package io.github.eslamasabry.oc.feature.git

import io.github.eslamasabry.oc.feature.git.presentation.GitSection
import io.github.eslamasabry.oc.feature.git.presentation.gitSections
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
