package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamHostMode

/*
 * Testing an address before it is saved (Flutter team_host_form.dart `TeamHostProbe`, GasCityProbe over the network).
 * The same probe the live page uses, so "the form said yes" and "the page can read it" are one check. No credential is
 * read, stored or sent; the address rule runs before any request.
 */

/** What the form learned from one address (Dart `ProbeVerdict`). */
public sealed interface ComputerTeamTest {
    /** A team answers. [city] is the one asked for, else the one the host reports; [front] means this phone may write. */
    public data class Found(val city: String, val front: Boolean) : ComputerTeamTest

    /** No team to use at this address, and why. */
    public data class NotFound(val failure: FleetFailure) : ComputerTeamTest
}

/** Probes [url] once (never throws except for cancellation); [city] null asks for whichever city the host reports. */
public suspend fun testComputerTeam(transports: FleetTransports, url: String, city: String?): ComputerTeamTest =
    when (val verdict = FleetProbe.probe(transports, url, city?.takeIf { it.isNotEmpty() }, TeamHostMode.Computer)) {
        is FleetVerdict.Found -> ComputerTeamTest.Found(verdict.city, front = verdict.capabilities == FleetCapabilities.FRONT)
        is FleetVerdict.Refused -> ComputerTeamTest.NotFound(verdict.failure)
    }
