package io.github.eslamasabry.oc.engine.agents.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectFolderNameTest {
    private fun words(name: String) = ProjectFolderName.problem(name)?.let(ProjectFolderNameWords::en)

    @Test
    fun `B-STATE-158 accepts one safe segment`() {
        assertNull(ProjectFolderName.problem("my-app"))
        assertNull(ProjectFolderName.problem("App_2.0"))
        assertNull(ProjectFolderName.problem(" app "))
        assertEquals("app", ProjectFolderName.validated(" app "))
    }

    @Test
    fun `B-STATE-158 rejects paths, hidden names and odd characters`() {
        assertEquals(ProjectFolderNameProblem.Empty, ProjectFolderName.problem(""))
        assertTrue("Enter" in words("")!!)
        assertEquals(ProjectFolderNameProblem.NotSingleSegment, ProjectFolderName.problem(".."))
        assertTrue("single" in words("..")!!)
        assertTrue("single" in words("a/b")!!)
        assertTrue("single" in words("a\\b")!!)
        assertEquals(ProjectFolderNameProblem.InvalidCharacters, ProjectFolderName.problem(".git"))
        assertTrue("letters" in words(".git")!!)
        assertTrue("letters" in words("my app")!!)
        assertTrue("letters" in words("x".repeat(65))!!)
        assertNull(ProjectFolderName.problem("x".repeat(64)))
        assertNull(ProjectFolderName.validated("a/b"))
    }
}
