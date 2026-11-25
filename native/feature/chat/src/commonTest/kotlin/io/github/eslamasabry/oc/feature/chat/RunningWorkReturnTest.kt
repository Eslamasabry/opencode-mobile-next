package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellList
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.work.RunningWork
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RunningWorkReturnTest {
    private class World : FakeChatWorld() {
        var shells: Outcome<ManagedShellList> = Outcome.Failed(Failure.Network(NetworkWhy.Offline))

        override suspend fun runningShells(): Outcome<ManagedShellList> = shells
    }

    private fun running(id: String, startedAtMs: Long) =
        ManagedShell(
            id = id,
            command = "npm run dev",
            status = ManagedShellStatus.Running,
            startedAtMs = startedAtMs,
            completedAtMs = null,
            exitCode = null,
            sessionId = Ref.sessionId.raw,
            directory = null,
            ownerToken = null,
        )

    @Test
    fun `B-CHAT-TASKS-014 coming back shows the running command as soon as the link is up`() = runTest {
        val world = World()
        world.link.value = ChatLink.Connecting
        val work = ChatWorkModel(world) { block -> backgroundScope.launch { block() } }
        work.poll()
        runCurrent()
        assertEquals(0, work.state.value.running, "no link yet: the first read fails without a word")

        val dev = running("sh1", startedAtMs = 1_000)
        world.shells = Outcome.Ok(ManagedShellList(true, persistentListOf(dev)))
        world.link.value = ChatLink.Live
        runCurrent()
        assertEquals(1, work.state.value.running, "the link's turn to Live reads the commands at once")
        assertEquals(1_000, RunningWork.lineTasks(emptyList(), listOf(dev), false).single().sinceMs)

        world.link.value = ChatLink.Connecting
        world.shells = Outcome.Ok(ManagedShellList(true, persistentListOf()))
        runCurrent()
        world.link.value = ChatLink.Live
        runCurrent()
        assertEquals(0, work.state.value.running, "every later return reads again")
    }
}
