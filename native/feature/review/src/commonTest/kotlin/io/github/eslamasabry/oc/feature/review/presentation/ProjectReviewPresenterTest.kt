package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.FileDiff
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class ProjectNav : ReviewNavigation {
    val calls = mutableListOf<String>()

    override fun back() { calls += "back" }

    override fun openFile(path: String, staged: Boolean) { calls += "file:$path" }

    override fun openWorkingTreeFile(path: String) { calls += "tree:$path" }

    override fun openStaged() { calls += "staged" }
}

class ProjectReviewPresenterTest {
    private val files =
        persistentListOf(
            FileDiff("lib/a.dart", additions = 3, deletions = 1, patch = "@@ -1 +1 @@\n-x\n+y"),
            FileDiff("README.md", additions = 2, deletions = 0, patch = "@@"),
        )

    @Test
    fun `a project's changes list the uncommitted files with no conversation actions`() = runTest(UnconfinedTestDispatcher()) {
        val presenter = ProjectChangesPresenter({ files.ok() }, ProjectNav(), backgroundScope)
        val ui = presenter.state.value
        assertEquals(ReviewLoad.Ready, ui.load)
        assertEquals(listOf("lib/a.dart", "README.md"), ui.files.map { it.path })
        assertEquals(ReviewScope.WorkingTree, ui.scope)
        assertEquals(5, ui.added)
        assertEquals(1, ui.removed)
        assertEquals(false, ui.canPickScope)
        assertEquals(false, ui.canSendBack)
        assertEquals(UndoAllUi.Unavailable, ui.undoAll)
        assertEquals(false, ui.conversation)
    }

    @Test
    fun `a file opens its working-tree diff and Looks good goes back`() = runTest(UnconfinedTestDispatcher()) {
        val nav = ProjectNav()
        val presenter = ProjectChangesPresenter({ files.ok() }, nav, backgroundScope)
        presenter.openFile("lib/a.dart")
        presenter.keepAll()
        assertEquals(listOf("tree:lib/a.dart", "back"), nav.calls)
    }

    @Test
    fun `a failed read is a failed page that Try again reads again`() = runTest(UnconfinedTestDispatcher()) {
        var fail = true
        val presenter =
            ProjectChangesPresenter(
                { if (fail) Failure.Unavailable(FeatureId("sessionDiff")).failed() else files.ok() },
                ProjectNav(),
                backgroundScope,
            )
        assertIs<ReviewLoad.Failed>(presenter.state.value.load)
        fail = false
        presenter.retry()
        assertEquals(ReviewLoad.Ready, presenter.state.value.load)
    }

    @Test
    fun `one file's page finds its diff, and says so when the file is gone`() = runTest(UnconfinedTestDispatcher()) {
        val here = ProjectFileDiffPresenter({ files.ok() }, "lib/a.dart", backgroundScope)
        assertEquals("lib/a.dart", here.state.value.diff?.file)
        val gone = ProjectFileDiffPresenter({ files.ok() }, "missing.txt", backgroundScope)
        assertTrue(gone.state.value.missing)
    }
}
