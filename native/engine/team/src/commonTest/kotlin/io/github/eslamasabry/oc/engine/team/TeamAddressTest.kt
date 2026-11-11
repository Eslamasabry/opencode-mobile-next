package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.team.fleet.ComputerTeamTest
import io.github.eslamasabry.oc.engine.team.fleet.FakeGasCity
import io.github.eslamasabry.oc.engine.team.fleet.FleetEngine
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/** Connect a team on a computer / Change address: the address is tested before it is saved, and the rule is never bypassed. */
class TeamAddressTest {
    private val tailnet = "http://100.101.102.103:8372"

    private class Host(var config: TeamConfig?) : TeamHost {
        val saved = mutableListOf<TeamConfig>()

        override suspend fun config(profileId: String): TeamConfig? = config

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> = error("a computer's team is not read here")

        override suspend fun command(profileId: String, config: TeamConfig, command: TeamCommand, body: Map<String, Any?>) =
            error("a computer's team takes no project command")

        override suspend fun saveComputerTeam(profileId: String, config: TeamConfig): Outcome<Unit> {
            saved += config
            this.config = config
            return Outcome.Ok(Unit)
        }
    }

    private fun engine(host: Host, gas: FakeGasCity) =
        TeamEngine(host, fleet = FleetEngine(gas, Clock.System), computerTransports = gas)

    @Test
    fun a_team_that_answers_is_found_with_the_city_the_host_reports() =
        runTest {
            val found = engine(Host(null), FakeGasCity()).testComputerTeam(tailnet, null)
            assertEquals(ComputerTeamTest.Found("main", front = false), found)
        }

    @Test
    fun plain_http_to_a_lan_address_is_refused_without_any_request() =
        runTest {
            val gas = FakeGasCity()
            val found = engine(Host(null), gas).testComputerTeam("http://192.168.1.20:8372", null)
            assertEquals(ComputerTeamTest.NotFound(FleetFailure.PlainHttpRefused), found)
            assertTrue(gas.opened.isEmpty())
        }

    @Test
    fun a_computer_that_does_not_answer_is_not_found_as_unreachable() =
        runTest {
            val gas = FakeGasCity().also { it.down = true }
            assertEquals(ComputerTeamTest.NotFound(FleetFailure.Unreachable), engine(Host(null), gas).testComputerTeam(tailnet, null))
        }

    @Test
    fun saving_turns_the_team_on_at_the_address_and_drops_the_old_one() =
        runTest {
            val host = Host(null)
            val engine = engine(host, FakeGasCity())
            engine.refresh("p")
            assertEquals(TeamPhase.Off, engine.state("p").value.phase)
            assertEquals(Outcome.Ok(Unit), engine.saveComputerTeam("p", " $tailnet ", " main ", front = true))
            val saved = host.saved.single()
            assertEquals(TeamConfig(TeamProvider.GasCity, tailnet, "main", TeamHostMode.Computer, front = true), saved)
            assertEquals(TeamPhase.Computer, engine.state("p").value.phase)
            assertEquals(saved, engine.state("p").value.config)
        }

    @Test
    fun an_address_the_rule_refuses_is_never_saved() =
        runTest {
            val host = Host(null)
            val engine = engine(host, FakeGasCity())
            for (url in listOf("http://192.168.1.20:8372", "ftp://100.101.102.103", "http://user@100.101.102.103:8372", "100.101.102.103:8372", "")) {
                assertTrue(engine.saveComputerTeam("p", url, "", front = false) is Outcome.Failed, url)
            }
            assertTrue(host.saved.isEmpty())
            assertEquals(Outcome.Ok(Unit), engine.saveComputerTeam("p", "https://team.example.com", "", front = false))
        }
}
