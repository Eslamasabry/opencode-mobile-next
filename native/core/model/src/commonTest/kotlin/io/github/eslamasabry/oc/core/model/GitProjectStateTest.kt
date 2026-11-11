package io.github.eslamasabry.oc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class GitProjectStateTest {
    private fun health(branch: String?, setup: VersionControlSetupState) =
        VersionControlHealth(branch, null, persistentListOf(), setup)

    @Test
    fun `a repository with a branch is a Git project`() =
        assertEquals(true, gitProjectState(health("main", VersionControlSetupState.Git)))

    @Test
    fun `a repository with no branch reported is still a Git project`() =
        assertEquals(true, gitProjectState(health(null, VersionControlSetupState.Git)))

    @Test
    fun `a folder the server says is not a repository is not one, whatever else is guessed`() {
        assertEquals(false, gitProjectState(health(null, VersionControlSetupState.Absent)))
        assertEquals(false, gitProjectState(health("main", VersionControlSetupState.Absent), projectIsGit = true))
    }

    @Test
    fun `not loaded yet or not said is unknown, never Git`() {
        assertEquals(null, gitProjectState(null))
        assertEquals(null, gitProjectState(health(null, VersionControlSetupState.Unknown)))
        assertEquals(null, gitProjectState(health("  ", VersionControlSetupState.Unknown)))
    }

    @Test
    fun `an unsure server falls back to the project record, a branch or a git entry`() {
        assertEquals(false, gitProjectState(health(null, VersionControlSetupState.Unknown), projectIsGit = false))
        assertEquals(true, gitProjectState(health("dev", VersionControlSetupState.Unknown)))
        assertEquals(true, gitProjectState(null, hasDotGit = true))
    }
}
