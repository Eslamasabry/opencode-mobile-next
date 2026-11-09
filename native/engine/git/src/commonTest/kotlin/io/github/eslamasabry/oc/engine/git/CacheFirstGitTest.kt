package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class CacheFirstGitTest {
    private val ref = ConversationRef(ProfileId("p"), SessionId("s"))
    private val nul = "\u0000"
    private val steps =
        arrayOf(
            StepReply("status", 0, "# branch.oid abc$nul# branch.head main$nul# branch.upstream origin/main$nul# branch.ab +2 -1${nul}1 .M N... 100644 100644 100644 a b app.kt$nul"),
            StepReply("unstaged"), StepReply("staged"), StepReply("log"), StepReply("branches"), StepReply("operation"),
            StepReply("remotes", 0, "remote.origin.url https://github.com/o/r.git\n"),
        )

    private class Shelf(var value: KeptGit? = null) : GitLastKnown {
        override fun peek(ref: ConversationRef) = value
        override suspend fun read(ref: ConversationRef) = value
        override suspend fun keep(ref: ConversationRef, kept: KeptGit) {
            value = Json.decodeFromString(KeptGit.serializer(), Json.encodeToString(KeptGit.serializer(), kept))
        }
    }

    private fun page(shell: GitShell, shelf: GitLastKnown?, connected: Boolean = true) =
        GitPage(ref, GitWorkspaces { resolveGitWorkspace(connected, "/p", true) { shell } }, kept = shelf)

    @Test
    fun `CF-40 the first frame with a kept snapshot has the live frame's structure`() = runTest {
        val shelf = Shelf()
        val live = page(FakeShell.steps(*steps), shelf)
        live.refresh()
        val liveSnap = assertIs<GitPageView.Ready>(live.state.value.view).snapshot

        val reopened = page(FakeShell.steps(*steps), shelf)
        val first = assertIs<GitPageView.Ready>(reopened.state.value.view).snapshot // before any read
        assertEquals(liveSnap.status.branch.head, first.status.branch.head)
        assertEquals(liveSnap.status.branch.ahead, first.status.branch.ahead)
        assertEquals(liveSnap.status.branch.behind, first.status.branch.behind)
        assertEquals(liveSnap.status.files.map { it.path to it.kind }, first.status.files.map { it.path to it.kind })
        assertEquals(liveSnap.commits.map { it.id }, first.commits.map { it.id })
        assertNull(first.identity)
    }

    @Test
    fun `CF-41 a failed or unreachable refresh keeps the kept rows and says so on the status line`() = runTest {
        val shelf = Shelf()
        page(FakeShell.steps(*steps), shelf).refresh()
        val away = page(FakeShell.steps(*steps), shelf, connected = false)
        away.refresh()
        assertEquals(1, assertIs<GitPageView.Ready>(away.state.value.view).snapshot.status.files.size)
        assertEquals(GitProblem.EnvironmentUnavailable, away.state.value.refreshFailure?.problem)

        val broken = page(FakeShell { GitShellReply.Failed(GitShellFailure.TimedOut) }, shelf)
        broken.refresh()
        assertEquals(1, assertIs<GitPageView.Ready>(broken.state.value.view).snapshot.status.files.size)
        assertEquals(GitProblem.TimedOut, broken.state.value.refreshFailure?.problem)
    }

    @Test
    fun `CF-42 with nothing kept the first-ever load is still Loading`() = runTest {
        val p = page(FakeShell.steps(*steps), Shelf())
        assertEquals(GitPageView.Loading, p.state.value.view)
    }
}
