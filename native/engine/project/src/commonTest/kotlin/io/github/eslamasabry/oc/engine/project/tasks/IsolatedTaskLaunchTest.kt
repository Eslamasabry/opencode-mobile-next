package io.github.eslamasabry.oc.engine.project.tasks

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DIR = "/data/worktree/project-1/wake-fix"
private val CREATED = WorktreeInfo(name = "wake-fix", directory = DIR, branch = null, ready = false)

private fun ready(directory: String, project: String? = "project-1") =
    WorktreeEvent(ready = true, directory = directory, project = project, name = "wake-fix", branch = "opencode/wake-fix")

private fun failedEvent(directory: String) = WorktreeEvent(ready = false, directory = directory, project = "project-1", message = "setup script exited 1")

private class Harness(scope: TestScope, timeout: Duration = 45.seconds) {
    val events = MutableSharedFlow<WorktreeEvent>(extraBufferCapacity = 16)
    val created = CompletableDeferred<Outcome<WorktreeInfo>>()
    val openCalls = ArrayList<String>()
    var openError: Failure? = null
    var createCalls = 0
    val phases = ArrayList<IsolatedTaskPhase>()

    val launch =
        IsolatedTaskLaunch(
            scope = scope.backgroundScope,
            projectId = "project-1",
            events = events,
            create = {
                createCalls++
                created.await()
            },
            open = { directory ->
                openCalls += directory
                openError?.failed() ?: Session(SessionId("ses_new"), directory = directory, projectId = ProjectId("project-1")).ok()
            },
            readinessTimeout = timeout,
        )

    init {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { launch.state.collect { phases += it.phase } }
    }

    val state: IsolatedTaskState get() = launch.state.value
}

class IsolatedTaskLaunchTest {
    @Test
    fun `B-AGENT-115 a ready event received before the create response is retained`() = runTest {
        val h = Harness(this)
        h.launch.start()
        assertEquals(IsolatedTaskPhase.Creating, h.state.phase)
        runCurrent()
        assertEquals(1, h.createCalls)
        // The server finished setup before its HTTP answer reached the phone.
        h.events.emit(ready("$DIR/"))
        runCurrent()
        assertEquals(IsolatedTaskPhase.Creating, h.state.phase)
        h.created.complete(CREATED.ok())
        runCurrent()
        assertEquals(IsolatedTaskPhase.Ready, h.state.phase)
        assertEquals("opencode/wake-fix", h.state.worktree?.branch)
        assertTrue(IsolatedTaskPhase.Ready in h.phases)
        assertFalse(IsolatedTaskPhase.Preparing in h.phases)
    }

    @Test
    fun `B-AGENT-115 events for another project or directory are ignored`() = runTest {
        val h = Harness(this)
        h.launch.start()
        h.events.emit(ready(DIR, project = "p2"))
        h.events.emit(ready("/data/worktree/project-1/other"))
        runCurrent()
        h.created.complete(CREATED.ok())
        runCurrent()
        assertEquals(IsolatedTaskPhase.Preparing, h.state.phase)
        h.events.emit(ready("/data/worktree/project-1/other"))
        h.events.emit(failedEvent("/data/worktree/project-1/other"))
        runCurrent()
        assertEquals(IsolatedTaskPhase.Preparing, h.state.phase)
        h.events.emit(ready(DIR))
        runCurrent()
        assertEquals(IsolatedTaskPhase.Ready, h.state.phase)
    }

    @Test
    fun `B-AGENT-115 start is idempotent and cancel is inert once terminal`() = runTest {
        val h = Harness(this)
        h.launch.start()
        h.launch.start()
        runCurrent()
        assertEquals(1, h.createCalls)
        h.created.complete(Failure.Server(503).failed())
        runCurrent()
        h.launch.cancel()
        assertEquals(IsolatedTaskPhase.Failed, h.state.phase)
    }

    private suspend fun TestScope.failedSetup(h: Harness) {
        h.launch.start()
        runCurrent()
        h.created.complete(CREATED.ok())
        runCurrent()
        h.events.emit(failedEvent(DIR))
        runCurrent()
    }

    @Test
    fun `B-AGENT-116 a failure event keeps the created worktree and reports the message`() = runTest {
        val h = Harness(this)
        failedSetup(h)
        assertEquals(IsolatedTaskPhase.Failed, h.state.phase)
        assertEquals("setup script exited 1", h.state.message)
        assertNotNull(h.state.worktree, "never dropped or deleted")
        assertFalse(h.state.canOpen)
        assertTrue(h.state.isTerminal)
    }

    @Test
    fun `B-AGENT-116 after a failed setup only an explicit Start anyway opens the copy`() = runTest {
        val h = Harness(this)
        failedSetup(h)
        assertTrue(h.state.canStartAfterFailedSetup)
        h.launch.open()
        assertTrue(h.openCalls.isEmpty(), "no implicit open after a failure")
        assertEquals(IsolatedTaskPhase.Failed, h.state.phase)
        h.launch.open(acceptFailedSetup = true)
        assertEquals(listOf(DIR), h.openCalls)
        assertEquals(IsolatedTaskPhase.Opened, h.state.phase)
    }

