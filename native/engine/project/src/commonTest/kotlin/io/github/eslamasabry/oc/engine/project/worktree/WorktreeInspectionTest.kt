package io.github.eslamasabry.oc.engine.project.worktree

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.VersionControlFile
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeHost : WorktreeHost {
    var requestedDirectory: String? = null
    var requestedCursor: String? = null
    var requestedArchived: Boolean? = null
    var requestedLimit: Int? = null
    var failure: Failure? = null
    var files: List<VersionControlFile> = emptyList()
    var page: ServerPage<GlobalSessionResult> = ServerPage(persistentListOf(), null)

    override suspend fun listWorktreeFileStatuses(directory: String): Outcome<List<VersionControlFile>> {
        requestedDirectory = directory
        return failure?.failed() ?: files.ok()
    }

    override suspend fun listGlobalSessions(includeArchived: Boolean, cursor: String?, limit: Int): Outcome<ServerPage<GlobalSessionResult>> {
        requestedCursor = cursor
        requestedArchived = includeArchived
        requestedLimit = limit
        return failure?.failed() ?: page.ok()
    }
}

private fun session(id: String, directory: String? = "/copy", project: String = "project", workspace: String? = null, parent: String? = null) =
    GlobalSessionResult(
        Session(
            SessionId(id),
            directory = directory,
            projectId = ProjectId(project),
            workspaceId = workspace?.let(::WorkspaceId),
            parentId = parent?.let(::SessionId),
        ),
        projectName = null,
        // The project root is not the session's copy and must never be used instead.
        projectDirectory = "/copy",
    )

private val ENABLED = WorktreeCapabilities(projectManagement = true, globalSessionSearch = true)

class WorktreeInspectionTest {
    @Test
    fun `B-AGENT-318 reads exact copy changes and an error remains unknown, not clean`() = runTest {
        val host = FakeHost().apply { files = listOf(VersionControlFile("lib/app.dart", "modified", 3, 1)) }
        val inspection = WorktreeInspection(host, ENABLED)
        val files = inspection.loadChanges("/copy").getOrNull()!!
        assertEquals("/copy", host.requestedDirectory)
        assertEquals("lib/app.dart", files.single().path)
        assertEquals(3, files.single().additions)
        host.files = emptyList()
        assertEquals(0, inspection.loadChanges("/copy").getOrNull()?.size, "an empty read is clean")
        host.failure = Failure.Server(503)
        val unknown = inspection.loadChanges("/copy")
        assertIs<Outcome.Failed>(unknown)
        assertNull(unknown.getOrNull(), "a failed read is never an empty (clean) list")
    }

    @Test
    fun `B-AGENT-318 conversation membership uses exact project, path and workspace`() = runTest {
        val host =
            FakeHost().apply {
                page =
                    ServerPage(
                        listOf(
                            session("match"),
                            session("nested", directory = "/copy/nested"),
                            session("prefix", directory = "/copy-old"),
                            session("wrong-project", project = "another"),
                            session("remote", workspace = "remote"),
                            session("unknown", directory = null),
                            session("child", parent = "match"),
                        ).toImmutableList(),
                        "opaque-next",
                    )
            }
        val inspection = WorktreeInspection(host, ENABLED)
        val page = inspection.conversationPage("/copy", "project", cursor = "opaque-current", limit = 7).getOrNull()!!
        assertEquals(listOf("match"), page.items.map { it.id.raw })
        assertEquals("opaque-next", page.nextCursor)
        assertEquals("opaque-current", host.requestedCursor)
        assertEquals(7, host.requestedLimit)
        assertEquals(true, host.requestedArchived)
        val remote = inspection.conversationPage("/copy", "project", workspaceId = "remote", includeArchived = false).getOrNull()!!
        assertEquals("remote", remote.items.single().id.raw)
        assertEquals(false, host.requestedArchived)
    }

    @Test
    fun `B-AGENT-318 an empty matching page keeps its continuation and failures propagate`() = runTest {
        val host = FakeHost().apply { page = ServerPage(persistentListOf(session("elsewhere", directory = "/other")), "next") }
        val inspection = WorktreeInspection(host, ENABLED)
        val page = inspection.conversationPage("/copy", "project").getOrNull()!!
        assertTrue(page.items.isEmpty())
        assertTrue(page.hasMore)
        host.failure = Failure.Server(503)
        assertIs<Failure.Server>(inspection.conversationPage("/copy", "project").failureOrNull())
    }

    @Test
    fun `B-AGENT-318 unsupported and invalid scopes dispatch no requests`() = runTest {
        val host = FakeHost()
        val disabled = WorktreeInspection(host, WorktreeCapabilities(projectManagement = false, globalSessionSearch = true))
        assertFalse(disabled.capabilities.worktreeMerge)
        assertFalse(disabled.capabilities.worktreeConversations)
        assertIs<Failure.Unavailable>(disabled.loadChanges("/copy").failureOrNull())
        assertIs<Failure.Unavailable>(disabled.conversationPage("/copy", "project").failureOrNull())
        assertNull(host.requestedDirectory)
        assertNull(host.requestedLimit)
        assertFalse(WorktreeCapabilities(projectManagement = true, globalSessionSearch = false).worktreeConversations)
        val enabled = WorktreeInspection(host, ENABLED)
        assertIs<Outcome.Failed>(enabled.loadChanges(" "))
        assertIs<Outcome.Failed>(enabled.conversationPage("/copy", ""))
        assertIs<Outcome.Failed>(enabled.conversationPage("/copy", "project", limit = 0))
        assertIs<Outcome.Failed>(enabled.conversationPage("/copy", "project", workspaceId = " "))
        assertNull(host.requestedDirectory)
        assertNull(host.requestedLimit)
    }
}
