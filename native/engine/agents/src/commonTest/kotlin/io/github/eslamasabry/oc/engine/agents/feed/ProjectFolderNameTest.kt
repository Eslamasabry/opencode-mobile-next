package io.github.eslamasabry.oc.engine.agents.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-STATE-158, ported from Flutter test/workspace_paths_test.dart `projectFolderNameProblem`. */
class ProjectFolderNameTest {
    @Test
    fun `B-STATE-158 one safe segment is accepted, trimmed`() {
        assertNull(ProjectFolderName.problem("my-app"))
        assertNull(ProjectFolderName.problem("App_2.0"))
        assertNull(ProjectFolderName.problem(" app "))
        assertEquals("app", ProjectFolderName.clean(" app "))
        assertNull(ProjectFolderName.problem("x".repeat(64)))
    }

    @Test
    fun `B-STATE-158 empty, paths, hidden names, spaces and long names get a plain reason`() {
        assertEquals(FolderNameProblem.Empty, ProjectFolderName.problem(""))
        assertEquals(FolderNameProblem.Empty, ProjectFolderName.problem("   "))
        assertEquals(FolderNameProblem.NotOneFolder, ProjectFolderName.problem(".."))
        assertEquals(FolderNameProblem.NotOneFolder, ProjectFolderName.problem("."))
        assertEquals(FolderNameProblem.NotOneFolder, ProjectFolderName.problem("a/b"))
        assertEquals(FolderNameProblem.NotOneFolder, ProjectFolderName.problem("a\\b"))
        assertEquals(FolderNameProblem.BadCharacters, ProjectFolderName.problem(".git"))
        assertEquals(FolderNameProblem.BadCharacters, ProjectFolderName.problem("my app"))
        assertEquals(FolderNameProblem.BadCharacters, ProjectFolderName.problem("x".repeat(65)))
        assertEquals(FolderNameProblem.BadCharacters, ProjectFolderName.problem("-dash-first"))
    }
}
