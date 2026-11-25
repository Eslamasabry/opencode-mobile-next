package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.feature.chat.work.BackgroundMoment
import io.github.eslamasabry.oc.feature.chat.work.BackgroundMoments
import io.github.eslamasabry.oc.feature.chat.work.MomentEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundMomentsTest {
    private fun shell(
        id: String,
        command: String = "npm test",
        status: ManagedShellStatus = ManagedShellStatus.Running,
        startedAtMs: Long = 1_000,
        completedAtMs: Long? = null,
        exitCode: Int? = null,
    ) =
        ManagedShell(
            id = id,
            command = command,
            status = status,
            startedAtMs = startedAtMs,
            completedAtMs = completedAtMs,
            exitCode = exitCode,
            sessionId = Ref.sessionId.raw,
            directory = null,
            ownerToken = null,
        )

    private fun ended(
        status: ManagedShellStatus,
        exitCode: Int?,
        startedAtMs: Long = 60_000,
        completedAtMs: Long? = 300_000,
    ): BackgroundMoment =
        BackgroundMoments.of(
            listOf(
                shell(
                    "sh1",
                    status = status,
                    startedAtMs = startedAtMs,
                    completedAtMs = completedAtMs,
                    exitCode = exitCode,
                )
            )
        ).single()

    @Test
    fun `B-CHAT-TASKS-015 a running command's moment names it and keeps its own start`() {
        val moment =
            BackgroundMoments.of(
                listOf(shell("sh1", "cd /srv && npm run dev", startedAtMs = 5_000, completedAtMs = 9_000))
            ).single()
        assertEquals("shell:sh1", moment.key)
        assertEquals("npm run dev", moment.name)
        assertEquals(5_000L, moment.startedAtMs)
        assertNull(moment.endedAtMs, "a running command has not ended, whatever time the server sent")
        assertNull(moment.tookMs)
        assertEquals(MomentEnd.Running, moment.end)
        assertEquals("a command", BackgroundMoments.of(listOf(shell("sh2", command = "  "))).single().name)

        val moments =
            BackgroundMoments.of(
                listOf(
                    shell("b", "npm run dev", startedAtMs = 5_000),
                    shell("d", "npm test", startedAtMs = 1_000),
                    shell("a", "sleep 1", startedAtMs = 5_000),
                    shell("d", "cargo build", startedAtMs = 9_000),
                )
            )
        assertEquals(
            listOf("shell:d", "shell:a", "shell:b"),
            moments.map { it.key },
            "oldest start first, equal starts by id",
        )
        assertEquals(1_000L, moments.first().startedAtMs, "the same id twice keeps the first")
        assertEquals("npm test", moments.first().name)
        assertTrue(BackgroundMoments.of(emptyList()).isEmpty(), "no commands give no moments")
    }

    @Test
    fun `B-CHAT-TASKS-016 an ended command keeps when it ended, how long it took and whether it worked`() {
        val worked = ended(ManagedShellStatus.Exited, exitCode = 0)
        assertEquals(MomentEnd.Worked, worked.end)
        assertEquals(300_000L, worked.endedAtMs)
        assertEquals(240_000L, worked.tookMs)
        assertEquals(MomentEnd.Failed, ended(ManagedShellStatus.Exited, exitCode = 2).end)
        assertEquals(MomentEnd.Ended, ended(ManagedShellStatus.Exited, exitCode = null).end)
        assertEquals(MomentEnd.Stopped, ended(ManagedShellStatus.Timeout, exitCode = null).end)
        assertEquals(MomentEnd.Stopped, ended(ManagedShellStatus.Killed, exitCode = 137).end)
        val unknown = listOf(shell("sh1", status = ManagedShellStatus.Unknown))
        assertTrue(BackgroundMoments.of(unknown).isEmpty(), "the server does not know how it ended: left out")

        val unsaid = ended(ManagedShellStatus.Exited, exitCode = 0, completedAtMs = null)
        assertNull(unsaid.endedAtMs, "no end time from the server")
        assertNull(unsaid.tookMs)
        val backwards = ended(ManagedShellStatus.Exited, exitCode = 0, startedAtMs = 300_000, completedAtMs = 240_000)
        assertEquals(240_000L, backwards.endedAtMs)
        assertEquals(0L, backwards.tookMs, "an end before the start never gives a negative time")
    }
}
