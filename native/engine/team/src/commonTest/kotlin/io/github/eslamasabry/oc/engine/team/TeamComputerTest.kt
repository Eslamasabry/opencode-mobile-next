package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.team.fleet.FakeGasCity
import io.github.eslamasabry.oc.engine.team.fleet.FleetEngine
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A team on a computer: the page is the computer's team, and Turn off clears its config without the phone engine. */
class TeamComputerTest {
    private val computer = TeamConfig(TeamProvider.GasCity, "http://100.101.102.103:8372", city = "main")

    private class Host(var config: TeamConfig?) : TeamHost {
        var phoneOff = 0
        var computerOff = 0

        override suspend fun config(profileId: String): TeamConfig? = config

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> = error("a computer's team is not read here")

        override suspend fun command(profileId: String, config: TeamConfig, command: TeamCommand, body: Map<String, Any?>) =
            error("a computer's team takes no project command")

        override suspend fun turnOffPhoneTeam(profileId: String): Outcome<Unit> {
            phoneOff++
            config = null
            return Outcome.Ok(Unit)
        }

        override suspend fun turnOffComputerTeam(profileId: String): Outcome<Unit> {
            computerOff++
            config = null
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun a_computers_team_is_the_computer_phase_and_its_fleet_answers() =
        runTest {
            val fleet = FleetEngine(FakeGasCity(), Clock.System)
            val engine = TeamEngine(Host(computer), fleet = fleet)
            engine.refresh("p")
            assertEquals(TeamPhase.Computer, engine.state("p").value.phase)
            assertEquals(computer, engine.state("p").value.config)
            backgroundScope.launch { fleet.live("p", computer) }
            runCurrent()
            assertEquals(FleetPhase.Ready, fleet.state("p").value.phase)
        }

    @Test
    fun turning_a_computers_team_off_clears_it_without_touching_the_phone_engine() =
        runTest {
            val host = Host(computer)
            val fleet = FleetEngine(FakeGasCity(), Clock.System)
            val engine = TeamEngine(host, fleet = fleet)
            engine.refresh("p")
            backgroundScope.launch { fleet.live("p", computer) }
            runCurrent()
            assertEquals(Outcome.Ok(Unit), engine.turnOff("p"))
            assertEquals(1, host.computerOff)
            assertEquals(0, host.phoneOff)
            assertEquals(TeamPhase.Off, engine.state("p").value.phase)
            assertEquals(FleetPhase.Idle, fleet.state("p").value.phase)
        }
}
