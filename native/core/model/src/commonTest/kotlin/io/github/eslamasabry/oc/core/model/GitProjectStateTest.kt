package io.github.eslamasabry.oc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.collections.immutable.persistentListOf

class GitProjectStateTest {
    private fun health(branch: String?, setup: VersionControlSetupState) =
        VersionControlHealth(branch, null, persistentListOf(), setup)

    /**
     * One folder as each screen sees it. [health] is what the server's version-control read answers (OpenCode 2.2.6
     * `GET /vcs`: `provider:"git"` = Git, otherwise Unknown, measured 2026-11-11; Paseo answers Absent itself);
     * [rootHasDotGit] is the project root's listing; [gitPage] is what the Git page's own `git status` finds.
     */
    private data class Folder(
        val name: String,
        val health: VersionControlHealth?,
        val rootHasDotGit: Boolean?,
        val gitPage: Boolean?,
        val header: Boolean?,
        val changes: Boolean?,
    )

    private val folders =
        listOf(
            Folder("a repository with a branch", health("main", VersionControlSetupState.Git), true, true, true, true),
            Folder("a detached HEAD (no branch reported)", health(null, VersionControlSetupState.Git), true, true, true, true),
            Folder("a plain folder (2.2.6 only stays silent)", health(null, VersionControlSetupState.Unknown), false, false, null, false),
            Folder("a repository the server has not looked at yet", health(null, VersionControlSetupState.Unknown), true, true, null, true),
            Folder("a subfolder of a repository (root listed)", health("main", VersionControlSetupState.Git), true, true, true, true),
            Folder("a server that says Absent itself (Paseo)", health(null, VersionControlSetupState.Absent), false, false, false, false),
            Folder("not loaded yet / server cannot read Git", null, null, null, null, null),
        )

    @Test
    fun `one fixture, three screens: the header, Changes and the Git page never contradict each other`() {
        for (f in folders) {
            // The header reads only the server's answer; Changes and the new-chat start add the project root's listing.
            val header = gitProjectState(f.health)
            val changes = gitProjectState(f.health, rootHasDotGit = f.rootHasDotGit)
            assertEquals(f.header, header, "${f.name}: header")
            assertEquals(f.changes, changes, "${f.name}: Changes")
            // Never one screen saying Git while another says not Git.
            for ((a, b) in listOf(header to changes, header to f.gitPage, changes to f.gitPage))
                if (a != null && b != null) assertEquals(a, b, "${f.name}: two screens disagree")
            // The header never shows the word Git for a folder that is not (or not known to be) Git.
            if (f.gitPage != true) assertNotEquals(true, header, "${f.name}: header claims Git")
        }
    }

    @Test
    fun `the server's own Git or Absent wins over the project record and the listing`() {
        assertEquals(false, gitProjectState(health("main", VersionControlSetupState.Absent), projectIsGit = true, rootHasDotGit = true))
        assertEquals(true, gitProjectState(health(null, VersionControlSetupState.Git), projectIsGit = false, rootHasDotGit = false))
    }

    @Test
    fun `an unsure server falls back to the project record, a branch, then the root listing`() {
        assertEquals(false, gitProjectState(health(null, VersionControlSetupState.Unknown), projectIsGit = false))
        assertEquals(true, gitProjectState(health(null, VersionControlSetupState.Unknown), projectIsGit = true, rootHasDotGit = false))
        assertEquals(true, gitProjectState(health("dev", VersionControlSetupState.Unknown)))
        assertEquals(true, gitProjectState(null, rootHasDotGit = true))
        assertEquals(false, gitProjectState(null, rootHasDotGit = false))
        assertEquals(null, gitProjectState(health("  ", VersionControlSetupState.Unknown)))
    }
}
