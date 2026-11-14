package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellList
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.work.RunningWork
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 ("Where is the option to see the background activity and the background agent and send to
 * background?"): work running besides the reply is counted where the conversation shows it, the sheet knows what the
 * server reports, and a task's row goes to the step that started it.
 */
class RunningWorkFindableTest {
    private fun shell(id: String, command: String, status: ManagedShellStatus, startedAtMs: Long) =
        ManagedShell(
            id = id,
            command = command,
            status = status,
            startedAtMs = startedAtMs,
            completedAtMs = null,
            exitCode = null,
            sessionId = Ref.sessionId.raw,
            directory = null,
            ownerToken = null,
        )

    private val streamTask = BackgroundTask(callId = "call-1", messageId = "a1", sinceMs = 1_000, name = "build_release.sh")

    @Test
    fun `B-CHAT-TASKS-005 what each server kind reports decides whether the sheet says it reports nothing`() {
        // OpenCode 2 (on this phone or a computer): the server lists its commands and keeps sub-agent conversations.
        assertTrue(RunningWork.reported(shellsSupported = true, subagentSessions = true, tasks = emptyList()))
        // The agent helper (Claude Code): no command list, but sub-agents and the stream's background work.
        assertTrue(RunningWork.reported(shellsSupported = false, subagentSessions = true, tasks = emptyList()))
        // Codex: nothing of either kind.
        assertFalse(RunningWork.reported(shellsSupported = false, subagentSessions = false, tasks = emptyList()))
        // Whatever the server, work its stream did report is listed.
        assertTrue(RunningWork.reported(shellsSupported = false, subagentSessions = false, tasks = listOf(streamTask)))
    }

    @Test
    fun `B-CHAT-TASKS-006 the background line counts commands still running after the reply, by a plain name`() {
        val shells =
            listOf(
                shell("sh2", "cd /srv/app && npm run dev", ManagedShellStatus.Running, startedAtMs = 5_000),
                shell("sh1", "./gradlew assemble", ManagedShellStatus.Exited, startedAtMs = 2_000),
                shell("sh0", "python3 -m http.server", ManagedShellStatus.Running, startedAtMs = 3_000),
            )
        // While the reply runs, its own command is the live line's: only the stream's background work shows.
        assertEquals(listOf(streamTask), RunningWork.lineTasks(listOf(streamTask), shells, replyBusy = true))
        val ended = RunningWork.lineTasks(listOf(streamTask), shells, replyBusy = false)
        assertEquals(listOf("call-1", "shell:sh0", "shell:sh2"), ended.map { it.callId }, "oldest first, ended ones left out")
        assertEquals("npm run dev", ended.last().name, "a plain name; the command stays for the opened row")
        assertEquals("cd /srv/app && npm run dev", ended.last().command)
        assertEquals(3, RunningWork.count(listOf(streamTask), shells), "the … menu counts every running piece")
        assertEquals(0, RunningWork.count(emptyList(), shells.filter { it.status != ManagedShellStatus.Running }))
    }

    private class World(
        private val support: BackgroundWorkSupport,
        private val shells: Outcome<ManagedShellList>? = null,
    ) : FakeChatWorld(), TurnFeedSource {
        override val turnFeed = MutableStateFlow(TurnFeed())

        override suspend fun backgroundWorkSupport(): Outcome<BackgroundWorkSupport> = Outcome.Ok(support)

        override suspend fun runningShells(): Outcome<ManagedShellList> = shells ?: super.runningShells()
    }

    @Test
    fun `B-CHAT-TASKS-007 a server without a command list is not a failed read, and the sheet knows what it can move`() {
        // The agent helper and Codex answer the command list with Unavailable (the interface default).
        val helper = ChatWorkModel(World(BackgroundWorkSupport.Unavailable), Immediate::launch)
        helper.refresh()
        val read = helper.state.value
        assertFalse(read.failed, "never \"Couldn't load this conversation's commands\"")
        assertFalse(read.supported, "the sheet says what this server reports instead")
        assertEquals(false, read.backgroundSupported, "the sheet says this server can't move work to the background")

        val oc2 =
            ChatWorkModel(
                World(
                    BackgroundWorkSupport.SubagentsAndShells,
                    Outcome.Ok(ManagedShellList(true, persistentListOf(shell("sh1", "npm test", ManagedShellStatus.Running, 1_000)))),
                ),
                Immediate::launch,
            )
        oc2.refresh()
        assertTrue(oc2.state.value.supported)
        assertEquals(1, oc2.state.value.running)
        assertEquals(true, oc2.state.value.backgroundSupported)
    }

    private class FeedWorld : FakeChatWorld(), TurnFeedSource {
        override val turnFeed = MutableStateFlow(TurnFeed())
    }

    @Test
    fun `B-CHAT-TASKS-008 a task's row goes to the message whose step started it`() {
        val world = FeedWorld()
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        world.turnFeed.value =
            TurnFeed(
                ledger =
                    LedgerSnapshot(
                        1,
                        listOf(
                            MessageWithParts(MessageInfo("u1", Ref.sessionId.raw, Role.User), persistentListOf()),
                            MessageWithParts(MessageInfo("a1", Ref.sessionId.raw, Role.Assistant), persistentListOf()),
                        ),
                        emptyList(),
                    )
            )
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        p.showWorkStart("gone")
        assertNull(p.current().arrival, "a message the transcript does not hold moves nothing")
        p.showWorkStart("a1")
        assertEquals(world.transcript.value.indexOf("b:a1/p1:0"), p.current().arrival?.landOnIndex)
    }
}
