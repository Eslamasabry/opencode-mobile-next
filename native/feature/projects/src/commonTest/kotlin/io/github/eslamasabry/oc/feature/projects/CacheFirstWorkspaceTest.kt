package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.projects.presentation.KeptWorkspace
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspace
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** Cache-first (owner rule, 2026-11-09), the project's checkout sheet and its copies pane. */
class CacheFirstWorkspaceTest {
    private val tree = WorktreeInfo("review-copy", "/work/review-copy", "review", ready = true)

    private fun server(list: () -> Outcome<ImmutableList<WorktreeInfo>>): ProjectServer {
        val gateway = FakeServerGateway()
        gateway.capabilities.value = ServerCapabilities(worktreeBrowsing = true)
        val ops =
            object : FakeOperationsGateway() {
                override suspend fun listWorktrees(directory: String) = list()
            }
        return ProjectServer(ProfileId("p"), "srv", gateway, ops)
    }

    private fun TestScope.workspace(kept: () -> KeptWorkspace?, keep: (KeptWorkspace) -> Unit, published: (ProjectWorkspaceUi) -> Unit) =
        ProjectWorkspace("/work", backgroundScope, published, {}, kept, { keep(it) })

    @Test
    fun `CF-90 the first frame of the checkout sheet with kept facts has the live worktrees, and no read has run`() = runTest {
        var shelf: KeptWorkspace? = null
        val live = server { Outcome.Ok(persistentListOf(tree)) }
        var shown = ProjectWorkspaceUi()
        val first = workspace({ shelf }, { shelf = Json.decodeFromString(KeptWorkspace.serializer(), Json.encodeToString(KeptWorkspace.serializer(), it)) }) { shown = it }
        first.adopt(live)
        first.openWorkspace()
        advanceUntilIdle()
        val liveRows = shown.worktrees
        assertNotNull(shelf)

        val reopened = workspace({ shelf }, {}) { shown = it }
        reopened.adopt(live)
        assertEquals(liveRows, shown.worktrees)
        assertFalse(shown.busy)
        assertTrue(shown.hasFacts)
    }

    @Test
    fun `CF-91 a failed read keeps the kept worktrees and shows the failure`() = runTest {
        var shown = ProjectWorkspaceUi()
        val kept = KeptWorkspace(worktrees = listOf(KeptWorkspace.KeptTree(tree.name, tree.directory, tree.branch, tree.ready)))
        val w = workspace({ kept }, {}) { shown = it }
        w.adopt(server { Outcome.Failed(Failure.Network(NetworkWhy.Reset)) })
        w.openWorkspace()
        advanceUntilIdle()
        assertNotNull(shown.failure)
        assertEquals(listOf(tree.name), shown.worktrees.map { it.name })
    }

    @Test
    fun `CF-92 with nothing kept the first-ever read has no facts, so the skeleton is allowed`() = runTest {
        var shown = ProjectWorkspaceUi()
        val w = workspace({ null }, {}) { shown = it }
        w.adopt(server { Outcome.Ok(persistentListOf(tree)) })
        assertFalse(shown.hasFacts)
        assertTrue(KeptWorkspace().isEmpty)
    }
}
