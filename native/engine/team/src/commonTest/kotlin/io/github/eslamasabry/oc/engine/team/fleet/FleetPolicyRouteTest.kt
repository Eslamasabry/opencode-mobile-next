package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private object PolicyClock : Clock {
    override fun now(): Instant = Instant.parse("2026-11-11T10:00:00Z")
}

private val POLICY_CONFIG = TeamConfig(TeamProvider.GasCity, url = "http://100.101.102.103:8372", city = "main")
private const val POLICY_FRONT_DOC = """{"provider":"gascity","front":true,"capabilities":{"control":true},"identity":{"allowed":true}}"""
private const val POLICY_DOC = """{"rig":"app","supervision":"HIGH","boundaries":[{"key":"require_approval","text":"Never merge without approval"},{"text":"no key"}]}"""

/** The host front's policy route (Dart `GasCityControl.policy`). */
class FleetPolicyRouteTest {
    private fun front(): FakeGasCity = FakeGasCity().also { it.route(FleetProbe.WELL_KNOWN, POLICY_FRONT_DOC) }

    @Test
    fun the_policy_of_a_rig_is_read_from_the_front_in_the_hosts_words() =
        runTest {
            val host = front()
            host.route("${FakeGasCity.CITY}/front/policy?rig=app", POLICY_DOC)
            val fleet = FleetEngine(host, PolicyClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", POLICY_CONFIG) }
            advanceUntilIdle()
            val policy = fleet.policy("p1", "app")
            assertEquals("app", policy?.rig)
            assertEquals(FleetSupervision.High, policy?.supervision)
            // A boundary with no key is not a boundary.
            assertEquals(listOf(FleetBoundary("require_approval", "Never merge without approval")), policy?.boundaries)
        }

    @Test
    fun a_bare_supervisor_has_no_policy_and_asks_nothing() =
        runTest {
            val host = FakeGasCity()
            val fleet = FleetEngine(host, PolicyClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", POLICY_CONFIG) }
            advanceUntilIdle()
            assertNull(fleet.policy("p1", "app"))
            assertTrue(host.gets.none { "/front/" in it })
        }

    @Test
    fun a_front_without_the_policy_route_answers_none() =
        runTest {
            val fleet = FleetEngine(front(), PolicyClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", POLICY_CONFIG) }
            advanceUntilIdle()
            assertNull(fleet.policy("p1"))
        }
}
