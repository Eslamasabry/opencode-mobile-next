package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Device walk 2026-11-08: the AI Team page was a dead end at "AI Team is off"; turning it on reaches the team. */
class TeamTurnOnTest {
    private class Host(var can: Boolean, var result: Outcome<Unit>, var off: Outcome<Unit> = Outcome.Ok(Unit)) : TeamHost {
        var config: TeamConfig? = null
        var turnOns = 0

        override suspend fun config(profileId: String): TeamConfig? = config

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
            Outcome.Ok(TeamRead(FakeTeamHost.READY, workspace(1, "app")))

        override suspend fun command(
            profileId: String,
            config: TeamConfig,
            command: TeamCommand,
            body: Map<String, Any?>,
        ): Outcome<TeamCommandResult> = Outcome.Ok(TeamCommandResult(accepted = true))

        override suspend fun canTurnOnPhoneTeam(profileId: String): Boolean = can

        var offs = 0

        override fun phoneSetup(profileId: String): PhoneSetupPorts = Ports()

        override suspend fun turnOffPhoneTeam(profileId: String): Outcome<Unit> {
            offs++
            if (off is Outcome.Ok) config = null
            return off
        }

        /** A phone that passes every step; keeping the team is what makes the next read answer. */
        inner class Ports : PhoneSetupPorts {
            override suspend fun hasServer(): Boolean = true

            override suspend fun replyRunning(): Boolean = false

            override suspend fun inspect(): SetupHost = SetupHost(serverRunning = false, terminals = 0, restartRequired = false)

            override suspend fun stopServer(): Outcome<Unit> = Outcome.Ok(Unit)

            override suspend fun closeTerminals(): Outcome<Unit> = Outcome.Ok(Unit)

            override suspend fun startEngine(): Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true))

            override suspend fun stopEngine() = Unit

            override suspend fun probeEngine(): Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true))

            override suspend fun startServer(): Outcome<Unit> = Outcome.Ok(Unit)

            override suspend fun restoreServer(): Boolean = true

            override suspend fun saveTeam(): Outcome<Unit> {
                turnOns++
                if (result is Outcome.Ok) config = FakeTeamHost.PHONE
                return result
            }
        }
    }

    @Test
    fun `B-AGENT-294 turning the phone's team on reads it again, so the off page becomes the team`() = runTest {
        val host = Host(can = true, result = Outcome.Ok(Unit))
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals(TeamPhase.Off, engine.state("p").value.phase)
        assertTrue(engine.canTurnOn("p"))
        engine.runSetup("p")
        assertEquals(SetupPhase.Done, engine.setupState("p").value.phase)
        assertEquals(1, host.turnOns)
        assertEquals(TeamPhase.Ready, engine.state("p").value.phase)
        assertEquals(listOf("app"), engine.state("p").value.workspace?.projects?.map { it.name })
    }

    @Test
    fun `B-AGENT-043 a failed turn-on leaves the team off and is never retried`() = runTest {
        val host = Host(can = true, result = Outcome.Failed(Failure.Unexpected()))
        val engine = TeamEngine(host)
        engine.refresh("p")
        engine.runSetup("p")
        assertEquals(SetupPhase.Failed, engine.setupState("p").value.phase)
        assertEquals(SetupProblem.Engine, engine.setupState("p").value.problem)
        assertEquals(1, host.turnOns)
        assertEquals(TeamPhase.Off, engine.state("p").value.phase)
    }

    @Test
    fun `B-AGENT-294 a host without the turn-on says so and changes nothing`() = runTest {
        val bare =
            object : TeamHost {
                override suspend fun config(profileId: String): TeamConfig? = null

                override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
                    Outcome.Failed(Failure.Unexpected())

                override suspend fun command(
                    profileId: String,
                    config: TeamConfig,
                    command: TeamCommand,
                    body: Map<String, Any?>,
                ): Outcome<TeamCommandResult> = Outcome.Failed(Failure.Unexpected())
            }
        val engine = TeamEngine(bare)
        assertFalse(engine.canTurnOn("p"))
        engine.runSetup("p")
        assertEquals(SetupProblem.NoServer, engine.setupState("p").value.problem)
    }

    @Test
    fun `B-AGENT-295 turning the team off clears it and the page says it is off`() = runTest {
        val host = Host(can = true, result = Outcome.Ok(Unit))
        host.config = FakeTeamHost.PHONE
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals(TeamPhase.Ready, engine.state("p").value.phase)
        assertIs<Outcome.Ok<Unit>>(engine.turnOff("p"))
        assertEquals(1, host.offs)
        assertEquals(TeamPhase.Off, engine.state("p").value.phase)
    }

    @Test
    fun `B-AGENT-043 a team that could not be stopped stays on`() = runTest {
        val host = Host(can = true, result = Outcome.Ok(Unit), off = Outcome.Failed(Failure.Unexpected()))
        host.config = FakeTeamHost.PHONE
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertIs<Outcome.Failed>(engine.turnOff("p"))
        assertEquals(TeamPhase.Ready, engine.state("p").value.phase)
    }
}