    @Test
    fun `B-AGENT-116 a failed create has no copy to start in and surfaces the error`() = runTest {
        val h = Harness(this)
        h.launch.start()
        runCurrent()
        h.created.complete(Failure.Rejected(400).failed())
        runCurrent()
        assertEquals(IsolatedTaskPhase.Failed, h.state.phase)
        assertIs<Failure.Rejected>(h.state.failure)
        assertNull(h.state.worktree)
        assertFalse(h.state.canStartAfterFailedSetup)
        h.launch.open(acceptFailedSetup = true)
        assertTrue(h.openCalls.isEmpty())
    }

    @Test
    fun `B-AGENT-116 a failed open keeps the launch openable`() = runTest {
        val h = Harness(this)
        h.launch.start()
        runCurrent()
        h.created.complete(CREATED.ok())
        runCurrent()
        h.events.emit(ready(DIR))
        runCurrent()
        h.openError = Failure.Server(500)
        h.launch.open()
        assertEquals(IsolatedTaskPhase.Ready, h.state.phase)
        assertIs<Failure.Server>(h.state.openError)
        assertTrue(h.state.canOpen)
        h.openError = null
        h.launch.open()
        assertEquals(IsolatedTaskPhase.Opened, h.state.phase)
        assertNull(h.state.openError)
    }

    @Test
    fun `B-AGENT-117 a timeout becomes unconfirmed and open needs explicit acceptance`() = runTest {
        val wait = 40.milliseconds
        val h = Harness(this, wait)
        h.launch.start()
        runCurrent()
        h.created.complete(CREATED.ok())
        runCurrent()
        assertEquals(IsolatedTaskPhase.Preparing, h.state.phase)
        advanceTimeBy(wait * 3)
        assertEquals(IsolatedTaskPhase.Unconfirmed, h.state.phase)
        assertTrue(h.state.canOpen)
        h.launch.open()
        assertTrue(h.openCalls.isEmpty(), "implicit open is refused")
        assertEquals(IsolatedTaskPhase.Unconfirmed, h.state.phase)
        h.launch.keepWaiting()
        assertEquals(IsolatedTaskPhase.Preparing, h.state.phase)
        advanceTimeBy(wait * 3)
        assertEquals(IsolatedTaskPhase.Unconfirmed, h.state.phase)
        // A late ready still upgrades an unconfirmed worktree.
        h.events.emit(ready(DIR))
        runCurrent()
        assertEquals(IsolatedTaskPhase.Ready, h.state.phase)
        h.launch.open()
        assertEquals(listOf(DIR), h.openCalls)
        assertEquals(IsolatedTaskPhase.Opened, h.state.phase)
        assertEquals("ses_new", h.state.session?.id?.raw)
    }

    @Test
    fun `B-AGENT-117 open anyway from unconfirmed records the choice`() = runTest {
        val wait = 40.milliseconds
        val h = Harness(this, wait)
        h.launch.start()
        runCurrent()
        h.created.complete(CREATED.ok())
        advanceTimeBy(wait * 3)
        assertEquals(IsolatedTaskPhase.Unconfirmed, h.state.phase)
        h.launch.open(acceptUnconfirmed = true)
        assertEquals(1, h.openCalls.size)
        assertEquals(IsolatedTaskPhase.Opened, h.state.phase)
    }

    @Test
    fun `B-AGENT-117 cancel before the response keeps whatever the server made`() = runTest {
        val h = Harness(this)
        h.launch.start()
        runCurrent()
        h.launch.cancel()
        assertEquals(IsolatedTaskPhase.Cancelled, h.state.phase)
        assertNull(h.state.worktree, "unknown until the server answers")
        // The request was already executing; its answer still lands.
        h.created.complete(CREATED.ok())
        runCurrent()
        assertEquals(IsolatedTaskPhase.Cancelled, h.state.phase)
        assertEquals(DIR, h.state.worktree?.directory)
        assertFalse(h.state.canOpen)
        assertTrue(h.openCalls.isEmpty())
    }

    @Test
    fun `B-AGENT-117 cancel while preparing stops waiting without deleting`() = runTest {
        val h = Harness(this)
        h.launch.start()
        runCurrent()
        h.created.complete(CREATED.ok())
        runCurrent()
        h.launch.cancel()
        assertEquals(IsolatedTaskPhase.Cancelled, h.state.phase)
        assertNotNull(h.state.worktree)
        // Late events no longer move a cancelled launch, and the timer is gone.
        h.events.emit(ready(DIR))
        advanceTimeBy(60_000)
        assertEquals(IsolatedTaskPhase.Cancelled, h.state.phase)
        assertFalse(h.state.canCancel)
    }
}
